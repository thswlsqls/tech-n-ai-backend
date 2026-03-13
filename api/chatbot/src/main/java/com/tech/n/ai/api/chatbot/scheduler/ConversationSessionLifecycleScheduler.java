package com.tech.n.ai.api.chatbot.scheduler;

import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 세션 생명주기 관리 스케줄러
 * 
 * 세션 자동 비활성화 및 만료 처리를 주기적으로 실행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chatbot.session.batch-enabled", havingValue = "true", matchIfMissing = true)
public class ConversationSessionLifecycleScheduler {
    
    private final ConversationSessionService conversationSessionService;
    
    @Value("${chatbot.session.inactive-threshold-minutes:30}")
    private int inactiveThresholdMinutes;
    
    @Value("${chatbot.session.expiration-days:90}")
    private int expirationDays;
    
    /**
     * 세션 자동 비활성화 (선택적)
     * 주기: 매시간 정각 실행 (실시간 처리로 충분하지만, 백업용으로 구현)
     * 
     * 참고: 메시지 교환 시 실시간 재활성화가 주된 처리 방식이지만,
     * 배치 작업으로 주기적으로 정리하여 데이터 일관성 보장
     */
    @Scheduled(cron = "0 0 * * * ?")  // 매시간 정각 실행
    public void deactivateInactiveSessions() {
        try {
            Duration threshold = Duration.ofMinutes(inactiveThresholdMinutes);
            int count = conversationSessionService.deactivateInactiveSessions(threshold);
            if (count > 0) {
                log.info("Batch deactivated {} inactive sessions", count);
            }
        } catch (Exception e) {
            log.error("Failed to deactivate inactive sessions", e);
        }
    }
    
    /**
     * 세션 만료 처리 (필수)
     * 주기: 매일 새벽 2시 실행
     * 
     * 마지막 메시지 후 90일 경과한 비활성 세션을 만료 처리합니다.
     * MongoDB TTL 인덱스가 Query Side에서 자동 삭제하지만,
     * Command Side (Aurora MySQL)에서는 만료 상태만 기록합니다.
     */
    @Scheduled(cron = "0 0 2 * * ?")  // 매일 새벽 2시 실행
    public void expireInactiveSessions() {
        try {
            int count = conversationSessionService.expireInactiveSessions(expirationDays);
            if (count > 0) {
                log.info("Batch expired {} inactive sessions (expiration: {} days)", count, expirationDays);
            }
        } catch (Exception e) {
            log.error("Failed to expire inactive sessions", e);
        }
    }
}
