package com.tech.n.ai.api.chatbot.service;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * LLMServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LLMServiceImpl 단위 테스트")
class LLMServiceImplTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private LLMServiceImpl llmService;

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("정상 응답 생성")
        void generate_정상() {
            // Given
            when(chatModel.chat("안녕하세요")).thenReturn("안녕하세요! 무엇을 도와드릴까요?");

            // When
            String result = llmService.generate("안녕하세요");

            // Then
            assertThat(result).isEqualTo("안녕하세요! 무엇을 도와드릴까요?");
        }

        @Test
        @DisplayName("ChatModel 예외 시 RuntimeException 래핑")
        void generate_예외() {
            // Given
            when(chatModel.chat("테스트")).thenThrow(new RuntimeException("API 에러"));

            // When & Then
            assertThatThrownBy(() -> llmService.generate("테스트"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM 응답 생성 실패");
        }
    }
}
