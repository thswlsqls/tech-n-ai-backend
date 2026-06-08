package com.tech.n.ai.common.kafka.consumer;

import com.tech.n.ai.common.kafka.event.BaseEvent;
import com.tech.n.ai.common.kafka.sync.ConversationSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 대화 동기화 이벤트 핸들러 공통 베이스
 *
 * ConversationSyncService가 있으면 sync()로 위임하고, 없으면 동기화를 건너뛴다.
 * 변하지 않는 가용성 체크·로깅 흐름을 여기에 두고, 각 이벤트별 동기화 호출만 sync()로 분리한다.
 */
@Slf4j
public abstract class AbstractConversationSyncEventHandler<T extends BaseEvent> implements EventHandler<T> {

    @Autowired(required = false)
    protected ConversationSyncService conversationSyncService;

    @Override
    public void handle(T event) {
        if (conversationSyncService != null) {
            sync(event);
        } else {
            log.debug("ConversationSyncService not available, skipping sync: eventId={}", event.eventId());
        }
    }

    protected abstract void sync(T event);
}
