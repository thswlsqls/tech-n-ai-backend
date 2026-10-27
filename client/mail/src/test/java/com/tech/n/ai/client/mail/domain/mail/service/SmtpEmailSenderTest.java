package com.tech.n.ai.client.mail.domain.mail.service;

import com.tech.n.ai.client.mail.config.MailProperties;
import com.tech.n.ai.client.mail.domain.mail.dto.EmailMessage;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SmtpEmailSender 단위 테스트
 *
 * 실제 MimeMessage를 만들어 send에 넘어간 메시지의 파트 구성을 확인한다. SMTP 서버에 붙지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpEmailSender 단위 테스트")
class SmtpEmailSenderTest {

    private static final String HTML = "<p>인증 링크</p>";
    private static final String TEXT = "인증 링크";

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailSender smtpEmailSender;

    @BeforeEach
    void setUp() {
        MailProperties properties = new MailProperties();
        properties.setFromAddress("noreply@example.com");
        smtpEmailSender = new SmtpEmailSender(mailSender, properties, Runnable::run);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("HTML 본문만 있으면 text/plain 파트 없이 text/html 파트 하나만 보낸다")
        void HTML만_있으면_HTML_파트만() throws Exception {
            // Given
            EmailMessage message = new EmailMessage("user@example.com", "제목", HTML, null);

            // When
            smtpEmailSender.send(message);

            // Then
            MimeMessage sent = captureSentMessage();
            assertThat(leafParts(sent, "text/plain")).isEmpty();
            List<Part> htmlParts = leafParts(sent, "text/html");
            assertThat(htmlParts).hasSize(1);
            assertThat(htmlParts.get(0).getContent()).isEqualTo(HTML);
        }

        @Test
        @DisplayName("HTML과 텍스트 본문이 모두 있으면 multipart/alternative 안에 두 파트를 하나씩 담는다")
        void 둘_다_있으면_alternative() throws Exception {
            // Given
            EmailMessage message = new EmailMessage("user@example.com", "제목", HTML, TEXT);

            // When
            smtpEmailSender.send(message);

            // Then
            MimeMessage sent = captureSentMessage();
            List<Part> alternatives = multipartParts(sent, "multipart/alternative");
            assertThat(alternatives).hasSize(1);
            Part alternative = alternatives.get(0);
            List<Part> plainParts = leafParts(alternative, "text/plain");
            List<Part> htmlParts = leafParts(alternative, "text/html");
            assertThat(plainParts).hasSize(1);
            assertThat(plainParts.get(0).getContent()).isEqualTo(TEXT);
            assertThat(htmlParts).hasSize(1);
            assertThat(htmlParts.get(0).getContent()).isEqualTo(HTML);
        }

        @Test
        @DisplayName("텍스트 본문만 있으면 text/plain 파트 하나만 보내고 text/html 파트는 없다")
        void 텍스트만_있으면_텍스트_파트만() throws Exception {
            // Given
            EmailMessage message = new EmailMessage("user@example.com", "제목", null, TEXT);

            // When
            smtpEmailSender.send(message);

            // Then
            MimeMessage sent = captureSentMessage();
            assertThat(leafParts(sent, "text/html")).isEmpty();
            List<Part> plainParts = leafParts(sent, "text/plain");
            assertThat(plainParts).hasSize(1);
            assertThat(plainParts.get(0).getContent()).isEqualTo(TEXT);
        }
    }

    private MimeMessage captureSentMessage() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        // 파트 Content-Type 헤더는 saveChanges() 뒤에 채워진다
        sent.saveChanges();
        return sent;
    }

    private List<Part> leafParts(Part root, String mimeType) throws Exception {
        List<Part> result = new ArrayList<>();
        collect(root, mimeType, false, result);
        return result;
    }

    private List<Part> multipartParts(Part root, String mimeType) throws Exception {
        List<Part> result = new ArrayList<>();
        collect(root, mimeType, true, result);
        return result;
    }

    private void collect(Part part, String mimeType, boolean multipartOnly, List<Part> result) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            if (multipartOnly && part.isMimeType(mimeType)) {
                result.add(part);
            }
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, mimeType, multipartOnly, result);
            }
        } else if (!multipartOnly && part.isMimeType(mimeType)) {
            result.add(part);
        }
    }
}
