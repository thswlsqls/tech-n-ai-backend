# common-conversation

대화 세션과 메시지를 다루는 공유 모듈입니다. `api-chatbot`과 `api-agent`가 같은 세션 관리 코드를 쓰도록 세션 CRUD·메시지 저장·LangChain4j `ChatMemoryStore`·CQRS 이벤트 발행을 한곳에 모았습니다.

CQRS의 쓰기와 읽기를 모두 거칩니다. **쓰기**는 Aurora에 저장하고 변경 사실을 Kafka 이벤트로 발행하며(MongoDB 반영은 `common-kafka` 컨슈머 담당), **읽기**는 세션은 Aurora 리더에서, 메시지는 MongoDB에서 하고 실패하면 Aurora로 폴백합니다.

## 서비스

**ConversationSessionService** — 세션 생성·조회·제목 변경·삭제와 생명주기 관리.

```java
String createSession(String userId, String title);
SessionResponse getSession(String sessionId, String userId);
void updateLastMessageAt(String sessionId);
Page<SessionResponse> listSessions(String userId, Pageable pageable);
Optional<SessionResponse> getActiveSession(String userId);
int deactivateInactiveSessions(Duration inactiveThreshold);  // 배치용
int expireInactiveSessions(int expirationDays);               // 배치용
SessionResponse updateSessionTitle(String sessionId, String userId, String title);
void deleteSession(String sessionId, String userId);
```

- 세션 접근 시 `userId`가 다르면 `UnauthorizedException`. 삭제는 `isDeleted` 플래그를 세우는 soft delete.
- `updateLastMessageAt`은 메시지가 오가면 비활성 세션을 다시 활성화합니다.
- 세션 변경마다 `ConversationSessionCreated/Updated/DeletedEvent`를 발행합니다. 단, `deactivateInactiveSessions`는 Aurora만 갱신하고 이벤트를 발행하지 않습니다.

**ConversationMessageService** — 메시지 저장과 조회.

```java
void saveMessage(String sessionId, String role, String content, Integer tokenCount);
Page<MessageResponse> getMessages(String sessionId, Pageable pageable);
List<ChatMessage> getMessagesForMemory(String sessionId, Integer maxTokens);
```

- `saveMessage`는 Aurora 저장 후 `ConversationMessageCreatedEvent`를 발행하고, 같은 세션 안에서 시퀀스 번호를 1씩 늘립니다.
- `getMessages`는 MongoDB를 먼저 보고, 결과가 없거나 조회에 실패하면 Aurora로 폴백합니다.
- `getMessagesForMemory`의 토큰 제한은 아직 미구현(TODO)입니다.

## MongoDbChatMemoryStore

LangChain4j `ChatMemoryStore` 구현체. `getMessages`는 세션의 최근 메시지를 최대 50개까지 읽어(agent 30 + chatbot 10 감안) Document 역할(`SYSTEM`/`USER`/`ASSISTANT`)을 LangChain4j 메시지 타입으로 매핑합니다. `updateMessages`/`deleteMessages`는 no-op입니다(저장은 서비스가, 삭제는 세션 삭제 흐름이 담당).

## DTO와 예외

- **SessionResponse**: `sessionId`, `title`, `createdAt`, `lastMessageAt`, `isActive`
- **MessageResponse**: `messageId`, `sessionId`, `role`, `content`, `tokenCount`, `sequenceNumber`, `createdAt`
- **ConversationSessionNotFoundException** (`BaseException`, `NOT_FOUND`) — 세션이 없거나 삭제됨.
- **InvalidSessionIdException** (`BusinessException`, `VALIDATION_ERROR`) — `sessionId`가 숫자가 아님. 정적 `parseSessionId(String)` 제공.

## 의존성

`common-core`·`common-exception`·`common-kafka`, `datasource-aurora`(쓰기)·`datasource-mongodb`(읽기). `dev.langchain4j:langchain4j`는 `api` 스코프라 이 모듈을 쓰는 쪽이 그대로 물려받습니다. 버전(1.10.0)은 루트 `build.gradle`의 `langchain4j-bom`이 정합니다.

## 참고 자료

- 설계서: `docs/reference/design/009-common-conversation-module.md`, `docs/prototype/step11/cqrs-kafka-sync-design.md`
- [LangChain4j ChatMemory 문서](https://docs.langchain4j.dev/tutorials/chat-memory)
