# common-core

모든 모듈의 바탕이 되는 공통 모듈입니다. API 응답 형식, 예외 기반 클래스, 공통 상수, 유틸리티, Jackson·Redis 설정을 제공하며, 트리 안 다른 모듈에 의존하지 않습니다.

## API 응답

**ApiResponse&lt;T&gt;** (record, `@JsonInclude(NON_NULL)`) — 모든 응답 래퍼. 필드는 `code`(성공 `2000`, 에러 `4xxx`/`5xxx`), `messageCode`(MessageCode), `message`(String), `data`(T).

```java
ApiResponse.success(data);             // code=2000, messageCode=SUCCESS
ApiResponse.error(code, messageCode);  // message, data는 null
```

- **MessageCode** (record): `code`, `text`. `MessageCode.success()` → `("SUCCESS", "성공")`.
- **PageData&lt;T&gt;** (record): `pageSize`, `pageNumber`, `totalPageNumber`, `totalSize`, `list`. `of(...)`에서 전체 페이지 수를 올림 계산.

## 예외 / 상수

- **BaseException** (`RuntimeException`) — `errorCode`, `messageCode`를 든 모든 커스텀 예외의 부모. **BusinessException**이 이를 상속(구체 예외는 `common-exception`).
- **ErrorCodeConstants** — 응답 코드와 짝이 되는 `MESSAGE_CODE_*`.

| 코드 | 의미 | 코드 | 의미 |
|------|------|------|------|
| 2000 | SUCCESS | 4006 | VALIDATION_ERROR |
| 4000 | BAD_REQUEST | 4029 | RATE_LIMIT_EXCEEDED |
| 4001 | AUTH_FAILED | 4050 | METHOD_NOT_ALLOWED |
| 4002 | AUTH_REQUIRED | 4150 | UNSUPPORTED_MEDIA_TYPE |
| 4003 | FORBIDDEN | 5000 | INTERNAL_SERVER_ERROR |
| 4004 | NOT_FOUND | 5001 | DATABASE_ERROR |
| 4005 | CONFLICT | 5003 | SERVICE_UNAVAILABLE |
| 5002 | EXTERNAL_API_ERROR | 5004 | TIMEOUT |

- **ApiConstants** — `API_BASE_PATH`(`/api/v1`), 헤더 이름(`Authorization`, `X-Request-Id`, `X-User-Id` 등), 콘텐츠 타입.

## 유틸리티

- **StringUtils** — `isEmpty`, `isBlank`, `trim`, `trimToNull`, `defaultString`.
- **DateUtils** — 포맷/파싱. 패턴 `yyyy-MM-dd`, `yyyy-MM-dd'T'HH:mm:ss`, `...SSS`(ISO).
- **ValidationUtils** — `isValidEmail`(RFC 5322 기반, TLD 2~7자), `isValidPassword`(8자 이상 + 영문·숫자·특수문자 중 2종 이상).

## 설정

- **JacksonConfig** — TSID(64비트 Long)가 JS `Number.MAX_SAFE_INTEGER`(2^53-1)를 넘어 정밀도가 깨지는 걸 막습니다. `JsonMapperBuilderCustomizer`로 `Long.class`·`Long.TYPE`를 `ToStringSerializer`에 등록해 전역에서 문자열로 직렬화합니다(Jackson 3, `tools.jackson.*`).
- **RedisConfig** — `RedisTemplate` 두 개: `redisTemplate`(`<String,String>`, String 직렬화)와 `redisTemplateForObjects`(`<String,Object>`, 값은 `GenericJackson2JsonRedisSerializer`).
- **application-common-core.yml** — Redis 접속, actuator 노출(health·metrics·prometheus), OTLP 트레이싱·메트릭 내보내기의 공통 기본값. 각 서비스가 `spring.profiles.include: common-core`로 불러옵니다.

## 의존성

Spring Boot starter(web, webflux, validation, actuator), Spring Data Redis(+reactive), Micrometer Tracing(OTel bridge)·OTLP exporter를 `api` 스코프로 전이. Prometheus 레지스트리와 macOS Netty DNS 네이티브는 runtime.

## 참고 자료

- 설계서: `docs/prototype/step2/3. api-response-format-design.md`, `docs/prototype/step2/4. error-handling-strategy-design.md`
- [Spring Boot 문서](https://docs.spring.io/spring-boot/index.html) · [Spring Data Redis](https://docs.spring.io/spring-data/redis/reference/) · [Jakarta Bean Validation](https://beanvalidation.org/)
