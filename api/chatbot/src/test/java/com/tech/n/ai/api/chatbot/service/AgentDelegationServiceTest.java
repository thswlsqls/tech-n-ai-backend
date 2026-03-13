package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.client.feign.domain.agent.contract.AgentContract;
import com.tech.n.ai.client.feign.domain.agent.contract.AgentDto;
import com.tech.n.ai.common.core.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentDelegationService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentDelegationService 단위 테스트")
class AgentDelegationServiceTest {

    @Mock
    private AgentContract agentApi;

    @InjectMocks
    private AgentDelegationService delegationService;

    // ========== delegateToAgent 테스트 ==========

    @Nested
    @DisplayName("delegateToAgent")
    class DelegateToAgent {

        @Test
        @DisplayName("정상 위임 - 결과 포맷팅 반환")
        void delegateToAgent_정상() {
            // Given
            ApiResponse<Object> apiResponse = ApiResponse.success("Agent 실행 완료");
            when(agentApi.runAgent(eq("1"), eq("ADMIN"), any(AgentDto.AgentRunRequest.class)))
                .thenReturn(apiResponse);

            // When
            String result = delegationService.delegateToAgent("AI 트렌드 분석", 1L, "ADMIN");

            // Then
            assertThat(result).contains("Agent 작업이 완료되었습니다.");
            verify(agentApi).runAgent(eq("1"), eq("ADMIN"), any(AgentDto.AgentRunRequest.class));
        }

        @Test
        @DisplayName("API 응답 data가 null일 때")
        void delegateToAgent_nullData() {
            // Given
            ApiResponse<Object> apiResponse = ApiResponse.success(null);
            when(agentApi.runAgent(any(), any(), any(AgentDto.AgentRunRequest.class)))
                .thenReturn(apiResponse);

            // When
            String result = delegationService.delegateToAgent("목표", 1L, "ADMIN");

            // Then
            assertThat(result).isEqualTo("Agent 작업이 완료되었습니다.");
        }

        @Test
        @DisplayName("API 호출 예외 시 에러 메시지 반환")
        void delegateToAgent_예외() {
            // Given
            when(agentApi.runAgent(any(), any(), any(AgentDto.AgentRunRequest.class)))
                .thenThrow(new RuntimeException("Connection refused"));

            // When
            String result = delegationService.delegateToAgent("목표", 1L, "ADMIN");

            // Then
            assertThat(result).contains("Agent 작업 요청에 실패했습니다");
        }
    }
}
