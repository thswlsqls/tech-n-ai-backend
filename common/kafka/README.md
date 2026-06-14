# common-kafka

CQRS의 쓰기(Aurora)와 읽기(MongoDB)를 잇는 Kafka 이벤트 모듈입니다. 이벤트 발행·수신, 멱등성 처리, MongoDB 동기화를 담당합니다.

> 현재 동기화 대상은 대화(conversation) 세션·메시지입니다. `UserDeletedEvent`는 정의돼 있지만 처리 핸들러가 아직 없습니다.

## 이벤트 모델

모든 이벤트는 `BaseEvent`를 구현합니다: `eventId()`(UUID), `eventType()`, `timestamp()`.

| 이벤트 | eventType | payload 필드 |
|--------|-----------|-------------|
| ConversationSessionCreatedEvent | `CONVERSATION_SESSION_CREATED` | sessionId, userId, title, lastMessageAt, isActive |
| ConversationSessionUpdatedEvent | `CONVERSATION_SESSION_UPDATED` | sessionId, userId, updatedFields(Map) |
| ConversationSessionDeletedEvent | `CONVERSATION_SESSION_DELETED` | sessionId, userId, deletedAt |
| ConversationMessageCreatedEvent | `CONVERSATION_MESSAGE_CREATED` | messageId, sessionId, role, content, tokenCount, sequenceNumber, createdAt |
| UserDeletedEvent | `USER_DELETED` | userTsid, userId, email, username, deletedAt, deletedBy |

`updatedFields`가 인식하는 키는 `title`, `lastMessageAt`, `isActive`이고, 그 외 키는 경고 로그만 남깁니다.

## 발행 / 수신

**EventPublisher** — `publish(topic, event, partitionKey)`와 `publish(topic, event)`(partitionKey로 eventId 사용). `CompletableFuture`로 비동기 발행하며 파티션 키로 순서를 보장합니다.

**EventConsumer** — `@KafkaListener` 하나가 토픽들을 받습니다.

- topics: `spring.kafka.consumer.topics` (기본 conversation 토픽 4개)
- groupId: `spring.kafka.consumer.group-id` (기본 `tech-n-ai-group`)
- 수동 커밋(`Acknowledgment`)

처리 순서: ① `IdempotencyService.isEventProcessed(eventId)`로 중복이면 건너뛰고 ack → ② `EventHandlerRegistry`로 처리 → ③ `markEventAsProcessed` 기록 후 ack.

## 핸들러 디스패치

```java
public interface EventHandler<T extends BaseEvent> {
    void handle(T event);
    String getEventType();
}
```

**EventHandlerRegistry**는 스프링이 주입한 모든 `EventHandler`를 `getEventType()` 값으로 Map에 담고, `event.eventType()`으로 찾아 호출합니다(없으면 경고 로그). 네 conversation 핸들러는 `AbstractConversationSyncEventHandler`를 상속하며, 이 추상 클래스는 `ConversationSyncService`를 `@Autowired(required = false)`로 받아 빈이 없으면 건너뜁니다.

**IdempotencyService** — Redis로 중복을 막습니다. 키 `processed_event:{eventId}`, 값 `processed`, TTL 7일.

## 동기화

**ConversationSyncService** 구현체는 `@ConditionalOnBean(MongoTemplate.class)`로 MongoTemplate이 있는 모듈에서만 활성화되고, 이벤트를 받아 MongoDB Document를 갱신합니다.

```java
void syncSessionCreated(...);  // upsert (session_id)
void syncSessionUpdated(...);  // updateFirst, updatedFields만 반영, 매칭 없으면 경고
void syncSessionDeleted(...);  // 물리 삭제 (remove) — MongoDB는 soft delete 미사용
void syncMessageCreated(...);  // upsert (message_id)
```

## 카프카 설정 (KafkaConfig)

- Producer: value `JacksonJsonSerializer`, idempotence 켜짐, `max.in.flight=5`
- Consumer: value `JacksonJsonDeserializer`, auto-commit 꺼짐, `isolation.level=read_committed`
- 신뢰 패키지: `com.tech.n.ai.common.kafka.event`, `com.tech.n.ai.*.event`

## 의존성

`common-core`, `datasource-mongodb`, `spring-kafka`, `kafka-streams`(`api` 스코프).

## 참고 자료

- 설계서: `docs/prototype/step11/cqrs-kafka-sync-design.md`
- [Spring for Apache Kafka 문서](https://docs.spring.io/spring-kafka/reference/) · [Apache Kafka 문서](https://kafka.apache.org/documentation/)
