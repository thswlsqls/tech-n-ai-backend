package com.tech.n.ai.common.kafka.consumer;

import com.tech.n.ai.common.kafka.event.ConversationSessionUpdatedEvent;
import org.springframework.stereotype.Component;

@Component
public class ConversationSessionUpdatedEventHandler
    extends AbstractConversationSyncEventHandler<ConversationSessionUpdatedEvent> {

    @Override
    protected void sync(ConversationSessionUpdatedEvent event) {
        conversationSyncService.syncSessionUpdated(event);
    }

    @Override
    public String getEventType() {
        return "CONVERSATION_SESSION_UPDATED";
    }
}
