package com.tech.n.ai.batch.eval.job;

import com.tech.n.ai.api.chatbot.chain.AnswerGenerationChain;
import com.tech.n.ai.api.chatbot.common.exception.TokenLimitExceededException;
import com.tech.n.ai.api.chatbot.service.PromptService;
import com.tech.n.ai.api.chatbot.service.TokenService;
import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import com.tech.n.ai.batch.eval.goldenset.GoldenSet;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItem;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;
import com.tech.n.ai.batch.eval.goldenset.GoldenSetLoader;
import com.tech.n.ai.batch.eval.judge.AnswerJudge;
import com.tech.n.ai.batch.eval.judge.JudgeVerdict;
import com.tech.n.ai.batch.eval.report.EvalConfigSnapshotFactory;
import com.tech.n.ai.batch.eval.report.EvalReport;
import com.tech.n.ai.batch.eval.report.EvalReportWriter;
import com.tech.n.ai.batch.eval.scoring.QuestionOutcome;
import com.tech.n.ai.batch.eval.scoring.RetrievalMetrics;
import org.bson.Document;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AnswerQualityTasklet 단위 테스트
 *
 * 실제 OpenAI·Atlas 호출은 하지 않는다. 협력자는 전부 목이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AnswerQualityTasklet 단위 테스트")
class AnswerQualityTaskletTest {

    private static final int MAX_CONTEXT_TOKENS = 3000;
    private static final String TOKEN_ESTIMATION = "OpenAiTokenCountEstimator (추정치, 실측 아님)";

    @Mock
    private GoldenSetLoader goldenSetLoader;

    @Mock
    private QuestionRunner questionRunner;

    @Mock
    private AnswerGenerationChain answerChain;

    @Mock
    private PromptService promptService;

    @Mock
    private TokenService tokenService;

    @Mock
    private AnswerJudge answerJudge;

    @Mock
    private EvalConfigSnapshotFactory configSnapshotFactory;

    @Mock
    private EvalReportWriter reportWriter;

    private final SearchResult evidence = SearchResult.builder()
        .documentId("doc1")
        .text("근거 문서 본문")
        .score(0.9)
        .metadata(new Document("external_id", "ext-1"))
        .build();

    @BeforeEach
    void setUp() {
        when(configSnapshotFactory.create(true)).thenReturn(new EvalReport.Config(
            5, 0.7, 6, true, false, 0.0,
            "text-embedding-3-small", 1536, 1234L, TOKEN_ESTIMATION, true));
        when(tokenService.truncateResults(any(), anyInt())).thenReturn(List.of(evidence));
        when(promptService.buildPrompt(anyString(), any())).thenReturn("PROMPT");
        when(answerChain.generate(anyString(), any())).thenReturn("답변 본문");
        when(tokenService.estimateTokens("PROMPT")).thenReturn(100);
        when(tokenService.estimateTokens("답변 본문")).thenReturn(20);
        givenVerdict(AnswerJudge.Axis.GROUNDEDNESS, new JudgeVerdict(1, "문서로 뒷받침된다", true));
        givenVerdict(AnswerJudge.Axis.ANSWER_RELEVANCE, new JudgeVerdict(0, "질문과 다른 내용이다", true));
    }

    private void givenVerdict(AnswerJudge.Axis axis, JudgeVerdict verdict) {
        when(answerJudge.judge(eq(axis), anyString(), anyString(), any())).thenReturn(verdict);
    }

    private AnswerQualityTasklet tasklet(int judgeCallLimit, int questionLimit, boolean measureJudgeFlip) {
        return new AnswerQualityTasklet(
            goldenSetLoader, questionRunner, answerChain, promptService, tokenService, answerJudge,
            configSnapshotFactory, reportWriter,
            judgeCallLimit, questionLimit, measureJudgeFlip, MAX_CONTEXT_TOKENS, "gpt-4o");
    }

    private GoldenSetItem item(String id, GoldenSetItemType type) {
        return new GoldenSetItem(id, "질문 " + id, type, List.of("ext-1"), null, "메모");
    }

    private void givenGoldenSet(GoldenSetItem... items) {
        when(goldenSetLoader.load()).thenReturn(new GoldenSet("2026-08-15.2", "emerging_techs", List.of(items)));
    }

    private void givenRun(GoldenSetItem item, String excludedReason) {
        when(questionRunner.run(item)).thenReturn(runResult(item, excludedReason));
    }

    private QuestionRunResult runResult(GoldenSetItem item, String excludedReason) {
        RetrievalMetrics metrics = new RetrievalMetrics(
            Map.of(1, 1.0), Map.of(1, true), 1.0, 1, Map.of(1, 0));

        EvalReport.Question question = new EvalReport.Question(
            item.id(), item.type(), item.question(), "RAG_REQUIRED", "HYBRID",
            false, excludedReason == null, excludedReason, null,
            new EvalReport.LatencyMs(120L, 15L, null),
            new EvalReport.Tokens(12, 0, 0),
            item.expectedExternalIds(), null, List.of(), List.of(),
            new EvalReport.Metrics(metrics, metrics, metrics));

        QuestionOutcome outcome = new QuestionOutcome(
            item.id(), item.type(), true, false, false, false,
            List.of("ext-1"), Set.of("ext-1"), null);

        return new QuestionRunResult(question, outcome, outcome, outcome, List.of(evidence));
    }

    private EvalReport captureReport() {
        ArgumentCaptor<EvalReport> captor = ArgumentCaptor.forClass(EvalReport.class);
        verify(reportWriter).write(captor.capture(), any(), eq("answer-quality-"));
        return captor.getValue();
    }

    @Nested
    @DisplayName("execute - 판정 결과 기록")
    class Verdicts {

        @Test
        @DisplayName("질문마다 두 축의 0/1 판정과 판정 근거를 남긴다")
        void recordsBothAxesWithReason() {
            // Given
            GoldenSetItem item = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(item);
            givenRun(item, null);

            // When
            RepeatStatus status = tasklet(200, 0, false).execute(null, null);

            // Then
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            EvalReport.AnswerQualityQuestion judged = captureReport().answerQuality().questions().get(0);
            assertThat(judged.id()).isEqualTo("SF-001");
            assertThat(judged.answer()).isEqualTo("답변 본문");
            assertThat(judged.groundedness()).isEqualTo(1);
            assertThat(judged.groundednessReason()).isEqualTo("문서로 뒷받침된다");
            assertThat(judged.answerRelevance()).isZero();
            assertThat(judged.answerRelevanceReason()).isEqualTo("질문과 다른 내용이다");
            assertThat(judged.skippedReason()).isNull();
        }

        @Test
        @DisplayName("축마다 분모·통과 건수·통과 비율을 따로 낸다")
        void aggregatesEachAxis() {
            // Given
            GoldenSetItem first = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            GoldenSetItem second = item("SF-002", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(first, second);
            givenRun(first, null);
            givenRun(second, null);

            // When
            tasklet(200, 0, false).execute(null, null);

            // Then
            EvalReport.AnswerQuality answerQuality = captureReport().answerQuality();
            assertThat(answerQuality.groundedness().denominator()).isEqualTo(2);
            assertThat(answerQuality.groundedness().passCount()).isEqualTo(2);
            assertThat(answerQuality.groundedness().passRate()).isEqualTo(1.0);
            assertThat(answerQuality.answerRelevance().denominator()).isEqualTo(2);
            assertThat(answerQuality.answerRelevance().passCount()).isZero();
            assertThat(answerQuality.answerRelevance().passRate()).isZero();
            assertThat(answerQuality.judgeCallCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("판정 응답을 읽지 못한 건은 분모에서 빼고 실패 건수로 센다")
        void countsParseFailureOutsideDenominator() {
            // Given
            GoldenSetItem item = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(item);
            givenRun(item, null);
            givenVerdict(AnswerJudge.Axis.GROUNDEDNESS, new JudgeVerdict(null, "JSON이 아니다", false));

            // When
            tasklet(200, 0, false).execute(null, null);

            // Then
            EvalReport.AnswerQualityAxis groundedness = captureReport().answerQuality().groundedness();
            assertThat(groundedness.denominator()).isZero();
            assertThat(groundedness.parseFailedCount()).isEqualTo(1);
            assertThat(groundedness.passRate()).isNull();
        }
    }

    @Nested
    @DisplayName("execute - 제외 질문")
    class Excluded {

        @Test
        @DisplayName("제외 사유가 있는 질문은 생성·판정을 건너뛰고 사유만 남긴다")
        void skipsExcludedQuestions() {
            // Given: 근거 없음 유형과 의도가 RAG가 아닌 질문
            GoldenSetItem noEvidence = item("NE-001", GoldenSetItemType.NO_EVIDENCE);
            GoldenSetItem notRag = item("NR-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(noEvidence, notRag);
            givenRun(noEvidence, "NO_EVIDENCE_TYPE");
            givenRun(notRag, "INTENT_NOT_RAG");

            // When
            tasklet(200, 0, false).execute(null, null);

            // Then
            EvalReport.AnswerQuality answerQuality = captureReport().answerQuality();
            assertThat(answerQuality.questions())
                .extracting(EvalReport.AnswerQualityQuestion::skippedReason)
                .containsExactly("NO_EVIDENCE_TYPE", "INTENT_NOT_RAG");
            assertThat(answerQuality.groundedness().denominator()).isZero();
            assertThat(answerQuality.answerRelevance().denominator()).isZero();
            assertThat(answerQuality.judgeCallCount()).isZero();
            verify(answerChain, times(0)).generate(anyString(), any());
        }

        @Test
        @DisplayName("답변 생성이 실패하면 두 축을 비우고 사유를 남긴다")
        void marksGenerationFailure() {
            // Given
            GoldenSetItem item = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(item);
            givenRun(item, null);
            when(answerChain.generate(anyString(), any()))
                .thenThrow(new TokenLimitExceededException("입력 토큰 초과"));

            // When
            RepeatStatus status = tasklet(200, 0, false).execute(null, null);

            // Then
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            EvalReport.AnswerQualityQuestion judged = captureReport().answerQuality().questions().get(0);
            assertThat(judged.skippedReason()).isEqualTo("GENERATION_FAILED");
            assertThat(judged.groundedness()).isNull();
            assertThat(judged.groundednessReason()).contains("입력 토큰 초과");
        }
    }

    @Nested
    @DisplayName("execute - 비용 상한")
    class CostLimit {

        @Test
        @DisplayName("판정 호출 상한에 걸리면 남은 질문에 사유를 남기고 리포트를 쓴다")
        void stopsAtJudgeCallLimit() {
            // Given: 상한 2회면 질문 한 건(두 축)만 판정할 수 있다
            GoldenSetItem first = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            GoldenSetItem second = item("SF-002", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(first, second);
            givenRun(first, null);
            givenRun(second, null);

            // When
            RepeatStatus status = tasklet(2, 0, false).execute(null, null);

            // Then
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            EvalReport.AnswerQuality answerQuality = captureReport().answerQuality();
            assertThat(answerQuality.judgeCallLimitReached()).isTrue();
            assertThat(answerQuality.judgeCallCount()).isEqualTo(2);
            assertThat(answerQuality.questions().get(1).skippedReason()).isEqualTo("JUDGE_CALL_LIMIT");
        }

        @Test
        @DisplayName("설정한 건수만큼만 골든셋 앞에서 잘라 실행한다")
        void runsOnlyFirstNQuestions() {
            // Given
            GoldenSetItem first = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            GoldenSetItem second = item("SF-002", GoldenSetItemType.SINGLE_FACT);
            GoldenSetItem third = item("SF-003", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(first, second, third);
            givenRun(first, null);

            // When
            tasklet(200, 1, false).execute(null, null);

            // Then
            verify(questionRunner).run(first);
            verify(questionRunner, times(0)).run(second);
            verify(questionRunner, times(0)).run(third);
            EvalReport report = captureReport();
            assertThat(report.questions()).hasSize(1);
            assertThat(report.answerQuality().questionLimit()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("execute - 리포트 구조")
    class ReportShape {

        @Test
        @DisplayName("채점 근거는 운영과 같은 방식으로 자른 검색 결과다")
        void usesTruncatedEvidence() {
            // Given
            GoldenSetItem item = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(item);
            givenRun(item, null);

            // When
            tasklet(200, 0, false).execute(null, null);

            // Then
            verify(tokenService).truncateResults(List.of(evidence), MAX_CONTEXT_TOKENS);
            EvalReport.AnswerQualityQuestion judged = captureReport().answerQuality().questions().get(0);
            assertThat(judged.evidenceCount()).isEqualTo(1);
            assertThat(judged.evidenceExternalIds()).containsExactly("ext-1");
        }

        @Test
        @DisplayName("토큰은 추정치임을 남기고 질문마다 세 값을 채운다")
        void fillsTokenFields() {
            // Given
            GoldenSetItem item = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(item);
            givenRun(item, null);

            // When
            tasklet(200, 0, false).execute(null, null);

            // Then
            EvalReport report = captureReport();
            assertThat(report.config().tokenEstimation()).isEqualTo(TOKEN_ESTIMATION);
            EvalReport.Tokens tokens = report.questions().get(0).tokens();
            assertThat(tokens.inputTokens()).isEqualTo(100);
            assertThat(tokens.outputTokens()).isEqualTo(20);
            assertThat(tokens.llmCallCount()).isEqualTo(1);
            assertThat(report.questions().get(0).latencyMs().generation()).isNotNull();
        }

        @Test
        @DisplayName("공통 필드는 그대로 쓰고 검색 집계는 비운다")
        void keepsSharedFields() {
            // Given
            GoldenSetItem item = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(item);
            givenRun(item, null);

            // When
            tasklet(200, 0, false).execute(null, null);

            // Then
            EvalReport report = captureReport();
            assertThat(report.schemaVersion()).isEqualTo(EvalReport.SCHEMA_VERSION);
            assertThat(report.goldenSetVersion()).isEqualTo("2026-08-15.2");
            assertThat(report.config().chatModelTemperature()).isZero();
            assertThat(report.aggregate()).isNull();
            assertThat(report.excluded()).isNotNull();
            assertThat(report.answerQuality().judgeModelName()).isEqualTo("gpt-4o");
            assertThat(report.answerQuality().judgeModelTemperature()).isZero();
        }
    }

    @Nested
    @DisplayName("execute - 판정 뒤집힘 측정")
    class JudgeFlip {

        @Test
        @DisplayName("켜면 같은 질문을 한 번 더 채점해 갈린 비율을 낸다")
        void measuresFlipRate() {
            // Given: 2차 채점에서 근거 기반성만 점수가 뒤집힌다
            GoldenSetItem item = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(item);
            givenRun(item, null);
            when(answerJudge.judge(eq(AnswerJudge.Axis.GROUNDEDNESS), anyString(), anyString(), any()))
                .thenReturn(new JudgeVerdict(1, "1차", true))
                .thenReturn(new JudgeVerdict(0, "2차", true));

            // When
            tasklet(200, 0, true).execute(null, null);

            // Then
            EvalReport.AnswerQuality answerQuality = captureReport().answerQuality();
            assertThat(answerQuality.flip().sampledCount()).isEqualTo(2);
            assertThat(answerQuality.flip().flippedCount()).isEqualTo(1);
            assertThat(answerQuality.flip().flipRate()).isEqualTo(0.5);
            // 집계 점수는 1차 결과 그대로다
            assertThat(answerQuality.groundedness().passCount()).isEqualTo(1);
            assertThat(answerQuality.judgeCallCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("끄면 뒤집힘 블록의 키는 남기고 값을 0으로 둔다")
        void keepsEmptyFlipBlockWhenDisabled() {
            // Given
            GoldenSetItem item = item("SF-001", GoldenSetItemType.SINGLE_FACT);
            givenGoldenSet(item);
            givenRun(item, null);

            // When
            tasklet(200, 0, false).execute(null, null);

            // Then: 설정에 따라 리포트 구조가 달라지면 두 실행을 비교할 수 없다
            EvalReport.JudgeFlip flip = captureReport().answerQuality().flip();
            assertThat(flip).isNotNull();
            assertThat(flip.sampledCount()).isZero();
            assertThat(flip.flippedCount()).isZero();
            assertThat(flip.flipRate()).isNull();
        }
    }
}
