package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.common.exception.exception.ConflictException;
import com.tech.n.ai.domain.aurora.repository.reader.auth.UserReaderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserValidator {
    
    private final UserReaderRepository userReaderRepository;
    
    public void validateEmailNotExists(String email) {
        userReaderRepository.findByEmail(email)
            .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
            .ifPresent(user -> {
                throw new ConflictException("email", "이미 사용 중인 이메일입니다.");
            });
    }
    
    public void validateUsernameNotExists(String username) {
        userReaderRepository.findByUsername(username)
            .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
            .ifPresent(user -> {
                throw new ConflictException("username", "이미 사용 중인 사용자명입니다.");
            });
    }
}
