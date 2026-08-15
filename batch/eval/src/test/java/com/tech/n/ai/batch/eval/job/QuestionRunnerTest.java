package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.api.chatbot.chain.InputInterpretationChain;
import com.tech.n.ai.api.chatbot.chain.ResultRefinementChain;
import com.tech.n.ai.api.chatbot.service.IntentClassificationService;
import com.tech.n.ai.api.chatbot.service.SearchOptionsFactory;
import com.tech.n.ai.api.chatbot.service.TokenService;
import com.tech.n.ai.api.chatbot.service.VectorSearchService;
import com.tech.n.ai.api.chatbot.service.dto.Intent;
import com.tech.n.ai.api.chatbot.service.dto.SearchContext;
import com.tech.n.ai.api.chatbot.service.dto.SearchOptions;
import com.tech.n.ai.api.chatbot.service.dto.SearchOutcome;
import com.tech.n.ai.api.chatbot.service.dto.SearchPath;
import com.tech.n.ai.api.chatbot.service.dto.SearchQuery;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItem;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;
import com.tech.n.ai.batch.eval.report.EvalReport;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * QuestionRunner 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QuestionRunner 단위 테스트")
class QuestionRunnerTest {

    @Mock
    private IntentClassificationService intentService;

    @Mock
    private InputInterpretationChain inputChain;

    @Mock
    private SearchOptionsFactory searchOptionsFactory;

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private ResultRefinementChain refinementChain;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private QuestionRunner questionRunner;

    private GoldenSetItem noEvidenceItem() {
        return new GoldenSetItem(
            "G999", "Mistral의 Le Chat 모델 업데이트 내용을 알려줘",
            GoldenSetItemType.NO_EVIDENCE, List.of(), null, "테스트용");
    }

    private void givenSearchPath(SearchPath path) {
        when(intentService.classifyIntent(anyString())).thenReturn(Intent.RAG_REQUIRED);
        when(inputChain.interpret(anyString()))
            .thenReturn(SearchQuery.builder().query("Mistral Le Chat").context(new SearchContext()).build());
        when(searchOptionsFactory.create(any())).thenReturn(SearchOptions.builder().build());
        when(vectorSearchService.search(anyString(), anyLong(), any()))
            .thenReturn(SearchOutcome.builder()
                .path(path)
                .candidates(List.of())
                .recencyQueryFailed(false)
                .results(List.of())
                .build());
        when(refinementChain.refine(anyString(), any(), anyBoolean(), anyBoolean()))
            .thenReturn(List.of());
        when(tokenService.estimateTokens(anyString())).thenReturn(10);
    }

    @Nested
    @DisplayName("run - 근거 없음 판정")
    class NoEvidenceBlock {

        @Test
        @DisplayName("하이브리드로 끝난 근거 없음 질문은 후보가 비면 맞힌 것으로 기록한다")
        void hybridPathFillsNoEvidenceBlock() {
            // Given
            givenSearchPath(SearchPath.HYBRID);

            // When
            EvalReport.Question question = questionRunner.run(noEvidenceItem()).question();

            // Then
            assertThat(question.excludedReason()).isEqualTo("NO_EVIDENCE_TYPE");
            assertThat(question.noEvidence()).isNotNull();
            assertThat(question.noEvidence().correct()).isTrue();
        }

        @Test
        @DisplayName("fallback으로 빠진 근거 없음 질문은 후보가 비어도 판정을 남기지 않는다")
        void fallbackPathLeavesNoEvidenceNull() {
            // Given — 하이브리드가 예외로 끝나면 candidates가 무조건 빈 리스트다
            givenSearchPath(SearchPath.HYBRID_FALLBACK_STANDARD);

            // When
            EvalReport.Question question = questionRunner.run(noEvidenceItem()).question();

            // Then
            assertThat(question.excludedReason()).isEqualTo("FALLBACK_PATH");
            assertThat(question.noEvidence()).isNull();
        }

        @Test
        @DisplayName("검색이 예외로 끝난 근거 없음 질문도 판정을 남기지 않는다")
        void searchFailedLeavesNoEvidenceNull() {
            // Given
            givenSearchPath(SearchPath.STANDARD_FAILED);

            // When
            EvalReport.Question question = questionRunner.run(noEvidenceItem()).question();

            // Then
            assertThat(question.excludedReason()).isEqualTo("SEARCH_FAILED");
            assertThat(question.noEvidence()).isNull();
        }
    }
}
