package com.tech.n.ai.batch.eval.scoring;

import com.tech.n.ai.batch.eval.goldenset.GoldenSetItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * AggregateScorer 단위 테스트
 *
 * Atlas·OpenAI 없이 POJO 입력만으로 검증한다.
 */
@DisplayName("AggregateScorer 단위 테스트")
class AggregateScorerTest {

    private static final List<Integer> K_VALUES = List.of(1, 5);

    @Nested
    @DisplayName("aggregate - 제외 버킷")
    class Excluded {

        @Test
        @DisplayName("판정 순서대로 한 질문이 한 버킷에만 들어간다")
        void bucketsAreMutuallyExclusive() {
            // Given: fallback이면서 검색도 실패한 질문은 fallback 쪽으로만 센다
            List<QuestionOutcome> outcomes = List.of(
                outcome("Q1", GoldenSetItemType.SINGLE_FACT, false, true, true, true, List.of(), Set.of("a")),
                outcome("Q2", GoldenSetItemType.SINGLE_FACT, true, true, true, true, List.of(), Set.of("a")),
                outcome("Q3", GoldenSetItemType.SINGLE_FACT, true, false, true, true, List.of(), Set.of("a")),
                outcome("Q4", GoldenSetItemType.NO_EVIDENCE, true, false, false, true, List.of(), Set.of())
            );

            // When
            AggregateMetrics metrics = AggregateScorer.aggregate(outcomes, K_VALUES);

            // Then
            assertThat(metrics.excluded().intentNotRag()).isEqualTo(1);
            assertThat(metrics.excluded().fallbackPath()).isEqualTo(1);
            assertThat(metrics.excluded().searchFailed()).isEqualTo(1);
            assertThat(metrics.excluded().noEvidenceType()).isEqualTo(1);
            assertThat(metrics.totalCount()).isEqualTo(4);
            assertThat(metrics.scoredCount()).isZero();
        }

        @Test
        @DisplayName("근거 없음 유형은 후보가 비었는지로 맞고 틀림을 가른다")
        void noEvidenceCorrectness() {
            // Given
            List<QuestionOutcome> outcomes = List.of(
                outcome("N1", GoldenSetItemType.NO_EVIDENCE, true, false, false, true, List.of(), Set.of()),
                outcome("N2", GoldenSetItemType.NO_EVIDENCE, true, false, false, false, List.of("x"), Set.of())
            );

            // When
            AggregateMetrics metrics = AggregateScorer.aggregate(outcomes, K_VALUES);

            // Then
            assertThat(metrics.noEvidence().total()).isEqualTo(2);
            assertThat(metrics.noEvidence().correctlyEmpty()).isEqualTo(1);
            assertThat(metrics.noEvidence().wronglyNonEmpty()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("aggregate - 채점 대상 평균")
    class ScoredAverage {

        @Test
        @DisplayName("채점 대상만 단순 평균한다")
        void averagesOnlyScoredQuestions() {
            // Given: 채점 대상 2건(recall 1.0, 0.0) + 제외 1건
            List<QuestionOutcome> outcomes = List.of(
                outcome("Q1", GoldenSetItemType.SINGLE_FACT, true, false, false, false,
                    List.of("a", "b"), Set.of("a")),
                outcome("Q2", GoldenSetItemType.SINGLE_FACT, true, false, false, false,
                    List.of("x", "y"), Set.of("z")),
                outcome("Q3", GoldenSetItemType.SINGLE_FACT, false, false, false, false,
                    List.of("a"), Set.of("a"))
            );

            // When
            AggregateMetrics metrics = AggregateScorer.aggregate(outcomes, K_VALUES);

            // Then
            assertThat(metrics.scoredCount()).isEqualTo(2);
            assertThat(metrics.recallAtK().get(5)).isEqualTo(0.5);
            assertThat(metrics.hitRateAtK().get(5)).isEqualTo(0.5);
            assertThat(metrics.mrr()).isCloseTo(0.5, within(0.0001));
            assertThat(metrics.zeroHitQuestionCount()).isEqualTo(1);
            assertThat(metrics.falsePositiveAtK().get(5)).isEqualTo(1.5);
            assertThat(metrics.scoredCountByType()).containsEntry(GoldenSetItemType.SINGLE_FACT, 2);
        }

        @Test
        @DisplayName("채점 대상이 없으면 평균은 0")
        void noScoredQuestions_averagesAreZero() {
            // Given
            List<QuestionOutcome> outcomes = List.of(
                outcome("Q1", GoldenSetItemType.SINGLE_FACT, false, false, false, false, List.of(), Set.of("a"))
            );

            // When
            AggregateMetrics metrics = AggregateScorer.aggregate(outcomes, K_VALUES);

            // Then
            assertThat(metrics.recallAtK().get(5)).isEqualTo(0.0);
            assertThat(metrics.mrr()).isEqualTo(0.0);
            assertThat(metrics.scoredCount()).isZero();
        }
    }

    @Nested
    @DisplayName("aggregate - 최신 문서 적중률")
    class RecencyLatestHit {

        @Test
        @DisplayName("최신성 질문에서 상위 5건에 최신 문서가 들어온 비율")
        void hitRateAtTop5() {
            // Given: 최신 문서가 3위인 질문과 6위인 질문
            List<QuestionOutcome> outcomes = List.of(
                recencyOutcome("R1", List.of("a", "b", "latest1"), "latest1"),
                recencyOutcome("R2", List.of("a", "b", "c", "d", "e", "latest2"), "latest2")
            );

            // When
            AggregateMetrics metrics = AggregateScorer.aggregate(outcomes, K_VALUES);

            // Then
            assertThat(metrics.recencyLatestHitRateAt5()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("최신성 질문이 없으면 null")
        void noRecencyQuestion_isNull() {
            // Given
            List<QuestionOutcome> outcomes = List.of(
                outcome("Q1", GoldenSetItemType.SINGLE_FACT, true, false, false, false,
                    List.of("a"), Set.of("a"))
            );

            // When
            AggregateMetrics metrics = AggregateScorer.aggregate(outcomes, K_VALUES);

            // Then
            assertThat(metrics.recencyLatestHitRateAt5()).isNull();
        }
    }

    private QuestionOutcome outcome(String id, GoldenSetItemType type, boolean intentRagRequired,
                                     boolean fallbackPath, boolean searchFailed, boolean candidatesEmpty,
                                     List<String> rankedExternalIds, Set<String> expectedExternalIds) {
        return new QuestionOutcome(id, type, intentRagRequired, fallbackPath, searchFailed,
            candidatesEmpty, rankedExternalIds, expectedExternalIds, null);
    }

    private QuestionOutcome recencyOutcome(String id, List<String> rankedExternalIds, String latestExternalId) {
        return new QuestionOutcome(id, GoldenSetItemType.RECENCY, true, false, false, false,
            rankedExternalIds, Set.of(latestExternalId), latestExternalId);
    }
}
