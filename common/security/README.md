# common-security

JWT 기반 인증·인가 모듈입니다. 토큰 생성·검증, 인증 필터, Spring Security 설정, 비밀번호 인코더를 제공합니다. Servlet 기반(`OncePerRequestFilter`)이며, WebFlux로 동작하는 `api-gateway`는 이 모듈을 쓰지 않습니다.

## JWT

**JwtTokenPayload** (record) — 토큰에 담는 정보: `userId`(JWT `sub`), `email`, `role`(`USER`/`ADMIN`).

**JwtTokenProvider** — jjwt(`io.jsonwebtoken`)로 HMAC-SHA 서명 토큰을 다룹니다. 토큰에는 `sub`(userId)·`email`·`role` 클레임과 `iat`/`exp`가 들어가며, 일반 사용자와 관리자의 유효기간을 따로 둡니다.

```java
String generateAccessToken(JwtTokenPayload p);        // generateAdminAccessToken 별도 존재
String generateRefreshToken(JwtTokenPayload p);       // generateAdminRefreshToken 별도 존재
JwtTokenPayload getPayloadFromToken(String token);
boolean validateToken(String token);
LocalDateTime getRefreshTokenExpiresAt();             // getAdminRefreshTokenExpiresAt 별도 존재
```

설정(`@Value`): `jwt.secret-key`, `jwt.access-token-validity-minutes`(기본 60), `jwt.refresh-token-validity-days`(7), `jwt.admin.access-token-validity-minutes`(15), `jwt.admin.refresh-token-validity-days`(1).

## 인증 흐름

**JwtAuthenticationFilter** (`OncePerRequestFilter`)

1. `Authorization` 헤더에서 `Bearer ` 접두사를 떼어 토큰을 꺼냅니다. 없으면 다음 필터로 넘깁니다.
2. 토큰을 파싱해 `UserPrincipal`을 만들고, 권한을 `SimpleGrantedAuthority("ROLE_" + role)`로 설정한 뒤 `SecurityContext`에 넣습니다.
3. 검증·파싱 실패 시 401(`AUTH_FAILED`) JSON을 직접 씁니다.

`SecurityConfig`에서 이 필터를 `UsernamePasswordAuthenticationFilter` 앞에 둡니다.

**UserPrincipal** (record, `Principal`/`Serializable`) — `userId`(Long), `email`, `role`. `getName()`은 userId 반환. 컨트롤러에서 `@AuthenticationPrincipal`로 받습니다.

## Spring Security 설정

**SecurityConfig** — CSRF 끔, CORS 켬, 세션 `STATELESS`. 인가 규칙(위에서부터 우선):

```
/api/v1/auth/me            → authenticated
/api/v1/auth/admin/login   → permitAll
/api/v1/auth/admin/refresh → permitAll
/api/v1/auth/admin/**      → hasRole("ADMIN")
/api/v1/auth/**            → permitAll
/actuator/**               → permitAll
그 외                       → authenticated
```

401은 `SecurityAuthenticationEntryPoint`, 403은 `SecurityAccessDeniedHandler`가 처리하며 둘 다 `ApiResponse.error` JSON으로 응답합니다. CORS는 현재 개발 설정(모든 origin 허용, `allowCredentials=false`)이며 운영에서는 도메인을 좁혀야 합니다(코드 TODO).

**PasswordEncoderConfig** — `BCryptPasswordEncoder` 강도 12.

## 에러 응답

**SecurityErrorResponseWriter**가 필터·핸들러 단계 에러를 `ApiResponse.error` JSON(UTF-8)으로 씁니다: 인증 필요 401(`AUTH_REQUIRED`), 인증 실패 401(`AUTH_FAILED`), 권한 없음 403(`FORBIDDEN`).

## 의존성

`common-core`, Spring Security + OAuth2(authorization-server·client·resource-server) 스타터(`api` 스코프), jjwt 0.12.5(`jjwt-api`, 런타임 `jjwt-impl`·`jjwt-jackson`).

## 참고 자료

- 설계서: `docs/prototype/step6/spring-security-auth-design-guide.md`, `docs/reference/design/002-admin-role-based-auth.md`
- [Spring Security 문서](https://docs.spring.io/spring-security/reference/) · [jjwt](https://github.com/jwtk/jjwt) · [JWT RFC 7519](https://datatracker.ietf.org/doc/html/rfc7519)
