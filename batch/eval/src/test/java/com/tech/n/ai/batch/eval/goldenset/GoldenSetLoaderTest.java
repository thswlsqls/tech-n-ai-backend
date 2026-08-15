package com.tech.n.ai.batch.eval.goldenset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GoldenSetLoader 단위 테스트
 */
@DisplayName("GoldenSetLoader 단위 테스트")
class GoldenSetLoaderTest {

    private GoldenSetLoader goldenSetLoader;

    @BeforeEach
    void setUp() {
        goldenSetLoader = new GoldenSetLoader();
    }

    @Nested
    @DisplayName("load - 형식")
    class Format {

        @Test
        @DisplayName("버전·컬렉션·질문 목록을 읽는다")
        void readsHeaderAndItems() {
            // When
            GoldenSet goldenSet = goldenSetLoader.load();

            // Then
            assertThat(goldenSet.version()).isNotBlank();
            assertThat(goldenSet.collection()).isEqualTo("emerging_techs");
            assertThat(goldenSet.items()).isNotEmpty();
        }

        @Test
        @DisplayName("모든 항목에 id·질문·유형이 있다")
        void everyItemHasIdQuestionType() {
            // When
            GoldenSet goldenSet = goldenSetLoader.load();

            // Then
            assertThat(goldenSet.items()).allSatisfy(item -> {
                assertThat(item.id()).isNotBlank();
                assertThat(item.question()).isNotBlank();
                assertThat(item.type()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("load - 유형 파싱")
    class TypeParsing {

        @Test
        @DisplayName("문자열이 GoldenSetItemType으로 변환된다")
        void parsesEnumValues() {
            // When
            GoldenSet goldenSet = goldenSetLoader.load();

            // Then
            assertThat(goldenSet.items())
                .extracting(GoldenSetItem::type)
                .contains(GoldenSetItemType.SINGLE_FACT, GoldenSetItemType.NO_EVIDENCE);
        }

        @Test
        @DisplayName("최신성 유형에는 latestExternalId가 있다")
        void recencyItemHasLatestExternalId() {
            // When
            GoldenSet goldenSet = goldenSetLoader.load();

            // Then
            assertThat(goldenSet.items())
                .filteredOn(item -> item.type() == GoldenSetItemType.RECENCY)
                .allSatisfy(item -> assertThat(item.latestExternalId()).isNotBlank());
        }
    }

    @Nested
    @DisplayName("load - 근거 없음")
    class NoEvidence {

        @Test
        @DisplayName("근거 없음 유형은 기대 근거가 비어 있다")
        void noEvidenceHasEmptyExpected() {
            // When
            GoldenSet goldenSet = goldenSetLoader.load();

            // Then
            assertThat(goldenSet.items())
                .filteredOn(item -> item.type() == GoldenSetItemType.NO_EVIDENCE)
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.expectedExternalIds()).isEmpty());
        }

        @Test
        @DisplayName("근거 없음이 아닌 유형은 기대 근거가 하나 이상")
        void otherTypesHaveExpected() {
            // When
            GoldenSet goldenSet = goldenSetLoader.load();

            // Then
            assertThat(goldenSet.items())
                .filteredOn(item -> item.type() != GoldenSetItemType.NO_EVIDENCE)
                .allSatisfy(item -> assertThat(item.expectedExternalIds()).isNotEmpty());
        }
    }
}
