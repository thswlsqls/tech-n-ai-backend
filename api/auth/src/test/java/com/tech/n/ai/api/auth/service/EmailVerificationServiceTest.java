package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.api.auth.dto.ResetPasswordConfirmRequest;
import com.tech.n.ai.api.auth.dto.ResetPasswordRequest;
import com.tech.n.ai.client.mail.config.MailProperties;
import com.tech.n.ai.client.mail.domain.mail.dto.EmailMessage;
import com.tech.n.ai.client.mail.domain.mail.service.EmailSender;
import com.tech.n.ai.client.mail.domain.mail.template.EmailTemplateService;
import com.tech.n.ai.common.exception.exception.ConflictException;
import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.domain.aurora.entity.auth.EmailVerificationEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.repository.reader.auth.EmailVerificationReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.auth.UserReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.auth.EmailVerificationWriterRepository;
import com.tech.n.ai.domain.aurora.repository.writer.auth.UserWriterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationService 단위 테스트")
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationReaderRepository emailVerificationReaderRepository;

    @Mock
    private EmailVerificationWriterRepository emailVerificationWriterRepository;

    @Mock
    private UserReaderRepository userReaderRepository;

    @Mock
    private UserWriterRepository userWriterRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailSender emailSender;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private MailProperties mailProperties;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    // ========== createEmailVerificationToken ==========

    @Nested
    @DisplayName("createEmailVerificationToken")
    class CreateEmailVerificationToken {

        @Test
        @DisplayName("정상 토큰 생성 및 이메일 발송")
        void createToken_성공() {
            // Given
            MailProperties.Template templateProps = mock(MailProperties.Template.class);
            when(mailProperties.getBaseUrl()).thenReturn("http://localhost:8082");
            when(mailProperties.getTemplate()).thenReturn(templateProps);
            when(templateProps.getVerificationSubject()).thenReturn("이메일 인증");
            when(emailTemplateService.renderVerificationEmail(anyString(), anyString(), anyString()))
                .thenReturn("<html>인증</html>");

            // When
            String token = emailVerificationService.createEmailVerificationToken("test@example.com");

            // Then
            assertThat(token).isNotBlank();
            verify(emailVerificationWriterRepository).save(any(EmailVerificationEntity.class));
            verify(emailSender).sendAsync(any(EmailMessage.class));
        }

        @Test
        @DisplayName("이메일 발송 실패해도 토큰은 정상 생성")
        void createToken_이메일_발송_실패() {
            // Given
            when(mailProperties.getBaseUrl()).thenReturn("http://localhost:8082");
            when(emailTemplateService.renderVerificationEmail(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("메일 서버 오류"));

            // When
            String token = emailVerificationService.createEmailVerificationToken("test@example.com");

            // Then - 예외 없이 토큰 반환
            assertThat(token).isNotBlank();
            verify(emailVerificationWriterRepository).save(any(EmailVerificationEntity.class));
        }
    }

    // ========== verifyEmail ==========

    @Nested
    @DisplayName("verifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("정상 이메일 인증")
        void verifyEmail_성공() {
            // Given
            EmailVerificationEntity verification = createVerification("EMAIL_VERIFICATION", false, false);
            UserEntity user = new UserEntity();
            user.setEmail("test@example.com");

            when(emailVerificationReaderRepository.findByTokenAndType("token", "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(verification));
            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            // When
            emailVerificationService.verifyEmail("token");

            // Then
            assertThat(verification.isVerified()).isTrue();
            verify(emailVerificationWriterRepository).save(verification);
            verify(userWriterRepository).save(user);
        }

        @Test
        @DisplayName("이미 인증된 토큰 - ConflictException")
        void verifyEmail_이미_인증됨() {
            EmailVerificationEntity verification = createVerification("EMAIL_VERIFICATION", false, true);
            when(emailVerificationReaderRepository.findByTokenAndType("token", "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> emailVerificationService.verifyEmail("token"))
                .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("존재하지 않는 토큰 - ResourceNotFoundException")
        void verifyEmail_토큰_미존재() {
            when(emailVerificationReaderRepository.findByTokenAndType("invalid", "EMAIL_VERIFICATION"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> emailVerificationService.verifyEmail("invalid"))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("만료된 토큰 - UnauthorizedException")
        void verifyEmail_만료된_토큰() {
            EmailVerificationEntity verification = createExpiredVerification("EMAIL_VERIFICATION");
            when(emailVerificationReaderRepository.findByTokenAndType("expired", "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> emailVerificationService.verifyEmail("expired"))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("삭제된 토큰 - ResourceNotFoundException")
        void verifyEmail_삭제된_토큰() {
            EmailVerificationEntity verification = createVerification("EMAIL_VERIFICATION", false, false);
            verification.setIsDeleted(true);
            when(emailVerificationReaderRepository.findByTokenAndType("deleted", "EMAIL_VERIFICATION"))
                .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> emailVerificationService.verifyEmail("deleted"))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ========== requestPasswordReset ==========

    @Nested
    @DisplayName("requestPasswordReset")
    class RequestPasswordReset {

        @Test
        @DisplayName("존재하는 사용자 - 리셋 토큰 생성")
        void requestPasswordReset_성공() {
            // Given
            ResetPasswordRequest request = new ResetPasswordRequest("test@example.com");
            UserEntity user = new UserEntity();
            user.setIsDeleted(false);
            MailProperties.Template templateProps = mock(MailProperties.Template.class);

            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(emailVerificationReaderRepository.findByEmailAndType("test@example.com", "PASSWORD_RESET"))
                .thenReturn(Collections.emptyList());
            when(mailProperties.getBaseUrl()).thenReturn("http://localhost:8082");
            when(mailProperties.getTemplate()).thenReturn(templateProps);
            when(templateProps.getPasswordResetSubject()).thenReturn("비밀번호 재설정");
            when(emailTemplateService.renderPasswordResetEmail(anyString(), anyString(), anyString()))
                .thenReturn("<html>리셋</html>");

            // When
            emailVerificationService.requestPasswordReset(request);

            // Then
            verify(emailVerificationWriterRepository).save(any(EmailVerificationEntity.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 - 조용히 무시 (보안상 에러 미노출)")
        void requestPasswordReset_사용자_미존재() {
            ResetPasswordRequest request = new ResetPasswordRequest("none@example.com");
            when(userReaderRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

            // When - 예외 없이 정상 종료
            emailVerificationService.requestPasswordReset(request);

            // Then - 토큰 생성 없음
            verify(emailVerificationWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("기존 리셋 토큰 무효화 후 새 토큰 생성")
        void requestPasswordReset_기존_토큰_무효화() {
            // Given
            ResetPasswordRequest request = new ResetPasswordRequest("test@example.com");
            UserEntity user = new UserEntity();
            user.setIsDeleted(false);
            EmailVerificationEntity existingToken = createVerification("PASSWORD_RESET", false, false);
            MailProperties.Template templateProps = mock(MailProperties.Template.class);

            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(emailVerificationReaderRepository.findByEmailAndType("test@example.com", "PASSWORD_RESET"))
                .thenReturn(List.of(existingToken));
            when(mailProperties.getBaseUrl()).thenReturn("http://localhost:8082");
            when(mailProperties.getTemplate()).thenReturn(templateProps);
            when(templateProps.getPasswordResetSubject()).thenReturn("비밀번호 재설정");
            when(emailTemplateService.renderPasswordResetEmail(anyString(), anyString(), anyString()))
                .thenReturn("<html>리셋</html>");

            // When
            emailVerificationService.requestPasswordReset(request);

            // Then - 기존 토큰 무효화 + 새 토큰 저장
            assertThat(existingToken.getIsDeleted()).isTrue();
            verify(emailVerificationWriterRepository, atLeast(2)).save(any(EmailVerificationEntity.class));
        }
    }

    // ========== confirmPasswordReset ==========

    @Nested
    @DisplayName("confirmPasswordReset")
    class ConfirmPasswordReset {

        @Test
        @DisplayName("정상 비밀번호 재설정")
        void confirmPasswordReset_성공() {
            // Given
            ResetPasswordConfirmRequest request = new ResetPasswordConfirmRequest("token", "NewPassword1!");
            EmailVerificationEntity verification = createVerification("PASSWORD_RESET", false, false);
            UserEntity user = new UserEntity();
            user.setEmail("test@example.com");
            user.setPassword("$2a$10$oldEncoded");

            when(emailVerificationReaderRepository.findByTokenAndType("token", "PASSWORD_RESET"))
                .thenReturn(Optional.of(verification));
            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("NewPassword1!", "$2a$10$oldEncoded")).thenReturn(false);
            when(passwordEncoder.encode("NewPassword1!")).thenReturn("$2a$10$newEncoded");

            // When
            emailVerificationService.confirmPasswordReset(request);

            // Then
            assertThat(user.getPassword()).isEqualTo("$2a$10$newEncoded");
            assertThat(verification.isVerified()).isTrue();
        }

        @Test
        @DisplayName("이전과 동일한 비밀번호 - ConflictException")
        void confirmPasswordReset_동일_비밀번호() {
            ResetPasswordConfirmRequest request = new ResetPasswordConfirmRequest("token", "SamePassword1!");
            EmailVerificationEntity verification = createVerification("PASSWORD_RESET", false, false);
            UserEntity user = new UserEntity();
            user.setEmail("test@example.com");
            user.setPassword("$2a$10$encoded");

            when(emailVerificationReaderRepository.findByTokenAndType("token", "PASSWORD_RESET"))
                .thenReturn(Optional.of(verification));
            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("SamePassword1!", "$2a$10$encoded")).thenReturn(true);

            assertThatThrownBy(() -> emailVerificationService.confirmPasswordReset(request))
                .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("이미 사용된 토큰 - ConflictException")
        void confirmPasswordReset_이미_사용된_토큰() {
            ResetPasswordConfirmRequest request = new ResetPasswordConfirmRequest("used-token", "NewPassword1!");
            EmailVerificationEntity verification = createVerification("PASSWORD_RESET", false, true);

            when(emailVerificationReaderRepository.findByTokenAndType("used-token", "PASSWORD_RESET"))
                .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> emailVerificationService.confirmPasswordReset(request))
                .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("사용자 미존재 - ResourceNotFoundException")
        void confirmPasswordReset_사용자_미존재() {
            ResetPasswordConfirmRequest request = new ResetPasswordConfirmRequest("token", "NewPassword1!");
            EmailVerificationEntity verification = createVerification("PASSWORD_RESET", false, false);

            when(emailVerificationReaderRepository.findByTokenAndType("token", "PASSWORD_RESET"))
                .thenReturn(Optional.of(verification));
            when(userReaderRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> emailVerificationService.confirmPasswordReset(request))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ========== 헬퍼 ==========

    private EmailVerificationEntity createVerification(String type, boolean expired, boolean verified) {
        EmailVerificationEntity entity = new EmailVerificationEntity();
        entity.setEmail("test@example.com");
        entity.setToken("token");
        entity.setType(type);
        entity.setExpiresAt(expired ? LocalDateTime.now().minusHours(1) : LocalDateTime.now().plusHours(24));
        entity.setIsDeleted(false);
        if (verified) {
            entity.markAsVerified();
        }
        return entity;
    }

    private EmailVerificationEntity createExpiredVerification(String type) {
        return createVerification(type, true, false);
    }
}
