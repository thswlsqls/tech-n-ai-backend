package com.tech.n.ai.common.kafka.consumer;

import com.tech.n.ai.common.kafka.event.ConversationMessageCreatedEvent;
import org.springframework.stereotype.Component;

@Component
public class ConversationMessageCreatedEventHandler
    extends AbstractConversationSyncEventHandler<ConversationMessageCreatedEvent> {

    @Override
    protected void sync(ConversationMessageCreatedEvent event) {
        conversationSyncService.syncMessageCreated(event);
    }

    @Override
    public String getEventType() {
        return "CONVERSATION_MESSAGE_CREATED";
    }
}
