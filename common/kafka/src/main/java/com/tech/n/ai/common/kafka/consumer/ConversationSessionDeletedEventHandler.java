package com.tech.n.ai.common.kafka.consumer;

import com.tech.n.ai.common.kafka.event.ConversationSessionDeletedEvent;
import org.springframework.stereotype.Component;

@Component
public class ConversationSessionDeletedEventHandler
    extends AbstractConversationSyncEventHandler<ConversationSessionDeletedEvent> {

    @Override
    protected void sync(ConversationSessionDeletedEvent event) {
        conversationSyncService.syncSessionDeleted(event);
    }

    @Override
    public String getEventType() {
        return "CONVERSATION_SESSION_DELETED";
    }
}
