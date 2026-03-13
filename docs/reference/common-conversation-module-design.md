# common-conversation 모듈 추출 설계서

**작성일**: 2026-03-13
**버전**: v1

---

## 1. 개요

### 목적

api-chatbot에 구현된 대화 세션 인프라(세션 CRUD, 메시지 영속화, ChatMemoryStore, Kafka CQRS 동기화)를 `common/conversation` 공유 모듈로 추출하여, api-chatbot과 api-agent가 동일한 세션 관리 인프라를 재사용하도록 한다.

### 현재 문제

api-agent의 멀티턴 대화는 **인메모리 `MessageWindowChatMemory`**에만 의존한다:

- 앱 재시작/배포 시 모든 대화 이력 소실
- 대화 이력 조회 API 없음 (프론트엔드에서 이전 대화를 불러올 수 없음)
- 세션 목록/관리 API 없음 (관리자가 진행 중인 세션을 확인할 수 없음)
- 세션 자동 정리(TTL) 없음 (메모리 누수 위험)

### 해결 방안

api-chatbot의 세션/메시지 서비스 계층을 `common-conversation` 모듈로 추출하고, api-agent가 이를 의존하여 세션 영속화 + REST API를 확보한다.

---

## 2. 추출 대상 분석

### 공유 가능 vs 모듈 고유 컴포넌트

| 컴포넌트 | 현재 위치 | 분류 | 이유 |
|---------|---------|------|------|
| `ConversationSessionService` (인터페이스) | api-chatbot | **공유 → common-conversation** | 세션 CRUD 계약은 두 모듈 동일 |
| `ConversationSessionServiceImpl` | api-chatbot | **공유 → common-conversation** | CQRS 저장 + Kafka 발행 로직 동일 |
| `ConversationMessageService` (인터페이스) | api-chatbot | **공유 → common-conversation** | 메시지 저장/조회 계약 동일 |
| `ConversationMessageServiceImpl` | api-chatbot | **공유 → common-conversation** | Aurora 저장 + MongoDB 읽기 + Kafka 발행 동일 |
| `MongoDbChatMemoryStore` | api-chatbot | **공유 → common-conversation** | LangChain4j ChatMemory 통합 로직 동일 |
| `SessionResponse` | api-chatbot | **공유 → common-conversation** | 두 모듈에서 동일한 세션 응답 구조 |
| `MessageResponse` | api-chatbot | **공유 → common-conversation** | 두 모듈에서 동일한 메시지 응답 구조 |
| `ConversationSessionNotFoundException` | api-chatbot | **공유 → common-conversation** | 세션 접근 검증 시 공통 사용 |
| `ConversationSessionLifecycleScheduler` | api-chatbot | **모듈 고유 → api-chatbot 유지** | 스케줄 주기/설정이 모듈별로 다름 |
| `ConversationChatMemoryProvider` | api-chatbot | **모듈 고유 → 각 모듈 유지** | chatbot: 10메시지/토큰 기반, agent: 30메시지 |
| `ChatbotController` | api-chatbot | **모듈 고유** | 엔드포인트 경로, 인증 방식 다름 |
| `ChatbotFacade` | api-chatbot | **모듈 고유** | RAG 파이프라인 오케스트레이션 |
| `ChatRequest`, `ChatResponse` 등 | api-chatbot | **모듈 고유** | chatbot 전용 DTO |
| Kafka 이벤트 클래스 | common-kafka | **이동 불필요** | 이미 공유 모듈에 위치 |
| ConversationSyncService | common-kafka | **이동 불필요** | 이미 공유 모듈에 위치 |
| Aurora 엔티티/리포지토리 | datasource-aurora | **이동 불필요** | 이미 공유 모듈에 위치 |
| MongoDB 도큐먼트/리포지토리 | datasource-mongodb | **이동 불필요** | 이미 공유 모듈에 위치 |

---

## 3. common-conversation 모듈 설계

### 3.1 Gradle 설정

```groovy
// common/conversation/build.gradle
group = 'com.tech.n.ai.common'
version = '0.0.1-SNAPSHOT'
description = 'common-conversation'

bootJar.enabled = false
jar.enabled = true

dependencies {
    implementation project(':common-core')
    implementation project(':common-exception')
    implementation project(':common-kafka')
    implementation project(':datasource-aurora')
    implementation project(':datasource-mongodb')

    // LangChain4j ChatMemory 인터페이스 (ChatMessage, ChatMemoryStore 등)
    api 'dev.langchain4j:langchain4j:1.10.0'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

> **참고**: settings.gradle 자동 발견으로 `:common-conversation`이 등록된다. 수동 `include` 추가 금지.

### 3.2 패키지 구조

```
common/conversation/src/main/java/com/tech/n/ai/common/conversation/
├── service/
│   ├── ConversationSessionService.java          ← 인터페이스
│   ├── ConversationSessionServiceImpl.java      ← 구현체
│   ├── ConversationMessageService.java          ← 인터페이스
│   └── ConversationMessageServiceImpl.java      ← 구현체
├── memory/
│   └── MongoDbChatMemoryStore.java              ← MongoDB 기반 ChatMessage 조회
├── dto/
│   ├── SessionResponse.java                     ← 세션 응답 DTO
│   └── MessageResponse.java                     ← 메시지 응답 DTO
└── exception/
    └── ConversationSessionNotFoundException.java ← 세션 미존재 예외
```

### 3.3 일반화가 필요한 부분

두 모듈 간 차이점을 처리하는 방법:

#### userId 타입 차이

| 항목 | api-chatbot | api-agent |
|------|------------|-----------|
| userId 타입 | `Long` (DB PK) | `String` (Gateway 헤더) |
| 인증 방식 | `@AuthenticationPrincipal UserPrincipal` | `@RequestHeader("x-user-id")` |

**해결**: `ConversationSessionService`의 userId 파라미터를 `String`으로 통일한다. Aurora 엔티티의 `userId` 필드도 `String`으로 변경한다.

- api-chatbot: `UserPrincipal.getId().toString()` → String으로 전달
- api-agent: `@RequestHeader("x-user-id")` → 그대로 전달
- 기존 Long userId를 사용하는 chatbot 코드는 `.toString()` 호출 추가

#### ConversationSessionService 인터페이스 변경

```java
// 변경 전 (chatbot 전용)
String createSession(Long userId, String title);
SessionResponse getSession(String sessionId, Long userId);

// 변경 후 (공유)
String createSession(String userId, String title);
SessionResponse getSession(String sessionId, String userId);
```

모든 메서드의 `Long userId` → `String userId`로 변경한다. Aurora 엔티티의 `userId` 컬럼도 `String` 타입으로 변경한다 (Aurora DDL 마이그레이션 필요).

#### ConversationSessionEntity 변경

```java
// 변경 전
@Column(name = "user_id", nullable = false)
private Long userId;

// 변경 후
@Column(name = "user_id", length = 50, nullable = false)
private String userId;
```

> **주의**: `createdBy`, `updatedBy`, `deletedBy` 등 BaseEntity의 감사 필드도 Long 타입이므로, 이들은 변경하지 않는다. `userId` 필드만 String으로 변경한다.

#### ChatMemoryProvider는 각 모듈에 유지

`ConversationChatMemoryProvider`는 공유 모듈로 추출하지 않는다. 이유:

- api-chatbot: `maxMessages=10` (→ 향후 토큰 기반)
- api-agent: `maxMessages=30`
- 메모리 전략(메시지 윈도우 vs 토큰 윈도우)이 모듈별로 다름

각 API 모듈이 `MongoDbChatMemoryStore`를 주입받아 자체 `ChatMemoryProvider`를 구성한다.

---

## 4. api-chatbot 수정 계획

### 4.1 제거할 파일

| 파일 | 이유 |
|------|------|
| `api/chatbot/.../service/ConversationSessionService.java` | common-conversation으로 이동 |
| `api/chatbot/.../service/ConversationSessionServiceImpl.java` | common-conversation으로 이동 |
| `api/chatbot/.../service/ConversationMessageService.java` | common-conversation으로 이동 |
| `api/chatbot/.../service/ConversationMessageServiceImpl.java` | common-conversation으로 이동 |
| `api/chatbot/.../memory/MongoDbChatMemoryStore.java` | common-conversation으로 이동 |
| `api/chatbot/.../dto/response/SessionResponse.java` | common-conversation으로 이동 |
| `api/chatbot/.../dto/response/MessageResponse.java` | common-conversation으로 이동 |
| `api/chatbot/.../common/exception/ConversationSessionNotFoundException.java` | common-conversation으로 이동 |

### 4.2 build.gradle 변경

```groovy
// api/chatbot/build.gradle — 추가
implementation project(':common-conversation')
```

기존 datasource-aurora, datasource-mongodb, common-kafka 의존성은 common-conversation이 전이적으로 제공하므로 제거 가능하나, 다른 용도로 직접 사용 중이면 유지한다.

### 4.3 import 경로 변경

```
// 변경 전
import com.tech.n.ai.api.chatbot.service.ConversationSessionService;
import com.tech.n.ai.api.chatbot.dto.response.SessionResponse;

// 변경 후
import com.tech.n.ai.common.conversation.service.ConversationSessionService;
import com.tech.n.ai.common.conversation.dto.SessionResponse;
```

영향 받는 파일:
- `ChatbotController.java` — SessionResponse, MessageResponse import 변경
- `ChatbotFacade.java` — 서비스 인터페이스 import 변경
- `ChatbotService.java` / `ChatbotServiceImpl.java` — 메시지 서비스 import 변경
- `ConversationChatMemoryProvider.java` — MongoDbChatMemoryStore import 변경
- `ConversationSessionLifecycleScheduler.java` — 세션 서비스 import 변경
- `ChatbotExceptionHandler.java` — ConversationSessionNotFoundException import 변경

### 4.4 userId 타입 변경

chatbot 컨트롤러/파사드에서 `Long userId` → `String userId` 변환 필요:

```java
// ChatbotController에서
String userId = userPrincipal.getId().toString();
```

### 4.5 기능 보존 확인

- 모든 기존 테스트(`ConversationSessionServiceTest` 등)는 common-conversation 모듈로 이동
- api-chatbot의 통합 테스트는 import 경로만 변경
- API 응답 형식, Kafka 이벤트 구조, CQRS 읽기 전략 모두 변경 없음

---

## 5. api-agent 통합 계획

### 5.1 build.gradle 의존성 추가

```groovy
// api/agent/build.gradle — 추가
implementation project(':common-conversation')
implementation project(':common-kafka')
implementation project(':datasource-aurora')
```

> `datasource-mongodb`는 이미 의존 중.

### 5.2 새로 추가할 REST API 엔드포인트

`AgentController.java`에 세션 관리 엔드포인트 추가:

```
GET    /api/v1/agent/sessions                      — 세션 목록 조회
GET    /api/v1/agent/sessions/{sessionId}           — 세션 상세 조회
GET    /api/v1/agent/sessions/{sessionId}/messages  — 대화 이력 조회
DELETE /api/v1/agent/sessions/{sessionId}           — 세션 삭제
```

**인증**: 모든 엔드포인트 ADMIN 역할 필수 (기존 `x-user-id` 헤더 사용).

**요청/응답 DTO**:
- 세션 목록: 기존 chatbot의 `SessionListRequest` 패턴을 따르되, api-agent 전용 DTO로 정의
- 응답: `SessionResponse`, `MessageResponse`는 common-conversation에서 제공

### 5.3 AgentFacade 수정

```java
// 현재
public AgentExecutionResult runAgent(String userId, AgentRunRequest request) {
    String sessionId = resolveSessionId(userId, request.sessionId());
    return agent.execute(request.goal(), sessionId);
}

// 변경 후
public AgentExecutionResult runAgent(String userId, AgentRunRequest request) {
    String sessionId = resolveSessionId(userId, request.sessionId());

    // 1. 세션이 없으면 생성
    ensureSession(sessionId, userId);

    // 2. 사용자 메시지 저장
    conversationMessageService.saveMessage(sessionId, "USER", request.goal(), null);

    // 3. Agent 실행
    AgentExecutionResult result = agent.execute(request.goal(), sessionId);

    // 4. Agent 응답 저장
    conversationMessageService.saveMessage(sessionId, "ASSISTANT", result.summary(), null);

    // 5. 세션 마지막 메시지 시간 업데이트
    conversationSessionService.updateLastMessageAt(sessionId);

    return result;
}
```

### 5.4 EmergingTechAgentImpl 수정

ChatMemoryProvider를 영속화 기반으로 전환:

```java
// 현재 (인메모리만)
@PostConstruct
void initAssistant() {
    ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId).maxMessages(MAX_MESSAGES).build();
    // ...
}

// 변경 후 (MongoDB 기반 + 인메모리 윈도우)
@PostConstruct
void initAssistant() {
    ChatMemoryProvider memoryProvider = memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(MAX_MESSAGES)
            .chatMemoryStore(mongoDbChatMemoryStore)  // 영속화 연결
            .build();
    // ...
}
```

> `MongoDbChatMemoryStore`를 주입받아 `chatMemoryStore`로 설정하면, MessageWindowChatMemory가 자동으로 영속 저장소에서 이력을 로드하고, 새 메시지를 저장한다. 단, langchain4j의 `ChatMemoryStore` 인터페이스를 `MongoDbChatMemoryStore`가 구현해야 한다.

### 5.5 MongoDbChatMemoryStore 확장

현재 `MongoDbChatMemoryStore`는 `getMessages()` 메서드만 있다. langchain4j `ChatMemoryStore` 인터페이스를 완전히 구현해야 한다:

```java
public class MongoDbChatMemoryStore implements ChatMemoryStore {

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // MongoDB에서 sessionId 기준으로 메시지 조회
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // MongoDB에 메시지 목록 업데이트 (기존 메시지 교체)
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // MongoDB에서 sessionId 기준으로 메시지 삭제
    }
}
```

### 5.6 AgentExecutionResult 변경

응답에 `sessionId`를 포함하여 프론트엔드가 다음 턴에 사용할 수 있도록 한다:

```java
// 현재
public record AgentExecutionResult(
    boolean success, String summary,
    int toolCallCount, int analyticsCallCount,
    long executionTimeMs, List<String> errors
)

// 변경 후
public record AgentExecutionResult(
    boolean success, String summary,
    String sessionId,              // 추가: 프론트엔드가 멀티턴에 사용
    int toolCallCount, int analyticsCallCount,
    long executionTimeMs, List<String> errors
)
```

---

## 6. datasource 계층 변경

### 6.1 Aurora 엔티티 패키지 변경

현재 `chatbot` 패키지에 위치한 엔티티를 `conversation` 패키지로 이동:

```
// 변경 전
datasource/aurora/.../entity/chatbot/ConversationSessionEntity.java
datasource/aurora/.../entity/chatbot/ConversationMessageEntity.java
datasource/aurora/.../repository/reader/chatbot/...
datasource/aurora/.../repository/writer/chatbot/...

// 변경 후
datasource/aurora/.../entity/conversation/ConversationSessionEntity.java
datasource/aurora/.../entity/conversation/ConversationMessageEntity.java
datasource/aurora/.../repository/reader/conversation/...
datasource/aurora/.../repository/writer/conversation/...
```

### 6.2 ConversationSessionEntity userId 타입 변경

```java
// 변경 전
@Column(name = "user_id", nullable = false)
private Long userId;

// 변경 후
@Column(name = "user_id", length = 50, nullable = false)
private String userId;
```

**Aurora DDL 마이그레이션**:

```sql
ALTER TABLE chatbot.conversation_sessions
    MODIFY COLUMN user_id VARCHAR(50) NOT NULL;
```

> 기존 Long 값은 문자열로 자동 호환된다 (예: `12345` → `"12345"`).

### 6.3 MongoDB 도큐먼트

MongoDB 도큐먼트는 이미 `userId`가 `String` 타입이므로 변경 불필요.

---

## 7. Kafka 이벤트 변경

### 토픽 호환성

현재 Kafka 토픽명:

```
tech-n-ai.conversation.session.created
tech-n-ai.conversation.session.updated
tech-n-ai.conversation.session.deleted
tech-n-ai.conversation.message.created
```

토픽명에 `chatbot`이 포함되어 있지 않으므로 **변경 불필요**. api-agent가 동일한 토픽으로 이벤트를 발행하면 기존 Kafka Consumer(ConversationSyncService)가 동일하게 처리한다.

### 이벤트 클래스

모든 이벤트 클래스(`ConversationSessionCreatedEvent` 등)는 이미 `common-kafka` 모듈에 위치하므로 **이동 불필요**.

### Payload userId 타입

현재 Kafka payload의 `userId`는 이미 `String` 타입이므로 **변경 불필요**:

```java
public record ConversationSessionCreatedPayload(
    @JsonProperty("sessionId") String sessionId,
    @JsonProperty("userId") String userId,    // 이미 String
    ...
)
```

---

## 8. 마이그레이션 순서

각 단계는 독립적으로 빌드·테스트 가능해야 한다.

### Phase 1: datasource 계층 패키지 이동 + userId 타입 변경

1. Aurora 엔티티/리포지토리 패키지를 `chatbot` → `conversation`으로 이동
2. `ConversationSessionEntity.userId`를 `Long` → `String`으로 변경
3. Aurora DDL 마이그레이션 스크립트 작성
4. api-chatbot의 import 경로 업데이트 + userId 변환 코드 추가
5. **검증**: `./gradlew :datasource-aurora:build`, `./gradlew :api-chatbot:test`

### Phase 2: common-conversation 모듈 생성 + 서비스 추출

1. `common/conversation/build.gradle` 작성 (의존성 정의)
2. `common/conversation/src` 디렉토리 구조 생성
3. api-chatbot에서 서비스, DTO, 예외 클래스를 common-conversation으로 이동
4. 패키지명 변경: `com.tech.n.ai.api.chatbot.*` → `com.tech.n.ai.common.conversation.*`
5. `MongoDbChatMemoryStore`에 `ChatMemoryStore` 인터페이스 구현 추가
6. api-chatbot에 `implementation project(':common-conversation')` 추가
7. api-chatbot의 import 경로 업데이트
8. **검증**: `./gradlew :common-conversation:build`, `./gradlew :api-chatbot:test`

### Phase 3: api-agent 통합

1. api-agent `build.gradle`에 의존성 추가
2. `AgentController`에 세션 관리 엔드포인트 추가
3. `AgentFacade` 수정 — 세션 생성/메시지 저장 로직 추가
4. `EmergingTechAgentImpl` 수정 — `MongoDbChatMemoryStore` 연결
5. `AgentExecutionResult`에 `sessionId` 필드 추가
6. api-agent-specification.md 문서 업데이트
7. **검증**: `./gradlew :api-agent:build`, 수동 멀티턴 테스트

### Phase 4: 세션 생명주기 관리

1. api-agent에 세션 비활성화/만료 스케줄러 추가 (chatbot 참고, 별도 설정값)
2. Gateway 라우팅에 새 엔드포인트 추가 (필요 시)
3. **검증**: `./gradlew build` (전체 빌드)

---

## 9. 검증 체크리스트

- [ ] `./gradlew :common-conversation:build` 성공
- [ ] `./gradlew :api-chatbot:test` 기존 테스트 모두 통과 (회귀 없음)
- [ ] `./gradlew :api-agent:build` 성공
- [ ] api-agent `POST /api/v1/agent/run` — sessionId 포함 응답 확인
- [ ] api-agent 동일 sessionId로 2회 요청 — 이전 대화 맥락 유지 확인
- [ ] api-agent `GET /api/v1/agent/sessions` — 세션 목록 조회 확인
- [ ] api-agent `GET /api/v1/agent/sessions/{id}/messages` — 대화 이력 조회 확인
- [ ] api-agent `DELETE /api/v1/agent/sessions/{id}` — 세션 삭제 확인
- [ ] Kafka CQRS 동기화 — Aurora 저장 후 MongoDB 반영 확인 (<1초)
- [ ] api-chatbot 기존 기능 — 세션 생성/조회/삭제/메시지 저장 모두 정상 동작
- [ ] `./gradlew build` 전체 프로젝트 빌드 성공

---

**문서 버전**: 1.0
**최종 업데이트**: 2026-03-13
