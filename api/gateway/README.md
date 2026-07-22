# Gateway API 모듈

Spring Cloud Gateway(WebFlux/Netty) 기반 API Gateway 서버다. 모든 외부 요청의 진입점으로, JWT 인증·권한(ADMIN) 검사, Rate Limiting, Circuit Breaker, 헤더 보안, 요청 추적, 접근 로그, CORS를 처리한 뒤 5개 백엔드 API 서버로 라우팅한다.

## 주요 기능

- **라우팅**: URI 경로 기준으로 auth, bookmark, emerging-tech, chatbot, agent 서버로 전달. 환경별(Local/Dev/Beta/Prod)로 백엔드 URL 분리
- **JWT 인증·권한**: `common-security`의 `JwtTokenProvider`로 검증. 공개/인증/관리자 전용 경로 구분은 `GatewaySecurityProperties`로 외부화. 검증 성공 시 `x-user-id`, `x-user-email`, `x-user-role` 헤더를 주입해 백엔드로 전달
- **헤더 보안**: 클라이언트가 위조한 `x-user-id`, `x-user-email`, `x-user-role` 헤더를 사전 제거해 identity spoofing 방지
- **Rate Limiting**: Redis Token Bucket (`RequestRateLimiter`). 공개 API는 IP 기반, 인증 API는 사용자 ID 기반(`x-user-id` 헤더 키, 없으면 IP 폴백)
- **Circuit Breaker**: Resilience4j, 라우트별 독립 서킷브레이커. 서킷 open 시 `/fallback`으로 넘겨 503 반환
- **요청 추적·Access Log**: X-Request-Id 발급/전파(클라이언트 제공 시 UUID 검증), 전용 로거(`ACCESS_LOG`)에 메서드·경로·상태코드·응답시간·IP·사용자 ID·라우트 ID·User-Agent 기록
- **Retry / Request Size**: GET 요청이 503을 받았을 때만 2회 재시도(지수 백오프 100ms→500ms), 최대 요청 크기 5MB (초과 시 413)
- **에러 처리**: `WebExceptionHandler` 기반 Reactive 예외 처리, 표준 `ApiResponse` 형식 응답

## 필터 실행 순서

| 순서 | 필터 | 역할 |
|-----|------|------|
| `HIGHEST_PRECEDENCE` | `HeaderSanitizeGlobalFilter` | 위조 identity 헤더 제거 |
| `HIGHEST_PRECEDENCE + 1` | `RequestIdGlobalFilter` | X-Request-Id 발급/전파 |
| `HIGHEST_PRECEDENCE + 2` | `JwtAuthenticationGatewayFilter` | JWT 검증, 사용자 정보 헤더 주입 |
| `LOWEST_PRECEDENCE` | `AccessLogGlobalFilter` | 접근 로그 기록 |

위 네 개는 모든 라우트에 적용되는 GlobalFilter다. 라우트별 필터(`CircuitBreaker`, `RequestRateLimiter`)는 `application.yml`의 각 라우트 정의에서, `Retry`·`RequestSize`는 default-filters로 적용된다. 라우트별 필터는 GlobalFilter(음수 order)보다 뒤에 실행되므로, JWT 인증을 통과한 요청만 Rate Limiter 검사를 받는다.

## 라우팅 규칙과 Rate Limit

| 경로 패턴 | 대상 서버 | 인증 필요 | Rate Limit (req/s, burst) | Key 기준 |
|----------|---------|---------|-----------|---------|
| `/api/v1/auth/**` | `@api/auth` | ❌ (단, `/auth/admin/**`는 ADMIN) | 10, burst 20 | IP |
| `/api/v1/bookmark/**` | `@api/bookmark` | ✅ | 100, burst 150 | User |
| `/api/v1/emerging-tech/**` | `@api/emerging-tech` | ❌ | 30, burst 50 | IP |
| `/api/v1/chatbot/**` | `@api/chatbot` | ✅ | 100, burst 150 | User |
| `/api/v1/agent/**` | `@api/agent` | ✅ (ADMIN 전용) | 100, burst 150 | User |

백엔드 URL: Local은 `http://localhost:{8083|8085|8082|8084|8086}`, Dev/Beta/Prod는 `http://api-{service}-service:8080`.

## 공개·인증·관리자 전용 경로

경로 분류는 `gateway.security` prefix의 `GatewaySecurityProperties`로 `application.yml`에서 관리한다. `public-paths`(인증 없이 통과), `public-path-exclusions`(와일드카드에서 다시 제외), `admin-only-paths`(ADMIN role 필수) 세 목록을 둔다.

| 경로 패턴 | 분류 |
|----------|------|
| `/api/v1/auth/**` | 공개 |
| `/api/v1/auth/admin/login`, `/api/v1/auth/admin/refresh` | 공개 (구체적 경로 → exclusion보다 우선) |
| `/api/v1/auth/admin/**` (위 두 경로 제외) | 관리자 전용 |
| `/api/v1/emerging-tech/**` | 공개 |
| `/actuator/**` | 공개 |
| `/api/v1/bookmark/**`, `/api/v1/chatbot/**` | 인증 |
| `/api/v1/agent/**` | 관리자 전용 |

매칭 우선순위: 와일드카드 없는 구체적 공개 경로 > exclusion > 와일드카드 공개 경로.

JWT는 `Authorization: Bearer {token}` 헤더에서 추출한다. 토큰이 없거나 무효하면 401(`4001`, `AUTH_FAILED`), 관리자 전용 경로인데 role이 `ADMIN`이 아니면 403(`4003`, `FORBIDDEN`)을 반환한다. 토큰 갱신은 Gateway가 관여하지 않고 클라이언트가 `/api/v1/auth/refresh`로 처리한다.

## Circuit Breaker

백엔드 장애가 Gateway로 번지지 않도록 라우트마다 Resilience4j 서킷브레이커를 둔다.

- **기본 설정**: `COUNT_BASED`, 윈도우 10개, 최소 5번 호출 후 판단. 실패율 50% 이상이면 open → 10초 차단 → half-open에서 3건 시험 호출 → 성공 시 closed
- **chatbot/agent 완화**: LLM 호출이라 응답이 느릴 수 있어 윈도우 20개·최소 10번으로 판단 완화
- **TimeLimiter**: 기본 60초, chatbot/agent는 120초
- **Fallback**: 서킷이 열리면 `forward:/fallback`으로 넘어가 `FallbackController`가 503과 함께 표준 `ApiResponse`(코드 `5003`) 반환

## 에러 처리

| HTTP 상태 | 에러 코드 | 설명 |
|----------|---------|------|
| 401 | `4001` | 인증 실패 (JWT 없음/무효) |
| 403 | `4003` | 권한 부족 (ADMIN role 아님) |
| 404 | `4004` | 라우팅 실패 |
| 502 | `5002` | 백엔드 연결 실패 |
| 503 | `5003` | Circuit Breaker 열림 (`/fallback`) |
| 504 | `5004` | 백엔드 타임아웃 |
| 500 | `5000` | 내부 서버 오류 |

401·403은 `JwtAuthenticationGatewayFilter`가, 503은 `FallbackController`가 직접 반환한다. 그 외 502/504/500은 `ApiGatewayExceptionHandler`(`WebExceptionHandler`)가 예외 타입을 보고 매핑한다. 모든 에러 응답은 `ApiResponse` 형식이다:

```json
{
  "code": "4001",
  "messageCode": { "code": "AUTH_FAILED", "text": "인증에 실패했습니다." },
  "message": null,
  "data": null
}
```

## 설정

### 환경 변수

- `JWT_SECRET_KEY`: JWT 시크릿 키. local 프로필은 `jwt.secret-key: ${JWT_SECRET_KEY}`로 기본값 없이 바인딩하므로 이 변수가 없으면 실행에 실패한다. dev/beta/prod는 `jwt.secret-key` 설정이 없어 `JwtTokenProvider`의 기본값(`default-secret-key-change-in-production-minimum-256-bits`)이 쓰인다.
- `REDIS_HOST`: Redis 호스트 (Rate Limiting용, dev/beta/prod에서 사용, 기본값 `localhost`. local은 `localhost` 고정)
- `REDIS_PORT`: Redis 포트 (기본값 `6379`)

### 연결 풀 (Reactor Netty)

Connection reset by peer 방지를 위한 설정: max-idle-time 30초(백엔드 keep-alive 60초보다 짧게), max-life-time 300초, max-connections 500, acquire-timeout 45초, pending-acquire-timeout 60초, connection-timeout 30초, response-timeout 60초.

### CORS

Global CORS를 환경별로 다르게 적용한다. Local/Dev는 `http://localhost:*`, `http://127.0.0.1:*` 패턴 허용, Beta/Prod는 구체적 도메인 목록만 허용. 모든 환경에서 `allowCredentials: true`, 모든 메서드·헤더 허용. `DedupeResponseHeader` 필터로 중복 CORS 헤더를 제거한다.

### 로깅

`com.tech.n.ai.api.gateway` 패키지 로그 레벨: Local DEBUG / Dev INFO / Beta·Prod WARN. 접근 로그(`ACCESS_LOG` 로거)는 모든 환경에서 INFO로 기록한다.

## 의존성

- `spring-cloud-starter-gateway-server-webflux` (Netty 기반)
- `reactor-netty-http` (classic 모드에서 transitive 제외되므로 명시 선언)
- `spring-boot-starter-data-redis-reactive` (Rate Limiting)
- `spring-cloud-starter-circuitbreaker-reactor-resilience4j`
- `common-core` (`ApiResponse`, `MessageCode`, `ErrorCodeConstants`), `common-security` (`JwtTokenProvider`, `JwtTokenPayload`)

Gateway는 reactive 전용 서버라 datasource나 Kafka 모듈에 의존하지 않는다. `ApiGatewayApplication`에서 DataSource/Flyway/MongoDB/Security autoconfiguration을 명시적으로 제외하고, `build.gradle`에서도 Servlet/Tomcat 의존성을 전역 제외한다.

## 참고 문서

- Gateway 설계서: `docs/step14/gateway-design.md`, 구현 계획: `docs/step14/gateway-implementation-plan.md`
- 보안·운영 강화 설계서: `docs/reference/api-gateway-improvement-design.md`
- [Spring Cloud Gateway 공식 문서](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)

버전: Java 21 · Spring Boot 4.0.2 · Spring Cloud 2025.1.0
