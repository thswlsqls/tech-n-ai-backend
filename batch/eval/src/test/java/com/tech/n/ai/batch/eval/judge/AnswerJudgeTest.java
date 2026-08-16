package com.tech.n.ai.batch.eval.judge;

import com.tech.n.ai.api.chatbot.service.dto.SearchResult;
import dev.langchain4j.model.chat.ChatModel;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AnswerJudge 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerJudge 단위 테스트")
class AnswerJudgeTest {

    @Mock
    private ChatModel judgeChatModel;

    private List<SearchResult> evidence() {
        return List.of(SearchResult.builder()
            .documentId("doc1")
            .text("GPT-4o mini는 2024년 7월에 공개됐다.")
            .score(0.9)
            .metadata(new Document("title", "GPT-4o mini 공개").append("provider", "OpenAI"))
            .build());
    }

    @Nested
    @DisplayName("parse - 판정 응답 읽기")
    class Parse {

        @Test
        @DisplayName("한 줄 JSON을 그대로 읽는다")
        void readsPlainJson() {
            // Given
            String response = "{\"score\": 1, \"reason\": \"문서로 뒷받침된다\"}";

            // When
            JudgeVerdict verdict = AnswerJudge.parse(response);

            // Then
            assertThat(verdict.parsed()).isTrue();
            assertThat(verdict.score()).isEqualTo(1);
            assertThat(verdict.reason()).isEqualTo("문서로 뒷받침된다");
        }

        @Test
        @DisplayName("코드 펜스로 감싸 와도 읽는다")
        void readsFencedJson() {
            // Given
            String response = "```json\n{\"score\": 0, \"reason\": \"문서에 없는 내용이다\"}\n```";

            // When
            JudgeVerdict verdict = AnswerJudge.parse(response);

            // Then
            assertThat(verdict.parsed()).isTrue();
            assertThat(verdict.score()).isZero();
            assertThat(verdict.reason()).isEqualTo("문서에 없는 내용이다");
        }

        @Test
        @DisplayName("JSON이 아니면 실패로 남기고 응답 앞부분을 사유에 넣는다")
        void marksNonJsonAsFailed() {
            // Given
            String response = "판정하기 어렵습니다";

            // When
            JudgeVerdict verdict = AnswerJudge.parse(response);

            // Then
            assertThat(verdict.parsed()).isFalse();
            assertThat(verdict.score()).isNull();
            assertThat(verdict.reason()).contains("판정하기 어렵습니다");
        }

        @Test
        @DisplayName("score가 0도 1도 아니면 실패로 남긴다")
        void marksOutOfRangeScoreAsFailed() {
            // Given
            String response = "{\"score\": 2, \"reason\": \"애매하다\"}";

            // When
            JudgeVerdict verdict = AnswerJudge.parse(response);

            // Then
            assertThat(verdict.parsed()).isFalse();
            assertThat(verdict.score()).isNull();
            assertThat(verdict.reason()).contains("0도 1도 아니다");
        }

        @Test
        @DisplayName("score 키가 없으면 실패로 남긴다")
        void marksMissingScoreAsFailed() {
            // Given
            String response = "{\"reason\": \"이유만 있다\"}";

            // When
            JudgeVerdict verdict = AnswerJudge.parse(response);

            // Then
            assertThat(verdict.parsed()).isFalse();
            assertThat(verdict.reason()).contains("score가 없다");
        }
    }

    @Nested
    @DisplayName("judge - 모델 호출")
    class Judge {

        @Test
        @DisplayName("모델이 예외를 던져도 밖으로 던지지 않고 실패로 남긴다")
        void swallowsModelException() {
            // Given
            AnswerJudge judge = new AnswerJudge(judgeChatModel);
            when(judgeChatModel.chat(anyString())).thenThrow(new RuntimeException("API 에러"));

            // When
            JudgeVerdict verdict = judge.judge(
                AnswerJudge.Axis.GROUNDEDNESS, "질문", "답변", evidence());

            // Then
            assertThat(verdict.parsed()).isFalse();
            assertThat(verdict.score()).isNull();
            assertThat(verdict.reason()).contains("API 에러");
        }

        @Test
        @DisplayName("축마다 다른 지시문으로 묻고 근거 문서를 프롬프트에 담는다")
        void buildsPromptPerAxis() {
            // Given
            AnswerJudge judge = new AnswerJudge(judgeChatModel);
            when(judgeChatModel.chat(anyString())).thenReturn("{\"score\": 1, \"reason\": \"ok\"}");

            // When
            judge.judge(AnswerJudge.Axis.ANSWER_RELEVANCE, "질문은 무엇인가", "답변 본문", evidence());

            // Then
            ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
            verify(judgeChatModel).chat(prompt.capture());
            assertThat(prompt.getValue())
                .contains("질문이 물은 것에 답했으면 1")
                .contains("[문서 1] GPT-4o mini 공개 (OpenAI)")
                .contains("GPT-4o mini는 2024년 7월에 공개됐다.");
        }
    }
}
