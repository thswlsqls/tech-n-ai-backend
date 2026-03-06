# API Gateway 개선 설계서

## 1. 개요 (Overview)

### 1.1 목적

`api/gateway` 모듈의 현재 구현을 **Spring Cloud Gateway 베스트 프랙티스**에 맞게 개선하여 보안, 안정성, 관측성을 강화합니다. 보안 취약점(헤더 스푸핑, Rate Limiting 부재)을 우선 해결하고, 운영에 필요한 관측성과 장애 복원력을 단계적으로 확보합니다.

### 1.2 범위

| 영역 | 현재 상태 | 개선 목표 |
|------|----------|----------|
| 라우팅 | 5개 Path 기반 라우트, 재시도 없음 | Retry 필터, 요청 크기 제한, 내부 서비스 보안 |
| 인증/인가 | 커스텀 JWT 필터, 경로 하드코딩 | 헤더 스푸핑 방지, 경로 외부화 |
| 공통 정책 | CORS, 커넥션 풀만 구현 | Rate Limiting, Circuit Breaker, IP 접근 제어 |
| 관측성 | SLF4J 개별 로깅만 | Access Log, Trace ID, 내장 메트릭, Health Check |
| 변환 | 순수 HTTP 프록시 | 현재 불필요 (향후 검토 조건 문서화) |

### 1.3 핵심 설계 원칙

1. **SCG 네이티브 우선**: `RemoveRequestHeader`, `Retry`, `CircuitBreaker`, `RequestRateLimiter`, `RequestSize` 등 내장 필터 팩토리를 우선 활용
2. **무상태 원칙 유지**: Redis 도입 시에도 Redis 불가용이 라우팅/인증에 영향 없어야 함
3. **Reactive 일관성**: 모든 새 필터는 논블로킹 구현 (블로킹 호출 금지)
4. **기존 API 호환성**: 클라이언트 경로, 헤더, 응답 형식 변경 금지

### 1.4 현재 시스템 현황

#### 기술 스택

- Spring Boot 4.0.2, Spring Cloud 2025.1.0
- Spring Cloud Gateway (`spring-cloud-starter-gateway-server-webflux`) — WebFlux/Netty 기반
- Java 21, Gradle 9.2.1 (Groovy DSL)

#### 핵심 파일

| 파일 | 역할 |
|------|------|
| `JwtAuthenticationGatewayFilter.java` | JWT 검증, 경로별 인증/인가, `x-user-*` 헤더 주입 |
| `GatewayConfig.java` | JWT 필터를 `@Order(HIGHEST_PRECEDENCE)` GlobalFilter로 등록 |
| `ApiGatewayExceptionHandler.java` | `WebExceptionHandler @Order(-2)` — 전역 예외 처리 |
| `ApiGatewayApplication.java` | DataSource, MongoDB, Redis, Security 등 12개 auto-config 제외 |
| `application.yml` | 5개 라우트, 커넥션 풀, CORS, default-filters |
| `build.gradle` | Servlet/Security/Redis 전역 제외 |

#### 현재 필터 실행 순서

```
Client → Netty (port 8081)
  → GlobalFilter: JwtAuthenticationGatewayFilter [HIGHEST_PRECEDENCE]
    → isPublicPath() → extractToken() → validateToken() → mutate().header()
  → Route Matching (Path predicate)
  → GatewayFilter(default-filters):
    → DedupeResponseHeader=Access-Control-Allow-Origin, RETAIN_LAST
  → Reactor Netty HTTP Client → Backend Service
  → Exception: ApiGatewayExceptionHandler [@Order(-2)]
```

---

## 2. Phase 1 — 즉시 조치 (보안/운영 필수)

### 2.1 x-user-* 헤더 스푸핑 방지

#### 문제 분석

외부 클라이언트가 `x-user-id`, `x-user-email`, `x-user-role` 헤더를 직접 설정하여 요청할 수 있음.

- **인증 경로**: `mutate().header()`가 `HttpHeaders.set()` 호출하여 기존 값을 대체 → 안전
- **공개 경로 (`isPublicPath() == true`)**: 헤더 검증/제거 로직을 타지 않아 **위조된 헤더가 백엔드에 그대로 전달** → 취약

#### 설계 방안 비교

| 방안 | 구현 방식 | 장점 | 단점 |
|------|----------|------|------|
| A. `RemoveRequestHeader` default-filter (YAML) | `default-filters`에 3개 추가 | 코드 변경 없음, SCG 표준 | **실행 순서 문제**: default-filter는 GatewayFilter 체인에서 실행되어 GlobalFilter(JWT) 이후에 동작 → JWT 필터가 주입한 헤더까지 제거될 수 있음 |
| **B. 커스텀 GlobalFilter** (권장) | `HeaderSanitizeGlobalFilter` 구현 | 순서 완전 제어 가능, 모든 경로에 적용 | 커스텀 코드 추가 |
| C. JWT 필터 내부 수정 | `isPublicPath()`에서도 헤더 제거 | 기존 코드 수정만 | 관심사 분리 위반 |

#### 채택: 방안 B — 커스텀 GlobalFilter

**실행 순서 분석**:
```
[HIGHEST_PRECEDENCE - 1] HeaderSanitizeGlobalFilter  → x-user-* 제거
[HIGHEST_PRECEDENCE]     JwtAuthenticationGlobalFilter → JWT 검증 후 x-user-* 주입
[route matching]         → Path predicate 매칭
[default-filters]        → DedupeResponseHeader, Retry, RequestSize 등
[proxy]                  → Backend Service
```

#### 구현 설계

**새 파일**: `api/gateway/src/main/java/.../filter/HeaderSanitizeGlobalFilter.java`

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE - 1) // JWT 필터보다 먼저 실행
public class HeaderSanitizeGlobalFilter implements GlobalFilter {

    private static final List<String> SANITIZE_HEADERS = List.of(
        "x-user-id", "x-user-email", "x-user-role"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
            .headers(headers -> SANITIZE_HEADERS.forEach(headers::remove))
            .build();
        return chain.filter(exchange.mutate().request(sanitizedRequest).build());
    }
}
```

**수정 파일**: 없음 (기존 코드 변경 불필요)

**검증 시나리오**:
1. 공개 경로 + `x-user-id: fake` 헤더 → 백엔드에 `x-user-id` 헤더 없음
2. 인증 경로 + 유효 JWT + `x-user-id: fake` → 백엔드에 JWT에서 추출한 실제 userId
3. 인증 경로 + JWT 없음 → 401 Unauthorized

---

### 2.2 구조화된 Access Log

#### 문제 분석

- 게이트웨이를 통과하는 요청/응답에 대한 체계적 로그 없음
- prod에서 `WARN` 레벨 → 정상 요청 로그가 전혀 남지 않음
- 장애 분석, 보안 감사, 트래픽 패턴 파악 불가

#### 구현 설계

**새 파일**: `api/gateway/src/main/java/.../filter/AccessLogGlobalFilter.java`

```java
@Component
@Order(Ordered.LOWEST_PRECEDENCE) // 필터 체인 가장 마지막에서 응답 시간 측정
public class AccessLogGlobalFilter implements GlobalFilter {

    // 비즈니스 로그와 분리된 전용 Logger
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            ACCESS_LOG.info("{}",
                Map.of(
                    "timestamp", Instant.now().toString(),
                    "requestId", request.getHeaders().getFirst("X-Request-Id"),
                    "method", request.getMethod().name(),
                    "path", request.getURI().getPath(),
                    "statusCode", response.getStatusCode().value(),
                    "responseTimeMs", System.currentTimeMillis() - startTime,
                    "clientIp", extractClientIp(request),
                    "userId", Optional.ofNullable(request.getHeaders().getFirst("x-user-id")).orElse("-"),
                    "userAgent", Optional.ofNullable(request.getHeaders().getFirst("User-Agent")).orElse("-"),
                    "routeId", exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)
                )
            );
        }));
    }
}
```

**Logback 설정** (각 프로필별 `logback-spring.xml`):

```xml
<!-- ACCESS_LOG는 prod에서도 항상 INFO 출력 -->
<logger name="ACCESS_LOG" level="INFO" additivity="false">
    <appender-ref ref="ACCESS_LOG_APPENDER"/>
</logger>

<!-- JSON 포맷 appender (ELK/Loki 수집 용이) -->
<appender name="ACCESS_LOG_APPENDER" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

**민감 정보 마스킹 규칙**:
- Authorization 헤더 값: 로깅하지 않음
- 쿼리 파라미터의 `token`, `key` 등: `****`로 마스킹
- 요청 본문: 로깅하지 않음 (성능, 보안)

---

### 2.3 Trace ID 발급 및 전파

#### 문제 분석

- `ApiConstants.HEADER_X_REQUEST_ID` 상수가 common-core에 정의되어 있으나 gateway에서 미사용
- `micrometer-tracing-bridge-otel` 전이 의존성 존재하지만 OTLP export 비활성화
- 게이트웨이 → 백엔드 서비스 간 요청 추적 불가

#### 구현 설계

**새 파일**: `api/gateway/src/main/java/.../filter/RequestIdGlobalFilter.java`

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // JWT 필터 바로 다음
public class RequestIdGlobalFilter implements GlobalFilter {

    private static final String REQUEST_ID_HEADER = ApiConstants.HEADER_X_REQUEST_ID;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);

        // 클라이언트가 보낸 X-Request-Id 검증, 없으면 생성
        if (requestId == null || !isValidUuid(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        // 요청에 X-Request-Id 주입 (백엔드 전파)
        String finalRequestId = requestId;
        ServerHttpRequest modifiedRequest = request.mutate()
            .header(REQUEST_ID_HEADER, finalRequestId)
            .build();

        ServerWebExchange modifiedExchange = exchange.mutate()
            .request(modifiedRequest)
            .build();

        // 응답에 X-Request-Id 포함
        modifiedExchange.getResponse().getHeaders()
            .add(REQUEST_ID_HEADER, finalRequestId);

        return chain.filter(modifiedExchange);
    }
}
```

**개선된 필터 실행 순서** (Phase 1 완료 후):
```
[HIGHEST_PRECEDENCE - 1] HeaderSanitizeGlobalFilter    → x-user-* 제거
[HIGHEST_PRECEDENCE]     JwtAuthenticationGlobalFilter  → JWT 검증, x-user-* 주입
[HIGHEST_PRECEDENCE + 1] RequestIdGlobalFilter          → X-Request-Id 발급/전파
[route matching]         Path predicate
[default-filters]        DedupeResponseHeader
[LOWEST_PRECEDENCE]      AccessLogGlobalFilter          → 구조화 로그 (requestId 포함)
```

---

### 2.4 Rate Limiting 기본 구현

#### 문제 분석

- 어떤 형태의 요청 제한도 없어 brute-force, API 남용에 무방비
- `ErrorCodeConstants.RATE_LIMIT_EXCEEDED = "4029"`, `MESSAGE_CODE_RATE_LIMIT_EXCEEDED` 상수는 정의되어 있으나 미사용

#### 방식 결정: SCG `RequestRateLimiter` + Redis

Redis 기반을 선택하는 이유:
- K8s 환경에서 게이트웨이 다중 인스턴스 운영 → 분산 Rate Limiting 필수
- SCG 네이티브 `RequestRateLimiter` 필터가 Redis와 통합되어 있음
- Token Bucket 알고리즘으로 버스트 트래픽 유연 처리

#### 의존성 변경

**수정 파일**: `api/gateway/build.gradle`

```groovy
configurations.all {
    exclude group: 'org.springframework.boot', module: 'spring-boot-starter-web'
    exclude group: 'org.springframework.boot', module: 'spring-boot-starter-webmvc'
    // 삭제: exclude group: 'org.springframework.boot', module: 'spring-boot-starter-data-redis'
    exclude group: 'org.springframework.boot', module: 'spring-boot-starter-security'
    // ... OAuth2 제외는 유지
}

dependencies {
    // ... 기존 의존성 유지

    // Rate Limiting을 위한 Reactive Redis
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
}
```

**수정 파일**: `ApiGatewayApplication.java` — Redis auto-config 제외 해제

```java
@SpringBootApplication(excludeName = {
    // ... 기존 제외 유지
    // 삭제: "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
    // 삭제: "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration",
})
```

#### Rate Limit 정책 설계

| 대상 | Key 기준 | replenishRate | burstCapacity | 근거 |
|------|---------|---------------|---------------|------|
| 인증된 사용자 | `x-user-id` | 100/min | 150 | 정상 사용 패턴 기준 |
| 비인증 (공개 경로) | Client IP | 30/min | 50 | 크롤링/남용 방지 |
| 관리자 로그인 | Client IP | 5/min | 5 | brute-force 방지 |

#### YAML 설정

**수정 파일**: `application.yml`

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: auth-route
              uri: ${gateway.routes.auth.uri:http://localhost:8083}
              predicates:
                - Path=/api/v1/auth/**
              filters:
                - name: RequestRateLimiter
                  args:
                    redis-rate-limiter:
                      replenishRate: 10
                      burstCapacity: 20
                      requestedTokens: 1
                    key-resolver: "#{@ipKeyResolver}"
                    deny-empty-key: false  # Redis 불가용 시 요청 통과

            # ... 다른 라우트도 유사하게 RateLimiter 추가
```

#### KeyResolver 구현

**새 파일**: `api/gateway/src/main/java/.../config/RateLimiterConfig.java`

```java
@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown")
        );
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("x-user-id");
            if (userId != null) {
                return Mono.just(userId);
            }
            // 인증되지 않은 요청은 IP로 fallback
            return Mono.just(
                Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse("unknown")
            );
        };
    }
}
```

#### Graceful Degradation

Redis 불가용 시 동작:
- `deny-empty-key: false` 설정 → KeyResolver가 빈 키를 반환해도 요청 통과
- Rate Limiter 자체가 실패하면 요청 통과 (게이트웨이의 라우팅/인증 기능은 영향 없음)
- Redis 연결 실패 로그를 WARN으로 기록

#### 프로필별 Redis 설정

**수정 파일**: `application-local.yml`
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

**수정 파일**: `application-prod.yml`
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
```

---

## 3. Phase 2 — 안정성 강화

### 3.1 공개/보호 경로 외부화

#### 문제 분석

현재 `JwtAuthenticationGatewayFilter.java`의 `isPublicPath()`와 `isAdminOnlyPath()`:
```java
// 하드코딩된 경로 분기 — 새 경로 추가 시 코드 수정 + 재배포 필요
private boolean isPublicPath(String path) {
    if (path.equals("/api/v1/auth/admin/login")) return true;
    if (path.startsWith("/api/v1/auth/admin")) return false;
    return path.startsWith("/api/v1/auth") ||
           path.startsWith("/api/v1/emerging-tech") ||
           path.startsWith("/actuator");
}
```

#### 구현 설계

**새 파일**: `api/gateway/src/main/java/.../config/GatewaySecurityProperties.java`

```java
@ConfigurationProperties(prefix = "gateway.security")
public record GatewaySecurityProperties(
    List<String> publicPaths,
    List<String> publicPathExclusions,
    List<String> adminOnlyPaths
) {
    public GatewaySecurityProperties {
        publicPaths = publicPaths != null ? publicPaths : List.of();
        publicPathExclusions = publicPathExclusions != null ? publicPathExclusions : List.of();
        adminOnlyPaths = adminOnlyPaths != null ? adminOnlyPaths : List.of();
    }
}
```

**새 파일**: `api/gateway/src/main/java/.../security/PathAuthorizationResolver.java`

```java
@Component
public class PathAuthorizationResolver {

    private final List<PathPattern> publicPatterns;
    private final List<PathPattern> exclusionPatterns;
    private final List<PathPattern> adminOnlyPatterns;

    public PathAuthorizationResolver(GatewaySecurityProperties props) {
        PathPatternParser parser = new PathPatternParser();
        this.publicPatterns = props.publicPaths().stream()
            .map(parser::parse).toList();
        this.exclusionPatterns = props.publicPathExclusions().stream()
            .map(parser::parse).toList();
        this.adminOnlyPatterns = props.adminOnlyPaths().stream()
            .map(parser::parse).toList();
    }

    public boolean isPublicPath(String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        // exclusion이 매칭되면 공개 아님
        if (exclusionPatterns.stream().anyMatch(p -> p.matches(pathContainer))) {
            return false;
        }
        return publicPatterns.stream().anyMatch(p -> p.matches(pathContainer));
    }

    public boolean isAdminOnlyPath(String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        return adminOnlyPatterns.stream().anyMatch(p -> p.matches(pathContainer));
    }
}
```

**YAML 설정**: `application.yml`

```yaml
gateway:
  security:
    public-paths:
      - /api/v1/auth/admin/login
      - /api/v1/auth/**
      - /api/v1/emerging-tech/**
      - /actuator/**
    public-path-exclusions:
      - /api/v1/auth/admin/**
    admin-only-paths:
      - /api/v1/agent/**
      - /api/v1/auth/admin/**
```

**수정 파일**: `JwtAuthenticationGatewayFilter.java` — `isPublicPath()`, `isAdminOnlyPath()` → `PathAuthorizationResolver` 위임

```java
// Before
private boolean isPublicPath(String path) { /* 하드코딩 */ }

// After
private final PathAuthorizationResolver pathResolver;

// filter() 메서드에서
if (pathResolver.isPublicPath(path)) { return chain.filter(exchange); }
if (pathResolver.isAdminOnlyPath(path) && !"ADMIN".equals(payload.role())) { ... }
```

---

### 3.2 Circuit Breaker 도입

#### 구현 설계

**의존성 추가**: `build.gradle`
```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'
```

**라우트별 CircuitBreaker 설정**: `application.yml`

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: chatbot-route
              uri: ${gateway.routes.chatbot.uri:http://localhost:8084}
              predicates:
                - Path=/api/v1/chatbot/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: chatbotCB
                    fallbackUri: forward:/fallback/service-unavailable
                    statusCodes:
                      - 500
                      - 503

            - id: agent-route
              uri: ${gateway.routes.agent.uri:http://localhost:8086}
              predicates:
                - Path=/api/v1/agent/**
              filters:
                - name: CircuitBreaker
                  args:
                    name: agentCB
                    fallbackUri: forward:/fallback/service-unavailable
                    statusCodes:
                      - 500
                      - 503

resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
        registerHealthIndicator: true
    instances:
      chatbotCB:
        baseConfig: default
      agentCB:
        baseConfig: default
      authCB:
        baseConfig: default
      bookmarkCB:
        baseConfig: default
      emergingTechCB:
        baseConfig: default
  timelimiter:
    configs:
      default:
        timeoutDuration: 30s
```

**Fallback 컨트롤러**: `api/gateway/src/main/java/.../controller/FallbackController.java`

```java
@RestController
public class FallbackController {

    @RequestMapping("/fallback/service-unavailable")
    public Mono<ResponseEntity<ApiResponse<Void>>> serviceUnavailable() {
        MessageCode msgCode = new MessageCode(
            ErrorCodeConstants.MESSAGE_CODE_SERVICE_UNAVAILABLE,
            "서비스가 일시적으로 불가합니다. 잠시 후 다시 시도해주세요."
        );
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCodeConstants.SERVICE_UNAVAILABLE, msgCode
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
```

---

### 3.3 Retry 필터 (GET 전용)

**수정 파일**: `application.yml` — `default-filters`에 추가

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
                methods: GET
                statuses: SERVICE_UNAVAILABLE
                backoff:
                  firstBackoff: 100ms
                  maxBackoff: 500ms
                  factor: 2
```

- GET 요청만 재시도 (멱등하지 않은 POST/PUT/DELETE 제외)
- 503(Service Unavailable)에서만 재시도
- 지수 백오프: 100ms → 200ms, 최대 2회

---

### 3.4 SCG 내장 Metrics 활성화

**수정 파일**: `application.yml`

```yaml
spring:
  cloud:
    gateway:
      metrics:
        enabled: true

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

활성화 시 자동 수집:
- `spring.cloud.gateway.requests` 타이머 — routeId, status, httpMethod, outcome 태그
- resilience4j CircuitBreaker 메트릭 — 자동 등록
- Redis Rate Limiter 메트릭

커스텀 메트릭 (JwtAuthenticationGatewayFilter에 추가):
- `gateway.auth.failures` (Counter) — JWT 검증 실패 횟수

---

### 3.5 요청 크기 제한

**수정 파일**: `application.yml` — `default-filters`에 추가

```yaml
default-filters:
  - DedupeResponseHeader=Access-Control-Allow-Origin, RETAIN_LAST
  - name: Retry
    args: { ... }
  - name: RequestSize
    args:
      maxSize: 5MB
```

- 기본 5MB 제한
- 초과 시 413 Payload Too Large 자동 반환

---

## 4. Phase 3 — 심화 보안

### 4.1 내부 서비스 보안

#### 방식 A+B 조합 (권장)

**K8s NetworkPolicy** (인프라 레벨):
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-only-gateway
  namespace: default
spec:
  podSelector:
    matchLabels:
      tier: backend  # 모든 백엔드 서비스에 적용
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - port: 8080
          protocol: TCP
```

**Gateway Secret 헤더** (애플리케이션 레벨 보완):

`application.yml`:
```yaml
default-filters:
  - AddRequestHeader=X-Gateway-Secret,${GATEWAY_SECRET:local-dev-secret}
```

백엔드 서비스 (`common-security`에 인터셉터 추가):
```java
// 요청에 X-Gateway-Secret이 없거나 불일치하면 403 반환
```

### 4.2 관리자 API IP 화이트리스트

**새 파일**: `api/gateway/src/main/java/.../filter/AdminIpWhitelistGlobalFilter.java`

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2) // JWT 필터 이후
public class AdminIpWhitelistGlobalFilter implements GlobalFilter {

    private final GatewaySecurityProperties props;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!pathResolver.isAdminOnlyPath(path)) {
            return chain.filter(exchange);
        }

        String clientIp = extractClientIp(exchange.getRequest());
        if (!props.adminAllowedIps().contains(clientIp)) {
            log.warn("Admin IP blocked: ip={}, path={}", clientIp, path);
            return handleForbidden(exchange);
        }
        return chain.filter(exchange);
    }
}
```

YAML 설정:
```yaml
gateway:
  security:
    admin-allowed-ips:
      - 127.0.0.1        # local
      - 10.0.0.0/8        # K8s internal
```

### 4.3 Health Check 강화

**새 파일**: `api/gateway/src/main/java/.../health/BackendServicesHealthIndicator.java`

```java
@Component("backendServices")
public class BackendServicesHealthIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;
    private final GatewayRouteProperties routeProperties;

    @Override
    public Mono<Health> health() {
        // 각 백엔드 서비스의 /actuator/health를 WebClient로 확인
        // 모든 서비스 UP → Health.up()
        // 하나라도 DOWN → Health.down()
    }
}
```

**수정 파일**: `application.yml`
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

### 4.4 XForwardedHeaders 처리

**수정 파일**: `application.yml`
```yaml
server:
  forward-headers-strategy: framework
```

K8s Ingress 뒤에서 `X-Forwarded-For`, `X-Forwarded-Proto` 헤더를 올바르게 해석하여 클라이언트 IP, 프로토콜을 정확하게 추출.

---

## 5. Phase 4 — 코드 품질 개선

### 5.1 예외 핸들러 리팩토링

**문제**: `ApiGatewayExceptionHandler.determineHttpStatus()`에서 예외 클래스 이름 문자열 매칭.

```java
// Before (깨지기 쉬움)
String exceptionName = ex.getClass().getSimpleName();
if (exceptionName.contains("Timeout")) return HttpStatus.GATEWAY_TIMEOUT;

// After (타입 안전)
if (ex instanceof ReadTimeoutException || ex instanceof WriteTimeoutException) {
    return HttpStatus.GATEWAY_TIMEOUT;
} else if (ex instanceof ConnectException || ex instanceof ConnectionException) {
    return HttpStatus.BAD_GATEWAY;
}
```

### 5.2 불필요 코드/설정 정리

| 대상 | 조치 |
|------|------|
| `WebConfig.java` | 클래스 삭제 (gateway에서 정적 리소스 서빙 불필요) |
| `application-prod.yml`의 `org.hibernate.*` 로그 설정 | 제거 |
| `README.md`의 archive/contest/news 라우트 | 현행화 |
| `JwtAuthenticationGatewayFilter.java:126-134` Javadoc 순서 오류 | 수정 |
| `build.gradle` 테스트 의존성 `spring-restdocs-mockmvc` | `spring-restdocs-webtestclient`로 교체 |

### 5.3 common-security 모듈 분리 검토

현황:
- gateway가 사용하는 것: `JwtTokenProvider`, `JwtTokenPayload` (2개 클래스)
- gateway에서 비활성: `SecurityConfig`, `JwtAuthenticationFilter`, `UserPrincipal`, handler 클래스들

**검토 결과**: 현재 규모에서 모듈 분리는 과도한 설계. `ServerConfig.java`의 `@ComponentScan(basePackages = "com.tech.n.ai.common.security.jwt")`으로 필요한 클래스만 정확히 로딩하고 있으므로 실질적 문제 없음. 서비스 수가 늘어나면 재검토.

---

## 6. 파일 변경 요약

### Phase 1 (즉시 조치)

| 유형 | 파일 | 변경 내용 |
|------|------|----------|
| 새 파일 | `filter/HeaderSanitizeGlobalFilter.java` | x-user-* 헤더 제거 GlobalFilter |
| 새 파일 | `filter/AccessLogGlobalFilter.java` | 구조화된 Access Log GlobalFilter |
| 새 파일 | `filter/RequestIdGlobalFilter.java` | X-Request-Id 발급/전파 GlobalFilter |
| 새 파일 | `config/RateLimiterConfig.java` | IP/User KeyResolver 빈 |
| 수정 | `build.gradle` | Redis reactive 의존성 추가, Redis 제외 규칙 조정 |
| 수정 | `ApiGatewayApplication.java` | Redis auto-config 제외 해제 |
| 수정 | `application.yml` | default-filters, route별 RateLimiter 추가 |
| 수정 | `application-{profile}.yml` | Redis 연결 정보 추가 |
| 새 파일 | `logback-spring.xml` | ACCESS_LOG 전용 appender |

### Phase 2 (안정성 강화)

| 유형 | 파일 | 변경 내용 |
|------|------|----------|
| 새 파일 | `config/GatewaySecurityProperties.java` | 경로 설정 record |
| 새 파일 | `security/PathAuthorizationResolver.java` | PathPattern 기반 경로 매칭 |
| 새 파일 | `controller/FallbackController.java` | CircuitBreaker fallback 엔드포인트 |
| 수정 | `build.gradle` | resilience4j 의존성 추가 |
| 수정 | `JwtAuthenticationGatewayFilter.java` | `isPublicPath()`/`isAdminOnlyPath()` → PathAuthorizationResolver 위임 |
| 수정 | `application.yml` | gateway.security, resilience4j, Retry, RequestSize, metrics 설정 |

### Phase 3 (심화 보안)

| 유형 | 파일 | 변경 내용 |
|------|------|----------|
| 새 파일 | `filter/AdminIpWhitelistGlobalFilter.java` | IP 화이트리스트 GlobalFilter |
| 새 파일 | `health/BackendServicesHealthIndicator.java` | 백엔드 서비스 health 체크 |
| 새 파일 | K8s NetworkPolicy YAML | 인프라 레벨 접근 제어 |
| 수정 | `application.yml` | forward-headers-strategy, health probe 설정 |

### Phase 4 (코드 품질)

| 유형 | 파일 | 변경 내용 |
|------|------|----------|
| 수정 | `ApiGatewayExceptionHandler.java` | 문자열 매칭 → instanceof 타입 매칭 |
| 삭제 | `WebConfig.java` | 불필요한 정적 리소스 핸들러 제거 |
| 수정 | `application-prod.yml` | Hibernate 로그 설정 제거 |
| 수정 | `README.md` | 라우트 목록 현행화 |
| 수정 | `build.gradle` | restdocs-mockmvc → webtestclient |

---

## 7. 필터 순서 총괄표

Phase 1~3 완료 후 전체 필터 체인:

| Order | 필터 | 유형 | 역할 |
|-------|------|------|------|
| `HIGHEST_PRECEDENCE - 1` | HeaderSanitizeGlobalFilter | GlobalFilter | x-user-* 헤더 제거 |
| `HIGHEST_PRECEDENCE` | JwtAuthenticationGlobalFilter | GlobalFilter | JWT 검증, x-user-* 주입 |
| `HIGHEST_PRECEDENCE + 1` | RequestIdGlobalFilter | GlobalFilter | X-Request-Id 발급/전파 |
| `HIGHEST_PRECEDENCE + 2` | AdminIpWhitelistGlobalFilter | GlobalFilter | 관리자 경로 IP 검증 |
| — | Route Matching | 내부 | Path predicate 매칭 |
| — | DedupeResponseHeader | default-filter | CORS 중복 헤더 제거 |
| — | Retry | default-filter | GET 503 재시도 |
| — | RequestSize | default-filter | 요청 크기 제한 |
| — | RequestRateLimiter | route-filter | Rate Limiting |
| — | CircuitBreaker | route-filter | 장애 차단 |
| `LOWEST_PRECEDENCE` | AccessLogGlobalFilter | GlobalFilter | 구조화 Access Log |
| `@Order(-2)` | ApiGatewayExceptionHandler | WebExceptionHandler | 전역 예외 처리 |

---

## 8. 테스트 전략

### 단위 테스트

| 테스트 대상 | 검증 항목 |
|------------|----------|
| `HeaderSanitizeGlobalFilter` | 외부 x-user-* 헤더 제거 확인 |
| `RequestIdGlobalFilter` | UUID 생성, 기존 헤더 전파, 응답 헤더 포함 |
| `PathAuthorizationResolver` | 공개/보호/관리자 경로 패턴 매칭 |
| `RateLimiterConfig.KeyResolver` | IP/userId 기반 키 추출 |

### 통합 테스트 (`WebTestClient` 활용)

| 시나리오 | 검증 |
|---------|------|
| 공개 경로 + 위조 x-user-id 헤더 | 백엔드에 x-user-id 미전달 |
| 유효 JWT + 인증 경로 | x-user-* 정상 전달, X-Request-Id 응답 포함 |
| Rate Limit 초과 | 429 응답 |
| 백엔드 503 + GET | 2회 재시도 후 503 반환 |
| 백엔드 503 + POST | 재시도 없이 즉시 503 반환 |
| CircuitBreaker Open | fallback 응답 (503 + ApiResponse 형식) |

---

## 9. 구현 시 참고 자료

| 자료 | 용도 |
|------|------|
| [SCG GatewayFilter Factories](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories.html) | RemoveRequestHeader, Retry, CircuitBreaker, RequestRateLimiter, RequestSize |
| [SCG GlobalFilter 순서](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html) | GlobalFilter vs GatewayFilter 실행 순서 |
| [Resilience4j 공식 문서](https://resilience4j.readme.io/docs) | CircuitBreaker, TimeLimiter 설정 |
| [Micrometer Tracing](https://micrometer.io/docs/tracing) | OpenTelemetry 연동 |
