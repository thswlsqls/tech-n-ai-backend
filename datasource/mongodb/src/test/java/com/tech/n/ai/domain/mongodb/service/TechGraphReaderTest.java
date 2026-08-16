package com.tech.n.ai.domain.mongodb.service;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.tech.n.ai.domain.mongodb.service.dto.GraphNodeMatch;
import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TechGraphReader 단위 테스트
 *
 * 질의 구조가 고정돼 있고 사용자 입력이 값 자리에만 들어가는지, 상한이 걸려 있는지,
 * 쓰기 스테이지가 없는지를 파이프라인 문서로 직접 확인한다. MongoDB에 붙지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TechGraphReader 단위 테스트")
class TechGraphReaderTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoCollection<Document> mongoCollection;

    @Mock
    private AggregateIterable<Document> aggregateIterable;

    @InjectMocks
    private TechGraphReader techGraphReader;

    private static final List<String> CANDIDATE_KEYS = List.of("Company|openai", "Model|gpt-4o");
    private static final List<String> NAME_LITERALS = List.of("v2.26.0");

    @Nested
    @DisplayName("buildPipeline - 질의 구조")
    class BuildPipeline {

        @Test
        @DisplayName("$match에 $in 배열과 정규식만 들어가고 $where·$function은 없다")
        void match_bindsUserInputAsValuesOnly() {
            // Given: 사용자 질문에서 나온 후보 키와 리터럴
            // When
            List<Document> pipeline = TechGraphReader.buildPipeline(
                CANDIDATE_KEYS, NAME_LITERALS, 20, 20);

            // Then: 첫 스테이지가 $match이고 조건은 $in 배열 하나와 정규식 하나뿐이다
            Document match = pipeline.get(0).get("$match", Document.class);
            List<?> conditions = match.getList("$or", Object.class);
            assertThat(conditions).hasSize(2);

            Document keyIn = (Document) conditions.get(0);
            assertThat(keyIn.get("key", Document.class).getList("$in", String.class))
                .isEqualTo(CANDIDATE_KEYS);

            Document keyRegex = (Document) conditions.get(1);
            assertThat(keyRegex.get("key")).isInstanceOf(BsonRegularExpression.class);

            // 파이프라인 어디에도 자바스크립트 실행 연산자가 없다
            String rendered = pipeline.toString();
            assertThat(rendered).doesNotContain("$where");
            assertThat(rendered).doesNotContain("$function");
            assertThat(rendered).doesNotContain("$accumulator");
        }

        @Test
        @DisplayName("후보 키가 없으면 $or에 정규식 조건만 남는다")
        void match_withoutCandidateKeys_hasRegexOnly() {
            // Given: 버전 리터럴만 있는 질문
            // When
            List<Document> pipeline = TechGraphReader.buildPipeline(List.of(), NAME_LITERALS, 20, 20);

            // Then
            Document match = pipeline.get(0).get("$match", Document.class);
            assertThat(match.getList("$or", Object.class)).hasSize(1);
        }

        @Test
        @DisplayName("결과 개수 상한($limit)이 시드와 엣지 양쪽에 걸려 있다")
        void limitStagesExist() {
            // Given
            int maxSeeds = 20;
            int maxEdgesPerSeed = 7;

            // When
            List<Document> pipeline = TechGraphReader.buildPipeline(
                CANDIDATE_KEYS, NAME_LITERALS, maxSeeds, maxEdgesPerSeed);

            // Then: 시드 상한
            assertThat(pipeline.get(1).get("$limit")).isEqualTo(maxSeeds);

            // Then: 엣지 상한 ($lookup 안쪽 파이프라인)
            Document edgeLookup = pipeline.get(3).get("$lookup", Document.class);
            List<?> edgePipeline = edgeLookup.getList("pipeline", Object.class);
            assertThat(((Document) edgePipeline.get(1)).get("$limit")).isEqualTo(maxEdgesPerSeed);
        }

        @Test
        @DisplayName("쓰기 스테이지($out·$merge)가 없다")
        void noWriteStages() {
            // Given
            // When
            List<Document> pipeline = TechGraphReader.buildPipeline(
                CANDIDATE_KEYS, NAME_LITERALS, 20, 20);

            // Then
            String rendered = pipeline.toString();
            assertThat(rendered).doesNotContain("$out");
            assertThat(rendered).doesNotContain("$merge");
        }

        @Test
        @DisplayName("Company 노드는 이웃 조회에서 빠진다")
        void hubNodeExcludedFromExpansion() {
            // Given
            // When
            List<Document> pipeline = TechGraphReader.buildPipeline(
                CANDIDATE_KEYS, NAME_LITERALS, 20, 20);

            // Then: 이웃 $lookup 안쪽에 type != Company 조건이 있다
            Document neighborLookup = pipeline.get(5).get("$lookup", Document.class);
            List<?> neighborPipeline = neighborLookup.getList("pipeline", Object.class);
            Document neighborMatch = ((Document) neighborPipeline.get(0)).get("$match", Document.class);
            assertThat(neighborMatch.get("type", Document.class).get("$ne")).isEqualTo("Company");
        }
    }

    @Nested
    @DisplayName("escapeLiteral - 정규식 메타문자 처리")
    class EscapeLiteral {

        @Test
        @DisplayName("메타문자 앞에 역슬래시를 붙인다")
        void escapesMetacharacters() {
            // Given: 정규식으로 읽히면 아무 문자나 맞아 버리는 입력
            // When
            String escaped = TechGraphReader.escapeLiteral("a.*b");

            // Then
            assertThat(escaped).isEqualTo("a\\.\\*b");
        }

        @Test
        @DisplayName("버전 번호의 점을 이스케이프한다")
        void escapesVersionDots() {
            // Given
            // When
            String escaped = TechGraphReader.escapeLiteral("v2.26.0");

            // Then
            assertThat(escaped).isEqualTo("v2\\.26\\.0");
        }

        @Test
        @DisplayName("\\E가 섞여 있어도 인용이 끊기지 않는다")
        void escapesBackslashItself() {
            // Given: Pattern.quote()를 썼다면 여기서 인용이 끊긴다
            // When
            String escaped = TechGraphReader.escapeLiteral("a\\Eb");

            // Then
            assertThat(escaped).isEqualTo("a\\\\Eb");
        }
    }

    @Nested
    @DisplayName("findMatches - 실행과 상한")
    class FindMatches {

        @Test
        @DisplayName("실행 시간 상한(maxTime)을 걸고 aggregate를 한 번만 부른다")
        void appliesMaxTime() {
            // Given
            setupAggregate(List.of(seedDocument()));

            // When
            techGraphReader.findMatches(CANDIDATE_KEYS, NAME_LITERALS, 20, 20, 2000L);

            // Then
            verify(mongoCollection, times(1)).aggregate(anyList());
            verify(aggregateIterable).maxTime(2000L, TimeUnit.MILLISECONDS);
        }

        @Test
        @DisplayName("시드와 이웃을 홉과 함께 한 목록으로 편다")
        void flattensSeedsAndNeighbors() {
            // Given
            setupAggregate(List.of(seedDocument()));

            // When
            List<GraphNodeMatch> matches =
                techGraphReader.findMatches(CANDIDATE_KEYS, NAME_LITERALS, 20, 20, 2000L);

            // Then
            assertThat(matches).hasSize(2);
            assertThat(matches.get(0).key()).isEqualTo("Company|openai");
            assertThat(matches.get(0).hop()).isZero();
            assertThat(matches.get(0).externalIds()).containsExactly("github:1");
            assertThat(matches.get(1).key()).isEqualTo("Release|openai python sdk v2.26.0");
            assertThat(matches.get(1).hop()).isEqualTo(1);
        }

        @Test
        @DisplayName("후보 키와 리터럴이 모두 비면 MongoDB를 부르지 않는다")
        void noSeeds_skipsQuery() {
            // Given: 뽑아낸 후보가 하나도 없는 질문
            // When
            List<GraphNodeMatch> matches =
                techGraphReader.findMatches(List.of(), List.of(), 20, 20, 2000L);

            // Then
            assertThat(matches).isEmpty();
            verify(mongoTemplate, times(0)).getCollection(any());
        }
    }

    private void setupAggregate(List<Document> documents) {
        when(mongoTemplate.getCollection(TechGraphReader.COLLECTION_NODES)).thenReturn(mongoCollection);
        when(mongoCollection.aggregate(anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.maxTime(anyLong(), any())).thenReturn(aggregateIterable);
        when(aggregateIterable.into(any())).thenReturn(new ArrayList<>(documents));
    }

    private Document seedDocument() {
        Document neighbor = new Document()
            .append("key", "Release|openai python sdk v2.26.0")
            .append("type", "Release")
            .append("name", "OpenAI Python SDK v2.26.0")
            .append("external_ids", List.of("github:1"));

        return new Document()
            .append("key", "Company|openai")
            .append("type", "Company")
            .append("name", "OpenAI")
            .append("external_ids", List.of("github:1"))
            .append("neighbors", List.of(neighbor));
    }
}
