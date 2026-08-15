package com.tech.n.ai.batch.eval.scoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalScorer 단위 테스트
 *
 * Atlas·OpenAI 없이 POJO 입력만으로 검증한다.
 */
@DisplayName("RetrievalScorer 단위 테스트")
class RetrievalScorerTest {

    private static final List<Integer> K_VALUES = List.of(1, 3, 5);

    @Nested
    @DisplayName("score - recall")
    class Recall {

        @Test
        @DisplayName("상위 k에 들어온 기대 근거 수를 기대 근거 수로 나눈다")
        void recallIsHitsOverExpected() {
            // Given: 기대 근거 2건 중 1건이 2위, 나머지는 5위 밖
            List<String> ranked = List.of("a", "b", "c", "d", "e");
            Set<String> expected = Set.of("b", "z");

            // When
            RetrievalMetrics metrics = RetrievalScorer.score(ranked, expected, K_VALUES);

            // Then
            assertThat(metrics.recallAtK().get(1)).isEqualTo(0.0);
            assertThat(metrics.recallAtK().get(3)).isEqualTo(0.5);
            assertThat(metrics.recallAtK().get(5)).isEqualTo(0.5);
        }

        @Test
        @DisplayName("기대 근거가 없으면 recall은 0으로 둔다")
        void noExpected_recallIsZero() {
            // Given
            List<String> ranked = List.of("a", "b");
            Set<String> expected = Set.of();

            // When
            RetrievalMetrics metrics = RetrievalScorer.score(ranked, expected, K_VALUES);

            // Then
            assertThat(metrics.recallAtK().values()).allMatch(value -> value == 0.0);
            assertThat(metrics.firstHitRank()).isNull();
        }
    }

    @Nested
    @DisplayName("score - 적중률과 MRR")
    class HitAndMrr {

        @Test
        @DisplayName("첫 적중 순위의 역수가 reciprocalRank")
        void reciprocalRankIsInverseOfFirstHit() {
            // Given: 기대 근거가 3위에 처음 등장
            List<String> ranked = List.of("a", "b", "c");
            Set<String> expected = Set.of("c");

            // When
            RetrievalMetrics metrics = RetrievalScorer.score(ranked, expected, K_VALUES);

            // Then
            assertThat(metrics.firstHitRank()).isEqualTo(3);
            assertThat(metrics.reciprocalRank()).isEqualTo(1.0 / 3);
            assertThat(metrics.hitAtK().get(1)).isFalse();
            assertThat(metrics.hitAtK().get(3)).isTrue();
        }

        @Test
        @DisplayName("적중이 없으면 firstHitRank는 null, reciprocalRank는 0")
        void noHit_reciprocalRankIsZero() {
            // Given
            List<String> ranked = List.of("a", "b");
            Set<String> expected = Set.of("z");

            // When
            RetrievalMetrics metrics = RetrievalScorer.score(ranked, expected, K_VALUES);

            // Then
            assertThat(metrics.firstHitRank()).isNull();
            assertThat(metrics.reciprocalRank()).isEqualTo(0.0);
            assertThat(metrics.hitAtK().values()).allMatch(hit -> !hit);
        }
    }

    @Nested
    @DisplayName("score - 오검출")
    class FalsePositive {

        @Test
        @DisplayName("상위 k 중 기대 근거가 아닌 문서 수를 센다")
        void countsNonExpectedInTopK() {
            // Given
            List<String> ranked = List.of("a", "b", "c", "d", "e");
            Set<String> expected = Set.of("b");

            // When
            RetrievalMetrics metrics = RetrievalScorer.score(ranked, expected, K_VALUES);

            // Then
            assertThat(metrics.falsePositiveAtK().get(1)).isEqualTo(1);
            assertThat(metrics.falsePositiveAtK().get(3)).isEqualTo(2);
            assertThat(metrics.falsePositiveAtK().get(5)).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("score - k 상한")
    class KUpperBound {

        @Test
        @DisplayName("k가 리스트 길이보다 크면 리스트 전체로 자르고 키는 남긴다")
        void kLargerThanList_usesWholeList() {
            // Given: 결과는 2건인데 k는 1·3·5
            List<String> ranked = List.of("a", "b");
            Set<String> expected = Set.of("b");

            // When
            RetrievalMetrics metrics = RetrievalScorer.score(ranked, expected, K_VALUES);

            // Then: 예외 없이 계산되고 k 키가 모두 남는다
            assertThat(metrics.recallAtK()).containsOnlyKeys(1, 3, 5);
            assertThat(metrics.recallAtK().get(3)).isEqualTo(1.0);
            assertThat(metrics.recallAtK().get(5)).isEqualTo(1.0);
            assertThat(metrics.falsePositiveAtK().get(5)).isEqualTo(1);
        }

        @Test
        @DisplayName("결과가 비어도 예외 없이 0으로 채운다")
        void emptyRanked_returnsZeros() {
            // Given
            List<String> ranked = List.of();
            Set<String> expected = Set.of("a");

            // When
            RetrievalMetrics metrics = RetrievalScorer.score(ranked, expected, K_VALUES);

            // Then
            assertThat(metrics.recallAtK().values()).allMatch(value -> value == 0.0);
            assertThat(metrics.falsePositiveAtK().values()).allMatch(count -> count == 0);
            assertThat(metrics.firstHitRank()).isNull();
        }
    }
}
