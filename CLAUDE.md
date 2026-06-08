# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Cursor IDE 사용자도 이 파일을 참고합니다 (별도 `.cursorrules` 없음).

## 작업 원칙

### 응답 언어
- 가능하면 사용자에게 보여지는 응답·설명·요약은 한국어로 번역해 출력한다.
- 단, 코드, 식별자, 명령어, 파일 경로, 로그·에러 메시지 원문은 번역하지 않고 원형을 유지한다.

### 사람이 검증하는 텍스트 작성 규칙
주석, commit message, pull request 본문, 보고서, 설계 문서처럼 사람이 읽고 검증하는 모든 텍스트를 작성할 때 다음을 지킨다.

- LLM이 흔히 쓰는 상투적 표현을 피한다. (예: "원활하게", "견고한", "포괄적인", "~를 활용하여", 과한 강조 등)
- 개발자가 실제로 쓰지 않는 어휘를 피한다. 뜻을 지나치게 압축한 조어, 한자어 남발, 번역투, 사전에는 있어도 현업 대화에서 안 쓰는 단어는 더 쉽고 흔한 표현으로 바꾼다. (예: "정합성을 담보" → "데이터가 맞는지 확인", "기동" → "실행")
- 한 번 읽고 바로 이해되지 않는 문장은 풀어 쓴다. 독자가 멈춰서 다시 해석해야 한다면 잘못 쓴 것이다.
- 키워드를 나열하지 말고, 동료 개발자는 물론 비개발자 독자도 이해할 수 있도록 서술형 문장으로 풀어 쓴다.
- 불필요하게 과하게 설계하거나 부풀려 쓰지 않는다. 필요한 만큼만 쓴다.

판단 기준: 작성한 텍스트를 그 분야를 모르는 동료에게 소리 내어 읽어준다고 가정한다. 그대로 알아들으면 통과, 단어를 바꿔 설명해야 하면 다시 쓴다.

### 오버엔지니어링 금지
- 요청된 작업 범위에 집중한다. 요청하지 않은 리팩토링이나 기능 추가를 하지 않는다.
- 현재 필요하지 않은 추상화나 미래 대비 코드를 작성하지 않는다.

### 외부 자료 참조 원칙
- 공식 문서와 공식 저장소만 참조한다. 비공식 블로그, 포럼, AI 생성 콘텐츠를 근거로 사용하지 않는다.
- 기술 논문은 arXiv/ACM/IEEE/Springer 등 공인 학술 플랫폼 게시본만 사용하고, 제목·저자·발행처·URL을 명시한다.
- 확인되지 않은 정보를 사실처럼 제시하지 않는다. 불확실하면 명시적으로 알린다.

## Coding Behavioral Guidelines

LLM 코딩 실수를 줄이기 위한 행동 지침. 프로젝트별 지침과 함께 적용한다.
Tradeoff: 이 지침은 속도보다 신중함에 무게를 둔다. 사소한 작업에는 판단껏 적용한다.

### 1. Think Before Coding
가정하지 말 것. 헷갈림을 숨기지 말 것. 트레이드오프를 드러낼 것.
- 가정은 명시한다. 불확실하면 묻는다.
- 해석이 여러 갈래면 모두 제시한다. 조용히 하나만 고르지 않는다.
- 더 단순한 방법이 있으면 말한다. 근거가 있으면 반대 의견을 낸다.
- 불명확하면 멈춘다. 무엇이 헷갈리는지 짚고 묻는다.

### 2. Simplicity First
문제를 푸는 최소한의 코드만. 짐작으로 미리 만들지 않는다.
- 요청한 것 이상의 기능을 넣지 않는다.
- 한 번만 쓰는 코드에 추상화를 만들지 않는다.
- 요청하지 않은 "유연성"이나 "설정 가능성"을 넣지 않는다.
- 일어날 수 없는 상황에 대한 예외 처리를 넣지 않는다.
- 200줄을 썼는데 50줄로 가능하면 다시 쓴다.
- "시니어 개발자가 과하게 짰다고 할까?" 자문하고, 그렇다면 단순화한다.

### 3. Surgical Changes
꼭 필요한 것만 건드린다. 내가 만든 흔적만 치운다.
- 인접 코드·주석·포맷을 "개선"하지 않는다.
- 멀쩡한 코드를 리팩토링하지 않는다.
- 내 방식과 다르더라도 기존 스타일을 따른다.
- 관련 없는 죽은 코드를 발견하면 언급만 하고 지우지 않는다.
- 내 변경으로 안 쓰이게 된 import·변수·함수만 제거한다.
- 기존에 있던 죽은 코드는 요청 없이는 지우지 않는다.
- 기준: 바뀐 모든 줄은 사용자의 요청으로 곧장 설명돼야 한다.

### 4. Goal-Driven Execution
성공 기준을 정하고, 검증될 때까지 반복한다.
- "검증 추가" → "잘못된 입력에 대한 테스트를 먼저 쓰고 통과시킨다"
- "버그 수정" → "버그를 재현하는 테스트를 먼저 쓰고 통과시킨다"
- "X 리팩토링" → "리팩토링 전후로 테스트가 통과하는지 확인한다"
- 여러 단계 작업은 짧은 계획을 적는다: `1. [단계] → 검증: [확인 항목]`
- 약한 기준("동작하게")은 반복 질문을 부른다. 강한 기준이라야 혼자 반복할 수 있다.

이 지침이 잘 적용되면: diff에 불필요한 변경이 줄고, 과설계로 인한 재작성이 줄고, 질문이 실수 이후가 아니라 구현 이전에 나온다.

## 설계·구현 검증 원칙

설계하고 구현할 때 다음을 지킨다. 단, 단순함이 우선이며(2. Simplicity First), 원칙을 지키려고 코드를 부풀리지 않는다.

- 객체지향 설계 기법을 가능한 한 따른다. 단, 한 번만 쓰는 코드에 억지로 적용하지 않는다.
- 클린코드 원칙을 가능한 한 따른다. 이름, 함수 크기, 중복 제거를 신경 쓴다.
- 외부 자료는 신뢰할 수 있는 공식 출처(공식 문서·공식 저장소)를 최우선으로 참고한다.
- 판단이 서지 않으면 업계 표준 베스트 프랙티스를 참고한다.

## Build and Test Commands

```bash
# Build entire project
./gradlew clean build

# Build a specific module (module name = {parentDir}-{moduleDir}, e.g. api/auth → api-auth)
./gradlew :api-auth:build

# Run tests
./gradlew test                    # All modules
./gradlew :api-auth:test          # Single module

# Run a single test class
./gradlew :api-auth:test --tests "com.tech.n.ai.api.auth.service.AuthServiceTest"

# Run applications (each defaults to the `local` profile via build.gradle)
./gradlew :api-gateway:bootRun        # 8081
./gradlew :api-emerging-tech:bootRun  # 8082
./gradlew :api-auth:bootRun           # 8083
./gradlew :api-chatbot:bootRun        # 8084
./gradlew :api-bookmark:bootRun       # 8085
./gradlew :api-agent:bootRun          # 8086

# Generate API documentation (Spring REST Docs → Asciidoctor)
./gradlew asciidoctor
```

`bootRun` and `test` are pre-configured in the root `build.gradle` with
`-Dspring.profiles.active=local`, `-Duser.timezone=Asia/Seoul`, and UTF-8 encoding —
no manual flags needed for local development.

## Architecture Overview

### CQRS Pattern (split across two datastores)
- **Command / Write**: Aurora MySQL via `datasource-aurora` (package `com.tech.n.ai.domain.aurora`)
- **Query / Read**: MongoDB Atlas via `datasource-mongodb` (package `com.tech.n.ai.domain.mongodb`)
- **Sync**: Kafka events propagate writes from Aurora to MongoDB (target latency < 1s)

This physical write/read split is the key mental model. Within `datasource-aurora`:
- `repository/writer/` — JPA + QueryDSL (write side)
- `repository/reader/` — MyBatis (read side, also used for complex Aurora queries)

### Multi-Module Structure
`settings.gradle` recursively scans `api/`, `batch/`, `common/`, `client/`, `datasource/`
and registers every directory containing `src/` as a module. The module name is derived
from its path: `api/auth` → `api-auth`, `common/security` → `common-security`. **Adding a
module requires no `settings.gradle` edit** — just create the directory with a `src/`.

```
api/          REST API servers: agent, auth, bookmark, chatbot, emerging-tech, gateway
batch/        Batch jobs (batch/source)
client/       External integrations: feign, mail, rss, scraper, slack
common/       Shared libraries: conversation, core, exception, kafka, security
datasource/   Data access: aurora (command), mongodb (query)
```

**Dependency direction**: `api → datasource → common → client`. API modules wire together
the relevant `common-*`, `datasource-*`, and `client-*` projects (see each module's `build.gradle`).

### Key Conventions
- **Entity / Document naming**: Aurora `*Entity` in `domain/aurora/entity/`; MongoDB `*Document` in `domain/mongodb/document/`.
- **Primary key**: TSID (Time-Sorted Unique Identifier) via `@Tsid` + `TsidGenerator` (in `domain/aurora/generator`).
- **History tracking**: `*HistoryEntity` populated by `HistoryEntityListener`.
- **Gradle DSL**: Groovy (not Kotlin DSL). Shared dependency config lives in root `build.gradle`; JPA/QueryDSL extras in `jpa.gradle`; REST Docs in `docs.gradle` — applied per-module via `apply from:`.

### API Gateway
`api-gateway` is the central entry point: JWT validation, CORS, and routing to backend
services. JWT handling comes from `common-security` (`JwtTokenProvider`).

### RAG Chatbot (`api-chatbot`)
langchain4j 1.10.0 with MongoDB Atlas Vector Search for retrieval, OpenAI as the default
LLM provider, and Cohere for re-ranking.

## Technology Stack
- Java 21, Spring Boot 4.0.2 (`spring-boot-starter-classic`), Spring Cloud 2025.1.0
- Aurora MySQL (command) + MongoDB Atlas (query), Apache Kafka, Redis
- JPA/Hibernate 7.2 + QueryDSL 5.1 (writers), MyBatis 4.0.1 (readers)
- langchain4j 1.10.0 (OpenAI + Cohere), Spring REST Docs + Asciidoctor
- Observability: OpenTelemetry, Micrometer (Prometheus / Dynatrace); see `monitoring/` and `docker-compose.yml`

## Configuration
- Profiles: `local`, `dev`, `beta`, `prod`. Tests and `bootRun` use `local` by default.
- Local infra (Kafka, Redis, MongoDB, monitoring stack) is provided via `docker-compose.yml`.

## tmux Development Environment
`./scripts/tmux-backend.sh` launches a 3-window session (project, module, test).
See `scripts/tmux-dev-guide.md`, `scripts/tmux-recommended-layouts.md`, `scripts/tmux-overview.md`.
