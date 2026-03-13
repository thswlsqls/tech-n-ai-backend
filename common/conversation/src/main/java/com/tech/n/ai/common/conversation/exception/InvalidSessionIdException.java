package com.tech.n.ai.common.conversation.exception;

import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.core.exception.BusinessException;

/**
 * 유효하지 않은 세션 ID 형식 예외
 */
public class InvalidSessionIdException extends BusinessException {

    public InvalidSessionIdException(String message) {
        super(ErrorCodeConstants.VALIDATION_ERROR, ErrorCodeConstants.MESSAGE_CODE_VALIDATION_ERROR, message);
    }

    public static Long parseSessionId(String sessionId) {
        try {
            return Long.parseLong(sessionId);
        } catch (NumberFormatException e) {
            throw new InvalidSessionIdException("유효하지 않은 세션 ID 형식입니다: " + sessionId);
        }
    }
}
