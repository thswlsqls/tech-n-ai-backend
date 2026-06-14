# Slack Client 모듈

## 개요

`client-slack`은 Slack Incoming Webhook으로 알림을 보내는 라이브러리입니다. 에러·성공·정보·배치 작업 네 가지 알림을 Block Kit 메시지로 만들어 전송합니다. `bootJar.enabled = false`인 라이브러리라 알림이 필요한 서비스·배치 모듈이 의존성으로 가져다 씁니다.

## 주요 기능

```java
public interface SlackContract {
    void sendNotification(SlackDto.NotificationRequest request);  // 타입으로 분기
    void sendErrorNotification(String message, Throwable error);
    void sendSuccessNotification(String message);
    void sendInfoNotification(String message);
    void sendBatchJobNotification(SlackDto.BatchJobResult result);
}
```

`SlackApi`가 이를 구현하며, `SlackMessageBuilder`로 Block Kit 메시지를 조립해 `SlackClient`로 전송합니다.

- **알림 유형**: 에러(메시지+예외), 성공/정보(단순 메시지), 배치 작업(잡 이름·상태·시각·처리 건수)
- **Block Kit 빌더**: `addSection`·`addDivider`·`addContext` 체이닝
- **호출 간격 제한 (`SlackRateLimiter`)**: Redis에 마지막 호출 시각을 기록해 최소 간격(기본 1초)을 지키고, 모자라면 `Thread.sleep`으로 멈춥니다.

## 패키지 구조

```
com.tech.n.ai.client.slack
├── config/   SlackConfig(빈 수동 등록), SlackProperties
├── domain/slack/
│   ├── contract/  SlackContract, SlackDto
│   ├── api/       SlackApi (SlackContract 구현, 메시지 조립)
│   ├── client/    SlackClient → SlackWebhookClient (Webhook POST)
│   ├── builder/   SlackMessageBuilder
│   └── service/   SlackNotificationService → ...Impl (위임)
├── util/     SlackRateLimiter (Redis)
└── exception/ SlackException (BaseException 상속)
```

## 빈 조립 (`SlackConfig`)

`WebClient.Builder → SlackWebhookClient(SlackClient) → SlackApi(SlackContract) → SlackNotificationServiceImpl` 순으로 수동 등록합니다. 전송(`SlackClient`)과 메시지 조립(`SlackContract`)을 분리해 HTTP 전송 방식과 알림 포맷팅을 따로 다룹니다.

**전송 동작 (`SlackWebhookClient`)**: `slack.webhook.url`에 메시지를 POST합니다. **전송이 실패해도 예외를 던지지 않고 `log.error`만 남깁니다** — 알림 실패가 본 작업을 막지 않게 한 설계입니다.

## 데이터 (`SlackDto`)

- `NotificationRequest(message, type, context)`, `BatchJobResult(jobName, status, startTime, endTime, processedItems, errorMessage)`
- `SlackMessage(text, blocks)`, `Block(type, text, elements)`
- enum `NotificationType { ERROR, SUCCESS, INFO, BATCH_JOB }`, `JobStatus { SUCCESS, FAILED, IN_PROGRESS }`

## 기술 스택

- **spring-boot-starter-webflux**: WebClient로 Webhook 호출
- **Redis**(루트 빌드 전역 제공): 호출 간격 제한
- **공통 모듈**: `common-core`(`BaseException`), `common-exception`

## 설정

```yaml
slack:
  webhook:
    url: ${SLACK_WEBHOOK_URL}
    enabled: true
  default-channel: "#general"
  notification:
    level: INFO
  rate-limit:
    min-interval-ms: 1000
    enabled: true
```

Webhook URL은 Slack 워크스페이스 → Apps → Incoming Webhooks에서 채널을 골라 발급한 뒤 환경 변수로 등록합니다.

## 사용 예시

```java
slack.sendErrorNotification(message, error);

slack.sendBatchJobNotification(SlackDto.BatchJobResult.builder()
    .jobName("SourceUpdateJob")
    .status(SlackDto.JobStatus.SUCCESS)
    .startTime(startTime).endTime(endTime).processedItems(42)
    .build());
```

## 현재 구현 범위

코드 그대로의 동작 기준입니다.

- **Webhook 전용**: 전송 구현체는 `SlackWebhookClient` 하나입니다. `slack.bot.*`(Bot Token) 설정 필드는 있지만 이를 쓰는 구현체는 아직 없습니다.
- **Resilience4j 미적용**: `application-slack.yml`에 `slackRetry` 설정이 있으나 코드에서 사용하지 않습니다. 전송 실패는 재시도 없이 로그만 남습니다.
- **`SlackException` 미사용**: 정의돼 있지만 전송 경로에서 던지지 않습니다.
- **채널 지정 제한**: Webhook은 URL에 채널이 고정돼 `sendMessage(text, channel)`의 채널 인자는 무시됩니다.

## 참고 문서

- [Slack Incoming Webhooks](https://api.slack.com/messaging/webhooks) · [Block Kit](https://api.slack.com/block-kit) · [Rate Limits](https://api.slack.com/docs/rate-limits)
- [Spring WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
