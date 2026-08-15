package com.tech.n.ai.api.chatbot.service;

import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    // 목이면 timer(...)가 null을 돌려줘 NPE가 난다. 실제 레지스트리를 쓴다.
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

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

        @Test
        @DisplayName("성공한 호출을 지연시간 미터에 기록")
        void generate_지연시간_기록() {
            // Given
            when(chatModel.chat("안녕하세요")).thenReturn("응답");

            // When
            llmService.generate("안녕하세요");

            // Then
            assertThat(meterRegistry.timer("chatbot.llm.duration").count()).isEqualTo(1);
            assertThat(meterRegistry.counter("chatbot.llm.errors").count()).isZero();
        }

        @Test
        @DisplayName("실패한 호출도 지연시간 미터에 남기고 실패 건수를 올린다")
        void generate_실패_미터기록() {
            // Given
            when(chatModel.chat("테스트")).thenThrow(new RuntimeException("API 에러"));

            // When
            assertThatThrownBy(() -> llmService.generate("테스트"))
                .isInstanceOf(RuntimeException.class);

            // Then: 실패율의 분모가 되려면 실패한 호출도 지연시간 미터에 세어져야 한다
            assertThat(meterRegistry.timer("chatbot.llm.duration").count()).isEqualTo(1);
            assertThat(meterRegistry.counter("chatbot.llm.errors").count()).isEqualTo(1.0);
        }
    }
}
