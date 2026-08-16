package com.tech.n.ai.batch.graph.job;

import com.tech.n.ai.batch.graph.extract.GraphExtractor;
import com.tech.n.ai.batch.graph.extract.GraphTokenUsageRecorder;
import com.tech.n.ai.batch.graph.report.GraphBuildReport;
import com.tech.n.ai.batch.graph.report.GraphBuildReportWriter;
import com.tech.n.ai.batch.graph.write.GraphWriter;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * GraphBuildTasklet 단위 테스트
 *
 * 추출기·저장기·리포트 작성기를 전부 목으로 두므로 OpenAI와 Atlas에 붙지 않는다.
 * @Value를 생성자 파라미터로 받게 해 둔 덕에 Spring 컨텍스트 없이 new로 만든다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GraphBuildTasklet 단위 테스트")
class GraphBuildTaskletTest {

    private static final Document SOURCE = Document.from("본문");
    private static final long PUBLISHED_COUNT = 615L;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private GraphExtractor graphExtractor;

    @Mock
    private GraphWriter graphWriter;

    @Mock
    private GraphTokenUsageRecorder tokenUsageRecorder;

    @Mock
    private GraphBuildReportWriter reportWriter;

    private GraphBuildTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new GraphBuildTasklet(
            mongoTemplate, graphExtractor, graphWriter, tokenUsageRecorder, reportWriter,
            20, "recent", "title-summary", "gpt-4o-mini", 0.15, 0.60);

        when(tokenUsageRecorder.snapshot())
            .thenReturn(new GraphTokenUsageRecorder.Snapshot(0, 0, 0));
        when(mongoTemplate.count(any(Query.class), eq(EmergingTechDocument.class)))
            .thenReturn(PUBLISHED_COUNT);
    }

    @Nested
    @DisplayName("execute - 원본 컬렉션 취급")
    class SourceCollection {

        @Test
        @DisplayName("emerging_techs는 세고 읽기만 한다")
        void readsOnly() {
            // Given
            givenDocuments(document("ext-1", "첫 문서"));
            when(graphExtractor.extract(anyString())).thenReturn(Optional.empty());

            // When
            tasklet.execute(null, null);

            // Then
            verify(mongoTemplate).count(any(Query.class), eq(EmergingTechDocument.class));
            verify(mongoTemplate).find(any(Query.class), eq(EmergingTechDocument.class));
            verifyNoMoreInteractions(mongoTemplate);
        }

        @Test
        @DisplayName("PUBLISHED 건수를 실행 시점에 다시 세서 리포트에 담는다")
        void recountsPublishedDocuments() {
            // Given
            givenDocuments(document("ext-1", "첫 문서"));
            when(graphExtractor.extract(anyString())).thenReturn(Optional.empty());

            // When
            tasklet.execute(null, null);

            // Then
            assertThat(capturedReport().corpus().publishedDocumentCount()).isEqualTo(PUBLISHED_COUNT);
        }
    }

    @Nested
    @DisplayName("execute - 화이트리스트")
    class Whitelist {

        @Test
        @DisplayName("목록 밖 타입은 저장하지 않고 건수만 센다")
        void rejectedTypesAreCountedNotSaved() {
            // Given
            GraphNode company = GraphNode.from("OpenAI", "Company");
            GraphNode person = GraphNode.from("Sam Altman", "Person");
            GraphNode model = GraphNode.from("GPT-4o", "Model");
            GraphDocument extracted = GraphDocument.from(
                Set.of(company, person, model),
                Set.of(GraphEdge.from(company, model, "RELEASED")),
                SOURCE);

            givenDocuments(document("ext-1", "첫 문서"));
            when(graphExtractor.extract(anyString())).thenReturn(Optional.of(extracted));

            // When
            tasklet.execute(null, null);

            // Then
            verify(graphWriter).upsertNode("Company|openai", GraphNodeType.COMPANY, "OpenAI", "ext-1");
            verify(graphWriter).upsertNode("Model|gpt-4o", GraphNodeType.MODEL, "GPT-4o", "ext-1");
            verify(graphWriter, never()).upsertNode(
                eq("Person|sam altman"), any(), anyString(), anyString());

            GraphBuildReport report = capturedReport();
            assertThat(report.rejectedNodeTypes()).containsEntry("Person", 1);
            assertThat(report.documents()).singleElement()
                .satisfies(row -> {
                    // provider로 만든 Company 노드 1개 + 추출 노드 2개
                    assertThat(row.nodeCount()).isEqualTo(3);
                    assertThat(row.edgeCount()).isEqualTo(1);
                });
        }
    }

    @Nested
    @DisplayName("execute - provider로 만드는 Company 노드")
    class ProviderNode {

        @Test
        @DisplayName("추출 결과가 비어도 provider로 Company 노드를 만든다")
        void alwaysUpsertsCompanyNodeFromProvider() {
            // Given
            givenDocuments(document("ext-1", "첫 문서"));
            when(graphExtractor.extract(anyString())).thenReturn(Optional.empty());

            // When
            tasklet.execute(null, null);

            // Then
            verify(graphWriter).upsertNode("Company|openai", GraphNodeType.COMPANY, "OPENAI", "ext-1");
            verify(graphWriter, never()).upsertEdge(anyString(), any(), anyString(), anyString(), anyString());
            assertThat(capturedReport().documents()).singleElement()
                .satisfies(row -> assertThat(row.nodes()).containsExactly("Company|openai (OPENAI)"));
        }

        @Test
        @DisplayName("provider가 비면 Company 노드를 만들지 않는다")
        void skipsWhenProviderIsBlank() {
            // Given
            EmergingTechDocument blankProvider = document("ext-1", "첫 문서");
            blankProvider.setProvider("   ");
            givenDocuments(blankProvider);
            when(graphExtractor.extract(anyString())).thenReturn(Optional.empty());

            // When
            tasklet.execute(null, null);

            // Then
            verify(graphWriter, never()).upsertNode(anyString(), any(), anyString(), anyString());
            assertThat(capturedReport().documents()).singleElement()
                .satisfies(row -> assertThat(row.nodes()).isEmpty());
        }
    }

    @Nested
    @DisplayName("execute - 표본 고르는 방식")
    class SelectMode {

        @Test
        @DisplayName("spread는 provider·update_type 묶음을 돌아가며 골라 쏠림을 없앤다")
        void spreadPicksAcrossGroups() {
            // Given: 최신순 그대로 3건을 자르면 OPENAI/BLOG_POST만 잡힌다
            tasklet = new GraphBuildTasklet(
                mongoTemplate, graphExtractor, graphWriter, tokenUsageRecorder, reportWriter,
                3, "spread", "title-summary", "gpt-4o-mini", 0.15, 0.60);
            givenDocuments(
                document("ext-1", "첫째", "OPENAI", "BLOG_POST"),
                document("ext-2", "둘째", "OPENAI", "BLOG_POST"),
                document("ext-3", "셋째", "OPENAI", "BLOG_POST"),
                document("ext-4", "넷째", "ANTHROPIC", "BLOG_POST"),
                document("ext-5", "다섯째", "OPENAI", "MODEL_RELEASE"));
            when(graphExtractor.extract(anyString())).thenReturn(Optional.empty());

            // When
            tasklet.execute(null, null);

            // Then: 묶음마다 한 건씩, 정렬된 원본 순서를 그대로 지킨다
            assertThat(capturedReport().documents())
                .extracting(GraphBuildReport.DocumentRow::externalId)
                .containsExactly("ext-1", "ext-4", "ext-5");
        }
    }

    @Nested
    @DisplayName("execute - 문서 하나가 어긋날 때")
    class PerDocumentFailure {

        @Test
        @DisplayName("문서 하나가 실패해도 다음 문서로 간다")
        void continuesAfterFailure() {
            // Given
            givenDocuments(document("ext-1", "첫 문서"), document("ext-2", "둘째 문서"));
            when(graphExtractor.extract("첫 문서")).thenThrow(new RuntimeException("추출 호출 실패"));
            when(graphExtractor.extract("둘째 문서")).thenReturn(Optional.empty());

            // When
            RepeatStatus status = tasklet.execute(null, null);

            // Then
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            GraphBuildReport report = capturedReport();
            assertThat(report.corpus().processedCount()).isEqualTo(2);
            assertThat(report.corpus().failedCount()).isEqualTo(1);
            assertThat(report.documents()).hasSize(2);
            assertThat(report.documents().get(0).failureReason()).contains("추출 호출 실패");
            assertThat(report.documents().get(1).failureReason()).isNull();
        }

        @Test
        @DisplayName("추출 결과가 없어도 죽지 않고 건수로 센다")
        void emptyExtractionIsNotFailure() {
            // Given: LLMGraphTransformer.transform()이 null을 돌려준 상황
            givenDocuments(document("ext-1", "첫 문서"));
            when(graphExtractor.extract(anyString())).thenReturn(Optional.empty());

            // When
            RepeatStatus status = tasklet.execute(null, null);

            // Then
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            GraphBuildReport report = capturedReport();
            assertThat(report.corpus().noExtractionCount()).isEqualTo(1);
            assertThat(report.corpus().failedCount()).isZero();
            // provider로 만드는 Company 노드 말고는 아무것도 쓰지 않는다
            verify(graphWriter, times(1)).upsertNode(anyString(), any(), anyString(), anyString());
            verify(graphWriter, never()).upsertEdge(anyString(), any(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("execute - 추출에 넣을 텍스트 고르기")
    class InputTextKind {

        @Test
        @DisplayName("embedding-text면 embedding_text를 넣는다")
        void usesEmbeddingText() {
            // Given
            GraphBuildTasklet embeddingTasklet = new GraphBuildTasklet(
                mongoTemplate, graphExtractor, graphWriter, tokenUsageRecorder, reportWriter,
                20, "recent", "embedding-text", "gpt-4o-mini", 0.15, 0.60);
            EmergingTechDocument document = document("ext-1", "제목");
            document.setEmbeddingText("OPENAI 제목 요약");
            givenDocuments(document);
            when(graphExtractor.extract(anyString())).thenReturn(Optional.empty());

            // When
            embeddingTasklet.execute(null, null);

            // Then: title이 아니라 embedding_text가 들어간다
            verify(graphExtractor).extract("OPENAI 제목 요약");
        }

        @Test
        @DisplayName("모르는 값이면 조용히 넘어가지 않고 멈춘다")
        void unknownKindFails() {
            // Given: 오타 하나로 기각한 방식으로 도는 일을 막는다
            GraphBuildTasklet typoTasklet = new GraphBuildTasklet(
                mongoTemplate, graphExtractor, graphWriter, tokenUsageRecorder, reportWriter,
                20, "recent", "embedding_text", "gpt-4o-mini", 0.15, 0.60);
            givenDocuments(document("ext-1", "본문"));

            // When & Then
            assertThatThrownBy(() -> typoTasklet.execute(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("graph.build.input-text");
        }
    }

    private void givenDocuments(EmergingTechDocument... documents) {
        when(mongoTemplate.find(any(Query.class), eq(EmergingTechDocument.class)))
            .thenReturn(List.of(documents));
    }

    private GraphBuildReport capturedReport() {
        ArgumentCaptor<GraphBuildReport> reportCaptor =
            ArgumentCaptor.forClass(GraphBuildReport.class);
        verify(reportWriter).write(reportCaptor.capture(), any(LocalDateTime.class));
        return reportCaptor.getValue();
    }

    private EmergingTechDocument document(String externalId, String title) {
        return document(externalId, title, "OPENAI", "MODEL_RELEASE");
    }

    private EmergingTechDocument document(String externalId, String title,
                                          String provider, String updateType) {
        EmergingTechDocument document = new EmergingTechDocument();
        document.setExternalId(externalId);
        document.setProvider(provider);
        document.setUpdateType(updateType);
        document.setTitle(title);
        return document;
    }
}
