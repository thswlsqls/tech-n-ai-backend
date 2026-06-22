# API Auth Module

## 목차

1. [개요](#1-개요)
2. [아키텍처](#2-아키텍처)
3. [주요 기능](#3-주요-기능)
4. [API 엔드포인트](#4-api-엔드포인트)
5. [인증/인가 플로우](#5-인증인가-플로우)
6. [OAuth 로그인](#6-oauth-로그인)
7. [보안](#7-보안)
8. [참고 자료](#8-참고-자료)

---

## 1. 개요

### 1.1 모듈 소개

`api/auth` 모듈은 사용자 인증·인가를 담당하는 REST API 서버입니다. Spring Security 7.x 기반의 JWT 토큰 인증을 사용하며, 서버 측 세션을 두지 않는 Stateless 구조입니다.

### 1.2 주요 특징

- **JWT 토큰 기반 인증**: Stateless 방식으로 서버 확장에 유리
- **관리자/사용자 토큰 분리**: 역할별 유효기간 분리 (Admin 15분/1일, User 60분/7일)
- **관리자 인증 보안**: 로그인 잠금(Brute Force Protection), 감사 추적(Audit Trail), Soft Delete
- **OAuth 2.0**: Google, Naver, Kakao 소셜 로그인
- **이메일 인증 / 비밀번호 재설정**: 토큰 기반(24시간 유효), 이메일 발송
- **Refresh Token 관리**: Rotate-on-use (갱신 시 기존 토큰 폐기)
- **회원 탈퇴**: Refresh Token 전체 삭제 + unique 컬럼 익명화 후 Soft Delete

### 1.3 기술 스택

- **Spring Boot 4.0.2** / **Spring Security 7.x** (Boot 4.0.2가 끌어오는 버전)
- **JWT**: jjwt로 토큰 생성·검증
- **BCryptPasswordEncoder**: 비밀번호 해시 (strength 12)
- **OpenFeign**: OAuth Provider API 호출 (`client-feign`)
- **client-mail**: 인증/비밀번호 재설정 메일 발송
- **Redis**: OAuth State 저장

---

## 2. 아키텍처

### 2.1 계층 구조

![Component Dependency Relationship](../../contents/api-auth/component-dependency-relationship.png)

```
AuthController / AdminController (Presentation)
    ↓
AuthFacade / AdminFacade (Facade)
    ↓
AuthService / AdminService / TokenService (Business Logic)
    ↓
Repository / External Services (Data Access)
```

### 2.2 주요 컴포넌트

- **common/security**: `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`(토큰 생성/검증/파싱), `PasswordEncoderConfig`
- **api/auth**: `AuthService`(사용자 인증), `AdminService`(관리자 인증 + 로그인 잠금/감사 추적), `TokenService`(역할 기반 토큰 발급), `RefreshTokenService`(user/admin 분리 저장), `OAuthProvider`/`OAuthStateService`(OAuth + State 관리)

---

## 3. 주요 기능

### 3.1 회원가입

이메일·사용자명·비밀번호로 계정을 만듭니다.

- 이메일·사용자명 중복 검증
- BCrypt 비밀번호 해시 저장
- 이메일 인증 토큰 생성·저장 (24시간 유효)

![Signup Flow](../../contents/api-auth/signup-flow.png)

### 3.2 로그인

이메일·비밀번호로 로그인합니다.

- Soft Delete·이메일 인증 완료 여부 확인 (`isActive`)
- BCrypt 비밀번호 검증
- Access/Refresh Token 발급 및 Refresh Token DB 저장
- 마지막 로그인 시간 갱신

![Login Flow](../../contents/api-auth/login-flow.png)

### 3.3 로그아웃

Refresh Token을 조회·검증한 뒤 Soft Delete하여 무효화합니다.

![Logout Flow](../../contents/api-auth/logout-flow.png)

### 3.4 토큰 갱신

Refresh Token을 JWT·DB 양쪽으로 검증하고, 기존 토큰을 Soft Delete한 뒤 새 Access/Refresh Token을 발급·저장합니다 (Rotate-on-use).

![Token Refresh Flow](../../contents/api-auth/token-refresh-flow.png)

### 3.5 이메일 인증

회원가입 시 발급한 토큰을 검증합니다. 만료(24시간)·중복 인증을 확인하고 사용자 인증 상태를 갱신합니다.

### 3.6 비밀번호 재설정

재설정 토큰을 메일로 보내고, 토큰 검증 후 비밀번호를 변경합니다. 이전 비밀번호와 동일하면 거부합니다.

![Password Reset Request Flow](../../contents/api-auth/password-reset-request-flow.png)

### 3.7 OAuth 로그인

Google/Naver/Kakao 소셜 로그인을 지원합니다. 자세한 플로우는 [6. OAuth 로그인](#6-oauth-로그인)을 참고하세요.

![OAuth Login Start](../../contents/api-auth/oauth-login-start.png)

![OAuth Login Callback Flow](../../contents/api-auth/oauth-login-callback-flow.png)

### 3.8 회원 탈퇴 (`DELETE /api/v1/auth/me`)

인증된 사용자가 자신의 계정을 탈퇴합니다.

- 이미 탈퇴한 계정인지 확인
- 비밀번호 확인(선택): 요청에 비밀번호가 있으면 검증, OAuth 사용자는 비밀번호가 없어 생략
- Refresh Token 전체 삭제로 남은 세션 무효화
- email·username 등 unique 컬럼을 `deleted_<timestamp>_` 접두어로 익명화(재가입 시 충돌 방지)
- User 엔티티 Soft Delete (`deletedBy`에 본인 ID 기록)

요청 본문 `WithdrawRequest`는 선택값이며 `password`(재확인), `reason`(사유) 필드를 가집니다.

---

## 4. API 엔드포인트

![Authentication API Endpoint Structure](../../contents/api-auth/authentication-api-endpoint-structure.png)

### 4.1 사용자 API

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|----------|
| POST | `/api/v1/auth/signup` | 회원가입 | ❌ |
| POST | `/api/v1/auth/login` | 로그인 | ❌ |
| POST | `/api/v1/auth/logout` | 로그아웃 | ✅ |
| DELETE | `/api/v1/auth/me` | 회원 탈퇴 | ✅ |
| POST | `/api/v1/auth/refresh` | 토큰 갱신 | ❌ |
| GET | `/api/v1/auth/verify-email` | 이메일 인증 | ❌ |
| POST | `/api/v1/auth/reset-password` | 비밀번호 재설정 요청 | ❌ |
| POST | `/api/v1/auth/reset-password/confirm` | 비밀번호 재설정 확인 | ❌ |
| GET | `/api/v1/auth/oauth2/{provider}` | OAuth 로그인 시작 | ❌ |
| GET | `/api/v1/auth/oauth2/{provider}/callback` | OAuth 로그인 콜백 | ❌ |

지원 OAuth Provider: `google`, `naver`, `kakao`

### 4.2 관리자 API

| Method | Endpoint | 설명 | 인증 필요 |
|--------|----------|------|----------|
| POST | `/api/v1/auth/admin/login` | 관리자 로그인 | ❌ |
| POST | `/api/v1/auth/admin/logout` | 관리자 로그아웃 | ✅ |
| POST | `/api/v1/auth/admin/refresh` | 관리자 토큰 갱신 | ❌ |
| POST | `/api/v1/auth/admin/accounts` | 관리자 계정 생성 | ✅ |
| GET | `/api/v1/auth/admin/accounts` | 관리자 목록 조회 | ✅ |
| GET | `/api/v1/auth/admin/accounts/{adminId}` | 관리자 단건 조회 | ✅ |
| PUT | `/api/v1/auth/admin/accounts/{adminId}` | 관리자 정보 수정 | ✅ |
| DELETE | `/api/v1/auth/admin/accounts/{adminId}` | 관리자 계정 삭제 | ✅ |

### 4.3 요청/응답 예시

#### 회원가입

```json
POST /api/v1/auth/signup
{ "email": "user@example.com", "username": "user", "password": "password123" }
```

```json
{
  "code": "2000",
  "message": "success",
  "data": {
    "userId": "1",
    "email": "user@example.com",
    "username": "user",
    "message": "회원가입이 완료되었습니다. 이메일 인증을 완료해주세요."
  }
}
```

#### 로그인 / 토큰 갱신

`POST /api/v1/auth/login`과 `POST /api/v1/auth/refresh`는 동일한 `TokenResponse`를 반환합니다. Long 필드는 전역 Jackson 설정으로 문자열로 직렬화됩니다.

```json
{
  "code": "2000",
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": "3600",
    "refreshTokenExpiresIn": "604800"
  }
}
```

---

## 5. 인증/인가 플로우

![Authentication Authorization Flow](../../contents/api-auth/authentication-authorization-flow.png)

### 5.1 JWT 인증 메커니즘

1. **토큰 추출**: `Authorization` 헤더에서 `Bearer <token>` 추출
2. **토큰 검증**: `JwtTokenProvider.validateToken()`으로 유효성 검증
3. **페이로드 추출**: userId·email·role 추출
4. **SecurityContext 설정**: 인증 정보를 `SecurityContext`에 저장
5. **요청 처리**: 인증된 요청으로 처리

### 5.2 토큰 구성

| 토큰 | 사용자 (USER) | 관리자 (ADMIN) |
|------|-------------|---------------|
| Access Token | 60분 | 15분 |
| Refresh Token | 7일 | 1일 |

- **Access / Refresh Token 페이로드**: userId, email, role
- **Refresh Token 저장**: DB에 저장하며 user_id/admin_id FK를 분리
- **갱신 전략**: Rotate-on-use (갱신 시 기존 토큰 삭제 후 새 토큰 발급)
- **관리자 토큰 보안**: 전용 갱신 엔드포인트(`POST /api/v1/auth/admin/refresh`), 사용자 토큰으로 갱신 시도 시 401, 갱신 시 관리자 활성 상태(isActive/isDeleted) 재검증

---

## 6. OAuth 로그인

### 6.1 Authorization Code Flow

1. 로그인 시작 → State 생성·Redis 저장(CSRF 방지)
2. OAuth Provider 인증 페이지로 리다이렉트, 사용자 로그인·동의
3. 콜백으로 Authorization Code 수신 → Redis에서 State 검증·삭제
4. Code를 Access Token으로 교환 → 사용자 정보 조회
5. 사용자 조회/생성 후 JWT 토큰 발급

### 6.2 State 파라미터 (CSRF 방지, RFC 6749 §10.12)

- **저장소**: Redis, **Key**: `oauth:state:{state_value}`, **Value**: Provider 이름(GOOGLE/NAVER/KAKAO)
- **TTL**: 10분(600초), 콜백 시 검증 후 즉시 삭제(일회성)
- State 값은 `SecureTokenGenerator`로 32바이트 난수 생성

### 6.3 Provider 구현

- 인터페이스 `OAuthProvider` + `Google/Naver/Kakao OAuthProvider` 구현
- `client/feign`의 Contract 패턴 FeignClient로 Provider API 호출

---

## 7. 보안

대부분 코드로 강제되는 항목입니다.

### 7.1 관리자 인증 보안

- **로그인 잠금**: 5회 실패 시 15분, 10회 실패 시 1시간 잠금. 잠금 상태는 Aurora MySQL에 저장되어 재시작 후에도 유지되고, 로그인 성공 시 카운터·잠금이 초기화됩니다.
- **감사 추적**: `createdBy/updatedBy/deletedBy`로 작업자를 기록하고, Soft Delete 시 모든 Refresh Token을 무효화하며, 자기 계정 삭제를 금지합니다.
- **토큰 소유권 검증**: 로그아웃·갱신 시 Refresh Token의 `admin_id`/`user_id` FK와 인증된 ID를 교차 검증합니다.

### 7.2 그 외

- **비밀번호**: BCrypt(strength 12)로 해시 저장, 재설정 시 이전 값 재사용 차단
- **JWT Secret**: 환경 변수로 주입(최소 256비트), 코드에 하드코딩 금지
- **에러 메시지**: 로그인 실패는 "이메일 또는 비밀번호가 올바르지 않습니다."처럼 원인을 특정하지 않음

---

## 8. 참고 자료

**프로젝트 설계 문서**
- `docs/prototype/step6/spring-security-auth-design-guide.md` — Spring Security 설계 가이드
- `docs/prototype/step6/oauth-provider-implementation-guide.md` — OAuth Provider 구현 가이드
- `docs/reference/design/002-admin-role-based-auth.md` — 관리자 인증 보안 강화 설계서

**코드 참조**
- `common/security/.../jwt/JwtTokenProvider.java`, `config/SecurityConfig.java`, `filter/JwtAuthenticationFilter.java`
- `api/auth/.../controller/AuthController.java`, `service/AuthService.java`, `oauth/OAuthProvider.java`

**외부 표준·문서**
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/) · [jjwt](https://github.com/jwtk/jjwt)
- [RFC 7519 (JWT)](https://tools.ietf.org/html/rfc7519) · [RFC 6749 (OAuth 2.0)](https://tools.ietf.org/html/rfc6749)
- OAuth Provider: [Google](https://developers.google.com/identity/protocols/oauth2/web-server) · [Naver](https://developers.naver.com/docs/login/api/) · [Kakao](https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api)

---

**작성일**: 2026-06-14 · **버전**: 1.3 · **모듈**: api/auth
