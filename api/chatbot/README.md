# Chatbot API Module

langchain4j 기반 RAG 챗봇 서비스다. MongoDB Atlas Vector Search로 Emerging Tech(AI 업데이트) 문서를 검색해 자연어 질문에 답한다. 하이브리드 검색(Score Fusion + RRF)으로 최신 문서 누락을 막고, 세션 기반 멀티턴 대화와 세션 타이틀 자동생성을 지원한다.

## 주요 기능

- **Emerging Tech 전용 RAG 검색**: `emerging_techs` 컬렉션 대상 Vector Search, `status: PUBLISHED` pre-filter, 메타데이터(provider, publishedAt, title, url)를 프롬프트에 포함
- **하이브리드 검색 (Score Fusion + RRF)**: 순수 벡터 유사도만으로는 최신 문서가 누락될 수 있어, 벡터 검색과 최신성 직접 쿼리를 결합
- **세션 타이틀 자동생성**: 새 세션의 첫 메시지-응답 완료 후 `@Async` LLM 호출로 3~5단어 타이틀 생성. 실패 시 예외를 흡수해 메인 채팅 흐름에 영향 없음. 사용자 수동 변경도 지원
- **멀티턴 대화**: `common-conversation` 모듈(Aurora 쓰기 + MongoDB 읽기) 기반 세션·메시지 관리, JWT 인증과 세션 소유권 검증
- **의도 분류**: 질문을 4종으로 자동 분류해 처리 경로를 나눔
- **토큰 제어**: 입력/출력 토큰 추적과 제한, Redis 캐싱으로 중복 호출 방지
- **세션 생명주기**: 30분 미사용 시 비활성화, 90일 경과 시 만료 (스케줄러 배치)
- **Provider별 메시지 포맷 변환**: OpenAI(기본), Anthropic(대안)
- **Re-Ranking·웹 검색 (선택, 기본 비활성)**: Cohere 재순위, Google Custom Search

## RAG 파이프라인

입력 전처리 → 의도 분류 → (RAG 필요 시) 입력 해석(검색 쿼리 추출) → 벡터 검색 → 결과 정제(유사도 필터링·중복 제거) → 답변 생성.

- **LLM**: OpenAI GPT-4o-mini (temperature 0.7, max-tokens 2000)
- **Embedding**: OpenAI text-embedding-3-small, 1536 dimensions
- **하이브리드 검색**: MongoDB Pipeline 내 Score Fusion(`$vectorSearch` → `$addFields(recencyScore)` Exponential Decay → `$addFields(combinedScore)` → `$sort` → `$limit`)과 `published_at` DESC 직접 쿼리를 Reciprocal Rank Fusion(k=60)으로 결합. MongoDB 8.2/8.3의 `$rankFusion`/`$scoreFusion` 효과를 8.0 환경에서 재현
- **의도 분류 4종**: `LLM_DIRECT` / `RAG_REQUIRED` / `WEB_SEARCH_REQUIRED` / `AGENT_COMMAND`. `@agent` 프리픽스 메시지는 `client-feign`으로 `api-agent`에 위임 (ADMIN 역할만 가능)
- **ChatMemory**: 설정상 기본 전략은 토큰 수 기준(`token-window`)이지만, 현재 구현은 메시지 개수 기준 `MessageWindowChatMemory`(최대 10개)로 동작 (토큰 기준 전환은 TODO)
- **Cohere Re-Ranking** (기본 비활성): `chatbot.reranking.enabled: true`일 때 결과 정제 체인에서 `rerank-multilingual-v3.0` 모델로 재순위
- **Google Custom Search** (기본 비활성): `WEB_SEARCH_REQUIRED` 의도에 대해 웹 검색 결과를 프롬프트에 넣어 답변. 검색 실패 시 LLM 직접 호출로 fallback

## API 엔드포인트

모든 엔드포인트는 JWT 인증이 필요하다 (`Authorization: Bearer {jwt-token}`). `userId`는 토큰에서 추출하고, 세션 관련 작업은 소유권을 검증한다.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/v1/chatbot` | 챗봇 대화 (message, 선택적 conversationId) |
| GET | `/api/v1/chatbot/sessions?page=1&size=20` | 세션 목록 조회 |
| GET | `/api/v1/chatbot/sessions/{sessionId}` | 세션 상세 조회 |
| GET | `/api/v1/chatbot/sessions/{sessionId}/messages?page=1&size=50` | 메시지 히스토리 조회 |
| PATCH | `/api/v1/chatbot/sessions/{sessionId}/title` | 세션 타이틀 수정 |
| DELETE | `/api/v1/chatbot/sessions/{sessionId}` | 세션 삭제 |

대표 응답 (POST `/api/v1/chatbot`):

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "response": "최근 AI 관련 업데이트 정보는 다음과 같습니다...",
    "conversationId": "session-id",
    "title": null,
    "sources": [
      { "documentId": "...", "collectionType": "EMERGING_TECH", "score": 0.87, "title": "...", "url": "..." }
    ]
  }
}
```

토큰 사용량은 응답에 포함하지 않고 서버에서 세션별로 추적만 한다.

## 설정

서버 포트 8084, Aurora 스키마 `chatbot` (로컬 MySQL 컨테이너 포트 3310). 프로필 include: `common-core`, `kafka`, `api-domain`, `mongodb-domain`, `feign-internal`, `chatbot-api`.

`application-chatbot-api.yml` 핵심 키:

- `langchain4j.open-ai.chat-model`: `gpt-4o-mini`, temperature 0.7, max-tokens 2000, timeout 60s
- `langchain4j.open-ai.embedding-model`: `text-embedding-3-small`, dimensions 1536, timeout 30s
- `chatbot.rag`: max-search-results 5, min-similarity-score 0.7, max-context-tokens 3000, recency-months 6
- `chatbot.reranking`: enabled `false`, model `rerank-multilingual-v3.0`, min-score 0.3
- `chatbot.web-search`: enabled `false`, max-results 5
- `chatbot.input`: max-length 500, min-length 1
- `chatbot.token`: max-input-tokens 4000, max-output-tokens 2000, warning-threshold 0.8
- `chatbot.cache`: enabled true, ttl-hours 1, max-size 1000
- `chatbot.session`: inactive-threshold-minutes 30, expiration-days 90, batch-enabled true
- `chatbot.chat-memory`: max-tokens 2000, strategy `token-window`

### 환경 변수

- `OPENAI_API_KEY`: OpenAI API 키
- `COHERE_API_KEY`: Cohere API 키 (Re-Ranking 사용 시)
- `GOOGLE_SEARCH_API_KEY`, `GOOGLE_SEARCH_ENGINE_ID`: Google Custom Search API (Web 검색 사용 시)
- `MONGODB_ATLAS_CONNECTION_STRING`, `MONGODB_ATLAS_DATABASE`: MongoDB Atlas 연결 정보

## 의존성

- **langchain4j 1.10.0** (`langchain4j`, `langchain4j-open-ai`). 통합 모듈 `langchain4j-mongodb-atlas`, `langchain4j-cohere`는 `1.10.0-beta18`
- 프로젝트 모듈: `common-core`, `common-exception`, `common-conversation`, `common-security`, `common-kafka`, `datasource-aurora`, `datasource-mongodb`, `client-feign`

`common-conversation`은 세션·메시지 저장과 `MongoDbChatMemoryStore`를 제공하며 `api-agent`와 공유한다. `client-feign`은 Agent 위임 시 `api-agent` 내부 호출에 쓴다.

## 구현 구조 (요약)

`ChatbotController` → `ChatbotFacade` → `ChatbotService`가 의도별로 분기한다 (LLM 직접 / RAG / Web 검색 / Agent 위임).

- **service**: InputPreprocessingService(입력 검증), IntentClassificationService(의도 분류), VectorSearchService(하이브리드 벡터 검색), PromptService, LLMService, TokenService, CacheService, ReRankingService(`CohereReRankingServiceImpl`), WebSearchService, AgentDelegationService, SessionTitleGenerationService
- **chain**: InputInterpretationChain / ResultRefinementChain / AnswerGenerationChain
- **memory**: ConversationChatMemoryProvider — 세션별 `MessageWindowChatMemory`(최대 10개) 제공
- **converter**: OpenAiMessageConverter / AnthropicMessageConverter
- **scheduler**: ConversationSessionLifecycleScheduler — 세션 비활성화·만료 배치

세션·메시지 관련 코드(`ConversationSessionService`, `ConversationMessageService`, `MongoDbChatMemoryStore` 등)는 이 모듈이 아니라 `common-conversation` 모듈에 있다.

## 참고 자료

- langchain4j: https://docs.langchain4j.dev/
- MongoDB Atlas Vector Search: https://www.mongodb.com/docs/atlas/atlas-vector-search/
- 프로젝트 내 설계 문서: `docs/step12/rag-chatbot-design.md`, `docs/reference/api-chatbot/` (RAG 개선·하이브리드 검색·타이틀 자동생성 설계서), `docs/reference/API-SPECIFICATIONS/api-chatbot-specification.md`

버전: Spring Boot 4.0.2 · Java 21 · langchain4j 1.10.0 · MongoDB Atlas 8.0
