package com.tech.n.ai.common.conversation.exception;

import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.core.exception.BaseException;

/**
 * 세션을 찾을 수 없을 때 발생하는 예외
 */
public class ConversationSessionNotFoundException extends BaseException {

    public ConversationSessionNotFoundException(String message) {
        super(ErrorCodeConstants.NOT_FOUND, ErrorCodeConstants.MESSAGE_CODE_NOT_FOUND, message);
    }
}
