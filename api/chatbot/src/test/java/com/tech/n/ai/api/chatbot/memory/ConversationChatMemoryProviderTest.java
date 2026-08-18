package com.tech.n.ai.api.chatbot.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConversationChatMemoryProvider 단위 테스트
 */
@DisplayName("ConversationChatMemoryProvider 단위 테스트")
class ConversationChatMemoryProviderTest {

    private static final String SESSION_ID = "session-1";

    private ConversationChatMemoryProvider chatMemoryProvider;

    @BeforeEach
    void setUp() {
        chatMemoryProvider = new ConversationChatMemoryProvider();
        ReflectionTestUtils.setField(chatMemoryProvider, "maxMessages", 10);
    }

    private static String textOf(ChatMessage message) {
        return ((UserMessage) message).singleText();
    }

    @Nested
    @DisplayName("get - 방금 넣은 메시지가 남는지")
    class AddedMessageStays {

        @Test
        @DisplayName("add()한 메시지가 messages()에 그대로 남는다 - ChatMemoryStore를 걸면 깨진다")
        void keepsAddedMessage() {
            // Given
            ChatMemory chatMemory = chatMemoryProvider.get(SESSION_ID);

            // When: 지금 하는 질문을 넣는다
            chatMemory.add(UserMessage.from("지금 하는 질문"));

            // Then: MongoDbChatMemoryStore처럼 쓰기가 비어 있는 store를 걸면 이 메시지가 사라진다
            assertThat(chatMemory.messages()).hasSize(1);
            assertThat(textOf(chatMemory.messages().get(0))).isEqualTo("지금 하는 질문");
        }
    }

    @Nested
    @DisplayName("get - 창 크기 설정 반영")
    class WindowSize {

        @Test
        @DisplayName("maxMessages가 3이면 최근 3건만 남는다")
        void keepsOnlyRecentMessages() {
            // Given
            ReflectionTestUtils.setField(chatMemoryProvider, "maxMessages", 3);
            ChatMemory chatMemory = chatMemoryProvider.get(SESSION_ID);

            // When: 창 크기보다 많은 5건을 넣는다
            for (int i = 1; i <= 5; i++) {
                chatMemory.add(UserMessage.from("질문 " + i));
            }

            // Then: 오래된 2건이 밀려나고 마지막 3건만 남는다
            List<ChatMessage> messages = chatMemory.messages();
            assertThat(messages).hasSize(3);
            assertThat(messages.stream().map(ConversationChatMemoryProviderTest::textOf))
                .containsExactly("질문 3", "질문 4", "질문 5");
        }
    }

    @Nested
    @DisplayName("get - 호출마다 새 메모리")
    class NewMemoryPerCall {

        @Test
        @DisplayName("같은 memoryId로 두 번 부르면 두 메모리가 상태를 공유하지 않는다")
        void doesNotShareStateBetweenCalls() {
            // Given
            ChatMemory first = chatMemoryProvider.get(SESSION_ID);
            first.add(UserMessage.from("첫 번째 메모리에 넣은 질문"));

            // When: 같은 세션 아이디로 다시 만든다
            ChatMemory second = chatMemoryProvider.get(SESSION_ID);

            // Then: 앞서 넣은 메시지가 보이지 않는다
            assertThat(first.messages()).hasSize(1);
            assertThat(second.messages()).isEmpty();
        }
    }
}
