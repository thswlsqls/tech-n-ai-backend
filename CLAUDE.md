# CLAUDE.md

이 파일은 Claude Code(claude.ai/code)가 이 저장소에서 작업할 때 참고하는 지침이다.

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

## 빌드·테스트 명령어

```bash
# 전체 프로젝트 빌드
./gradlew clean build

# 특정 모듈 빌드 (모듈 이름 = {parentDir}-{moduleDir}, 예: api/auth → api-auth)
./gradlew :api-auth:build

# 테스트 실행
./gradlew test                    # 전체 모듈
./gradlew :api-auth:test          # 단일 모듈

# 단일 테스트 클래스 실행
./gradlew :api-auth:test --tests "com.tech.n.ai.api.auth.service.AuthServiceTest"

# 애플리케이션 실행 (각각 build.gradle을 통해 기본 `local` 프로필 사용)
./gradlew :api-gateway:bootRun        # 8081
./gradlew :api-emerging-tech:bootRun  # 8082
./gradlew :api-auth:bootRun           # 8083
./gradlew :api-chatbot:bootRun        # 8084
./gradlew :api-bookmark:bootRun       # 8085
./gradlew :api-agent:bootRun          # 8086

# API 문서 생성 (Spring REST Docs → Asciidoctor)
./gradlew asciidoctor
```

`bootRun`과 `test`는 루트 `build.gradle`에 `-Dspring.profiles.active=local`,
`-Duser.timezone=Asia/Seoul`, UTF-8 인코딩이 미리 설정돼 있다 —
로컬 개발에서는 따로 플래그를 줄 필요가 없다.

## 아키텍처 개요

### CQRS 패턴 (두 개의 데이터 저장소로 분리)
- **Command / 쓰기**: `datasource-aurora`를 통한 Aurora MySQL (패키지 `com.tech.n.ai.domain.aurora`)
- **Query / 읽기**: `datasource-mongodb`를 통한 MongoDB Atlas (패키지 `com.tech.n.ai.domain.mongodb`)
- **동기화**: Kafka 이벤트가 Aurora의 쓰기 내용을 MongoDB로 전파한다 (목표 지연 시간 < 1초)

이 물리적 쓰기/읽기 분리가 핵심 개념이다. `datasource-aurora` 안에서는:
- `repository/writer/` — JPA + QueryDSL (쓰기 쪽)
- `repository/reader/` — MyBatis (읽기 쪽, 복잡한 Aurora 쿼리에도 사용)

### 멀티모듈 구조
`settings.gradle`이 `api/`, `batch/`, `common/`, `client/`, `datasource/`를 재귀적으로 훑어서
`src/`가 들어있는 모든 디렉터리를 모듈로 등록한다. 모듈 이름은 경로에서 만들어진다:
`api/auth` → `api-auth`, `common/security` → `common-security`. **모듈을 추가할 때
`settings.gradle`을 수정할 필요가 없다** — `src/`가 있는 디렉터리만 만들면 된다.

```
api/          REST API 서버: agent, auth, bookmark, chatbot, emerging-tech, gateway
batch/        배치 잡 (batch/source)
client/       외부 연동: feign, mail, rss, scraper, slack
common/       공유 라이브러리: conversation, core, exception, kafka, security
datasource/   데이터 접근: aurora (command), mongodb (query)
```

**의존 방향**: `api → datasource → common → client`. API 모듈은 필요한 `common-*`,
`datasource-*`, `client-*` 프로젝트를 엮어서 쓴다 (각 모듈의 `build.gradle` 참고).

### 핵심 규칙
- **Entity / Document 이름**: Aurora는 `domain/aurora/entity/`의 `*Entity`, MongoDB는 `domain/mongodb/document/`의 `*Document`.
- **기본키**: `@Tsid` + `TsidGenerator`(`domain/aurora/generator`에 위치)를 통한 TSID (Time-Sorted Unique Identifier).
- **이력 추적**: `HistoryEntityListener`가 채우는 `*HistoryEntity`.
- **Gradle DSL**: Groovy (Kotlin DSL 아님). 공유 의존성 설정은 루트 `build.gradle`에, JPA/QueryDSL 추가분은 `jpa.gradle`에, REST Docs는 `docs.gradle`에 두고 모듈마다 `apply from:`으로 적용한다.

### API Gateway
`api-gateway`가 중앙 진입점이다: JWT 검증, CORS, 백엔드 서비스로의 라우팅을 맡는다.
JWT 처리는 `common-security`(`JwtTokenProvider`)에서 온다.

### RAG 챗봇 (`api-chatbot`)
langchain4j 1.10.0을 사용하며, 검색에는 MongoDB Atlas Vector Search를, 기본 LLM
제공자로는 OpenAI를, 재순위(re-ranking)에는 Cohere를 쓴다.

## 기술 스택
- Java 21, Spring Boot 4.0.2 (`spring-boot-starter-classic`), Spring Cloud 2025.1.0
- Aurora MySQL (command) + MongoDB Atlas (query), Apache Kafka, Redis
- JPA/Hibernate 7.2 + QueryDSL 5.1 (writer), MyBatis 4.0.1 (reader)
- langchain4j 1.10.0 (OpenAI + Cohere), Spring REST Docs + Asciidoctor
- 관측(observability): OpenTelemetry, Micrometer (Prometheus / Dynatrace); `monitoring/`과 `docker-compose.yml` 참고

## 설정
- 프로필: `local`, `dev`, `beta`, `prod`. 테스트와 `bootRun`은 기본적으로 `local`을 쓴다.
- 로컬 인프라(Kafka, Redis, MongoDB, 모니터링 스택)는 `docker-compose.yml`로 제공된다.

## 인프라·배포·관측 (`devops/`, `monitoring/`)

애플리케이션 코드와 별개로, 인프라와 관측 설정이 저장소 안에 함께 들어 있다.

### Terraform (IaC) — `devops/terraform/`
AWS 인프라는 Terraform으로 관리한다. 세 부분으로 나뉜다.
- `bootstrap/` — Terraform 상태 저장용 S3·KMS, ECR, GitHub Actions용 OIDC 역할처럼 다른 모든 것보다 먼저 있어야 하는 리소스. 한 번만 적용한다.
- `modules/` — 재사용하는 리소스 묶음: `network`, `aurora-mysql`, `elasticache-valkey`, `msk-serverless`/`msk-provisioned`(Kafka), `ecs-service`, `cloudfront-spa`, `amplify-app`, `s3-bucket`, `iam-role-workload`, `observability`.
- `envs/{dev,beta,prod}/` — 환경별로 위 모듈을 엮어 실제 인프라를 정의한다. 환경마다 상태가 분리돼 있다.

리팩토링할 때는 `terraform plan`이 아무 변경도 만들지 않는지(no-op)로 동작이 그대로인지 확인한다.

### AWS 아키텍처 다이어그램 — `devops/aws/{dev,beta,prod}/`
환경별로 네트워크 구성, 참조 아키텍처, 보안, 관측 다이어그램을 `.drawio`와 `.png`로 둔다. 인프라를 바꾸면 이 다이어그램도 같이 맞춘다.

### 관측(observability) — 로컬과 운영이 분리돼 있다
헷갈리기 쉬우므로 둘을 구분한다.
- **로컬**: 루트 `docker-compose.yml` 하나에 DB·Kafka와 함께 Prometheus, Pushgateway, Alertmanager, Jaeger(트레이스), Loki + Promtail(로그), Grafana가 들어 있다. 각 도구의 설정 파일은 `monitoring/` 아래에 있다. 브라우저 접속 방법은 `monitoring/README.md` 참고.
- **운영(AWS)**: CloudWatch + X-Ray + Amazon Managed Grafana로 설계돼 있고, 설명은 `devops/results/08-observability.md`에 있다.

`monitoring/README.md`에 적힌 URL은 전부 로컬 PC 주소다. 운영 관측과 섞지 않는다.

## tmux 개발 환경
`./scripts/tmux/tmux-backend.sh`가 3창 세션(project, module, test)을 띄운다.
`scripts/tmux/tmux-dev-guide.md`, `scripts/tmux/tmux-recommended-layouts.md`, `scripts/tmux/tmux-overview.md` 참고.

<!-- rtk-instructions v2 -->
# RTK (Rust Token Killer) - Token-Optimized Commands

## Golden Rule

**Always prefix commands with `rtk`**. If RTK has a dedicated filter, it uses it. If not, it passes through unchanged. This means RTK is always safe to use.

**Important**: Even in command chains with `&&`, use `rtk`:
```bash
# ❌ Wrong
git add . && git commit -m "msg" && git push

# ✅ Correct
rtk git add . && rtk git commit -m "msg" && rtk git push
```

## RTK Commands by Workflow

### Build & Compile (80-90% savings)
```bash
rtk cargo build         # Cargo build output
rtk cargo check         # Cargo check output
rtk cargo clippy        # Clippy warnings grouped by file (80%)
rtk tsc                 # TypeScript errors grouped by file/code (83%)
rtk lint                # ESLint/Biome violations grouped (84%)
rtk prettier --check    # Files needing format only (70%)
rtk next build          # Next.js build with route metrics (87%)
```

### Test (60-99% savings)
```bash
rtk cargo test          # Cargo test failures only (90%)
rtk go test             # Go test failures only (90%)
rtk jest                # Jest failures only (99.5%)
rtk vitest              # Vitest failures only (99.5%)
rtk playwright test     # Playwright failures only (94%)
rtk pytest              # Python test failures only (90%)
rtk rake test           # Ruby test failures only (90%)
rtk rspec               # RSpec test failures only (60%)
rtk test <cmd>          # Generic test wrapper - failures only
```

### Git (59-80% savings)
```bash
rtk git status          # Compact status
rtk git log             # Compact log (works with all git flags)
rtk git diff            # Compact diff (80%)
rtk git show            # Compact show (80%)
rtk git add             # Ultra-compact confirmations (59%)
rtk git commit          # Ultra-compact confirmations (59%)
rtk git push            # Ultra-compact confirmations
rtk git pull            # Ultra-compact confirmations
rtk git branch          # Compact branch list
rtk git fetch           # Compact fetch
rtk git stash           # Compact stash
rtk git worktree        # Compact worktree
```

Note: Git passthrough works for ALL subcommands, even those not explicitly listed.

### GitHub (26-87% savings)
```bash
rtk gh pr view <num>    # Compact PR view (87%)
rtk gh pr checks        # Compact PR checks (79%)
rtk gh run list         # Compact workflow runs (82%)
rtk gh issue list       # Compact issue list (80%)
rtk gh api              # Compact API responses (26%)
```

### JavaScript/TypeScript Tooling (70-90% savings)
```bash
rtk pnpm list           # Compact dependency tree (70%)
rtk pnpm outdated       # Compact outdated packages (80%)
rtk pnpm install        # Compact install output (90%)
rtk npm run <script>    # Compact npm script output
rtk npx <cmd>           # Compact npx command output
rtk prisma              # Prisma without ASCII art (88%)
```

### Files & Search (60-75% savings)
```bash
rtk ls <path>           # Tree format, compact (65%)
rtk read <file>         # Code reading with filtering (60%)
rtk grep <pattern>      # Search grouped by file (75%). Format flags (-c, -l, -L, -o, -Z) run raw.
rtk find <pattern>      # Find grouped by directory (70%)
```

### Analysis & Debug (70-90% savings)
```bash
rtk err <cmd>           # Filter errors only from any command
rtk log <file>          # Deduplicated logs with counts
rtk json <file>         # JSON structure without values
rtk deps                # Dependency overview
rtk env                 # Environment variables compact
rtk summary <cmd>       # Smart summary of command output
rtk diff                # Ultra-compact diffs
```

### Infrastructure (85% savings)
```bash
rtk docker ps           # Compact container list
rtk docker images       # Compact image list
rtk docker logs <c>     # Deduplicated logs
rtk kubectl get         # Compact resource list
rtk kubectl logs        # Deduplicated pod logs
```

### Network (65-70% savings)
```bash
rtk curl <url>          # Compact HTTP responses (70%)
rtk wget <url>          # Compact download output (65%)
```

### Meta Commands
```bash
rtk gain                # View token savings statistics
rtk gain --history      # View command history with savings
rtk discover            # Analyze Claude Code sessions for missed RTK usage
rtk proxy <cmd>         # Run command without filtering (for debugging)
rtk init                # Add RTK instructions to CLAUDE.md
rtk init --global       # Add RTK to ~/.claude/CLAUDE.md
```

## Token Savings Overview

| Category | Commands | Typical Savings |
|----------|----------|-----------------|
| Tests | vitest, playwright, cargo test | 90-99% |
| Build | next, tsc, lint, prettier | 70-87% |
| Git | status, log, diff, add, commit | 59-80% |
| GitHub | gh pr, gh run, gh issue | 26-87% |
| Package Managers | pnpm, npm, npx | 70-90% |
| Files | ls, read, grep, find | 60-75% |
| Infrastructure | docker, kubectl | 85% |
| Network | curl, wget | 65-70% |

Overall average: **60-90% token reduction** on common development operations.
<!-- /rtk-instructions -->