package com.tech.n.ai.common.kafka.consumer;

import com.tech.n.ai.common.kafka.event.ConversationSessionCreatedEvent;
import org.springframework.stereotype.Component;

@Component
public class ConversationSessionCreatedEventHandler
    extends AbstractConversationSyncEventHandler<ConversationSessionCreatedEvent> {

    @Override
    protected void sync(ConversationSessionCreatedEvent event) {
        conversationSyncService.syncSessionCreated(event);
    }

    @Override
    public String getEventType() {
        return "CONVERSATION_SESSION_CREATED";
    }
}
