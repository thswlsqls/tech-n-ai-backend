package com.tech.n.ai.api.agent.tool.handler;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ToolErrorHandlers 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ToolErrorHandlers 단위 테스트")
class ToolErrorHandlersTest {

    @Mock
    private ToolErrorContext toolErrorContext;

    @Mock
    private ToolExecutionRequest toolExecutionRequest;

    // ========== handleToolExecutionError 테스트 ==========

    @Nested
    @DisplayName("handleToolExecutionError")
    class HandleToolExecutionError {

        @Test
        @DisplayName("예외 발생 시 에러 메시지 생성")
        void handleToolExecutionError_예외메시지() {
            // Given
            when(toolErrorContext.toolExecutionRequest()).thenReturn(toolExecutionRequest);
            when(toolExecutionRequest.name()).thenReturn("fetch_github_releases");
            when(toolExecutionRequest.arguments()).thenReturn("{\"owner\":\"openai\"}");

            RuntimeException error = new RuntimeException("API 호출 실패");

            // When
            ToolErrorHandlerResult result = ToolErrorHandlers.handleToolExecutionError(error, toolErrorContext);

            // Then
            assertThat(result.text()).contains("fetch_github_releases");
            assertThat(result.text()).contains("API 호출 실패");
        }

        @Test
        @DisplayName("에러 메시지에 Tool 이름과 에러 내용 포함")
        void handleToolExecutionError_Tool이름포함() {
            // Given
            String toolName = "scrape_web_page";
            when(toolErrorContext.toolExecutionRequest()).thenReturn(toolExecutionRequest);
            when(toolExecutionRequest.name()).thenReturn(toolName);
            when(toolExecutionRequest.arguments()).thenReturn("{}");

            RuntimeException error = new RuntimeException("타임아웃");

            // When
            ToolErrorHandlerResult result = ToolErrorHandlers.handleToolExecutionError(error, toolErrorContext);

            // Then
            assertThat(result.text()).contains(toolName);
            assertThat(result.text()).contains("타임아웃");
        }
    }

    // ========== handleToolArgumentsError 테스트 ==========

    @Nested
    @DisplayName("handleToolArgumentsError")
    class HandleToolArgumentsError {

        @Test
        @DisplayName("인자 오류 시 에러 메시지 생성")
        void handleToolArgumentsError_인자오류() {
            // Given
            when(toolErrorContext.toolExecutionRequest()).thenReturn(toolExecutionRequest);
            when(toolExecutionRequest.name()).thenReturn("list_emerging_techs");
            when(toolExecutionRequest.arguments()).thenReturn("invalid json");

            RuntimeException error = new RuntimeException("JSON 파싱 실패");

            // When
            ToolErrorHandlerResult result = ToolErrorHandlers.handleToolArgumentsError(error, toolErrorContext);

            // Then
            assertThat(result.text()).contains("list_emerging_techs");
            assertThat(result.text()).contains("JSON 파싱 실패");
        }

        @Test
        @DisplayName("에러 메시지에 Tool 이름과 에러 내용 포함")
        void handleToolArgumentsError_Tool이름포함() {
            // Given
            String toolName = "get_emerging_tech_detail";
            when(toolErrorContext.toolExecutionRequest()).thenReturn(toolExecutionRequest);
            when(toolExecutionRequest.name()).thenReturn(toolName);
            when(toolExecutionRequest.arguments()).thenReturn("{}");

            RuntimeException error = new RuntimeException("타입 불일치");

            // When
            ToolErrorHandlerResult result = ToolErrorHandlers.handleToolArgumentsError(error, toolErrorContext);

            // Then
            assertThat(result.text()).contains(toolName);
            assertThat(result.text()).contains("타입 불일치");
        }
    }

    // ========== handleHallucinatedToolName 테스트 ==========

    @Nested
    @DisplayName("handleHallucinatedToolName")
    class HandleHallucinatedToolName {

        @Test
        @DisplayName("존재하지 않는 Tool 이름 처리")
        void handleHallucinatedToolName_존재하지않는Tool() {
            // Given
            String invalidToolName = "non_existent_tool";
            when(toolExecutionRequest.name()).thenReturn(invalidToolName);
            when(toolExecutionRequest.id()).thenReturn("tool-call-id-123");

            // When
            ToolExecutionResultMessage result = ToolErrorHandlers.handleHallucinatedToolName(toolExecutionRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.text()).contains("Error");
            assertThat(result.text()).contains(invalidToolName);
        }

        @Test
        @DisplayName("사용 가능한 Tool 목록 포함")
        void handleHallucinatedToolName_Tool목록포함() {
            // Given
            when(toolExecutionRequest.name()).thenReturn("fake_tool");
            when(toolExecutionRequest.id()).thenReturn("tool-id");

            // When
            ToolExecutionResultMessage result = ToolErrorHandlers.handleHallucinatedToolName(toolExecutionRequest);

            // Then
            assertThat(result.text()).contains("fetch_github_releases");
            assertThat(result.text()).contains("scrape_web_page");
            assertThat(result.text()).contains("list_emerging_techs");
            assertThat(result.text()).contains("collect_github_releases");
        }

        @Test
        @DisplayName("ToolExecutionResultMessage 형식 검증")
        void handleHallucinatedToolName_반환타입() {
            // Given
            when(toolExecutionRequest.name()).thenReturn("unknown");
            when(toolExecutionRequest.id()).thenReturn("id-1");

            // When
            ToolExecutionResultMessage result = ToolErrorHandlers.handleHallucinatedToolName(toolExecutionRequest);

            // Then
            assertThat(result.id()).isEqualTo("id-1");
            assertThat(result.toolName()).isEqualTo("unknown");
        }
    }
}
