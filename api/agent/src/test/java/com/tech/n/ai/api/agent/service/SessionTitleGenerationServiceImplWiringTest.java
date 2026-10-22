package com.tech.n.ai.api.agent.service;

import com.tech.n.ai.api.agent.config.AiAgentConfig;
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SessionTitleGenerationServiceImpl 빈 연결 테스트
 */
@SpringJUnitConfig(classes = {AiAgentConfig.class, SessionTitleGenerationServiceImpl.class})
@TestPropertySource(properties = "langchain4j.open-ai.chat-model.api-key=test-api-key")
@DisplayName("SessionTitleGenerationServiceImpl 빈 연결 테스트")
class SessionTitleGenerationServiceImplWiringTest {

    @MockitoBean
    private ConversationSessionService sessionService;

    @Autowired
    private SessionTitleGenerationServiceImpl titleService;

    @Test
    @DisplayName("제목 생성 서비스는 병렬 Tool 호출 설정이 없는 모델을 받는다")
    void 제목생성서비스_병렬Tool호출_미설정모델주입() {
        // When
        Object chatModel = ReflectionTestUtils.getField(titleService, "chatModel");

        // Then
        assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
        assertThat(((OpenAiChatModel) chatModel).defaultRequestParameters().parallelToolCalls()).isNull();
    }
}
