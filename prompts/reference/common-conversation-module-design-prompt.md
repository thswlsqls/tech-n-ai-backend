# 공유 세션 인프라 모듈(common-conversation) 추출 설계서 작성 프롬프트

## 목표

api-chatbot 모듈에 구현된 대화 세션 인프라(세션 CRUD, 메시지 영속화, ChatMemoryStore)를 `common/conversation` 공유 모듈로 추출하여, api-chatbot과 api-agent가 동일한 세션 관리 인프라를 재사용하도록 설계서를 작성하라.

## 배경

### 현재 문제

api-agent 모듈은 관리자 페이지에서 멀티턴 채팅을 지원해야 하지만, 세션 관리가 **인메모리(MessageWindowChatMemory)**에만 의존한다:

- 앱 재시작/배포 시 모든 대화 이력 소실
- 대화 이력 조회 API 없음 → 프론트엔드에서 이전 대화를 불러올 수 없음
- 세션 목록/관리 API 없음 → 관리자가 진행 중인 세션을 볼 수 없음
- 세션 자동 정리(TTL) 없음 → 메모리 누수 위험

반면 api-chatbot은 이미 완성된 세션 인프라를 갖추고 있다:

- Aurora MySQL + MongoDB CQRS 영속화
- 세션 CRUD + 메시지 저장/조회 + Kafka 동기화
- 세션 비활성화/만료 스케줄러
- LangChain4j ChatMemoryStore 통합

이 인프라를 api-agent에서도 사용할 수 있도록 공유 모듈로 추출한다.

### 두 모듈의 차이점 (설계 시 반드시 고려)

| 항목 | api-chatbot | api-agent |
|------|------------|-----------|
| LLM 패턴 | RAG (검색 증강 생성) | Tool Calling (자율 에이전트) |
| 사용자 | 일반 사용자 (Long userId) | 관리자 (String userId from Gateway) |
| 인증 | JWT 일반 사용자 | JWT ADMIN 역할 |
| 대화 특성 | 짧은 Q&A (1-2초 응답) | 긴 자율 실행 (10-60초 응답) |
| 메시지 구조 | user/assistant 텍스트만 | user/assistant + Tool 호출 결과 포함 가능 |
| 세션 ID 패턴 | TSID (Long → String) | `admin-{userId}-{uuid8}` |
| ChatMemory 크기 | 10 메시지 (→ 토큰 기반 예정) | 30 메시지 |

---

## 현재 코드베이스 맥락

> 설계서 작성 전에 아래 파일들을 반드시 읽고 현재 구조를 이해하라.

### api-chatbot 세션 인프라 (추출 대상)

```
# 서비스 인터페이스
api/chatbot/src/main/java/.../service/ConversationSessionService.java
api/chatbot/src/main/java/.../service/ConversationMessageService.java

# 서비스 구현체
api/chatbot/src/main/java/.../service/ConversationSessionServiceImpl.java
api/chatbot/src/main/java/.../service/ConversationMessageServiceImpl.java

# ChatMemory 통합
api/chatbot/src/main/java/.../memory/ConversationChatMemoryProvider.java
api/chatbot/src/main/java/.../memory/MongoDbChatMemoryStore.java

# 세션 생명주기 스케줄러
api/chatbot/src/main/java/.../scheduler/ConversationSessionLifecycleScheduler.java

# DTO
api/chatbot/src/main/java/.../dto/response/SessionResponse.java
api/chatbot/src/main/java/.../dto/response/MessageResponse.java

# 예외
api/chatbot/src/main/java/.../common/exception/ConversationSessionNotFoundException.java
```

### datasource 계층 (이미 공유 모듈에 위치)

```
# Aurora 엔티티 (datasource-aurora)
datasource/aurora/src/main/java/.../entity/chatbot/ConversationSessionEntity.java
datasource/aurora/src/main/java/.../entity/chatbot/ConversationMessageEntity.java

# Aurora 리포지토리 (datasource-aurora)
datasource/aurora/.../repository/reader/chatbot/ConversationSessionReaderRepository.java
datasource/aurora/.../repository/writer/chatbot/ConversationSessionWriterRepository.java
datasource/aurora/.../repository/writer/chatbot/ConversationMessageWriterRepository.java

# MongoDB 도큐먼트/리포지토리 (datasource-mongodb)
datasource/mongodb/.../document/ConversationMessageDocument.java
datasource/mongodb/.../repository/ConversationMessageRepository.java
```

### api-agent 모듈 (세션 인프라 소비자)

```
# 현재 멀티턴 구현 (인메모리만)
api/agent/src/main/java/.../agent/EmergingTechAgentImpl.java
api/agent/src/main/java/.../agent/AgentAssistant.java
api/agent/src/main/java/.../facade/AgentFacade.java
api/agent/src/main/java/.../controller/AgentController.java
api/agent/src/main/java/.../dto/request/AgentRunRequest.java
api/agent/src/main/java/.../agent/AgentExecutionResult.java
```

### 프로젝트 구조

```
common/        → 공유 라이브러리 (core, exception, kafka, security)
datasource/    → 데이터 접근 (aurora, mongodb) — 이미 공유됨
api/chatbot/   → RAG 챗봇 (포트 8084)
api/agent/     → AI Agent (포트 8086)
```

의존성 방향: `API → Datasource → Common → Client`

---

## 설계서에 포함할 내용

### 1. 추출 대상 분석

현재 api-chatbot의 세션 인프라를 분석하여, **공유 가능한 부분**과 **모듈 고유 부분**을 명확히 구분하라.

판단 기준:
- 두 모듈에서 동일한 동작이 필요한가? → 공유 모듈로 추출
- 모듈별로 다른 동작이 필요한가? → 인터페이스로 추상화하거나 각 모듈에 유지
- 이미 datasource 계층에 있는가? → 이동 불필요

### 2. common-conversation 모듈 설계

#### 2.1 모듈 구조

- Gradle 설정 (`common/conversation/build.gradle`)
- 패키지 구조
- 의존성 (datasource-aurora, datasource-mongodb, common-kafka 등)

#### 2.2 추출할 컴포넌트

각 컴포넌트에 대해:
- 원본 위치 (api-chatbot 내 경로)
- 이동 대상 (common-conversation 내 경로)
- 필요한 수정 사항 (패키지 변경, 일반화 등)
- 인터페이스 변경이 필요한 경우 그 이유

#### 2.3 일반화가 필요한 부분

두 모듈 간 차이점(사용자 ID 타입, 세션 ID 패턴, ChatMemory 크기 등)을 어떻게 처리할지 설계하라.

### 3. api-chatbot 수정 계획

공유 모듈 추출 후 api-chatbot에서:
- 제거할 파일
- build.gradle 의존성 변경
- import 경로 변경
- 기능 동작 변경 없음을 보장하는 방법

### 4. api-agent 통합 계획

공유 모듈을 활용하여 api-agent에 추가할 항목:

#### 4.1 새로 추가할 REST API 엔드포인트

```
GET  /api/v1/agent/sessions                      — 세션 목록
GET  /api/v1/agent/sessions/{sessionId}           — 세션 상세
GET  /api/v1/agent/sessions/{sessionId}/messages  — 대화 이력
DELETE /api/v1/agent/sessions/{sessionId}         — 세션 삭제
```

#### 4.2 EmergingTechAgentImpl 수정

- 인메모리 ChatMemory → 영속화된 ChatMemory로 전환
- 실행 전 이력 로드 + 실행 후 메시지 저장
- `evictSession()` 로직 변경

#### 4.3 AgentFacade 수정

- 세션 서비스 연동
- sessionId 생성/조회 로직 변경

#### 4.4 build.gradle 의존성 추가

### 5. datasource 계층 변경

Aurora 엔티티/리포지토리의 패키지 변경이 필요한 경우 (chatbot → conversation 등) 그 범위와 마이그레이션 방법을 명시하라.

### 6. Kafka 이벤트

세션/메시지 관련 Kafka 토픽이 기존 chatbot 전용인지, 공유 가능한지 분석하라. 토픽명 변경이 필요하면 하위 호환성 방안을 제시하라.

### 7. 마이그레이션 순서

단계별 구현 순서를 정의하라. 각 단계는 독립적으로 빌드·테스트 가능해야 한다.

### 8. 검증 체크리스트

- [ ] common-conversation 모듈 빌드 성공
- [ ] api-chatbot 기존 기능 회귀 테스트 통과
- [ ] api-agent 세션 CRUD API 동작 확인
- [ ] api-agent 멀티턴 대화 이력 영속화 확인
- [ ] 전체 프로젝트 빌드 성공: `./gradlew build`

---

## 제약사항

### 필수 준수

1. **기존 기능 보존**: api-chatbot의 현재 동작이 절대 변경되어서는 안 된다
2. **의존성 방향 준수**: `API → Common → Datasource` 방향만 허용. 순환 의존 금지
3. **CQRS 패턴 유지**: Command(Aurora) + Query(MongoDB) + Kafka 동기화 구조 유지
4. **모듈 자동 발견**: settings.gradle의 자동 발견 규칙(`{parentDir}-{moduleDir}`) 준수
5. **공식 문서만 참조**: LangChain4j, Spring Boot, Spring Data 공식 문서만 참고

### 금지

1. **오버엔지니어링 금지**: 현재 필요하지 않은 추상화나 미래 대비 코드 금지
2. **불필요한 리팩토링 금지**: 추출 대상이 아닌 코드를 건드리지 않는다
3. **구현 코드 작성 금지**: 설계서만 작성한다. 의사코드나 인터페이스 시그니처는 허용

---

## 출력 형식

Markdown 형식으로 다음 구조를 따르라:

```markdown
# common-conversation 모듈 추출 설계서

## 1. 개요
- 목적, 현재 문제, 해결 방안 요약

## 2. 추출 대상 분석
- 공유 가능 vs 모듈 고유 컴포넌트 분류 표

## 3. common-conversation 모듈 설계
- 패키지 구조, Gradle 설정, 핵심 인터페이스

## 4. api-chatbot 수정 계획
- 제거/변경 파일, 의존성 변경

## 5. api-agent 통합 계획
- 새 엔드포인트, 서비스/컨트롤러 변경, ChatMemory 전환

## 6. datasource 계층 변경
- 엔티티/리포지토리 패키지 변경 범위

## 7. Kafka 이벤트 변경
- 토픽 호환성, 변경 범위

## 8. 마이그레이션 순서
- 단계별 구현 계획 (각 단계 독립 빌드/테스트 가능)

## 9. 검증 체크리스트
```

## 시작 지시

위 요구사항에 따라 설계서를 작성하라. 설계서 작성 전에 "현재 코드베이스 맥락"에 명시된 파일들을 모두 읽고 현재 구조를 이해한 뒤 시작하라.
