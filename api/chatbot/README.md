# Chatbot API Module

langchain4j를 활용한 RAG(Retrieval-Augmented Generation) 기반 챗봇 시스템입니다. MongoDB Atlas Vector Search를 활용하여 Emerging Tech(AI 업데이트 정보)를 검색하고 자연어로 질문할 수 있는 기능을 제공합니다. 하이브리드 검색(Score Fusion + RRF)으로 최신 문서 누락을 방지하고, 세션 타이틀 자동생성 기능을 지원합니다.

## 목차

1. [개요](#개요)
2. [아키텍처](#아키텍처)
3. [주요 기능](#주요-기능)
4. [기술 스택](#기술-스택)
5. [API 엔드포인트](#api-엔드포인트)
6. [설정](#설정)
7. [의존성](#의존성)
8. [구현 구조](#구현-구조)
9. [참고 자료](#참고-자료)

---

## 개요

### 배경

현재 프로젝트는 CQRS 패턴을 적용하여 Command Side(Aurora MySQL)와 Query Side(MongoDB Atlas)를 분리하고 있으며, MongoDB Atlas에는 다음 컬렉션이 저장되어 있습니다:

- **EmergingTechDocument**: AI 서비스 업데이트 정보 (OpenAI, Anthropic, Google, Meta, xAI)

이 도큐먼트를 임베딩하여 벡터 검색 기반의 지식 검색 챗봇을 구축함으로써, 사용자가 자연어로 최신 AI 업데이트를 검색하고 질문할 수 있도록 합니다.

### LLM 및 Embedding Model 선택

- **LLM Provider**: OpenAI GPT-4o-mini (기본)
  - 비용 최적화: $0.15/$0.60 per 1M tokens (입력/출력)
  - 빠른 응답 속도
  - 128K 컨텍스트 윈도우

- **Embedding Model**: OpenAI text-embedding-3-small (기본)
  - LLM Provider와 동일한 Provider 사용으로 통합성 최적화
  - 비용 최적화: $0.02 per 1M tokens
  - 1536 dimensions (기본값)

### 설계 원칙

1. **클린코드 원칙**
   - 단일 책임 원칙 (SRP)
   - 의존성 역전 원칙 (DIP)
   - 개방-폐쇄 원칙 (OCP)

2. **최소 구현 원칙**
   - 현재 필요한 기능만 구현
   - 단순하고 명확한 구조
   - 단계적 확장 가능한 구조

3. **운영 환경 고려**
   - 비용 통제 (토큰 사용량 추적 및 제한)
   - 성능 최적화 (검색 결과 수 제한, 프롬프트 최적화)
   - 에러 처리 (각 단계별 에러 핸들링)

---

## 아키텍처

### Overall System Architecture

![Overall System Architecture](../../contents/api-chatbot/overall-system-architecture.png)

시스템은 다음과 같은 계층 구조로 구성됩니다:

- **API Layer**: RESTful API 엔드포인트 제공
- **Service Layer**: 비즈니스 로직 처리
- **Chain Layer**: RAG 파이프라인 체인 처리
- **Data Layer**: MongoDB Atlas Vector Search 및 Redis 캐싱
- **External**: LLM Provider 및 Embedding Model

### Chatbot LLM RAG Pipeline

![Chatbot LLM RAG Pipeline](../../contents/api-chatbot/chatbot-llm-rag-pipeline.png)

RAG 파이프라인은 다음과 같은 단계로 구성됩니다:

1. **입력 전처리**: 사용자 입력 검증 및 정규화
2. **의도 분류**: RAG 필요 여부 판단
3. **벡터 검색**: MongoDB Atlas Vector Search를 통한 관련 문서 검색
4. **결과 정제**: 유사도 필터링 및 중복 제거
5. **답변 생성**: LLM을 통한 최종 답변 생성

### 데이터 흐름

```
사용자 입력
  ↓
입력 전처리 (검증, 정규화)
  ↓
의도 분류 (RAG 필요 여부)
  ↓
[RAG 필요 시]
  ↓
입력 해석 체인 (검색 쿼리 추출)
  ↓
RetrievalService — 검색 단계를 한곳에 모은다
  ├─ 벡터 검색 (MongoDB Atlas Vector Search, 하이브리드 + RRF)
  ├─ 그래프 검색 (tech_graph_*, 기본 꺼짐)  → 벡터 결과 뒤에 붙임
  └─ 근거가 약하면 조건을 완화해 재검색 (기본 꺼짐) → 1차 결과 뒤에 붙임
  ↓
결과 정제 체인 (유사도 필터링, 중복 제거, 점수 내림차순 정렬 후 상위 N건)
  ↓
답변 생성 체인 (프롬프트 구축, LLM 호출)
  ↓
최종 답변 반환
```

`RetrievalService`를 둔 이유는 운영 챗봇과 평가 잡(`batch-eval`)이 같은 검색 코드를 타게 하려는 것입니다. 검색 옵션이 두 벌로 갈리면 `enableScoreFusion` 한 줄만 어긋나도 평가에서 잰 수치가 실제 서비스 동작과 달라집니다. 옵션 조립도 `SearchOptionsFactory` 하나로 모았습니다.

---

## 주요 기능

### 1. Emerging Tech 전용 RAG 검색

- `emerging_techs` 컬렉션 전용 MongoDB Atlas Vector Search
- status: PUBLISHED pre-filter 적용
- Emerging Tech 메타데이터(provider, publishedAt, title, url) 포함 프롬프트

### 2. 하이브리드 검색 (Score Fusion + RRF)

- **벡터 검색 + 최신성 결합**: 순수 벡터 유사도만으로는 최신 문서가 누락될 수 있는 문제 해결
- **MongoDB Pipeline 내 Score Fusion**: `$vectorSearch` → `$addFields(recencyScore)` Exponential Decay 함수 → `$addFields(combinedScore)` → `$sort` → `$limit`
- **최신성 직접 쿼리**: `published_at` DESC 정렬로 최신 문서 보장
- **RRF 결합**: 두 검색 소스를 Reciprocal Rank Fusion (k=60) 알고리즘으로 결합
- MongoDB 8.2/8.3의 `$rankFusion`/`$scoreFusion` 효과를 MongoDB 8.0 환경에서 재현

### 2-1. 지식 그래프 검색 (기본 꺼짐)

`batch-graph`가 만든 `tech_graph_nodes`·`tech_graph_edges`를 읽어 벡터 검색이 놓친 문서를 더합니다. 문서 여러 건을 엮어야 답이 나오는 질문을 겨냥한 경로입니다.

- `GraphSeedExtractor`가 질문에서 노드 키 후보를 만듭니다. 붙어 있는 단어 1~4개를 하나의 이름으로 보고 노드 타입 5종에 각각 키를 만듭니다. 정규화는 그래프를 만들 때 쓴 `GraphKeys.normalizeName`을 그대로 부릅니다 — 여기서 다르게 다듬으면 배치가 저장한 키와 어긋나 아무것도 못 찾습니다. LLM을 부르지 않아 같은 질문이면 늘 같은 후보가 나옵니다
- `TechGraphReader`(`datasource-mongodb`)가 aggregation 한 번으로 시드와 1홉 이웃을 가져옵니다
- `GraphEvidenceRanker`가 문서 단위로 접어 순위를 매깁니다. 여러 시드가 함께 가리킨 문서가 먼저입니다
- 그래프 문서 점수는 `벡터 최저점 ÷ (순위+1)`로 다시 매깁니다. 어떤 그래프 문서도 벡터 최저점을 넘지 못하므로 벡터 상위 자리를 밀어내지 않습니다
- 그래프 조회가 실패해도 벡터 결과만으로 답을 만듭니다

`chatbot.rag.graph.enabled`가 기본 꺼짐입니다. 켜고 측정한 결과 다중 홉 질문의 recall@5가 오르지 않았습니다 — 근거는 찾아왔지만 벡터가 이미 상위 5칸을 채워 6위 아래로만 들어갔고, 답변에 넘어가는 것은 상위 5건이기 때문입니다.

### 2-2. 근거가 약할 때 재검색 (기본 꺼짐)

벡터 후보가 없거나 후보 최고 `vectorScore`가 문턱(`min-vector-score`, 기본 0.72)에 못 미치면 검색 조건을 단계적으로 풉니다.

- 질의 문자열은 그대로 두고 provider·updateType 필터와 유사도 문턱만 완화합니다. 임베딩 호출만 한 번 더 나가고 채팅 호출은 늘지 않습니다
- 다시 찾은 결과는 1차 결과를 갈아치우지 않고 **뒤에 붙입니다**. 필터를 풀면 최신성 직접 쿼리까지 같이 바뀌어 재검색 결과가 1차를 다 담고 있지 않기 때문입니다
- 앞 단계와 실질적으로 같아지는 단계는 건너뜁니다

`chatbot.rag.augment.enabled`도 기본 꺼짐입니다. 켜고 측정했을 때 새 근거를 실제로 찾아내긴 했지만(`byVectorRank` recall@5 0.6825 → 0.6944) 사용자에게 가는 상위 5건에는 들어가지 못했습니다.

### 3. 세션 타이틀 자동생성

- 새 세션의 첫 메시지-응답 완료 후 `@Async` 비동기 LLM 호출로 3~5단어 타이틀 자동 생성
- 실패 시 예외 흡수 (메인 채팅 흐름에 영향 없음)
- 사용자 수동 변경 지원 (`PATCH /api/v1/chatbot/sessions/{sessionId}/title`)
- 기존 CQRS 인프라 활용 (title 필드, Kafka 이벤트 연동)

### 4. 멀티턴 대화 히스토리 관리

- 세션 기반 대화 컨텍스트 관리
- JWT 토큰 기반 사용자 인증 및 세션 소유권 검증
- 메시지 개수 기준 윈도우 관리 (`MessageWindowChatMemory`). 창 크기는 `chatbot.chat-memory.max-messages`(기본 10)로 정합니다
- 이력을 채우는 쪽은 `ChatbotServiceImpl.loadHistoryToMemory()`이고, ChatMemory에 저장소(`MongoDbChatMemoryStore`)를 걸지 않습니다. 저장소를 걸면 `MessageWindowChatMemory`가 메시지를 자기 안에 들고 있지 않고 매번 저장소를 다시 읽는데, 그 저장소의 쓰기 메서드는 로그만 찍는 no-op이라 방금 `add()`한 현재 질문이 프롬프트에서 빠집니다
- 일반 대화 경로는 대화 이력을 `List<ChatMessage>` 그대로 LLM에 넘깁니다. 예전에는 자바 컬렉션의 `toString()` 결과를 문자열로 붙였는데, 그러면 구조를 나타내는 `role=user`와 사용자가 쓴 텍스트를 구분할 근거가 없고 사용자 입력이 이스케이프 없이 섞였습니다

### 5. 의도 분류

- 4종 자동 분류: `LLM_DIRECT`(일반 대화) / `RAG_REQUIRED` / `WEB_SEARCH_REQUIRED` / `AGENT_COMMAND`
- LLM을 부르지 않는 키워드 규칙입니다. 한 턴당 LLM 호출 횟수에 영향을 주지 않습니다
- 영어 키워드는 `Pattern`으로 단어 경계(`\b`)를 확인하고, 한국어 키워드는 `Set.contains`로 부분 문자열을 봅니다. 한국어는 조사가 붙어 단어 경계가 생기지 않기 때문입니다. 단어 경계를 확인하기 전에는 `explain` 같은 영어 단어 안의 `ai`가 RAG 키워드로 잘못 걸렸습니다
- RAG 필요 시 하이브리드 벡터 검색 수행
- Agent 위임은 `@agent` 프리픽스 명령으로 동작하며 ADMIN 권한 사용자만 가능 (`api-agent`로 feign 위임)
- 일반 대화 시 LLM 직접 호출

### 5-1. Cohere Re-Ranking·웹 검색 (선택 기능, 기본 비활성)

- **Cohere Re-Ranking**: `rerank-multilingual-v3.0` 모델로 검색 결과 재순위. `COHERE_API_KEY` 설정 시 활성화
- **웹 검색**: Google Custom Search API 기반. `GOOGLE_SEARCH_API_KEY`/`GOOGLE_SEARCH_ENGINE_ID` 설정 시 활성화

### 6. 토큰 제어 및 비용 통제

- **토큰 사용량은 OpenAI가 응답에 실어 보내는 실측값으로 계측합니다.** `LLMServiceImpl`이 `ChatResponse.tokenUsage()`를 꺼내 `chatbot.llm.input.tokens`·`chatbot.llm.output.tokens` 미터에 남깁니다. 예전에는 질문 문자열만 추정해 세서 실제 입력 토큰의 2.7%(평균 17.9 대 660.4 토큰)만 잡혔습니다
- 사용량이 없거나 필드가 `null`이면 기록을 건너뜁니다. 0으로 채우면 실제 0토큰과 구분되지 않습니다
- 입력 토큰 상한(`chatbot.token.max-input-tokens`, 기본 4,000) 검증과 경고. 이 검증은 프롬프트 문자열을 추정해 재는 쪽이라 실측 미터와 별개이고, 일반 대화 경로는 검사를 거치지 않습니다
- 캐싱을 통한 중복 호출 방지
- `SessionTitleGenerationServiceImpl`은 `ChatModel`을 직접 부르므로 위 미터에 잡히지 않습니다. 미터 합계는 청구 총액이 아니라 하한입니다

미터 이름과 확인 방법은 [`monitoring/README.md`](../../monitoring/README.md) 5절에 있습니다.

### 7. 세션 생명주기 관리

- 비활성 세션 자동 비활성화 (30분 미사용 시)
- 만료된 세션 자동 처리 (90일 경과 시)
- 배치 작업을 통한 세션 정리

---

## 기술 스택

### Core Framework

- **Spring Boot**: 4.0.2
- **Java**: 21
- **Gradle**: 멀티모듈 빌드

### AI/ML 라이브러리

- **langchain4j**: 1.10.0 (mongodb-atlas·cohere 통합 모듈은 1.10.0-beta18)
  - LLM 통합 및 추상화
  - MongoDB Atlas Vector Search 통합
  - ChatMemory 관리
  - Cohere Re-Ranking (`rerank-multilingual-v3.0`, 기본 비활성)

### LLM Provider

- **OpenAI** (기본)
  - Model: GPT-4o-mini
  - Embedding Model: text-embedding-3-small

### 데이터베이스

- **MongoDB Atlas**: Vector Search를 위한 문서 저장소
- **Aurora MySQL**: 세션 및 메시지 히스토리 저장

### 캐싱

- **Redis**: 검색 결과 및 임베딩 캐싱

### 인증

- **Spring Security**: JWT 토큰 기반 인증
- **JWT**: 사용자 식별 및 권한 확인

---

## API 엔드포인트

### 1. 챗봇 대화

**POST** `/api/v1/chatbot`

사용자 메시지를 받아 챗봇 응답을 생성합니다.

**Request Body:**
```json
{
  "message": "최근 AI 관련 대회 정보를 알려주세요",
  "conversationId": "optional-session-id"
}
```

**Response:**
```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "success" },
  "message": "success",
  "data": {
    "response": "최근 AI 업데이트 정보는 다음과 같습니다...",
    "conversationId": "session-id",
    "title": "AI 업데이트 질문",
    "sources": [
      {
        "documentId": "665f...",
        "collectionType": "emerging_techs",
        "score": 0.87,
        "title": "OpenAI GPT-4o 업데이트",
        "url": "https://example.com/release"
      }
    ]
  }
}
```

응답에 토큰 사용량은 포함되지 않습니다.

### 2. 세션 목록 조회

**GET** `/api/v1/chatbot/sessions?page=1&size=20`

사용자의 대화 세션 목록을 조회합니다.

**Query Parameters:**
- `page`: 페이지 번호 (기본값: 1)
- `size`: 페이지 크기 (기본값: 20)

**Response:**
```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "success" },
  "message": "success",
  "data": {
    "pageSize": 20,
    "pageNumber": 1,
    "totalPageNumber": 1,
    "totalSize": 10,
    "list": [
      {
        "sessionId": "session-id",
        "title": "AI 관련 질문",
        "createdAt": "2024-01-16T10:00:00",
        "lastMessageAt": "2024-01-16T10:05:00",
        "isActive": true
      }
    ]
  }
}
```

### 3. 세션 상세 조회

**GET** `/api/v1/chatbot/sessions/{sessionId}`

특정 세션의 상세 정보를 조회합니다.

**Response:**
```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "success" },
  "message": "success",
  "data": {
    "sessionId": "session-id",
    "title": "AI 관련 질문",
    "createdAt": "2024-01-16T10:00:00",
    "lastMessageAt": "2024-01-16T10:05:00",
    "isActive": true
  }
}
```

### 4. 메시지 히스토리 조회

**GET** `/api/v1/chatbot/sessions/{sessionId}/messages?page=1&size=50`

특정 세션의 메시지 히스토리를 조회합니다.

**Query Parameters:**
- `page`: 페이지 번호 (기본값: 1)
- `size`: 페이지 크기 (기본값: 50)

**Response:**
```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "success" },
  "message": "success",
  "data": {
    "pageSize": 50,
    "pageNumber": 1,
    "totalPageNumber": 1,
    "totalSize": 2,
    "list": [
      {
        "messageId": "message-id",
        "sessionId": "session-id",
        "role": "USER",
        "content": "최근 AI 업데이트 정보를 알려주세요",
        "sequenceNumber": 1,
        "createdAt": "2024-01-16T10:00:00"
      },
      {
        "messageId": "message-id-2",
        "sessionId": "session-id",
        "role": "ASSISTANT",
        "content": "최근 AI 업데이트 정보는 다음과 같습니다...",
        "sequenceNumber": 2,
        "createdAt": "2024-01-16T10:00:05"
      }
    ]
  }
}
```

### 5. 세션 타이틀 수정

**PATCH** `/api/v1/chatbot/sessions/{sessionId}/title`

세션의 타이틀을 수정합니다.

**Request Body:**
```json
{
  "title": "AI 트렌드 대화"
}
```

**Response:**
```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "sessionId": "session-id",
    "title": "AI 트렌드 대화",
    "createdAt": "2024-01-16T10:00:00",
    "lastMessageAt": "2024-01-16T10:05:00",
    "isActive": true
  }
}
```

### 6. 세션 삭제

**DELETE** `/api/v1/chatbot/sessions/{sessionId}`

특정 세션을 삭제합니다.

**Response:**
```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

### 인증

모든 API 엔드포인트는 JWT 토큰 기반 인증이 필요합니다.

- **Authorization Header**: `Bearer {jwt-token}`
- **userId**: JWT 토큰에서 자동 추출 (요청 본문에 포함하지 않음)
- **세션 소유권 검증**: 모든 세션 관련 작업은 소유권 검증 필수

---

## 설정

### application-chatbot-api.yml

서버 포트는 8084, 로컬 MySQL은 3310(chatbot 스키마)을 쓰며, `application.yml`의 `profiles.include`에 `kafka`, `feign-internal` 프로필이 포함됩니다.

```yaml
spring:
  application:
    name: chatbot-api
  profiles:
    include:
      - common-core
      - api-domain
      - mongodb-domain

module:
  aurora:
    schema: chatbot
    port: 3310

langchain4j:
  open-ai:
    chat-model:
      api-key: ${OPENAI_API_KEY}
      model-name: gpt-4o-mini
      temperature: 0.7
      max-tokens: 2000
      timeout: 60s
    embedding-model:
      api-key: ${OPENAI_API_KEY}
      model-name: text-embedding-3-small
      dimensions: 1536
      timeout: 30s

chatbot:
  rag:
    max-search-results: 5
    min-similarity-score: 0.7
    max-context-tokens: 3000
    recency-months: 6      # 최신성 키워드 감지 시 검색 기간 (개월)

    graph:                 # 지식 그래프 검색 (기본 꺼둠 — 평가 잡에서만 켠다)
      enabled: false
      max-results: 10        # 그래프가 돌려줄 문서 최대 개수
      max-seeds: 20          # 질문이 맞춘 시드 노드 최대 개수
      max-edges-per-seed: 20 # 시드 하나당 훑을 엣지 최대 개수
      max-time-ms: 2000      # 그래프 조회 실행 시간 상한

    augment:               # 근거가 약할 때 조건을 완화해 재검색 (기본 꺼둠)
      enabled: false
      max-attempts: 2        # 재검색 최대 횟수
      min-vector-score: 0.72 # 이 값 미만이면 근거가 약하다고 본다
      relaxed-min-score: 0.5 # 2단계에서 낮출 유사도 문턱

  reranking:
    enabled: false         # Cohere Re-Ranking (COHERE_API_KEY 설정 시 활성화)
    model-name: rerank-multilingual-v3.0

  web-search:
    enabled: false         # Google Custom Search (API 키 설정 시 활성화)
  
  input:
    max-length: 500
    min-length: 1
  
  token:
    max-input-tokens: 4000
    max-output-tokens: 2000
    warning-threshold: 0.8
  
  cache:
    enabled: true
    ttl-hours: 1
    max-size: 1000
  
  session:
    inactive-threshold-minutes: 30
    expiration-days: 90
    batch-enabled: true
  
  chat-memory:
    max-messages: 10   # 창에 남길 최대 메시지 수 (이력 조회가 최근 50건까지라 50을 넘겨도 더 오지 않는다)
```

### 환경 변수

- `OPENAI_API_KEY`: OpenAI API 키
- `MONGODB_ATLAS_CONNECTION_STRING`: MongoDB Atlas 연결 문자열
- `MONGODB_ATLAS_DATABASE`: MongoDB Atlas 데이터베이스 이름
- `COHERE_API_KEY`: Cohere Re-Ranking용 API 키 (선택)
- `GOOGLE_SEARCH_API_KEY` / `GOOGLE_SEARCH_ENGINE_ID`: Google Custom Search 웹 검색용 (선택)

---

## 의존성

### build.gradle

langchain4j 의존성에는 버전을 적지 않습니다. 루트 `build.gradle`이 `langchain4j-bom`을 import해 한곳에서 정하고, stable 모듈은 1.10.0, `mongodb-atlas`·`cohere` 같은 beta 모듈은 1.10.0-beta18로 해석됩니다.

```gradle
dependencies {
    // 버전은 루트 build.gradle의 langchain4j-bom이 정한다
    implementation 'dev.langchain4j:langchain4j'
    implementation 'dev.langchain4j:langchain4j-mongodb-atlas'
    implementation 'dev.langchain4j:langchain4j-open-ai'
    implementation 'dev.langchain4j:langchain4j-cohere'

    // 프로젝트 모듈 의존성
    implementation project(':common-core')
    implementation project(':common-exception')
    implementation project(':common-conversation')
    implementation project(':common-kafka')
    implementation project(':common-security')
    implementation project(':client-feign')
    implementation project(':datasource-aurora')
    implementation project(':datasource-mongodb')
}
```

`bootJar`과 함께 `jar.enabled = true`도 켜 둡니다. `batch-eval`이 이 모듈을 의존성으로 가져와 운영과 같은 검색 코드를 타기 때문입니다. 꺼 두면 소비 모듈이 클래스가 없는 `-plain.jar`를 가리켜 조용히 빠집니다.

---

## 구현 구조

### 패키지 구조

```
api/chatbot/
├── src/main/java/com/tech/n/ai/api/chatbot/
│   ├── ApiChatbotApplication.java
│   ├── config/
│   │   ├── LangChain4jConfig.java
│   │   ├── SchedulerConfig.java
│   │   ├── ServerConfig.java
│   │   └── WebSearchConfig.java
│   ├── controller/
│   │   └── ChatbotController.java
│   ├── facade/
│   │   └── ChatbotFacade.java
│   ├── service/
│   │   ├── ChatbotService.java
│   │   ├── InputPreprocessingService.java
│   │   ├── IntentClassificationService.java
│   │   ├── RetrievalService.java            # 벡터·그래프·재검색을 묶는 검색 진입점
│   │   ├── VectorSearchService.java
│   │   ├── SearchOptionsFactory.java        # 검색 옵션 조립 (운영·평가 공용)
│   │   ├── GraphSearchService.java          # 지식 그래프 검색 (기본 꺼짐)
│   │   ├── GraphSeedExtractor.java          # 질문 → 노드 키 후보
│   │   ├── GraphEvidenceRanker.java         # 그래프 결과를 문서 단위로 접어 순위 매김
│   │   ├── PromptService.java
│   │   ├── LLMService.java
│   │   ├── TokenService.java
│   │   ├── CacheService.java
│   │   ├── ReRankingService.java (CohereReRankingServiceImpl)
│   │   ├── WebSearchService.java
│   │   ├── AgentDelegationService.java
│   │   ├── SessionTitleGenerationService.java
│   │   └── dto/                             # SearchOutcome, GraphSearchOutcome,
│   │                                        # RetrievalOutcome, RetrievalPath, AugmentOutcome 등
│   ├── chain/
│   │   ├── InputInterpretationChain.java
│   │   ├── ResultRefinementChain.java
│   │   └── AnswerGenerationChain.java
│   ├── memory/
│   │   └── ConversationChatMemoryProvider.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── ChatRequest.java
│   │   │   ├── SessionListRequest.java
│   │   │   ├── MessageListRequest.java
│   │   │   └── UpdateSessionTitleRequest.java
│   │   └── response/
│   │       ├── ChatResponse.java
│   │       ├── SessionListResponse.java
│   │       ├── MessageListResponse.java
│   │       └── SourceResponse.java
│   ├── common/
│   │   └── exception/
│   │       ├── ChatbotExceptionHandler.java
│   │       ├── InvalidInputException.java
│   │       └── TokenLimitExceededException.java
│   └── scheduler/
│       └── ConversationSessionLifecycleScheduler.java
└── src/main/resources/
    └── application-chatbot-api.yml
```

### 주요 컴포넌트

#### 1. Controller Layer

- **ChatbotController**: RESTful API 엔드포인트 제공
  - JWT 토큰에서 `userId` 추출
  - 요청/응답 DTO 변환

#### 2. Facade Layer

- **ChatbotFacade**: Controller와 Service 사이의 중간 계층
  - `userId`를 파라미터로 받아 서비스에 전달

#### 3. Service Layer

- **ChatbotService**: 챗봇 응답 생성 오케스트레이션
- **InputPreprocessingService**: 입력 전처리 및 검증
- **IntentClassificationService**: 의도 분류 (RAG 필요 여부)
- **RetrievalService**: 검색 단계 진입점. 벡터 검색을 돌리고, 켜져 있으면 그래프 결과와 재검색 결과를 뒤에 붙인다. 운영과 `batch-eval`이 이 클래스를 함께 탄다
- **VectorSearchService**: MongoDB Atlas Vector Search 수행. `SearchOutcome`으로 검색 경로·RRF 직전 후보·최신성 쿼리 실패 여부·최종 결과를 함께 돌려준다
- **SearchOptionsFactory**: 검색 옵션 조립. 운영과 평가가 같은 옵션을 쓰게 한다
- **GraphSearchService / GraphSeedExtractor / GraphEvidenceRanker**: 지식 그래프 검색 (기본 꺼짐)
- **PromptService**: 프롬프트 구축 및 최적화
- **LLMService**: LLM 호출 및 응답 처리. `generate(String)`과 `generate(List<ChatMessage>)` 두 오버로드가 같은 통로로 지연시간·실패·토큰 미터를 남긴다
- **TokenService**: 프롬프트 토큰 추정과 입력 상한 검증 (실측 토큰 계측은 `LLMServiceImpl`이 담당)
- **CacheService**: 검색 결과 및 임베딩 캐싱
- **ReRankingService**: Cohere 기반 검색 결과 재순위 (기본 비활성)
- **WebSearchService**: Google Custom Search 웹 검색 (기본 비활성)
- **AgentDelegationService**: `@agent` 명령을 api-agent로 feign 위임 (ADMIN 전용)
- **SessionTitleGenerationService**: 비동기 세션 타이틀 자동생성 (LLM 호출)

세션·메시지 CRUD(`ConversationSessionService`, `ConversationMessageService`)와 `MongoDbChatMemoryStore`는 `common-conversation` 모듈이 제공합니다.

#### 4. Chain Layer

- **InputInterpretationChain**: 입력 해석 및 검색 쿼리 추출
- **ResultRefinementChain**: 검색 결과 정제 (유사도 필터링, 중복 제거)
- **AnswerGenerationChain**: 최종 답변 생성

#### 5. Memory Layer

- **ConversationChatMemoryProvider**: 요청마다 새 `MessageWindowChatMemory`를 만들어 준다. 창 크기는 `chatbot.chat-memory.max-messages`(기본 10). 저장소를 걸지 않는 이유는 위 [4. 멀티턴 대화 히스토리 관리](#4-멀티턴-대화-히스토리-관리)에 있다

---

## 참고 자료

### 공식 문서

#### langchain4j

- **공식 문서**: https://docs.langchain4j.dev/
- **GitHub**: https://github.com/langchain4j/langchain4j
- **MongoDB Atlas 통합**: https://docs.langchain4j.dev/integrations/embedding-stores/mongodb-atlas

#### MongoDB Atlas Vector Search

- **공식 문서**: https://www.mongodb.com/docs/atlas/atlas-vector-search/
- **Vector Search Index 생성**: https://www.mongodb.com/docs/atlas/atlas-vector-search/create-index/

#### OpenAI

- **공식 문서**: https://platform.openai.com/docs
- **Chat Completions API**: https://platform.openai.com/docs/guides/chat-completions
- **GPT-4o-mini**: https://platform.openai.com/docs/models/gpt-4o-mini
- **Embeddings**: https://platform.openai.com/docs/guides/embeddings
- **text-embedding-3-small**: https://platform.openai.com/docs/models/text-embedding-3-small
- **Pricing**: https://openai.com/api/pricing/

#### Spring Security

- **공식 문서**: https://docs.spring.io/spring-security/reference/index.html
- **JWT 공식 스펙 (RFC 7519)**: https://tools.ietf.org/html/rfc7519

### 프로젝트 내 참고 문서

- **RAG 챗봇 설계서**: `docs/step12/rag-chatbot-design.md`
- **Emerging Tech 전용 RAG 검색 개선 설계서**: `docs/reference/api-chatbot/1-emerging-tech-rag-redesign.md`
- **하이브리드 검색 Score Fusion 설계서**: `docs/reference/api-chatbot/2-hybrid-search-score-fusion-design.md`
- **세션 타이틀 자동생성 설계서**: `docs/reference/api-chatbot/3-session-title-generation-design.md`
- **Chatbot API 명세서**: `docs/reference/API-SPECIFICATIONS/api-chatbot-specification.md`
- **MongoDB 스키마 설계**: `docs/step1/2. mongodb-schema-design.md`
- **CQRS Kafka 동기화 설계**: `docs/step11/cqrs-kafka-sync-design.md`
- **API 엔드포인트 설계**: `docs/step2/1. api-endpoint-design.md`
- **Spring Security 인증 설계**: `docs/step6/spring-security-auth-design-guide.md`

---

## 버전 정보

- **langchain4j**: 1.10.0 (mongodb-atlas·cohere 통합 모듈은 1.10.0-beta18)
- **Spring Boot**: 4.0.2
- **Java**: 21
- **MongoDB Atlas**: 8.0

---

**문서 버전**: 1.3
**최종 업데이트**: 2026-08-18

