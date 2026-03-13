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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.tech.n.ai.api.auth.service.VerificationConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    
    private final EmailVerificationReaderRepository emailVerificationReaderRepository;
    private final EmailVerificationWriterRepository emailVerificationWriterRepository;
    private final UserReaderRepository userReaderRepository;
    private final UserWriterRepository userWriterRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final EmailTemplateService emailTemplateService;
    private final MailProperties mailProperties;
    
    @Transactional
    public String createEmailVerificationToken(String email) {
        String token = SecureTokenGenerator.generate();
        EmailVerificationEntity verification = EmailVerificationEntity.create(
            email, 
            token, 
            EMAIL_VERIFICATION_TYPE, 
            LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS)
        );
        emailVerificationWriterRepository.save(verification);
        
        // 이메일 발송 (비동기, 실패해도 트랜잭션 롤백 없음)
        sendVerificationEmail(email, token);
        
        return token;
    }
    
    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationEntity verification = findAndValidateToken(token, EMAIL_VERIFICATION_TYPE);
        
        if (verification.isVerified()) {
            throw new ConflictException("이미 인증이 완료된 토큰입니다.");
        }
        
        verification.markAsVerified();
        emailVerificationWriterRepository.save(verification);
        
        UserEntity user = findUserByEmail(verification.getEmail());
        user.verifyEmail();
        userWriterRepository.save(user);
    }
    
    @Transactional
    public void requestPasswordReset(ResetPasswordRequest request) {
        if (!userExists(request.email())) {
            return;
        }
        
        invalidateExistingPasswordResetTokens(request.email());
        
        String resetToken = SecureTokenGenerator.generate();
        EmailVerificationEntity verification = EmailVerificationEntity.create(
            request.email(), 
            resetToken, 
            PASSWORD_RESET_TYPE, 
            LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS)
        );
        emailVerificationWriterRepository.save(verification);
        
        // 이메일 발송 (비동기, 실패해도 트랜잭션 롤백 없음)
        sendPasswordResetEmail(request.email(), resetToken);
    }
    
    @Transactional
    public void confirmPasswordReset(ResetPasswordConfirmRequest request) {
        EmailVerificationEntity verification = findAndValidateToken(request.token(), PASSWORD_RESET_TYPE);
        
        if (verification.isVerified()) {
            throw new ConflictException("이미 사용된 토큰입니다.");
        }
        
        UserEntity user = findUserByEmail(verification.getEmail());
        validateNewPassword(user, request.newPassword());
        
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userWriterRepository.save(user);
        
        verification.markAsVerified();
        emailVerificationWriterRepository.save(verification);
    }
    
    private EmailVerificationEntity findAndValidateToken(String token, String type) {
        EmailVerificationEntity verification = emailVerificationReaderRepository
            .findByTokenAndType(token, type)
            .orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 토큰입니다."));
        
        if (Boolean.TRUE.equals(verification.getIsDeleted())) {
            throw new ResourceNotFoundException("유효하지 않은 토큰입니다.");
        }
        
        if (verification.isExpired()) {
            throw new UnauthorizedException("만료된 토큰입니다.");
        }
        
        return verification;
    }
    
    private UserEntity findUserByEmail(String email) {
        return userReaderRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }
    
    private boolean userExists(String email) {
        return userReaderRepository.findByEmail(email)
            .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
            .isPresent();
    }
    
    private void invalidateExistingPasswordResetTokens(String email) {
        emailVerificationReaderRepository.findByEmailAndType(email, PASSWORD_RESET_TYPE)
            .forEach(existing -> {
                existing.invalidate();
                emailVerificationWriterRepository.save(existing);
            });
    }
    
    private void validateNewPassword(UserEntity user, String newPassword) {
        if (user.getPassword() != null && passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ConflictException("이전 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
        }
    }
    
    private void sendVerificationEmail(String email, String token) {
        try {
            String verifyUrl = mailProperties.getBaseUrl() + "/verify-email?token=" + token;
            String htmlContent = emailTemplateService.renderVerificationEmail(email, token, verifyUrl);
            
            EmailMessage message = EmailMessage.builder()
                .to(email)
                .subject(mailProperties.getTemplate().getVerificationSubject())
                .htmlContent(htmlContent)
                .build();
            
            emailSender.sendAsync(message);
            log.info("인증 이메일 발송 요청: to={}", email);
        } catch (Exception e) {
            // Fail-Safe: 이메일 발송 실패해도 회원가입은 정상 완료
            log.error("인증 이메일 발송 실패: to={}", email, e);
        }
    }
    
    private void sendPasswordResetEmail(String email, String token) {
        try {
            String resetUrl = mailProperties.getBaseUrl() + "/reset-password?token=" + token;
            String htmlContent = emailTemplateService.renderPasswordResetEmail(email, token, resetUrl);
            
            EmailMessage message = EmailMessage.builder()
                .to(email)
                .subject(mailProperties.getTemplate().getPasswordResetSubject())
                .htmlContent(htmlContent)
                .build();
            
            emailSender.sendAsync(message);
            log.info("비밀번호 재설정 이메일 발송 요청: to={}", email);
        } catch (Exception e) {
            // Fail-Safe: 이메일 발송 실패해도 토큰 생성은 정상 완료
            log.error("비밀번호 재설정 이메일 발송 실패: to={}", email, e);
        }
    }
}
