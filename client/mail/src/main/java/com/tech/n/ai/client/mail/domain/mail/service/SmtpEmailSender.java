package com.tech.n.ai.client.mail.domain.mail.service;

import com.tech.n.ai.client.mail.config.MailProperties;
import com.tech.n.ai.client.mail.domain.mail.dto.EmailMessage;
import com.tech.n.ai.client.mail.exception.EmailSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.Executor;

/**
 * SMTP 기반 이메일 발송 구현체.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {
    
    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final Executor mailTaskExecutor;
    
    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mimeMessage = createMimeMessage(message);
            mailSender.send(mimeMessage);
            log.info("이메일 발송 완료: to={}, subject={}", message.to(), message.subject());
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.error("이메일 발송 실패: to={}, error={}", message.to(), e.getMessage());
            throw new EmailSendException("이메일 발송에 실패했습니다.", e);
        }
    }
    
    @Override
    public void sendAsync(EmailMessage message) {
        mailTaskExecutor.execute(() -> {
            try {
                send(message);
            } catch (Exception e) {
                // Fail-Safe: 비동기 발송 실패 시 로그만 남기고 예외 전파하지 않음
                log.error("비동기 이메일 발송 실패: to={}", message.to(), e);
            }
        });
    }
    
    private MimeMessage createMimeMessage(EmailMessage message) 
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        
        helper.setFrom(properties.getFromAddress(), properties.getFromName());
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        
        if (message.htmlContent() != null && message.textContent() != null) {
            // multipart/alternative: HTML과 Plain Text 모두 제공
            helper.setText(message.textContent(), message.htmlContent());
        } else if (message.htmlContent() != null) {
            helper.setText(message.htmlContent(), true);
        } else {
            helper.setText(message.textContent());
        }
        
        return mimeMessage;
    }
}
