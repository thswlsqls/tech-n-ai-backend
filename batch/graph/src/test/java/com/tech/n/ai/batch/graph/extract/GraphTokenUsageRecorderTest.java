package com.tech.n.ai.batch.graph.extract;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GraphTokenUsageRecorder 단위 테스트
 *
 * 응답 객체를 직접 만들어 리스너에 넘기므로 OpenAI를 부르지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GraphTokenUsageRecorder 단위 테스트")
class GraphTokenUsageRecorderTest {

    private GraphTokenUsageRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new GraphTokenUsageRecorder();
    }

    @Nested
    @DisplayName("onResponse - 토큰 누적")
    class OnResponse {

        @Test
        @DisplayName("응답마다 입력·출력 토큰과 호출 수가 쌓인다")
        void accumulatesTokens() {
            // Given
            recorder.onResponse(responseContext(new TokenUsage(100, 30)));
            recorder.onResponse(responseContext(new TokenUsage(50, 20)));

            // When
            GraphTokenUsageRecorder.Snapshot snapshot = recorder.snapshot();

            // Then
            assertThat(snapshot.inputTokens()).isEqualTo(150);
            assertThat(snapshot.outputTokens()).isEqualTo(50);
            assertThat(snapshot.callCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("사용량이 없는 응답은 0으로 세고 호출 수만 올린다")
        void nullTokenUsageCountsAsZero() {
            // Given
            recorder.onResponse(responseContext(null));

            // When
            GraphTokenUsageRecorder.Snapshot snapshot = recorder.snapshot();

            // Then
            assertThat(snapshot.inputTokens()).isZero();
            assertThat(snapshot.outputTokens()).isZero();
            assertThat(snapshot.callCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("snapshot - 문서별 몫 계산")
    class SnapshotDifference {

        @Test
        @DisplayName("두 스냅샷의 차이가 그 사이에 쓴 몫이다")
        void differenceBetweenSnapshots() {
            // Given: 앞 문서에서 이미 쓴 양이 있다
            recorder.onResponse(responseContext(new TokenUsage(100, 30)));
            GraphTokenUsageRecorder.Snapshot before = recorder.snapshot();

            // When: 다음 문서를 처리한다
            recorder.onResponse(responseContext(new TokenUsage(70, 10)));
            GraphTokenUsageRecorder.Snapshot spent = recorder.snapshot().minus(before);

            // Then
            assertThat(spent.inputTokens()).isEqualTo(70);
            assertThat(spent.outputTokens()).isEqualTo(10);
            assertThat(spent.callCount()).isEqualTo(1);
        }
    }

    private ChatModelResponseContext responseContext(TokenUsage tokenUsage) {
        ChatResponse chatResponse = ChatResponse.builder()
            .aiMessage(AiMessage.from("응답 본문"))
            .metadata(ChatResponseMetadata.builder().tokenUsage(tokenUsage).build())
            .build();

        ChatModelResponseContext context = mock(ChatModelResponseContext.class);
        when(context.chatResponse()).thenReturn(chatResponse);
        return context;
    }
}
