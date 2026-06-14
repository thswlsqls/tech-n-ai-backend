# Mail Client 모듈

## 개요

`client-mail`은 SMTP로 이메일을 보내고 Thymeleaf 템플릿으로 HTML 본문을 만드는 라이브러리입니다. 회원가입 인증과 비밀번호 재설정 두 가지 메일 템플릿을 갖추고 있습니다. `bootJar.enabled = false`인 라이브러리라 단독 실행은 안 되고, `api-auth` 같은 서비스가 의존성으로 가져다 씁니다.

## 주요 기능

- **동기 발송 (`EmailSender.send`)**: 실패 시 `EmailSendException`을 던집니다.
- **비동기 발송 (`sendAsync`)**: 전용 스레드 풀(`mail-`)에서 보내고, 실패해도 로그만 남깁니다. 인증 메일처럼 실패가 본 흐름을 막으면 안 될 때 씁니다.
- **HTML / 텍스트 본문**: 둘 다 주면 `multipart/alternative`로 보냅니다.
- **템플릿 렌더링 (`EmailTemplateService`)**: `renderVerificationEmail`, `renderPasswordResetEmail`로 `templates/email/*.html`을 렌더링해 HTML을 돌려줍니다.

## 패키지 구조

```
com.tech.n.ai.client.mail
├── config/        MailConfig(@EnableAsync, 빈 구성), MailProperties
├── domain/mail/
│   ├── dto/       EmailMessage (record)
│   ├── service/   EmailSender → SmtpEmailSender(JavaMailSender)
│   └── template/  EmailTemplateService → ThymeleafEmailTemplateService
└── exception/     EmailSendException (BaseException 상속)

resources/templates/email/  verification.html, password-reset.html
```

## 설계 포인트

- **인터페이스/구현 분리**: 발송(`EmailSender`)과 템플릿(`EmailTemplateService`)을 인터페이스로 두고 SMTP·Thymeleaf 구현을 분리했습니다.
- **기본 빈 + 교체 가능**: `MailConfig`가 `emailSender`·`emailTemplateService`를 `@ConditionalOnMissingBean`으로 등록해, 쓰는 쪽이 자체 구현을 올리면 그쪽이 우선합니다.
- **DTO 자체 검증**: `EmailMessage`(record)는 compact constructor에서 수신자·제목·본문 누락을 검사해 `IllegalArgumentException`을 던집니다.
- **비동기 구조**: `sendAsync()`는 `@Async` 대신 `MailConfig`의 `mailTaskExecutor`에 작업을 직접 제출하고, 내부 `send()`의 예외는 잡아 로그만 남깁니다.

## 기술 스택

- **spring-boot-starter-mail**: `JavaMailSender`로 SMTP 발송 (`JavaMailSender` 빈은 Spring Boot가 `spring.mail.*`로 자동 생성)
- **spring-boot-starter-thymeleaf**: `TemplateEngine`으로 HTML 렌더링
- **공통 모듈**: `common-core`(`BaseException`), `common-exception`

## 설정

SMTP 연결은 Spring Boot 자동 설정(`spring.mail.host/port/username/password`)이 맡고, 아래는 이 모듈의 `MailProperties`가 다룹니다.

```yaml
mail:
  from-address: ${MAIL_FROM_ADDRESS}      # 발신자 주소 (필수)
  from-name: ${MAIL_FROM_NAME:Shrimp TM}
  base-url: ${MAIL_BASE_URL}              # 메일 안 링크 베이스 URL
  template:
    verification-subject: 이메일 인증을 완료해주세요
    password-reset-subject: 비밀번호 재설정 안내
  async:
    core-pool-size: 2
    max-pool-size: 5
    queue-capacity: 100
```

## 사용 예시

```java
@Service
@RequiredArgsConstructor
public class SignUpEmailService {
    private final EmailSender emailSender;
    private final EmailTemplateService templateService;

    public void sendVerification(String email, String token, String verifyUrl) {
        String html = templateService.renderVerificationEmail(email, token, verifyUrl);
        EmailMessage message = EmailMessage.builder()
            .to(email).subject("이메일 인증을 완료해주세요").htmlContent(html).build();
        emailSender.sendAsync(message);  // 실패해도 회원가입 흐름을 막지 않음
    }
}
```

## 예외 처리

`EmailSendException`은 `BaseException`을 상속하며 에러 코드는 `SERVICE_UNAVAILABLE`(HTTP 503)입니다. `SmtpEmailSender.send()`에서 `MailException`·`MessagingException`·`UnsupportedEncodingException`이 나면 이 예외로 감싸 던지고, `sendAsync()`는 잡아서 로그만 남깁니다.

## 참고 문서

- [Spring Boot Email](https://docs.spring.io/spring-boot/reference/io/email.html)
- [Thymeleaf 공식 문서](https://www.thymeleaf.org/documentation.html)
- [Jakarta Mail](https://jakartaee.github.io/mail-api/)
