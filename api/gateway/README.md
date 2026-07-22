# Gateway API 모듈

## 개요

`api-gateway` 모듈은 Spring Cloud Gateway(WebFlux/Netty) 기반의 API Gateway 서버입니다. 모든 외부 요청을 중앙에서 관리하고, 적절한 백엔드 API 서버로 라우팅하는 역할을 수행합니다. JWT 토큰 기반 인증과 권한(ADMIN) 검사, Rate Limiting, Circuit Breaker, 헤더 보안, 요청 추적, 접근 로그, CORS 정책 관리, 연결 풀 최적화 등의 기능을 제공합니다.

## 주요 기능

### 1. 라우팅
- **URI 기반 라우팅**: 요청 URI 경로를 기준으로 적절한 API 서버로 요청 전달
- **5개 API 서버 라우팅**: auth, bookmark, emerging-tech, chatbot, agent 서버로 라우팅
- **환경별 백엔드 URL 설정**: Local/Dev/Beta/Prod 환경별로 다른 백엔드 서비스 URL 사용

### 2. 인증 및 보안
- **JWT 토큰 검증**: `common-security` 모듈의 `JwtTokenProvider`를 활용한 JWT 토큰 검증
- **인증 필요/불필요 경로 구분**: 공개 API와 인증 필요 API 자동 구분 (경로 설정을 `GatewaySecurityProperties`로 외부화)
- **관리자 전용 경로 검사**: `admin-only-paths`에 속한 경로는 JWT의 role이 `ADMIN`인 경우에만 통과, 아니면 403 반환
- **사용자 정보 헤더 주입**: 검증 성공 시 사용자 정보를 헤더에 주입하여 백엔드 서버로 전달
- **헤더 보안 (Header Sanitization)**: 클라이언트가 위조한 `x-user-id`, `x-user-email`, `x-user-role` 헤더를 사전 제거하여 identity spoofing 방지

### 3. Rate Limiting
- **Redis Token Bucket 알고리즘**: Spring Cloud Gateway `RequestRateLimiter` 필터 적용
- **IP 기반 제한**: 공개 API (auth, emerging-tech)에 IP 기반 요청 제한
- **사용자 기반 제한**: 인증 API (bookmark, chatbot, agent)에 사용자 ID 기반 요청 제한
- **경로별 차등 제한**: auth(10 req/s), emerging-tech(30 req/s), 인증 경로(100 req/s). 각 경로는 짧은 순간 몰리는 요청을 위한 burst 여유(burstCapacity)를 함께 둡니다.
- **사용자 키 폴백**: 사용자 기반 제한은 `x-user-id` 헤더를 키로 쓰되, 헤더가 없으면 IP로 폴백합니다.

### 4. Circuit Breaker
- **라우트별 Circuit Breaker**: Resilience4j 기반으로 각 백엔드(auth, bookmark, chatbot, agent, emerging-tech)마다 독립된 서킷브레이커 적용
- **실패율 기반 차단**: 최근 호출 중 실패율이 임계값(50%)을 넘으면 서킷을 열어 백엔드로의 호출을 차단하고, 일정 시간 뒤 half-open 상태에서 일부 호출로 회복 여부를 확인
- **LLM 경로 완화**: 응답이 느릴 수 있는 chatbot/agent는 판단 윈도우를 넓히고 타임아웃을 120초로 늘려 둠 (기본 60초)
- **Fallback 응답**: 서킷이 열리면 요청을 `/fallback`으로 보내 `FallbackController`가 503(Service Unavailable)을 표준 `ApiResponse` 형식으로 반환

### 5. 요청 추적 및 Access Log
- **X-Request-Id 추적**: UUID 형식의 요청 ID 자동 생성, 클라이언트 제공 시 UUID 검증 후 사용
- **분산 추적 전파**: 요청 ID를 백엔드 서비스로 전파하고, 응답 헤더에 포함
- **구조화된 Access Log**: HTTP 메서드, 경로, 상태코드, 응답시간(ms), 클라이언트 IP, 사용자 ID, 라우트 ID, User-Agent를 전용 로거(`ACCESS_LOG`)에 기록

### 6. CORS 설정
- **Global CORS 설정**: 모든 경로에 대한 CORS 정책 적용
- **환경별 CORS 정책**: Local/Dev/Beta/Prod 환경별로 다른 CORS 정책 적용
- **중복 헤더 제거**: `DedupeResponseHeader` 필터로 중복 CORS 헤더 자동 제거

### 7. 연결 풀 및 성능 최적화
- **Reactor Netty 연결 풀 설정**: Connection reset by peer 에러 방지
- **타임아웃 설정**: 연결 타임아웃, 응답 타임아웃 최적화
- **Retry**: GET 요청이 503(Service Unavailable)을 받았을 때만 자동 재시도 (2회, 지수 백오프 100ms→500ms)
- **Request Size 제한**: 최대 요청 크기 5MB 제한 (413 응답)
- **연결 풀 모니터링**: 최대 연결 수 500, 유휴 연결 관리

### 8. 에러 처리
- **공통 예외 처리**: `WebExceptionHandler`를 통한 Reactive 기반 예외 처리
- **표준 에러 응답 형식**: `ApiResponse` 형식의 일관된 에러 응답
- **에러 로깅**: 환경별 로그 레벨에 따른 에러 로깅

## 아키텍처

### 인프라 아키텍처

![Gateway Infrastructure Architecture](../../contents/api-gateway/architecture-diagram.png)

### 전체 시스템 아키텍처

Gateway를 포함한 전체 시스템 아키텍처는 다음과 같습니다:

![Overall System Architecture](../../contents/api-chatbot/overall-system-architecture.png)

### 요청 처리 흐름

**필터 실행 순서**:

| 순서 | 필터 | 역할 |
|-----|------|------|
| `HIGHEST_PRECEDENCE` | `HeaderSanitizeGlobalFilter` | 위조 identity 헤더 제거 |
| `HIGHEST_PRECEDENCE + 1` | `RequestIdGlobalFilter` | X-Request-Id 발급/전파 |
| `HIGHEST_PRECEDENCE + 2` | `JwtAuthenticationGatewayFilter` | JWT 검증, 사용자 정보 헤더 주입 |
| `LOWEST_PRECEDENCE` | `AccessLogGlobalFilter` | 요청/응답 접근 로그 기록 |

위 네 개는 모든 라우트에 적용되는 GlobalFilter입니다. 이와 별개로 `CircuitBreaker`와 `RequestRateLimiter`는 `application.yml`의 각 라우트 정의에서, `Retry`와 `RequestSize`는 default-filters(전 라우트 공통)에서 적용됩니다. GlobalFilter는 음수 order라 라우트별 필터보다 먼저 실행됩니다 — 즉 JWT 인증을 통과한 뒤에 Rate Limiter 검사를 받습니다.

**인증이 필요한 요청 처리**:
1. Client → ALB → Gateway: 요청 수신
2. HeaderSanitizeGlobalFilter: 위조된 x-user-id, x-user-email, x-user-role 헤더 제거
3. RequestIdGlobalFilter: X-Request-Id 발급/검증
4. Gateway: 라우팅 규칙 매칭 (`/api/v1/bookmark/**`)
5. Gateway: JWT 인증 필터 실행
   - JWT 토큰 추출 (Authorization 헤더)
   - JWT 토큰 검증 (`JwtTokenProvider.validateToken`)
   - 사용자 정보 추출 및 헤더 주입 (`x-user-id`, `x-user-email`, `x-user-role`)
6. Rate Limiter: 사용자 기반 요청 제한 확인
7. Gateway → Bookmark 서버: 인증된 요청 전달 (사용자 정보 헤더 포함)
8. Bookmark 서버 → Gateway: API 응답
9. AccessLogGlobalFilter: 접근 로그 기록
10. Gateway → ALB → Client: 최종 응답 (CORS 헤더, X-Request-Id 포함)

**인증이 불필요한 요청 처리**:
1. Client → ALB → Gateway: 요청 수신
2. HeaderSanitizeGlobalFilter + RequestIdGlobalFilter 실행
3. Gateway: 라우팅 규칙 매칭 (`/api/v1/emerging-tech/**`)
4. Gateway: 인증 필터 우회 (공개 API)
5. Rate Limiter: IP 기반 요청 제한 확인
6. Gateway → Emerging Tech 서버: 요청 전달
7. Emerging Tech 서버 → Gateway: API 응답
8. AccessLogGlobalFilter: 접근 로그 기록
9. Gateway → ALB → Client: 최종 응답

### 계층 구조

```
ApiGatewayApplication
  ├── config/
  │   ├── ServerConfig (ComponentScan, ConfigurationProperties 활성화)
  │   ├── GatewayConfig (JWT 필터를 GlobalFilter로 등록)
  │   ├── RateLimiterConfig (IP/User 기반 KeyResolver)
  │   └── GatewaySecurityProperties (공개/제외/관리자 경로 설정 바인딩)
  ├── filter/
  │   ├── HeaderSanitizeGlobalFilter (위조 헤더 제거, HIGHEST_PRECEDENCE)
  │   ├── RequestIdGlobalFilter (요청 추적 ID, HIGHEST_PRECEDENCE + 1)
  │   ├── JwtAuthenticationGatewayFilter (JWT 인증/권한, HIGHEST_PRECEDENCE + 2)
  │   └── AccessLogGlobalFilter (접근 로그, LOWEST_PRECEDENCE)
  ├── controller/
  │   └── FallbackController (Circuit Breaker fallback, 503 응답)
  ├── common/exception/
  │   └── ApiGatewayExceptionHandler (WebExceptionHandler 기반 예외 처리)
  └── application.yml (라우팅, Rate Limiting, Circuit Breaker, 연결 풀, CORS 설정)
```

### 모듈 의존성 관계

```
api/gateway
├── common-core          # 공통 DTO·유틸리티 (ApiResponse, MessageCode, ErrorCodeConstants)
└── common-security      # JwtTokenProvider, JwtTokenPayload (JWT 검증)
```

Gateway는 reactive 전용 서버이므로 datasource(`domain-aurora`/`domain-mongodb`)나 Kafka 모듈에 의존하지 않습니다. `ApiGatewayApplication`에서 DataSource/Flyway/MongoDB/Security 관련 autoconfiguration을 명시적으로 제외하고, `build.gradle`에서도 Servlet/Tomcat 의존성을 전역 제외합니다.

## 라우팅 규칙

### 서비스별 라우팅 매핑

| 경로 패턴 | 대상 서버 | 인증 필요 | Rate Limit (req/s, burst) | Key 기준 | 설명 |
|----------|---------|---------|-----------|---------|------|
| `/api/v1/auth/**` | `@api/auth` | ❌ (단, `/auth/admin/**`는 ADMIN) | 10, burst 20 | IP | 인증 서버 (회원가입, 로그인, 토큰 갱신 등) |
| `/api/v1/bookmark/**` | `@api/bookmark` | ✅ | 100, burst 150 | User | 사용자 북마크 관리 API |
| `/api/v1/emerging-tech/**` | `@api/emerging-tech` | ❌ | 30, burst 50 | IP | AI 업데이트 정보 조회 API (공개) |
| `/api/v1/chatbot/**` | `@api/chatbot` | ✅ | 100, burst 150 | User | RAG 기반 챗봇 API |
| `/api/v1/agent/**` | `@api/agent` | ✅ (ADMIN 전용) | 100, burst 150 | User | AI Agent 실행 API (관리자 전용) |

### 환경별 백엔드 서비스 URL

**Local 환경**:
- `auth`: `http://localhost:8083`
- `bookmark`: `http://localhost:8085`
- `emerging-tech`: `http://localhost:8082`
- `chatbot`: `http://localhost:8084`
- `agent`: `http://localhost:8086`

**Dev/Beta/Prod 환경**:
- `auth`: `http://api-auth-service:8080`
- `bookmark`: `http://api-bookmark-service:8080`
- `emerging-tech`: `http://api-emerging-tech-service:8080`
- `chatbot`: `http://api-chatbot-service:8080`
- `agent`: `http://api-agent-service:8080`

## 인증 및 보안

### JWT 토큰 기반 인증

Gateway 서버는 `common-security` 모듈의 `JwtTokenProvider`를 활용하여 JWT 토큰을 검증합니다.

**인증 통합 방안**: `@api/auth` 모듈을 별도 서버로 유지, Gateway에서 JWT 검증만 수행
- `/api/v1/auth/**` 요청은 `@api/auth` 서버로 라우팅
- 다른 API 요청은 Gateway에서 JWT 검증 후 라우팅
- JWT는 stateless이므로 Gateway에서 직접 검증 가능

### JWT 인증 필터 동작

**경로 분류 (공개 / 인증 / 관리자 전용)**:

경로 분류는 `GatewaySecurityProperties`(prefix `gateway.security`)로 외부화되어 `application.yml`에서 관리됩니다. 세 가지 목록을 둡니다.

- `public-paths`: 인증 없이 통과하는 경로
- `public-path-exclusions`: `public-paths`의 와일드카드에서 다시 제외할 경로
- `admin-only-paths`: JWT가 있고 role이 `ADMIN`이어야 통과하는 경로

| 경로 패턴 | 분류 | 설명 |
|----------|---------|------|
| `/api/v1/auth/**` | 공개 | 인증 서버 자체 경로 (로그인·토큰 갱신 등) |
| `/api/v1/auth/admin/login`, `/api/v1/auth/admin/refresh` | 공개 | 관리자 로그인·토큰 갱신 (구체적 경로 → 아래 exclusion보다 우선) |
| `/api/v1/auth/admin/**` | 관리자 전용 | 위 두 경로를 뺀 나머지 관리자 API (ADMIN role 필수) |
| `/api/v1/emerging-tech/**` | 공개 | 공개 조회 API |
| `/actuator/**` | 공개 | 헬스체크·메트릭 엔드포인트 |
| `/api/v1/bookmark/**` | 인증 | 사용자별 데이터 접근 필요 |
| `/api/v1/chatbot/**` | 인증 | 사용자별 세션 관리 필요 |
| `/api/v1/agent/**` | 관리자 전용 | AI Agent 실행 (ADMIN role 필수) |

**경로 매칭 우선순위**: 와일드카드 없는 구체적 공개 경로 > exclusion > 와일드카드 공개 경로. 그래서 `/api/v1/auth/admin/login`은 공개로 통과하지만, `/api/v1/auth/admin/**`의 다른 경로는 exclusion에 걸려 관리자 전용으로 처리됩니다.

**JWT 토큰 추출 및 검증**:
- 헤더: `Authorization: Bearer {JWT_TOKEN}`
- 토큰이 없거나 무효한 경우: 401 Unauthorized 반환
- 관리자 전용 경로인데 role이 `ADMIN`이 아닌 경우: 403 Forbidden 반환
- 401 응답 형식: `{"code": "4001", "messageCode": {"code": "AUTH_FAILED", "text": "인증에 실패했습니다."}}`
- 403 응답 형식: `{"code": "4003", "messageCode": {"code": "FORBIDDEN", "text": "권한이 없습니다."}}`

**사용자 정보 헤더 주입**:
검증 성공 시 다음 헤더를 추가하여 백엔드 서버로 전달:
- `x-user-id`: 사용자 ID
- `x-user-email`: 사용자 이메일
- `x-user-role`: 사용자 역할

### 토큰 갱신 흐름

Gateway는 토큰 검증만 수행하며, 토큰 갱신은 클라이언트가 처리합니다.

**시나리오 1: Access Token 만료 (클라이언트 자동 처리)**
1. Bookmark 요청 (만료된 토큰) → Gateway: 401 Unauthorized
2. POST `/api/v1/auth/refresh` (유효한 Refresh Token, 자동 요청) → Gateway → Auth 서버: 200 OK (새 토큰 발급)
3. Bookmark 요청 (새 토큰, 자동 재시도) → Gateway: JWT 검증 성공 → Bookmark 서버: 200 OK

**시나리오 2: Refresh Token도 만료 (사용자 개입 필요)**
1. Bookmark 요청 (만료된 토큰) → Gateway: 401 Unauthorized
2. POST `/api/v1/auth/refresh` (만료된 Refresh Token, 자동 시도) → Gateway → Auth 서버: 401 Unauthorized
3. 사용자 개입: 로그인 화면 표시, 이메일/비밀번호 입력
4. POST `/api/v1/auth/login` (사용자 입력 후 요청) → Gateway → Auth 서버: 200 OK (새 토큰 발급)
5. Bookmark 요청 (새 토큰, 자동 재시도) → Gateway: JWT 검증 성공 → Bookmark 서버: 200 OK

## 기술 스택

### 의존성

- **Spring Cloud Gateway (WebFlux)**: `spring-cloud-starter-gateway-server-webflux` (Netty 기반)
- **Reactor Netty HTTP**: `reactor-netty-http` (classic 모드에서 transitive 제외되므로 명시 선언)
- **Reactive Redis**: `spring-boot-starter-data-redis-reactive` (Rate Limiting용)
- **Resilience4j**: `spring-cloud-starter-circuitbreaker-reactor-resilience4j` (Circuit Breaker)
- **Common 모듈**:
  - `common-core`: 공통 DTO 및 유틸리티 (`ApiResponse`, `MessageCode`, `ErrorCodeConstants`)
  - `common-security`: JWT 토큰 검증 (`JwtTokenProvider`, `JwtTokenPayload`)

### 버전 정보

- **Java**: 21
- **Spring Boot**: 4.0.2
- **Spring Cloud**: 2025.1.0

## 설정

### 환경 변수

- `JWT_SECRET_KEY`: JWT 시크릿 키. local 프로필은 기본값 없는 `${JWT_SECRET_KEY}` 바인딩이라 변수가 없으면 실행이 실패한다. dev/beta/prod는 `jwt.secret-key` 설정 자체가 없어 `JwtTokenProvider` 코드의 기본값을 쓴다.
- `REDIS_HOST`: Redis 호스트 (Rate Limiting용). local 프로필은 `localhost` 고정.
- `REDIS_PORT`: Redis 포트 (기본값: `6379`)

## 연결 풀 및 성능 최적화

### Connection reset by peer 방지

Connection reset by peer 에러를 방지하기 위해 Reactor Netty의 연결 풀 설정을 최적화합니다.

**연결 풀 설정 근거**:
- **max-idle-time: 30초**: 백엔드 서비스의 keep-alive 시간(60초)보다 짧게 설정하여 유휴 연결을 미리 종료
- **max-life-time: 300초 (5분)**: 연결의 최대 생명주기, 오래된 연결을 주기적으로 갱신
- **max-connections: 500**: 동시 처리 가능한 최대 연결 수
- **acquire-timeout: 45초**: 풀에서 연결을 빌려올 때까지 기다리는 타임아웃
- **pending-acquire-timeout: 60초**: 풀이 가득 찼을 때 대기 큐에서 기다리는 타임아웃
- **connection-timeout: 30초**: 백엔드 서버와의 연결 시도 타임아웃
- **response-timeout: 60초**: 백엔드 서버의 응답 대기 타임아웃, 백엔드 타임아웃보다 길게 설정

## CORS 설정

### 환경별 CORS 정책

**Local 환경 (개발 편의성 우선)**:
- `allowedOriginPatterns`: `http://localhost:*`, `http://127.0.0.1:*`
- `allowCredentials: true`
- `allowedHeaders: "*"`
- `allowedMethods`: 모든 HTTP 메서드 허용

**Dev 환경 (개발 편의성 + 보안)**:
- `allowedOriginPatterns`: `http://localhost:*`, `http://127.0.0.1:*` (추가 도메인 가능)
- `allowCredentials: true`
- `allowedHeaders: "*"`
- `allowedMethods`: 모든 HTTP 메서드 허용

**Beta/Prod 환경 (보안 우선)**:
- `allowedOriginPatterns`: 구체적인 도메인 목록만 허용
- `allowCredentials: true`
- `allowedHeaders: "*"`
- `allowedMethods`: 모든 HTTP 메서드 허용

### 중복 헤더 제거

외부 API 연동 시 Global CORS와 외부 API 응답 헤더가 충돌할 수 있으므로, `DedupeResponseHeader` 필터로 중복 헤더를 제거합니다.

## Circuit Breaker

백엔드 서비스가 느려지거나 죽었을 때 Gateway가 계속 호출을 던지면 장애가 번질 수 있습니다. 이를 막기 위해 라우트마다 Resilience4j 서킷브레이커를 두고, 호출이 일정 비율 이상 실패하면 잠시 호출을 끊었다가 회복 여부를 확인합니다.

**기본 설정 (`resilience4j.circuitbreaker`)**:
- 판단 방식: 최근 호출 수 기준(`COUNT_BASED`), 윈도우 10개, 최소 5번 호출 후 판단
- 실패율 50% 이상이면 서킷을 연다(open)
- 열린 뒤 10초 동안 호출 차단 → half-open에서 3건만 시험 호출 → 성공하면 닫힘(closed)

**라우트별 차이**:
- chatbot/agent: LLM 호출이라 응답이 느릴 수 있어 윈도우 20개·최소 10번으로 판단을 완화
- TimeLimiter: 기본 60초, chatbot/agent는 120초

**Fallback**:
- 서킷이 열려 있는 동안 들어온 요청은 `forward:/fallback`으로 넘어가 `FallbackController`가 처리합니다.
- 503 Service Unavailable과 함께 표준 `ApiResponse`(코드 `5003`, "서비스가 일시적으로 불가합니다. 잠시 후 다시 시도해주세요.")를 반환합니다.

## 에러 처리

### 공통 예외 처리

Gateway 서버는 `WebExceptionHandler` 인터페이스를 구현하여 Reactive 기반 예외 처리를 수행합니다.

**주요 에러 코드**:

| HTTP 상태 | 에러 코드 | 설명 |
|----------|---------|------|
| 401 | `4001` | 인증 실패 (JWT 토큰 없음 또는 무효) |
| 403 | `4003` | 권한 부족 (관리자 전용 경로에 ADMIN role 아님) |
| 404 | `4004` | 라우팅 실패 (경로를 찾을 수 없음) |
| 502 | `5002` | 백엔드 서버 연결 실패 |
| 503 | `5003` | Circuit Breaker 열림 (`/fallback`) |
| 504 | `5004` | 백엔드 서버 타임아웃 |
| 500 | `5000` | 내부 서버 오류 |

> 401·403은 `JwtAuthenticationGatewayFilter`가, 503은 `FallbackController`가 직접 반환합니다. 그 외 502/504/500은 `ApiGatewayExceptionHandler`(`WebExceptionHandler`)가 예외 타입을 보고 매핑합니다.

**에러 응답 형식**:
모든 에러 응답은 `ApiResponse` 형식을 따릅니다:

```json
{
  "code": "4001",
  "messageCode": {
    "code": "AUTH_FAILED",
    "text": "인증에 실패했습니다."
  },
  "message": null,
  "data": null
}
```

### 로깅 전략

**환경별 로그 레벨**:
- **Local**: DEBUG 레벨 상세 로깅
- **Dev**: INFO 레벨 일반 로깅
- **Beta/Prod**: WARN 레벨 로깅
- **ACCESS_LOG**: 전 환경 INFO 레벨

**로깅 항목**:
- 요청 로깅: 요청 URI, HTTP 메서드, 헤더 (민감 정보 제외)
- 인증 로깅: 인증 성공/실패, JWT 토큰 검증 결과
- 라우팅 로깅: 라우팅 규칙 매칭 결과, 백엔드 서버 URL
- 에러 로깅: 에러 발생 시 상세 스택 트레이스, 에러 코드 및 메시지

## 참고 문서

### 프로젝트 내부 문서

- **Gateway 설계서**: `docs/step14/gateway-design.md`
- **Gateway 구현 계획**: `docs/step14/gateway-implementation-plan.md`
- **Gateway 보안·운영 강화 설계서**: `docs/reference/api-gateway-improvement-design.md`
- **API 엔드포인트 설계**: `docs/step2/1. api-endpoint-design.md`
- **Spring Security 설계 가이드**: `docs/step6/spring-security-auth-design-guide.md`
- **Chatbot API 설계**: `docs/step12/rag-chatbot-design.md`
- **Bookmark API 설계**: `docs/step13/user-bookmark-feature-design.md`

### 공식 문서

- [Spring Cloud Gateway 공식 문서](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [Reactor Netty 공식 문서](https://projectreactor.io/docs/netty/release/reference/index.html)
- [Spring Boot 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/)

