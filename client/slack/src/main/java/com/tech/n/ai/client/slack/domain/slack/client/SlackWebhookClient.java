package com.tech.n.ai.client.slack.domain.slack.client;

import com.tech.n.ai.client.slack.config.SlackProperties;
import com.tech.n.ai.client.slack.domain.slack.contract.SlackDto;
import com.tech.n.ai.client.slack.util.SlackRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slack Webhook Client 구현체
 * Incoming Webhooks를 통한 메시지 전송
 */
@Slf4j
@RequiredArgsConstructor
public class SlackWebhookClient implements SlackClient {
    
    private final WebClient webClient;
    private final SlackProperties properties;
    private final SlackRateLimiter rateLimiter;
    
    @Override
    public void sendMessage(SlackDto.SlackMessage message) {
        if (!properties.getWebhook().isEnabled()) {
            log.debug("Slack webhook is disabled");
            return;
        }
        
        String webhookUrl = properties.getWebhook().getUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Slack webhook URL is not configured");
            return;
        }
        
        // Rate Limiting 확인
        if (properties.getRateLimit().isEnabled()) {
            rateLimiter.checkAndWait("webhook", properties.getRateLimit().getMinIntervalMs());
        }
        
        try {
            // SlackMessage를 JSON으로 변환
            Map<String, Object> payload = buildPayload(message);
            
            webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            log.debug("Slack message sent successfully");
        } catch (WebClientResponseException e) {
            // 예외 메시지에는 요청 URL(=webhook 비밀 경로)이 들어 있어 상태 코드와 Slack 응답 본문만 남긴다
            log.error("Failed to send Slack message: status={}, body={}",
                e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            // 스택트레이스의 checkpoint에도 요청 URL이 붙으므로 예외 객체는 넘기지 않는다
            log.error("Failed to send Slack message: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            // 알림 실패는 시스템에 치명적이지 않으므로 예외를 다시 던지지 않음
        }
    }
    
    @Override
    public void sendMessage(String text) {
        SlackDto.SlackMessage message = SlackDto.SlackMessage.builder()
            .text(text)
            .build();
        sendMessage(message);
    }
    
    @Override
    public void sendMessage(String text, String channel) {
        // Webhook은 채널이 URL에 고정되어 있으므로 channel 파라미터 무시
        sendMessage(text);
    }
    
    /**
     * SlackMessage를 Slack API 페이로드로 변환
     * 
     * @param message SlackMessage
     * @return JSON 페이로드 Map
     */
    private Map<String, Object> buildPayload(SlackDto.SlackMessage message) {
        Map<String, Object> payload = new HashMap<>();
        
        if (message.text() != null && !message.text().isEmpty()) {
            payload.put("text", message.text());
        }
        
        if (message.blocks() != null && !message.blocks().isEmpty()) {
            List<Map<String, Object>> blocks = message.blocks().stream()
                .map(block -> {
                    Map<String, Object> blockMap = new HashMap<>();
                    blockMap.put("type", block.type());
                    
                    if (block.text() != null) {
                        blockMap.put("text", block.text());
                    }
                    
                    if (block.elements() != null) {
                        blockMap.put("elements", block.elements());
                    }
                    
                    return blockMap;
                })
                .toList();
            
            payload.put("blocks", blocks);
        }
        
        return payload;
    }
}
