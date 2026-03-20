# 001 - MongoDB Conversation Session 중복 문서로 인한 Kafka 동기화 실패

## 기본 정보
- **발생일**: 2026-03-20
- **모듈**: `common/kafka`, `datasource/mongodb`
- **심각도**: Critical (세션 재조회 완전 실패)
- **상태**: 해결 완료

## 증상
- Admin 페이지(`/agent`)에서 세션 사이드바를 클릭하여 과거 대화를 재조회하면 빈 화면 또는 일부만 조회됨
- 백엔드 로그에 `ConversationSessionUpdatedEvent` 동기화 실패가 10회 재시도 후 exhausted
- `IncorrectResultSizeDataAccessException: returned non unique result` 반복 발생

## 근본 원인

### Race Condition in Application-Level Upsert
`ConversationSyncServiceImpl.syncSessionCreated()` 메서드가 application-level upsert 패턴을 사용:
```java
// Before (문제 코드)
ConversationSessionDocument document = conversationSessionRepository
    .findBySessionId(payload.sessionId())
    .orElse(new ConversationSessionDocument());
document.setSessionId(payload.sessionId());
// ...
conversationSessionRepository.save(document);
```

**문제**: `agent-api`와 `chatbot-api`가 서로 다른 Kafka consumer group으로 동일 토픽(`tech-n-ai.conversation.session.created`)을 구독. 동일한 `SESSION_CREATED` 이벤트를 양쪽에서 동시 처리 → 두 consumer가 동시에 `findBySessionId`에서 "없음" 판정 → 각각 새 document를 insert → MongoDB에 같은 `sessionId`로 **중복 문서 생성**.

이후 `SESSION_UPDATED` 이벤트 처리 시 `findBySessionId`가 2개 이상의 document를 반환하여 `IncorrectResultSizeDataAccessException` 발생.

### 영향 범위
- `syncSessionUpdated`: 중복 문서로 인해 매번 실패 → 10회 재시도 후 exhausted
- `syncSessionDeleted`: 같은 패턴으로 잠재적 동일 문제
- `syncMessageCreated`: 유사한 race condition 가능성 (message는 발생 빈도가 낮아 실제 발생하지 않았을 수 있음)
- 메시지 조회 시 MongoDB 우선 조회 → 페이지네이션 불일치 → Aurora fallback으로 전환되지만 totalPageNumber 계산 오류

## 수정 내용

### 파일: `common/kafka/src/main/java/com/tech/n/ai/common/kafka/sync/ConversationSyncServiceImpl.java`

모든 sync 메서드를 `MongoTemplate`의 atomic operation으로 변경:

| 메서드 | Before | After |
|---|---|---|
| `syncSessionCreated` | `findBySessionId().orElse(new Doc())` + `save()` | `mongoTemplate.upsert(query, update, class)` |
| `syncSessionUpdated` | `findBySessionId().orElseThrow()` + `save()` | `mongoTemplate.updateFirst(query, update, class)` |
| `syncSessionDeleted` | `findBySessionId().ifPresent(delete)` | `mongoTemplate.remove(query, class)` |
| `syncMessageCreated` | `findByMessageId().orElse(new Doc())` + `save()` | `mongoTemplate.upsert(query, update, class)` |

주요 변경:
- `ConversationSessionRepository`, `ConversationMessageRepository` 의존성 제거 → `MongoTemplate` 단일 의존성으로 교체
- `@ConditionalOnBean(ConversationSessionRepository.class)` → `@ConditionalOnBean(MongoTemplate.class)` 변경
- `syncSessionUpdated`에서 document 없을 시 warn 로그 + skip (throw 대신)
- `syncSessionCreated`에서 `setOnInsert("created_at", ...)` 사용하여 최초 생성 시에만 created_at 설정

### 운영 조치
- MongoDB Atlas `conversation_sessions` 컬렉션에서 중복 sessionId 문서 수동 정리 필요
- `@Indexed(unique = true)` 어노테이션이 `ConversationSessionDocument.sessionId`에 이미 존재하나, 중복 문서가 인덱스 생성 전에 이미 들어간 경우 인덱스가 적용되지 않을 수 있음

## 교훈
- 여러 consumer group이 동일 토픽을 구독하는 CQRS 환경에서 MongoDB write 시 반드시 **MongoDB-level atomic upsert** 사용
- `findById().orElse(new) + save()` 패턴은 단일 consumer에서만 안전
