package com.tech.n.ai.batch.eval.report;

import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;
import com.tech.n.ai.batch.eval.scoring.RankedCandidate;
import com.tech.n.ai.batch.eval.scoring.RetrievalMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalReportWriter 단위 테스트
 */
@DisplayName("EvalReportWriter 단위 테스트")
class EvalReportWriterTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("write - 파일 생성")
    class FileCreation {

        @Test
        @DisplayName("없는 디렉터리를 만들고 타임스탬프 이름으로 쓴다")
        void createsDirectoryAndTimestampedFile() {
            // Given
            Path reportDir = tempDir.resolve("eval-reports");
            EvalReportWriter writer = new EvalReportWriter(reportDir.toString());
            LocalDateTime executedAt = LocalDateTime.of(2026, 8, 15, 9, 30, 0);

            // When
            Path path = writer.write(report(), executedAt);

            // Then
            assertThat(path).exists();
            assertThat(path.getFileName()).hasToString("20260815093000.json");
        }
    }

    @Nested
    @DisplayName("write - 키 집합")
    class KeySet {

        @Test
        @DisplayName("최상위 키 집합이 고정이다")
        void topLevelKeysAreFixed() throws Exception {
            // Given
            EvalReportWriter writer = new EvalReportWriter(tempDir.toString());

            // When
            Path path = writer.write(report(), LocalDateTime.now());
            JsonNode root = OBJECT_MAPPER.readTree(Files.readString(path, UTF_8));

            // Then
            assertThat(root.propertyNames()).containsExactlyInAnyOrder(
                "schemaVersion", "executedAt", "goldenSetVersion",
                "config", "questions", "aggregate", "excluded", "answerQuality");
        }

        @Test
        @DisplayName("값이 없어도 키를 빼지 않고 null로 남긴다")
        void keepsKeysWithNullValues() throws Exception {
            // Given: 생성 모델을 부르지 않아 latencyMs.generation이 null
            EvalReportWriter writer = new EvalReportWriter(tempDir.toString());

            // When
            Path path = writer.write(report(), LocalDateTime.now());
            JsonNode root = OBJECT_MAPPER.readTree(Files.readString(path, UTF_8));
            JsonNode question = root.get("questions").get(0);

            // Then
            assertThat(question.get("latencyMs").has("generation")).isTrue();
            assertThat(question.get("latencyMs").get("generation").isNull()).isTrue();
            assertThat(question.has("noEvidence")).isTrue();
            assertThat(question.get("noEvidence").isNull()).isTrue();
        }

        @Test
        @DisplayName("질문 항목과 집계 모두 벡터 순위·융합 순위 두 기준을 담는다")
        void bothRankingBasesArePresent() throws Exception {
            // Given
            EvalReportWriter writer = new EvalReportWriter(tempDir.toString());

            // When
            Path path = writer.write(report(), LocalDateTime.now());
            JsonNode root = OBJECT_MAPPER.readTree(Files.readString(path, UTF_8));

            // Then
            JsonNode questionMetrics = root.get("questions").get(0).get("metrics");
            assertThat(questionMetrics.propertyNames())
                .containsExactlyInAnyOrder("byVectorRank", "byFusionRank", "byChainOutput");
            assertThat(root.get("aggregate").propertyNames())
                .containsExactlyInAnyOrder("byVectorRank", "byFusionRank", "byChainOutput");
            assertThat(questionMetrics.get("byVectorRank").get("recallAtK").get("5").asDouble())
                .isEqualTo(1.0);
            assertThat(root.get("aggregate").get("byFusionRank").get("recallAtK").get("5").asDouble())
                .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("write - 답변 품질 블록")
    class AnswerQualityBlock {

        @Test
        @DisplayName("검색 기준선 리포트는 키만 남기고 값을 비운다")
        void keepsNullAnswerQualityKey() throws Exception {
            // Given
            EvalReportWriter writer = new EvalReportWriter(tempDir.toString());

            // When
            Path path = writer.write(report(), LocalDateTime.now());
            JsonNode root = OBJECT_MAPPER.readTree(Files.readString(path, UTF_8));

            // Then
            assertThat(root.has("answerQuality")).isTrue();
            assertThat(root.get("answerQuality").isNull()).isTrue();
        }

        @Test
        @DisplayName("답변 품질 리포트는 두 축의 분모·통과 건수·통과 비율을 담는다")
        void writesBothAxes() throws Exception {
            // Given
            EvalReportWriter writer = new EvalReportWriter(tempDir.toString());

            // When
            Path path = writer.write(answerQualityReport(), LocalDateTime.now(), "answer-quality-");
            JsonNode answerQuality = OBJECT_MAPPER
                .readTree(Files.readString(path, UTF_8))
                .get("answerQuality");

            // Then
            assertThat(path.getFileName().toString()).startsWith("answer-quality-");
            for (String axis : List.of("groundedness", "answerRelevance")) {
                assertThat(answerQuality.get(axis).get("denominator").asInt()).isEqualTo(2);
                assertThat(answerQuality.get(axis).get("passCount").asInt()).isEqualTo(1);
                assertThat(answerQuality.get(axis).get("passRate").asDouble()).isEqualTo(0.5);
            }
            assertThat(answerQuality.get("questions").get(0).get("groundednessReason").asString())
                .isEqualTo("문서로 뒷받침된다");
        }

        @Test
        @DisplayName("answerQuality 하위 키 집합이 고정이다")
        void answerQualityKeysAreFixed() throws Exception {
            // Given
            EvalReportWriter writer = new EvalReportWriter(tempDir.toString());

            // When
            Path path = writer.write(answerQualityReport(), LocalDateTime.now(), "answer-quality-");
            JsonNode answerQuality = OBJECT_MAPPER
                .readTree(Files.readString(path, UTF_8))
                .get("answerQuality");

            // Then
            assertThat(answerQuality.propertyNames()).containsExactlyInAnyOrder(
                "judgeModelName", "judgeModelTemperature", "judgeCallLimit", "judgeCallCount",
                "judgeCallLimitReached", "questionLimit", "questions",
                "groundedness", "answerRelevance", "flip");
        }

        @Test
        @DisplayName("뒤집힘 측정을 안 해도 flip 하위 키를 빼지 않는다")
        void flipKeysArePresentWhenNotMeasured() throws Exception {
            // Given: 측정을 끈 실행이라 건수가 0이고 비율이 null이다
            EvalReportWriter writer = new EvalReportWriter(tempDir.toString());

            // When
            Path path = writer.write(answerQualityReport(), LocalDateTime.now(), "answer-quality-");
            JsonNode flip = OBJECT_MAPPER
                .readTree(Files.readString(path, UTF_8))
                .get("answerQuality").get("flip");

            // Then
            assertThat(flip.propertyNames())
                .containsExactlyInAnyOrder("sampledCount", "flippedCount", "flipRate");
            assertThat(flip.get("sampledCount").asInt()).isZero();
            assertThat(flip.get("flippedCount").asInt()).isZero();
            assertThat(flip.get("flipRate").isNull()).isTrue();
        }
    }

    private EvalReport answerQualityReport() {
        EvalReport base = report();
        EvalReport.AnswerQualityAxis axis = new EvalReport.AnswerQualityAxis(2, 1, 0.5, 0);
        EvalReport.AnswerQualityQuestion judged = new EvalReport.AnswerQualityQuestion(
            "SF-001", "답변 본문", 1, List.of("ext-1"),
            1, "문서로 뒷받침된다", 0, "질문과 다른 내용이다", null);

        return new EvalReport(
            base.schemaVersion(), base.executedAt(), base.goldenSetVersion(), base.config(),
            base.questions(), null, base.excluded(),
            new EvalReport.AnswerQuality(
                "gpt-4o", 0.0, 200, 4, false, 0, List.of(judged), axis, axis,
                new EvalReport.JudgeFlip(0, 0, null)));
    }

    private EvalReport report() {
        RetrievalMetrics metrics = new RetrievalMetrics(
            Map.of(1, 1.0, 5, 1.0),
            Map.of(1, true, 5, true),
            1.0,
            1,
            Map.of(1, 0, 5, 1));

        EvalReport.Question question = new EvalReport.Question(
            "SF-001",
            GoldenSetItemType.SINGLE_FACT,
            "OpenAI 임베딩 모델 차원은?",
            "RAG_REQUIRED",
            "HYBRID",
            false,
            true,
            null,
            null,
            new EvalReport.LatencyMs(120L, 15L, null),
            new EvalReport.Tokens(18, 0, 0),
            List.of("ext-1"),
            null,
            List.of(new RankedCandidate("ext-1", "doc1", 1, 1, 0.91, 0.88, true)),
            List.of(new EvalReport.ChainOutputItem("ext-1", "doc1", 1, 0.88, true)),
            new EvalReport.Metrics(metrics, metrics, metrics));

        EvalReport.AggregateBlock block = new EvalReport.AggregateBlock(
            1, 1,
            Map.of(1, 1.0, 5, 1.0),
            Map.of(1, 1.0, 5, 1.0),
            1.0,
            0,
            Map.of(1, 0.0, 5, 1.0),
            Map.of(GoldenSetItemType.SINGLE_FACT, 1),
            null,
            new EvalReport.NoEvidenceSummary(0, 0, 0));

        return new EvalReport(
            "1",
            "2026-08-15T09:30:00",
            "draft-schema-only",
            new EvalReport.Config(5, 0.7, 6, true, false, 0.7,
                "text-embedding-3-small", 1536, 1234L, "추정치", false),
            List.of(question),
            new EvalReport.Aggregate(block, block, block),
            new EvalReport.Excluded(0, 0, 0, 0),
            null);
    }
}
