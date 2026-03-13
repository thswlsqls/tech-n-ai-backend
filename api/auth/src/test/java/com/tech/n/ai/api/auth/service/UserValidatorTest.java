package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.common.exception.exception.ConflictException;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.repository.reader.auth.UserReaderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserValidator 단위 테스트")
class UserValidatorTest {

    @Mock
    private UserReaderRepository userReaderRepository;

    @InjectMocks
    private UserValidator userValidator;

    @Nested
    @DisplayName("validateEmailNotExists")
    class ValidateEmailNotExists {

        @Test
        @DisplayName("사용 가능한 이메일 - 예외 없음")
        void validateEmail_사용_가능() {
            when(userReaderRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

            assertThatCode(() -> userValidator.validateEmailNotExists("new@example.com"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이미 사용 중인 이메일 - ConflictException")
        void validateEmail_이미_사용중() {
            UserEntity user = new UserEntity();
            user.setIsDeleted(false);
            when(userReaderRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userValidator.validateEmailNotExists("dup@example.com"))
                .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("삭제된 사용자의 이메일 - 예외 없음 (재사용 가능)")
        void validateEmail_삭제된_사용자() {
            UserEntity user = new UserEntity();
            user.setIsDeleted(true);
            when(userReaderRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(user));

            assertThatCode(() -> userValidator.validateEmailNotExists("deleted@example.com"))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateUsernameNotExists")
    class ValidateUsernameNotExists {

        @Test
        @DisplayName("사용 가능한 사용자명 - 예외 없음")
        void validateUsername_사용_가능() {
            when(userReaderRepository.findByUsername("newuser")).thenReturn(Optional.empty());

            assertThatCode(() -> userValidator.validateUsernameNotExists("newuser"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이미 사용 중인 사용자명 - ConflictException")
        void validateUsername_이미_사용중() {
            UserEntity user = new UserEntity();
            user.setIsDeleted(false);
            when(userReaderRepository.findByUsername("dupuser")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userValidator.validateUsernameNotExists("dupuser"))
                .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("삭제된 사용자의 사용자명 - 예외 없음 (재사용 가능)")
        void validateUsername_삭제된_사용자() {
            UserEntity user = new UserEntity();
            user.setIsDeleted(true);
            when(userReaderRepository.findByUsername("deleteduser")).thenReturn(Optional.of(user));

            assertThatCode(() -> userValidator.validateUsernameNotExists("deleteduser"))
                .doesNotThrowAnyException();
        }
    }
}
