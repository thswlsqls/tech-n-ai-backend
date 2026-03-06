# API Gateway 개선 프롬프트

## 프롬프트 목적

이 프롬프트는 `api/gateway` 모듈의 **현재 구현을 Spring Cloud Gateway 베스트 프랙티스에 맞게 개선**하기 위한 설계와 구현을 지시합니다.
5가지 핵심 책임 영역(라우팅, 인증/인가, 변환, 공통 정책, 관측성)에 대해 현재 상태를 검증하고 갭을 보완합니다.

**핵심 원칙**: Spring Cloud Gateway가 제공하는 네이티브 필터 팩토리(`RemoveRequestHeader`, `Retry`, `CircuitBreaker`, `RequestRateLimiter`, `RequestSize` 등)를 우선 활용하고, 네이티브로 해결할 수 없는 경우에만 커스텀 필터를 구현합니다.

---

## 현재 상태 분석

### 기술 스택

- Spring Cloud Gateway (WebFlux/Netty 기반, `spring-cloud-starter-gateway-server-webflux`)
- Spring Cloud 2025.1.0, Spring Boot 4.0.2
- Reactor Netty HTTP Client (커넥션 풀 설정 포함)
- 커스텀 JWT 검증 필터 (`JwtAuthenticationGatewayFilter` — `GatewayFilter` 구현, `GlobalFilter`로 래핑)
- Spring Security 완전 비활성화 (reactive 환경에서 servlet 기반 Security 제외)
- 무상태 프록시 (DB, Redis, 영속성 의존성 없음)

### 핵심 파일 목록

| 파일 | 역할 |
|------|------|
| `api/gateway/src/main/java/.../filter/JwtAuthenticationGatewayFilter.java` | JWT 검증, 경로별 인증/인가, 헤더 주입 |
| `api/gateway/src/main/java/.../config/GatewayConfig.java` | 필터를 `@Order(HIGHEST_PRECEDENCE)` GlobalFilter로 등록 |
| `api/gateway/src/main/java/.../config/ServerConfig.java` | `@ComponentScan`으로 `com.tech.n.ai.common.security.jwt` 패키지 로딩 |
| `api/gateway/src/main/java/.../config/WebConfig.java` | `WebFluxConfigurer` — 정적 리소스 핸들러 (gateway에서 불필요) |
| `api/gateway/src/main/java/.../common/exception/ApiGatewayExceptionHandler.java` | `WebExceptionHandler @Order(-2)` — 전역 예외 처리 |
| `api/gateway/src/main/java/.../ApiGatewayApplication.java` | DataSource, MongoDB, Redis, Security 등 12개 auto-configuration 제외 |
| `api/gateway/src/main/resources/application.yml` | 라우트, 타임아웃, CORS, 커넥션 풀, default-filters |
| `api/gateway/src/main/resources/application-{profile}.yml` | 프로필별 서비스 URI, CORS origin, 로그 레벨 |
| `api/gateway/build.gradle` | 의존성 및 Servlet/Security/Redis 전역 제외 |
| `common/security/.../jwt/JwtTokenProvider.java` | JWT 토큰 검증/파싱 (Admin Access Token: 15분, User: 60분) |
| `common/core/.../constants/ErrorCodeConstants.java` | 에러 코드 상수 (`RATE_LIMIT_EXCEEDED` 등 미사용 코드 포함) |
| `common/core/.../constants/ApiConstants.java` | `HEADER_X_REQUEST_ID`, `HEADER_X_USER_ID` 등 헤더 상수 (gateway 미사용) |

---

## 영역별 현재 상태 및 개선 요구사항

### 1. 라우팅

#### 현재 상태

```yaml
# application.yml — 5개 라우트 (선언적 YAML, Path predicate만 사용)
spring.cloud.gateway.server.webflux.routes:
  - id: auth-route
    uri: ${gateway.routes.auth.uri:http://localhost:8083}
    predicates: [Path=/api/v1/auth/**]
  - id: bookmark-route      # → localhost:8085
  - id: chatbot-route       # → localhost:8084
  - id: agent-route         # → localhost:8086
  - id: emerging-tech-route # → localhost:8082
```

- Path predicate만 사용 (Host, Method, Header predicate 없음)
- StripPrefix, RewritePath 필터 없음 — 클라이언트 요청 경로가 백엔드에 그대로 전달
- 프로필별 서비스 URI: local은 `localhost:port`, dev/beta/prod는 `http://api-{service}-service:8080` (K8s DNS)
- 서비스 디스커버리 없음 (Eureka, Consul 미사용) — 정적 URI, K8s Service DNS에 의존
- 로드밸런싱 없음 (`spring-cloud-starter-loadbalancer` 미포함) — K8s Service가 L4 LB 담당
- 라우트별 필터 없음 (default-filters만 사용: `DedupeResponseHeader`)

#### 개선 요구사항

##### 1.1 내부 서비스 보안 (Critical)

**문제**: 백엔드 서비스가 게이트웨이를 거치지 않고 직접 접근 가능한 경우, `x-user-id`, `x-user-email`, `x-user-role` 헤더를 위조할 수 있음.

**설계 요구사항**:

방식 A — K8s NetworkPolicy (권장):
```yaml
# K8s NetworkPolicy — 백엔드 서비스는 gateway pod에서만 ingress 허용
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-only-gateway
spec:
  podSelector:
    matchLabels:
      app: api-auth-service  # 각 백엔드 서비스별 적용
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - port: 8080
```

방식 B — Gateway Secret 헤더 (보완):
- 게이트웨이가 `X-Gateway-Secret` 헤더를 주입하고 백엔드가 검증
- SCG `default-filters`에 `AddRequestHeader=X-Gateway-Secret,${GATEWAY_SECRET}` 추가
- 시크릿 값은 환경변수로 주입, 주기적 교체

방식 C — mTLS:
- 게이트웨이 ↔ 백엔드 간 상호 인증서 검증
- 운영 복잡도가 높아 현 단계에서는 방식 A+B 조합 권장

##### 1.2 Retry 필터 추가 (Medium)

**문제**: 일시적 백엔드 장애(503, 커넥션 리셋 등)에 대한 재시도가 없음.

**설계 — SCG 네이티브 `Retry` 필터 활용**:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          default-filters:
            - DedupeResponseHeader=Access-Control-Allow-Origin, RETAIN_LAST
            - name: Retry
              args:
                retries: 2
                methods: GET          # 멱등한 GET만 재시도
                statuses: SERVICE_UNAVAILABLE  # 503만 재시도
                backoff:
                  firstBackoff: 100ms
                  maxBackoff: 500ms
                  factor: 2
```

- POST/PUT/DELETE는 재시도하지 않음 (`methods: GET`)
- 503(Service Unavailable)에 대해서만 재시도 (502는 즉시 실패 반환)
- 최대 2회 재시도, 지수 백오프 (100ms → 200ms)

##### 1.3 요청 크기 제한 추가 (Medium)

**문제**: 요청 본문 크기 제한이 없어 대용량 페이로드 공격에 취약.

**설계 — SCG 네이티브 `RequestSize` 필터 활용**:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          default-filters:
            - name: RequestSize
              args:
                maxSize: 5MB
```

- 기본 5MB 제한 (파일 업로드가 필요한 라우트는 개별 설정)
- 초과 시 413 Payload Too Large 자동 반환

##### 1.4 README 최신화 (Low)

**문제**: `README.md`에 `archive`, `contest`, `news` 등 존재하지 않는 라우트명이 문서화되어 있음.

**설계 요구사항**: 현재 5개 라우트(auth, bookmark, chatbot, agent, emerging-tech)에 맞게 README 갱신

---

### 2. 인증과 인가

#### 현재 상태

```
요청 흐름:
Client → Netty (port 8081)
  → GatewayConfig.jwtAuthenticationGlobalFilter() [HIGHEST_PRECEDENCE]
    → JwtAuthenticationGatewayFilter.filter()
      → isPublicPath(path) 체크
      → extractToken() (Authorization: Bearer)
      → JwtTokenProvider.validateToken() (HMAC-SHA 서명 검증, jjwt 라이브러리)
      → JwtTokenProvider.getPayloadFromToken() (userId, email, role 추출)
      → isAdminOnlyPath(path) + role == "ADMIN" 체크
      → request.mutate().header() → x-user-id, x-user-email, x-user-role 주입
    → chain.filter() → 백엔드 서비스
```

**경로 분류 (코드 하드코딩)**:
- 공개: `/api/v1/auth/admin/login` (특별 허용), `/api/v1/auth/**` (admin 제외), `/api/v1/emerging-tech/**`, `/actuator/**`
- 보호: 위에 해당하지 않는 모든 경로 (JWT 필수)
- 관리자 전용: `/api/v1/agent/**`, `/api/v1/auth/admin/**` (ADMIN role 필수)

**JWT 유효기간** (`JwtTokenProvider` 기본값):
- User Access Token: 60분, Refresh Token: 7일
- Admin Access Token: **15분**, Refresh Token: 1일

**특이사항**:
- `request.mutate().header(name, value)`는 내부적으로 `HttpHeaders.set()`을 호출하여 기존 값을 **대체**함. 그러나 공개 경로(`isPublicPath() == true`)로 진입하는 요청은 이 로직을 타지 않으므로, 공개 경로에서 `x-user-*` 헤더가 그대로 백엔드로 전달될 수 있음
- 백엔드 서비스는 `common-security`의 `SecurityConfig`로 자체 Spring Security 필터 체인도 보유 (심층 방어)
- 토큰 해지 불가 — 순수 무상태 JWT, 탈취된 토큰은 만료까지 유효

#### 개선 요구사항

##### 2.1 x-user-* 헤더 스푸핑 방지 (Critical)

**문제**: 외부 클라이언트가 `x-user-id` 등의 헤더를 직접 설정하여 요청할 수 있음. 인증 경로에서는 `mutate().header()`가 대체하지만, **공개 경로(`isPublicPath`)에서는 헤더 검증/제거 로직을 타지 않아 위조된 헤더가 백엔드에 그대로 전달**됨.

**설계 — SCG 네이티브 `RemoveRequestHeader` default-filter 활용**:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          default-filters:
            - DedupeResponseHeader=Access-Control-Allow-Origin, RETAIN_LAST
            # 외부 클라이언트가 보낸 x-user-* 헤더를 모든 라우트에서 무조건 제거
            # JWT 필터가 검증 후 다시 주입하므로, 외부 위조 헤더가 백엔드에 도달 불가
            - RemoveRequestHeader=x-user-id
            - RemoveRequestHeader=x-user-email
            - RemoveRequestHeader=x-user-role
```

이 방식의 장점:
1. **모든 요청에 적용** — 공개 경로 포함, 필터 순서와 무관하게 헤더 제거
2. **코드 변경 불필요** — YAML 설정만으로 해결
3. **SCG 표준 패턴** — 커스텀 코드 대비 유지보수 용이
4. **JWT 필터와의 호환** — `RemoveRequestHeader`가 외부 헤더를 제거한 후, JWT 필터의 `mutate().header()`가 검증된 값을 주입

> **주의**: `RemoveRequestHeader`는 default-filter이므로 `GatewayFilter` 체인에서 실행됨. `JwtAuthenticationGatewayFilter`는 `HIGHEST_PRECEDENCE` GlobalFilter이므로 default-filter보다 **먼저** 실행됨. 따라서 JWT 필터가 주입한 헤더가 이후 `RemoveRequestHeader`에 의해 제거되지 않도록 **실행 순서를 검증**해야 함.
>
> 실행 순서: GlobalFilter(HIGHEST_PRECEDENCE) → route matching → GatewayFilter(default-filters) → proxy
>
> GlobalFilter에서 `mutate().header()`로 설정한 헤더가 이후 default-filter의 `RemoveRequestHeader`에 의해 제거될 수 있음. 이 경우 두 가지 해결 방안:
> - **방안 A**: `RemoveRequestHeader`를 default-filter 대신 **커스텀 GlobalFilter로 구현**하여 JWT 필터보다 먼저 실행 (`@Order(HIGHEST_PRECEDENCE - 1)`)
> - **방안 B**: JWT 필터 내부에서 `mutate().headers(h -> { h.remove("x-user-id"); h.remove("x-user-email"); h.remove("x-user-role"); })` 후 검증된 값 주입. 공개 경로에서도 제거 로직을 실행하되 주입은 하지 않음

##### 2.2 공개/보호 경로 관리 유연성 (High)

**문제**: `isPublicPath()`와 `isAdminOnlyPath()`가 코드에 하드코딩되어 있어 새 경로 추가 시 코드 수정과 재배포 필요. `/api/v1/auth/admin/login`만 공개로 열고 나머지 `/api/v1/auth/admin/**`은 보호하는 복잡한 로직이 if 분기로 구현되어 있어 취약.

**설계 요구사항**:

```yaml
gateway:
  security:
    public-paths:
      - /api/v1/auth/admin/login   # 구체적 경로가 우선
      - /api/v1/auth/**
      - /api/v1/emerging-tech/**
      - /actuator/**
    public-path-exclusions:        # public-paths에서 제외 (exclusion > inclusion)
      - /api/v1/auth/admin/**
    admin-only-paths:
      - /api/v1/agent/**
      - /api/v1/auth/admin/**
```

```java
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
    List<String> publicPaths,
    List<String> publicPathExclusions,
    List<String> adminOnlyPaths
) {}
```

- Spring WebFlux의 `PathPattern`을 사용하여 패턴 매칭 (WebFlux 환경에서 `AntPathMatcher`보다 권장)
- 경로 우선순위: 구체적 경로(exact match) > exclusion > inclusion (wildcard)
- 변경 시 YAML만 수정하면 되며, 코드 재배포 없이 ConfigMap 교체로 적용 가능 (K8s)

##### 2.3 토큰 해지 메커니즘 검토 (Medium)

**문제**: 순수 무상태 JWT라 토큰 해지 불가. 관리자 계정 탈취 시 Access Token 만료까지 대응 불가.

**현재 완화 조치 확인**: `JwtTokenProvider`에서 Admin Access Token 유효기간이 **15분**으로 이미 짧게 설정되어 있음. User는 60분.

**설계 요구사항**:
- 15분 유효기간이 충분한 완화인지 평가 (업계 표준: 관리자 5~15분 권장 → 현재 적합)
- Redis 기반 토큰 블록리스트 도입 시 트레이드오프 분석:
  - 장점: 즉시 해지 가능
  - 단점: gateway에 Redis 의존성 추가 → 무상태 원칙 위반, Redis 장애 시 인증 실패 가능
  - 판단: 현 단계에서는 짧은 유효기간(15분)으로 충분. Redis 블록리스트는 보안 사고 발생 시 후속 조치로 검토

---

### 3. 변환 (Transformation)

#### 현재 상태

- 순수 HTTP-to-HTTP 리버스 프록시
- 유일한 변환: JWT 검증 후 `x-user-id/email/role` 헤더 주입
- 응답 통합(Aggregation), BFF 패턴, gRPC 변환 없음

#### 개선 요구사항

##### 3.1 BFF/Aggregation 필요성 평가 (Low — 현재 불필요)

**현재 판단**: 각 API 모듈이 독립적인 도메인을 담당하고 있으므로 현 시점에서 BFF 패턴은 불필요.

**향후 검토 조건**:
- 프론트엔드에서 여러 API를 호출하여 데이터를 조합하는 패턴이 반복적으로 발생할 때
- 모바일 클라이언트를 위한 최적화된 응답이 필요할 때

##### 3.2 gRPC 변환 필요성 평가 (Low — 현재 불필요)

**현재 판단**: 모든 백엔드 서비스가 REST API이므로 gRPC 변환 불필요.

---

### 4. 공통 정책

#### 현재 상태

| 정책 | 상태 | 구현 방식 |
|------|------|----------|
| CORS | 구현됨 | `globalcors` YAML, 프로필별 origin 관리, `DedupeResponseHeader` |
| 커넥션 풀 | 구현됨 | `httpclient.pool` (max-connections: 500, idle: 30s, life: 5m) |
| Rate Limiting | **미구현** | `ErrorCodeConstants.RATE_LIMIT_EXCEEDED` 상수만 정의 |
| Circuit Breaker | **미구현** | resilience4j 미포함 |
| IP 차단 | **미구현** | — |
| 요청 크기 제한 | **미구현** | — |

#### 개선 요구사항

##### 4.1 Rate Limiting 구현 (Critical)

**문제**: 어떤 형태의 요청 제한도 없어 DDoS, brute-force, API 남용에 무방비.

**방식 선택 분석**:

| 방식 | 장점 | 단점 | 적합 시나리오 |
|------|------|------|--------------|
| SCG `RequestRateLimiter` + Redis | 네이티브, 분산 환경, Token Bucket 알고리즘 | Redis 의존성 추가 | 다중 인스턴스 운영 (권장) |
| Bucket4j (인메모리) | 외부 의존성 없음 | 인스턴스별 독립 카운트 | 단일 인스턴스 |
| K8s Ingress 레벨 | 코드 변경 없음 | 세밀한 제어 불가 | 1차 방어벽 |

**설계 — SCG 네이티브 `RequestRateLimiter` + Redis (권장)**:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: auth-route
              uri: ${gateway.routes.auth.uri}
              predicates:
                - Path=/api/v1/auth/**
              filters:
                - name: RequestRateLimiter
                  args:
                    redis-rate-limiter:
                      replenishRate: 10     # 초당 10개 토큰 보충
                      burstCapacity: 20     # 최대 버스트 20개
                      requestedTokens: 1
                    key-resolver: "#{@ipKeyResolver}"
```

```java
@Bean
public KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(
        Optional.ofNullable(exchange.getRequest().getRemoteAddress())
            .map(addr -> addr.getAddress().getHostAddress())
            .orElse("unknown")
    );
}

@Bean
public KeyResolver userKeyResolver() {
    return exchange -> Mono.just(
        Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("x-user-id"))
            .orElseGet(() -> /* fallback to IP */ ...)
    );
}
```

**Rate Limit 정책**:
- 인증된 사용자: `x-user-id` 기준, replenishRate=100/min, burstCapacity=150
- 비인증 사용자/공개 경로: IP 기준, replenishRate=30/min, burstCapacity=50
- 관리자 로그인 (`/api/v1/auth/admin/login`): IP 기준, replenishRate=5/min, burstCapacity=5
- 429 응답 시 `Retry-After` 헤더 포함, `ErrorCodeConstants.RATE_LIMIT_EXCEEDED` 활용

**의존성 추가** (`build.gradle`):
```groovy
// Redis 전역 제외를 해제하고 reactive Redis만 추가
implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
```

**주의**: 현재 `build.gradle`에서 `spring-boot-starter-data-redis`를 전역 제외하고 있으므로, `spring-boot-starter-data-redis-reactive`를 명시적으로 추가하거나 제외 규칙을 조정해야 함. `ApiGatewayApplication`의 `DataRedisAutoConfiguration`/`DataRedisReactiveAutoConfiguration` 제외도 해제 필요.

**Graceful Degradation**: Redis 불가용 시에도 게이트웨이 기본 동작(라우팅, 인증)은 영향받지 않아야 함. Rate Limiter가 실패하면 요청을 통과시키는 `deny-empty-key: false` 설정 적용.

##### 4.2 Circuit Breaker 도입 (High)

**문제**: 백엔드 서비스 장애 시 게이트웨이가 계속 요청을 전달하여 장애가 전파됨.

**설계 — SCG 네이티브 `CircuitBreaker` 필터 + Resilience4j**:

```groovy
// build.gradle
implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'
```

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: chatbot-route
              uri: ${gateway.routes.chatbot.uri}
              predicates:
                - Path=/api/v1/chatbot/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: chatbotCircuitBreaker
                    fallbackUri: forward:/fallback/chatbot
                    statusCodes:
                      - 500
                      - 503

resilience4j:
  circuitbreaker:
    instances:
      chatbotCircuitBreaker:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        waitDurationInOpenState: 10s
        failureRateThreshold: 50
  timelimiter:
    instances:
      chatbotCircuitBreaker:
        timeoutDuration: 30s
```

- 라우트별 CircuitBreaker 인스턴스 (서비스 간 장애 격리)
- Fallback 엔드포인트: `ApiResponse` 형식으로 503 서비스 일시적 불가 메시지 반환
- Half-open에서 3개 프로브 요청 허용 (`permittedNumberOfCallsInHalfOpenState: 3`)

##### 4.3 IP 기반 접근 제어 (Medium)

**문제**: 관리자 API에 대한 IP 기반 접근 제어가 없음.

**설계 요구사항**:
- 관리자 경로(`/api/v1/auth/admin/**`, `/api/v1/agent/**`)에 IP 화이트리스트 적용
- `application.yml`에서 허용 IP 목록 관리
- `XForwardedRemoteAddressResolver`를 활용하여 프록시 뒤 실제 클라이언트 IP 추출 (K8s Ingress 환경)
- 차단 시 403 응답과 함께 감사 로그 기록 (클라이언트 IP, 요청 경로, 시간)

---

### 5. 관측성 (Observability)

#### 현재 상태

| 영역 | 상태 | 상세 |
|------|------|------|
| Access Log | **미구현** | JWT 필터, 예외 핸들러에서 SLF4J 개별 로깅만 |
| Trace ID | **미활성** | `micrometer-tracing-bridge-otel` 전이 의존성 존재, OTLP export 비활성화, 요청/응답에 Trace ID 미주입 |
| Request ID | **미사용** | `ApiConstants.HEADER_X_REQUEST_ID` 상수 정의됨, gateway에서 미사용 |
| Metrics | 부분 구현 | Actuator + Prometheus/Dynatrace 레지스트리, SCG 내장 메트릭 미활성화 |
| Health Check | 기본 | `/actuator` 공개 경로, 커스텀 HealthIndicator 없음 |
| 로그 레벨 | **문제** | prod에서 `WARN` — 정상 요청 로그 전혀 남지 않음 |

#### 개선 요구사항

##### 5.1 구조화된 Access Log (Critical)

**문제**: 게이트웨이를 통과하는 모든 요청/응답에 대한 체계적인 로그가 없어 장애 분석, 보안 감사, 트래픽 패턴 파악이 불가능.

**설계 요구사항**:
- 게이트웨이 전용 Access Log `GlobalFilter` 구현 (응답 완료 후 로그 기록)
- `@Order(Ordered.LOWEST_PRECEDENCE)` — 필터 체인의 가장 마지막에서 응답 시간 측정
- 기록 항목 (JSON 구조화 로그):

```json
{
  "timestamp": "2026-03-06T10:30:00.123Z",
  "traceId": "abc123...",
  "requestId": "X-Request-Id 값",
  "method": "GET",
  "path": "/api/v1/chatbot/sessions",
  "statusCode": 200,
  "responseTimeMs": 45,
  "clientIp": "192.168.1.100",
  "userId": "12345",
  "userAgent": "Mozilla/5.0...",
  "contentLength": 1024,
  "routeId": "chatbot-route"
}
```

- **별도 Logger 사용**: `LoggerFactory.getLogger("ACCESS_LOG")` — 비즈니스 로그와 분리하여 prod에서도 항상 INFO 출력
- 민감 정보 마스킹: Authorization 헤더, 쿼리 파라미터의 토큰 값
- 요청 본문은 로깅하지 않음 (성능, 보안)
- Logback 설정에서 ACCESS_LOG 전용 appender 구성 가능

##### 5.2 Trace ID 발급 및 전파 (Critical)

**문제**: 분산 추적을 위한 Trace ID가 요청/응답에 포함되지 않아, 게이트웨이 → 백엔드 요청 추적 불가.

**설계 요구사항**:
- `GlobalFilter`로 구현 (`@Order(Ordered.HIGHEST_PRECEDENCE + 1)` — JWT 필터 바로 다음)
- 게이트웨이 진입 시 `X-Request-Id` 헤더가 없으면 UUID 기반으로 생성
- 클라이언트가 보낸 `X-Request-Id`가 있으면 그대로 전파 (단, UUID 형식 검증)
- 응답에 `X-Request-Id` 헤더 포함 — SCG 네이티브 `AddResponseHeader`나 `SetResponseHeader` 필터 대신 GlobalFilter에서 동적 생성이므로 커스텀 구현
- Micrometer Tracing의 `traceId` 활용하여 OpenTelemetry와 자연스럽게 연동
- 기존 `ApiConstants.HEADER_X_REQUEST_ID` 상수 활용
- Access Log에 traceId, requestId 모두 포함

##### 5.3 SCG 내장 Metrics 활성화 (High)

**문제**: Spring Cloud Gateway의 내장 라우트 메트릭이 비활성화 상태.

**설계 — `spring.cloud.gateway.metrics.enabled` 활성화**:

```yaml
spring:
  cloud:
    gateway:
      metrics:
        enabled: true        # spring.cloud.gateway.requests 타이머 활성화
        tags:
          path:
            enabled: true    # 경로별 태그 (주의: 카디널리티)

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,gateway
  prometheus:
    metrics:
      export:
        enabled: true
```

활성화 시 자동 수집되는 메트릭:
- `spring.cloud.gateway.requests` — routeId, status, httpMethod, outcome 태그 포함
- 라우트별 요청 수, 응답 시간 분포, 에러율 자동 수집

커스텀 메트릭 추가:
- 인증 실패 횟수 (`gateway.auth.failures`, Counter)
- Rate Limit 도달 횟수 (`gateway.ratelimit.exceeded`, Counter)
- Circuit Breaker 상태 변경 — resilience4j가 자동 등록

##### 5.4 Health Check 강화 (Medium)

**설계 요구사항**:
- 백엔드 서비스 연결 상태를 확인하는 커스텀 `ReactiveHealthIndicator` 구현
- 각 라우트 대상 서비스의 `/actuator/health`를 `WebClient`로 주기적 확인 (논블로킹)
- Liveness/Readiness probe 분리:

```yaml
management:
  endpoint:
    health:
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState, backendServices
      probes:
        enabled: true
```

- Liveness: 게이트웨이 자체 상태만 (JVM, 디스크)
- Readiness: 백엔드 서비스 연결 포함 (하나라도 불가하면 readiness fail → K8s가 트래픽 차단)

---

## 추가 발견 사항

### 코드 품질 이슈

| 이슈 | 위치 | 심각도 | 개선 방향 |
|------|------|--------|----------|
| `application-prod.yml`에 `org.hibernate.*` 로그 설정 — gateway에서 JPA 미사용 | `application-prod.yml` | Low | 제거 |
| `ApiGatewayExceptionHandler`에서 예외 클래스 이름 문자열 매칭 (`contains("Timeout")`) | `ApiGatewayExceptionHandler.java` | Medium | `instanceof` 타입 매칭으로 변경 (`ReadTimeoutException`, `ConnectException` 등 구체 타입) |
| `WebConfig.java`의 정적 리소스 핸들러 — gateway에서 정적 파일 서빙 불필요 | `WebConfig.java` | Low | 클래스 제거 또는 빈 구현으로 교체 |
| `README.md`에 존재하지 않는 라우트(archive, contest, news) 기재 | `README.md` | Low | 현행화 |
| `JwtAuthenticationGatewayFilter` 내 Javadoc 순서 오류 — `handleUnauthorized`의 Javadoc이 `isAdminOnlyPath` 위에 위치 | `JwtAuthenticationGatewayFilter.java:126-134` | Low | Javadoc 위치 수정 |

### 의존성 정리

- `common-security` 모듈에서 gateway가 사용하는 것은 `JwtTokenProvider`와 `JwtTokenPayload`뿐
- `SecurityConfig`, `JwtAuthenticationFilter`, `UserPrincipal` 등은 gateway에서 완전히 비활성
- `common-security`를 `common-security-jwt`(JWT 유틸)과 `common-security-web`(Spring Security 체인)으로 분리 검토
- `build.gradle`에 `spring-boot-restdocs`, `spring-restdocs-mockmvc` 테스트 의존성이 있으나 gateway는 WebFlux 기반이므로 `spring-restdocs-webtestclient`가 적합

### XForwardedHeaders 처리

- K8s Ingress → Gateway 경로에서 `X-Forwarded-For`, `X-Forwarded-Proto` 등의 헤더 처리 확인 필요
- Spring Cloud Gateway는 기본적으로 `ForwardedHeadersFilter`를 활성화하지만, 신뢰할 수 있는 프록시 범위 설정 권장:

```yaml
server:
  forward-headers-strategy: framework   # Spring이 X-Forwarded-* 처리
```

---

## 구현 우선순위

보안 리스크와 운영 영향도를 기준으로 산정합니다.

### Phase 1 — 즉시 조치 (보안/운영 필수)

| # | 항목 | 영역 | 근거 | SCG 네이티브 |
|---|------|------|------|-------------|
| 1 | x-user-* 헤더 스푸핑 방지 | 인증/인가 | 공개 경로에서 권한 위조 가능 | `RemoveRequestHeader` default-filter 또는 커스텀 GlobalFilter |
| 2 | 구조화된 Access Log | 관측성 | 장애 분석, 보안 감사 불가 | 커스텀 GlobalFilter |
| 3 | Trace ID 발급 및 전파 | 관측성 | 분산 추적 불가 | 커스텀 GlobalFilter + `ApiConstants` 활용 |
| 4 | Rate Limiting 기본 구현 | 공통 정책 | brute-force 무방비 | `RequestRateLimiter` + Redis |

### Phase 2 — 안정성 강화

| # | 항목 | 영역 | 근거 | SCG 네이티브 |
|---|------|------|------|-------------|
| 5 | 공개/보호 경로 외부화 | 인증/인가 | 하드코딩 유지보수 리스크 | `@ConfigurationProperties` |
| 6 | Circuit Breaker 도입 | 공통 정책 | 장애 전파 방지 | `CircuitBreaker` 필터 + Resilience4j |
| 7 | Retry 필터 (GET 전용) | 라우팅 | 일시적 장애 복원력 | `Retry` 필터 (네이티브) |
| 8 | SCG 내장 Metrics 활성화 | 관측성 | 운영 가시성 | `spring.cloud.gateway.metrics.enabled` |
| 9 | 요청 크기 제한 | 공통 정책 | 대용량 페이로드 공격 방어 | `RequestSize` 필터 (네이티브) |

### Phase 3 — 심화 보안

| # | 항목 | 영역 | 근거 |
|---|------|------|------|
| 10 | 내부 서비스 보안 (NetworkPolicy + Gateway Secret) | 라우팅 | 내부 네트워크 공격 방어 |
| 11 | 관리자 API IP 화이트리스트 | 공통 정책 | 관리자 경로 추가 보호 |
| 12 | Health Check 강화 | 관측성 | K8s 운영 안정성 |
| 13 | XForwardedHeaders 처리 확인 | 라우팅 | 프록시 뒤 클라이언트 IP 정확성 |

### Phase 4 — 아키텍처 개선

| # | 항목 | 영역 | 근거 |
|---|------|------|------|
| 14 | common-security 모듈 분리 검토 | 아키텍처 | 의존성 명확화 |
| 15 | 예외 핸들러 리팩토링 (문자열 → 타입 매칭) | 코드 품질 | 유지보수성 |
| 16 | 코드/설정 정리 (WebConfig, Hibernate 로그, README) | 코드 품질 | 문서-코드 일관성 |
| 17 | 테스트 의존성 수정 (restdocs-mockmvc → webtestclient) | 빌드 | WebFlux 호환 |

---

## 구현 제약사항

- **gateway의 무상태 원칙 유지**: Rate Limiting에 Redis를 도입하더라도, Redis 불가용 시 게이트웨이의 기본 동작(라우팅, 인증)은 영향받지 않아야 함 (`deny-empty-key: false`)
- **Reactive 스택 일관성**: 모든 새 필터는 `GlobalFilter` 또는 `GatewayFilter` 기반의 논블로킹 구현. 블로킹 호출 금지
- **SCG 네이티브 우선**: 네이티브 필터로 해결 가능한 경우 커스텀 구현을 만들지 않음
- **기존 API 호환성**: 클라이언트가 사용하는 경로, 헤더, 응답 형식 변경 금지
- **프로필별 분리**: 모든 설정은 환경별 프로필(local/dev/beta/prod)로 관리
- **성능**: 게이트웨이 필터 체인의 추가 레이턴시는 5ms 미만이어야 함
- **필터 순서 문서화**: 모든 GlobalFilter와 GatewayFilter의 `@Order` 값을 한 곳에 문서화

---

## 참고 자료

구현 시 반드시 아래 공식 문서를 참조하세요:

- [Spring Cloud Gateway 공식 문서 (최신)](https://docs.spring.io/spring-cloud-gateway/reference/)
- [SCG GatewayFilter Factories](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories.html) — RemoveRequestHeader, Retry, CircuitBreaker, RequestRateLimiter, RequestSize 등
- [SCG GlobalFilter 순서](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html)
- [Resilience4j 공식 문서](https://resilience4j.readme.io/docs)
- [Micrometer Tracing 공식 문서](https://micrometer.io/docs/tracing)
- [OpenTelemetry Java SDK 공식 문서](https://opentelemetry.io/docs/languages/java/)
