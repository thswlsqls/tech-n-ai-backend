package com.tech.n.ai.batch.eval.goldenset;

import com.tech.n.ai.api.chatbot.service.IntentClassificationService;
import com.tech.n.ai.api.chatbot.service.IntentClassificationServiceImpl;
import com.tech.n.ai.api.chatbot.service.dto.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 골든셋 질문이 전부 RAG_REQUIRED로 분류되는지 확인하는 단위 테스트
 *
 * 평가 잡의 QuestionRunner는 RAG_REQUIRED가 아닌 질문을 INTENT_NOT_RAG로 빼버린다.
 * 한 건이라도 빠지면 채점 분모가 줄어 기존 리포트와 나란히 볼 수 없다.
 * MongoDB·OpenAI 접속 없이 분류기만 직접 호출한다.
 */
@DisplayName("골든셋 의도 분류 단위 테스트")
class GoldenSetIntentTest {

    private static final int EXPECTED_ITEM_COUNT = 52;

    private GoldenSetLoader goldenSetLoader;
    private IntentClassificationService intentService;

    @BeforeEach
    void setUp() {
        goldenSetLoader = new GoldenSetLoader();
        intentService = new IntentClassificationServiceImpl();
    }

    @Nested
    @DisplayName("classifyIntent - 골든셋 전건")
    class AllItems {

        @Test
        @DisplayName("모든 질문이 RAG_REQUIRED로 분류된다")
        void everyQuestionIsRagRequired() {
            // Given
            GoldenSet goldenSet = goldenSetLoader.load();

            // When & Then: 빠진 항목이 있으면 어느 id가 어떤 Intent로 갔는지 실패 메시지에 나온다
            assertThat(goldenSet.items()).allSatisfy(item ->
                assertThat(intentService.classifyIntent(item.question()))
                    .as("골든셋 %s가 RAG에서 빠졌다: %s", item.id(), item.question())
                    .isEqualTo(Intent.RAG_REQUIRED));
        }

        @Test
        @DisplayName("항목 수가 52건이다")
        void itemCountIsFixed() {
            // Given
            GoldenSet goldenSet = goldenSetLoader.load();

            // When & Then: 골든셋이 조용히 늘거나 줄면 채점 분모가 바뀐다
            assertThat(goldenSet.items()).hasSize(EXPECTED_ITEM_COUNT);
        }
    }
}
