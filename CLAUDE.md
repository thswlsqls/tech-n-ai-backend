# CLAUDE.md

이 파일은 Claude AI (Cursor IDE 및 claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

> **참고**: Cursor IDE 사용자는 `.cursorrules` 파일도 함께 참고하세요.

## 작업 원칙

### 오버엔지니어링 금지
- **요청된 작업 범위에 집중한다.** 요청하지 않은 리팩토링이나 기능 추가를 하지 않는다.
- 현재 필요하지 않은 추상화나 미래 대비 코드를 작성하지 않는다.

### 외부 자료 참조 원칙
- **공식 문서와 공식 저장소만 참조한다.** 비공식 블로그, 포럼, AI 생성 콘텐츠를 근거로 사용하지 않는다.
- API, 라이브러리 사용법은 반드시 해당 프로젝트의 공식 문서에서 확인한 후 적용한다.
- 확인되지 않은 정보를 사실처럼 제시하지 않는다. 불확실한 경우 명시적으로 알린다.

## Build and Test Commands

```bash
# Build entire project
./gradlew clean build

# Build specific module (module names follow {parentDir}-{moduleDir} pattern)
./gradlew :api-auth:build
./gradlew :api-chatbot:build
./gradlew :api-gateway:build

# Run tests
./gradlew test                    # All tests
./gradlew :api-auth:test          # Specific module

# Run single test class
./gradlew :api-auth:test --tests "com.tech.n.ai.api.auth.service.AuthServiceTest"

# Run applications (local profile)
./gradlew :api-gateway:bootRun        # Gateway: port 8081
./gradlew :api-emerging-tech:bootRun  # Emerging Tech: port 8082
./gradlew :api-auth:bootRun           # Auth: port 8083
./gradlew :api-chatbot:bootRun        # Chatbot: port 8084
./gradlew :api-bookmark:bootRun       # Bookmark: port 8085
./gradlew :api-agent:bootRun          # Agent: port 8086

# Generate API documentation
./gradlew asciidoctor
```

## Architecture Overview

### CQRS Pattern
- **Command Side (Write)**: Aurora MySQL via `datasource-aurora` module
- **Query Side (Read)**: MongoDB Atlas via `datasource-mongodb` module
- **Sync**: Kafka events synchronize writes to MongoDB (target: <1 second latency)

### Multi-Module Structure
Modules are auto-discovered by `settings.gradle`. Module names: `{parentDir}-{moduleDir}` (e.g., `api/auth` → `api-auth`)

```
api/          → REST API servers (agent, auth, bookmark, chatbot, emerging-tech, gateway)
batch/        → Batch jobs (source)
client/       → External integrations (feign, rss, scraper, slack)
common/       → Shared libraries (core, exception, kafka, security)
datasource/   → Data access (aurora, mongodb)
```

**Dependency direction**: API → Datasource → Common → Client

### Key Patterns

**Repository Pattern (CQRS)**:
- Aurora: `repository/reader/` (MyBatis) and `repository/writer/` (JPA)
- MongoDB: Single repository per document in `datasource-mongodb`

**Entity/Document Naming**:
- Aurora: `*Entity.java` in `datasource/aurora/entity/`
- MongoDB: `*Document.java` in `datasource/mongodb/document/`

**Primary Key**: TSID (Time-Sorted Unique Identifier) via `@Tsid` annotation and `TsidGenerator`

**History Tracking**: Entities with `*HistoryEntity` via `HistoryEntityListener`

### API Gateway
Central entry point handling JWT validation, CORS, and routing to backend services. Uses `common-security` module's `JwtTokenProvider`.

### RAG Chatbot
Uses langchain4j 0.35.0 with MongoDB Atlas Vector Search and OpenAI GPT-4o-mini for retrieval-augmented generation.

## Technology Stack

- Java 21, Spring Boot 4.0.2, Spring Cloud 2025.1.0
- Gradle with Groovy DSL (Kotlin DSL not used)
- Aurora MySQL 3.x (Command), MongoDB Atlas 7.0+ (Query)
- Apache Kafka, Redis
- langchain4j 0.35.0 with OpenAI
- Spring REST Docs for API documentation

## Configuration

Environment profiles: `local`, `dev`, `beta`, `prod`

Tests run with `-Dspring.profiles.active=local` by default.
