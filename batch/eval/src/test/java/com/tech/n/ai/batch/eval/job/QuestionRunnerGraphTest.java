package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.api.chatbot.chain.InputInterpretationChain;
import com.tech.n.ai.api.chatbot.chain.ResultRefinementChain;
import com.tech.n.ai.api.chatbot.service.IntentClassificationService;
import com.tech.n.ai.api.chatbot.service.RetrievalService;
import com.tech.n.ai.api.chatbot.service.SearchOptionsFactory;
import com.tech.n.ai.api.chatbot.service.TokenService;
import com.tech.n.ai.api.chatbot.service.dto.GraphSearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.Intent;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalOutcome;
import com.tech.n.ai.api.chatbot.service.dto.RetrievalPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchContext;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchQuery;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItem;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;
import com.tech.n.ai.batch.eval.report.EvalReport;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * QuestionRunner가 그래프 관련 항목을 리포트에 어떻게 채우는지 본다.
 *
 * 그래프를 켠 실행과 끈 실행을 나란히 두고, 끈 실행이 기준선과 같은 수치를 내는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestionRunner 그래프 항목 단위 테스트")
class QuestionRunnerGraphTest {

    @Mock
    private IntentClassificationService intentService;

    @Mock
    private InputInterpretationChain inputChain;

    @Mock
    private SearchOptionsFactory searchOptionsFactory;

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private ResultRefinementChain refinementChain;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private QuestionRunner questionRunner;

    private GoldenSetItem multiHopItem() {
        return new GoldenSetItem(
            "G036", "GPT-4o를 쓰는 프레임워크는?",
            GoldenSetItemType.MULTI_HOP, List.of("ext-1", "ext-2"), null, "테스트용");
    }

    private SearchResult result(String externalId, String documentId, Double score) {
        return SearchResult.builder()
            .documentId(documentId)
            .text(externalId + " 본문")
            .score(score)
            .collectionType("EMERGING_TECH")
            .metadata(new Document("external_id", externalId))
            .build();
    }

    private void givenRetrieval(RetrievalOutcome retrieval) {
        when(intentService.classifyIntent(anyString())).thenReturn(Intent.RAG_REQUIRED);
        when(inputChain.interpret(anyString()))
            .thenReturn(SearchQuery.builder().query("GPT-4o 프레임워크").context(new SearchContext()).build());
        when(searchOptionsFactory.create(any())).thenReturn(SearchOptions.builder().build());
        when(retrievalService.retrieve(anyString(), anyLong(), any())).thenReturn(retrieval);
        // 정제 체인은 순서를 그대로 흘려보낸다. 합친 목록과 체인 결과를 곧장 견줄 수 있게 한다.
        when(refinementChain.refine(anyString(), any(), anyBoolean(), anyBoolean()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(tokenService.estimateTokens(anyString())).thenReturn(10);
    }

    private SearchOutcome vectorOutcome(List<SearchResult> results) {
        return SearchOutcome.builder()
            .path(SearchPath.HYBRID)
            .candidates(results)
            .recencyQueryFailed(false)
            .results(results)
            .build();
    }

    @Nested
    @DisplayName("run - 그래프를 켠 실행")
    class GraphEnabled {

        private RetrievalOutcome retrievalWithGraph() {
            SearchResult vectorHit = result("ext-1", "doc1", 0.8);
            SearchResult graphHit = result("ext-2", "doc2", 0.4);
            GraphSearchOutcome graph = new GraphSearchOutcome(
                true,
                List.of(graphHit),
                List.of("Model|gpt-4o"),
                List.of("Framework|langchain4j"),
                List.of("ext-2"),
                true,
                97L);
            return new RetrievalOutcome(
                vectorOutcome(List.of(vectorHit)), graph, List.of(vectorHit, graphHit),
                RetrievalPath.BOTH, 210L, 97L);
        }

        @Test
        @DisplayName("합친 목록을 순서대로 적고 어느 쪽이 물고 왔는지 표시한다")
        void writesMergedOutputWithSource() {
            // Given
            givenRetrieval(retrievalWithGraph());

            // When
            EvalReport.Question question = questionRunner.run(multiHopItem()).question();

            // Then
            assertThat(question.mergedOutput())
                .extracting(EvalReport.MergedItem::externalId,
                    EvalReport.MergedItem::rank,
                    EvalReport.MergedItem::source)
                .containsExactly(
                    tuple("ext-1", 1, "VECTOR"),
                    tuple("ext-2", 2, "GRAPH"));
        }

        @Test
        @DisplayName("그래프가 더한 문서까지 세어 byMergedRank를 채점한다")
        void scoresByMergedRank() {
            // Given: 기대 근거 두 건 중 하나는 그래프만 물고 왔다
            givenRetrieval(retrievalWithGraph());

            // When
            EvalReport.Question question = questionRunner.run(multiHopItem()).question();

            // Then
            assertThat(question.metrics().byVectorRank().recallAtK().get(5)).isEqualTo(0.5);
            assertThat(question.metrics().byMergedRank().recallAtK().get(5)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("그래프 블록과 경로, 그래프 지연을 그대로 옮긴다")
        void writesGraphBlockAndPath() {
            // Given
            givenRetrieval(retrievalWithGraph());

            // When
            EvalReport.Question question = questionRunner.run(multiHopItem()).question();

            // Then
            assertThat(question.retrievalPath()).isEqualTo("BOTH");
            assertThat(question.graph().enabled()).isTrue();
            assertThat(question.graph().seedKeys()).containsExactly("Model|gpt-4o");
            assertThat(question.graph().expandedKeys()).containsExactly("Framework|langchain4j");
            assertThat(question.graph().externalIds()).containsExactly("ext-2");
            assertThat(question.graph().documentCount()).isEqualTo(1);
            assertThat(question.graph().capped()).isTrue();
            assertThat(question.graph().latencyMs()).isEqualTo(97L);
            // 검색 지연은 기준선과 같은 뜻(벡터 검색만)이고 그래프 지연은 따로 적는다
            assertThat(question.latencyMs().search()).isEqualTo(210L);
            assertThat(question.latencyMs().graph()).isEqualTo(97L);
        }
    }

    @Nested
    @DisplayName("run - 그래프를 끈 실행")
    class GraphDisabled {

        private RetrievalOutcome retrievalWithoutGraph() {
            List<SearchResult> vectorResults = List.of(result("ext-1", "doc1", 0.8));
            return new RetrievalOutcome(
                vectorOutcome(vectorResults), GraphSearchOutcome.disabled(), vectorResults,
                RetrievalPath.VECTOR_ONLY, 210L, 0L);
        }

        @Test
        @DisplayName("그래프 블록은 키를 남긴 채 꺼진 상태로 채운다")
        void keepsGraphBlockFilledWithDisabledValues() {
            // Given
            givenRetrieval(retrievalWithoutGraph());

            // When
            EvalReport.Question question = questionRunner.run(multiHopItem()).question();

            // Then
            assertThat(question.graph()).isNotNull();
            assertThat(question.graph().enabled()).isFalse();
            assertThat(question.graph().seedKeys()).isEmpty();
            assertThat(question.graph().expandedKeys()).isEmpty();
            assertThat(question.graph().externalIds()).isEmpty();
            assertThat(question.graph().documentCount()).isZero();
            assertThat(question.graph().capped()).isFalse();
            assertThat(question.graph().latencyMs()).isZero();
            assertThat(question.retrievalPath()).isEqualTo("VECTOR_ONLY");
        }

        @Test
        @DisplayName("byMergedRank가 byChainOutput과 같아진다")
        void mergedRankEqualsChainOutput() {
            // Given: 그래프가 아무것도 더하지 않으면 합친 목록은 벡터 결과 그대로다
            givenRetrieval(retrievalWithoutGraph());

            // When
            EvalReport.Question question = questionRunner.run(multiHopItem()).question();

            // Then
            assertThat(question.mergedOutput())
                .extracting(EvalReport.MergedItem::externalId)
                .containsExactlyElementsOf(question.chainOutput().stream()
                    .map(EvalReport.ChainOutputItem::externalId)
                    .toList());
            assertThat(question.metrics().byMergedRank())
                .isEqualTo(question.metrics().byChainOutput());
            assertThat(question.mergedOutput())
                .extracting(EvalReport.MergedItem::source)
                .containsOnly("VECTOR");
        }
    }
}
