# API Agent 모듈

LangChain4j 기반 Emerging Tech 데이터 분석 및 업데이트 추적 AI Agent 서비스입니다.

## 개요

`api-agent`는 LangChain4j와 OpenAI(gpt-4o-mini)로 빅테크 AI 서비스(OpenAI, Anthropic, Google, Meta, xAI)의 업데이트를 자율 추적·분석하는 Agent입니다. 자연어 목표(Goal)를 받아 GitHub Release 수집, 웹 스크래핑/RSS, 통계 집계, 키워드 빈도 분석을 자동 수행하고 결과를 Markdown 표와 Mermaid 차트로 정리합니다. ADMIN JWT 인증과 `sessionId` 기반 멀티턴 대화를 지원하며, 6시간 주기 스케줄러로도 실행됩니다(실행 실패 시 Slack 알림).

실행 흐름: REST API 또는 스케줄러 → `AgentFacade` → `EmergingTechAgentImpl` → `AgentAssistant`(LangChain4j AiServices)가 OpenAI Function Calling으로 Tool을 자율 선택해 호출합니다. Tool 어댑터는 각각 api-emerging-tech(내부 API), MongoDB Aggregation, Slack, GitHub API/RSS/웹 스크래퍼와 연결됩니다.

## LangChain4j Tools (9개)

| 분류 | Tool | 설명 | 주요 파라미터 |
|------|------|------|---------------|
| 조회 | `search_emerging_techs` | 키워드로 Emerging Tech 데이터 검색 | `query`(필수), `provider` |
| 조회 | `list_emerging_techs` | 기간/Provider/UpdateType/SourceType/Status 필터 + 페이징 목록 조회 | `startDate`, `endDate`, `provider`, `updateType`, `sourceType`, `status`, `page`, `size` |
| 조회 | `get_emerging_tech_detail` | MongoDB ObjectId 기반 상세 조회 | `id`(필수, 24자 hex) |
| 분석 | `get_emerging_tech_statistics` | Provider/SourceType/UpdateType별 통계 집계 (MongoDB Aggregation) | `groupBy`(필수), `startDate`, `endDate` |
| 분석 | `analyze_text_frequency` | title/summary 키워드 빈도 분석 (영어 불용어 필터링, `AnalyticsConfig.stopWords`로 오버라이드) | `provider`, `updateType`, `sourceType`, `startDate`, `endDate`, `topN`(기본20, 최대100) |
| 알림 | `send_slack_notification` | Slack 채널(#emerging-tech) 알림 전송 (현재 비활성화 - Mock 응답) | `message`(필수) |
| 수집 | `collect_github_releases` | GitHub 릴리스 수집 → DB 저장 (화이트리스트 기반) | `owner`(필수), `repo`(필수) |
| 수집 | `collect_rss_feeds` | OpenAI/Google 블로그 RSS 피드 수집 → DB 저장 | `provider`(선택: OPENAI, GOOGLE) |
| 수집 | `collect_scraped_articles` | Anthropic/Meta 블로그 웹 스크래핑 수집 → DB 저장 | `provider`(선택: ANTHROPIC, META) |

> 분석 Tool은 실행 시 `ChartData`(pie/bar)를 함께 수집해 응답에 포함합니다. 동일 인자 반복 호출은 `ToolExecutionMetrics`(ThreadLocal)가 차단하며, 한도를 넘으면 `AgentLoopDetectedException`으로 강제 종료합니다.

## API 엔드포인트

모든 Agent API는 ADMIN 역할 JWT 인증이 필요합니다. Gateway가 먼저 검증하고, 서비스 자체에서도 `common-security`의 JWT 필터가 다시 검증합니다(`/api/v1/agent/**`는 ADMIN 전용). 컨트롤러는 JWT에서 꺼낸 `UserPrincipal`의 userId로 세션 소유자를 식별합니다.

| Method | Endpoint | 설명 |
|--------|---------|------|
| POST | `/api/v1/agent/run` | Agent 실행 (자연어 목표 입력) |
| GET | `/api/v1/agent/sessions` | 세션 목록 조회 (페이징) |
| GET | `/api/v1/agent/sessions/{sessionId}` | 세션 상세 조회 |
| GET | `/api/v1/agent/sessions/{sessionId}/messages` | 대화 이력 조회 (페이징) |
| PATCH | `/api/v1/agent/sessions/{sessionId}/title` | 세션 타이틀 수동 변경 |
| DELETE | `/api/v1/agent/sessions/{sessionId}` | 세션 삭제 |

실행 요청은 `goal`(자연어 목표)과 선택 `sessionId`를 받습니다. `sessionId`를 생략하면 새 세션이 생성되어 응답으로 반환되고, 같은 `sessionId`로 재요청하면 이전 대화 맥락을 유지합니다. 응답에는 `summary`(Markdown), `toolCallCount`, `analyticsCallCount`, `executionTimeMs`, `chartData`가 담깁니다.

## 설정

`application.yml`: 포트 8086, 프로필 include(`common-core`, `kafka`, `api-domain`, `agent-api`, `mongodb-domain`, `feign-github`, `feign-internal`, `slack`, `scraper`, `rss`). Aurora 스키마는 `chatbot`을 공유하며 로컬 MySQL 포트는 `MYSQL_PORT`(기본 3310)로 지정합니다 — 즉 chatbot MySQL 컨테이너를 함께 씁니다.

`application-agent-api.yml` 핵심 키:

- `langchain4j.open-ai.chat-model`: `api-key=${OPENAI_API_KEY:}`, `model-name=gpt-4o-mini`, `temperature=0.3`, `max-tokens=4096`, `timeout=120`
- `internal-api.emerging-tech.api-key=${EMERGING_TECH_INTERNAL_API_KEY:}`
- `agent.scheduler`: `enabled=${AGENT_SCHEDULER_ENABLED:false}` (기본 비활성), `cron="0 0 */6 * * *"` (6시간 주기)
- `agent.analytics`: `default-top-n=20`, `max-top-n=100`
- `agent.slack.enabled=false` (true로 변경 시 실제 발송, 기본 Mock)

### 환경 변수

| 변수명 | 설명 | 필수 |
|--------|------|------|
| `OPENAI_API_KEY` | OpenAI API 키 | Yes |
| `EMERGING_TECH_INTERNAL_API_KEY` | emerging-tech 및 Agent API 인증 키 | Yes |
| `AGENT_SCHEDULER_ENABLED` | 스케줄러 활성화 (true/false) | No |
| `GITHUB_TOKEN` | GitHub API 토큰 (Rate Limit 완화) | No |

## 대상 AI 서비스 (GitHub 화이트리스트)

`ToolInputValidator`로 아래 조합만 허용하며, 목록 밖 저장소 호출은 거부합니다.

| Provider | Owner | Repository |
|----------|-------|------------|
| OpenAI | openai | openai-python, whisper, tiktoken |
| Anthropic | anthropics | anthropic-sdk-python, claude-code |
| Google | google | generative-ai-python, gemma.cpp |
| Google | google-deepmind | gemma |
| Meta | meta-llama | llama-models, llama-stack |
| xAI | xai-org | grok-1 |

## 빌드·실행

```bash
./gradlew :api-agent:build     # 빌드
./gradlew :api-agent:bootRun   # 실행 (8086)
./gradlew :api-agent:test      # 테스트
```

주요 의존성: `langchain4j`/`langchain4j-open-ai` 1.10.0(Tool Error Handler 지원), `common-conversation`(세션/메시지 저장), `datasource-aurora`/`datasource-mongodb`, `client-feign`/`client-slack`/`client-scraper`/`client-rss`, jsoup.

## 연동 모듈

- **api-emerging-tech**: 업데이트 검색 API 제공
- **datasource-mongodb**: MongoDB Aggregation 기반 통계/빈도 집계
- **client-feign**: GitHub API, Internal API Feign 클라이언트
- **client-slack** / **client-scraper** / **client-rss**: Slack 알림, 웹 크롤링, RSS 수집

## 참고 자료

- 설계 문서: [Agent 파이프라인 설계서 (Phase 1~7)](../../docs/reference/agent-pipeline/) · [테스트 결과](../../docs/reference/agent-pipeline/tests/)
- 공식 문서: [LangChain4j](https://docs.langchain4j.dev/) · [Tools](https://docs.langchain4j.dev/tutorials/tools) · [AI Services](https://docs.langchain4j.dev/tutorials/ai-services)
