package com.tech.n.ai.api.agent.tool.handler;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * LangChain4j Tool Error Handler 구현
 * AiServices.builder()에서 사용되는 3종 에러 핸들러를 제공
 *
 * <p>핸들러 목록:
 * <ul>
 *   <li>toolExecutionErrorHandler: Tool 실행 중 예외 처리</li>
 *   <li>toolArgumentsErrorHandler: Tool 인자 오류 처리</li>
 *   <li>hallucinatedToolNameStrategy: 존재하지 않는 Tool 호출 처리</li>
 * </ul>
 */
@Slf4j
public final class ToolErrorHandlers {

    private ToolErrorHandlers() {
        // 유틸리티 클래스
    }

    /**
     * Tool 실행 중 예외 발생 시 핸들러
     * LLM이 에러를 인지하고 재시도하거나 다른 접근법을 선택할 수 있도록 명확한 에러 메시지 반환
     *
     * @param error 발생한 예외
     * @param context Tool 실행 컨텍스트
     * @return LLM에게 전달할 에러 결과
     */
    public static ToolErrorHandlerResult handleToolExecutionError(Throwable error, ToolErrorContext context) {
        String toolName = context.toolExecutionRequest().name();
        log.error("Tool 실행 중 예외 발생: tool={}, arguments={}, error={}",
                toolName,
                context.toolExecutionRequest().arguments(),
                error.getMessage(),
                error);

        return ToolErrorHandlerResult.text(
                String.format("Tool '%s' 실행 실패: %s. 다른 방법을 시도해주세요.",
                        toolName,
                        error.getMessage()));
    }

    /**
     * Tool 인자 오류 발생 시 핸들러
     * JSON 파싱 실패, 타입 불일치 등의 인자 관련 오류 처리
     *
     * @param error 발생한 오류
     * @param context Tool 실행 컨텍스트
     * @return LLM에게 전달할 에러 결과
     */
    public static ToolErrorHandlerResult handleToolArgumentsError(Throwable error, ToolErrorContext context) {
        String toolName = context.toolExecutionRequest().name();
        log.warn("Tool 인자 오류: tool={}, arguments={}, error={}",
                toolName,
                context.toolExecutionRequest().arguments(),
                error.getMessage());

        return ToolErrorHandlerResult.text(
                String.format("Tool '%s' 인자 오류: %s. 올바른 형식으로 다시 시도해주세요.",
                        toolName,
                        error.getMessage()));
    }

    /**
     * 존재하지 않는 Tool 호출 시 핸들러 (Hallucination 처리)
     * hallucinatedToolNameStrategy에서 요구하는 시그니처: Function&lt;ToolExecutionRequest, ToolExecutionResultMessage&gt;
     *
     * @param request Tool 실행 요청 정보
     * @return ToolExecutionResultMessage 에러 메시지
     */
    /** 사용 가능한 Tool 이름 목록 (Tool 추가/삭제 시 이 목록도 갱신) */
    private static final List<String> AVAILABLE_TOOLS = List.of(
        "fetch_github_releases", "scrape_web_page",
        "list_emerging_techs", "get_emerging_tech_detail", "search_emerging_techs",
        "get_emerging_tech_statistics", "analyze_text_frequency",
        "send_slack_notification",
        "collect_github_releases", "collect_rss_feeds", "collect_scraped_articles"
    );

    public static ToolExecutionResultMessage handleHallucinatedToolName(ToolExecutionRequest request) {
        String toolName = request.name();
        log.warn("존재하지 않는 Tool 호출 시도: {}", toolName);

        String errorMessage = String.format("Error: Tool '%s'은(는) 존재하지 않습니다. 사용 가능한 Tool: %s",
                toolName, String.join(", ", AVAILABLE_TOOLS));

        return ToolExecutionResultMessage.from(request, errorMessage);
    }
}
