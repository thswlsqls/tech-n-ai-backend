# common-exception

전역 예외 처리와 예외 로깅 모듈입니다. `common-core`의 `BaseException`을 확장한 구체 예외들을 제공하고, `GlobalExceptionHandler`가 모든 예외를 `ApiResponse` 형식으로 변환하며, 예외를 MongoDB에 비동기로 기록합니다.

## 전역 예외 처리

**GlobalExceptionHandler** (`@RestControllerAdvice`, Servlet 기반) — 예외를 HTTP 상태·에러 코드로 매핑합니다.

| 예외 | HTTP | 에러 코드 |
|------|------|----------|
| ResourceNotFoundException | 404 | 4004 |
| UnauthorizedException | 401 | 4001 |
| ForbiddenException | 403 | 4003 |
| ConflictException | 400 | 4006 |
| RateLimitExceededException | 429 | 4029 |
| ExternalApiException | 503 | 5003 |
| MethodArgumentNotValid / HandlerMethodValidation / MethodArgumentTypeMismatch / MissingServletRequestParameter / DataIntegrityViolation | 400 | 4006 |
| HttpMessageNotReadable / MissingRequestHeader | 400 | 4000 |
| NoResourceFound | 404 | 4004 |
| HttpRequestMethodNotSupported | 405 | 4050 |
| HttpMediaTypeNotSupported | 415 | 4150 |
| Exception (그 외 전부) | 500 | 5000 |

- `BaseException` 계열은 예외가 든 `errorCode`로 상태를 정합니다.
- 4006 응답은 필드별 메시지를 `Map<String,String>`으로 돌려줍니다. `ConflictException`은 예외 자체는 4005를 들고 있지만, 전용 핸들러가 검증 오류와 같은 형식(400·4006, `fieldName` 키의 필드 맵)으로 응답합니다.
- 검증·서버 예외는 `ExceptionLoggingService`로 기록하고, 405/415 같은 단순 요청 오류는 경고 로그만 남깁니다.

## 예외 클래스

모두 `BaseException`을 상속하며 `(message)`·`(message, cause)` 생성자를 가집니다.

| 클래스 | errorCode | 비고 |
|--------|-----------|------|
| ResourceNotFoundException | 4004 | |
| UnauthorizedException | 4001 | |
| ForbiddenException | 4003 | |
| ConflictException | 4005 | `fieldName` 보유(기본 `"field"`) |
| RateLimitExceededException | 4029 | |
| ExternalApiException | 5003 | |

## 예외 로깅

**ExceptionLoggingService** — `@Async("exceptionLoggingExecutor")`로 예외를 MongoDB `exception_logs`에 저장. `logReadException`(source=READ)·`logWriteException`(source=WRITE) 두 메서드. `MongoTemplate`은 선택 주입(Optional)이라 없거나 저장 실패 시 로컬 로그로 폴백합니다. severity는 `IllegalArgument/IllegalStateException`=MEDIUM, 그 밖 `RuntimeException`=HIGH.

**ExceptionContext** (record) — `source`, `exceptionType`, `exceptionMessage`, `stackTrace`, `occurredAt`, `severity`와 중첩 `ContextInfo`(`module`, `method`, `parameters`, `requestUri`, `userId`, `requestId`). 핸들러가 `module`=URI 첫 세그먼트, `userId`=`X-User-Id`, `requestId`=`X-Request-Id`로 채웁니다. `ExternalApiException`과 `DataIntegrityViolation`은 WRITE, 나머지는 READ.

**AsyncConfig** (`@EnableAsync`) — `exceptionLoggingExecutor`: core=2, max=5, queue=100, 접두사 `exception-log-`. 예외 로깅은 fire-and-forget이라 작은 풀로 요청 스레드와의 경쟁을 줄입니다.

## 의존성

`common-core`, `datasource-mongodb`.

## 참고 자료

- 설계서: `docs/prototype/step2/4. error-handling-strategy-design.md`
- [Spring Boot 문서](https://docs.spring.io/spring-boot/index.html) · [Spring @Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
