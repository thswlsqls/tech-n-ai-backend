# API Auth Module

## 개요

`api/auth` 모듈은 사용자 인증·인가를 담당하는 REST API 서버입니다. Spring Security 7.x 기반의 JWT 토큰 인증을 사용하며, 서버 측 세션을 두지 않는 Stateless 구조입니다. Google/Naver/Kakao OAuth 2.0 소셜 로그인과 관리자 전용 인증(로그인 잠금, 감사 추적)을 함께 제공합니다.

주요 기술: jjwt(토큰 생성·검증), BCryptPasswordEncoder(strength 12), OpenFeign(`client-feign`, OAuth Provider API 호출), `client-mail`(인증/재설정 메일 발송), Redis(OAuth State 저장).

## 아키텍처

```
AuthController / AdminController (Presentation)
    ↓
AuthFacade / AdminFacade (Facade)
    ↓
AuthService / AdminService / TokenService (Business Logic)
    ↓
Repository / External Services (Data Access)
```

- **common/security**: `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`(토큰 생성/검증/파싱), `PasswordEncoderConfig`
- **api/auth**: `AuthService`(사용자 인증), `AdminService`(관리자 인증 + 로그인 잠금/감사 추적), `TokenService`(역할 기반 토큰 발급), `RefreshTokenService`(user/admin 분리 저장), `OAuthProvider`/`OAuthStateService`(OAuth + State 관리)

## 주요 기능

- **회원가입**: 이메일·사용자명 중복 검증, BCrypt 해시 저장, 이메일 인증 토큰 생성 (24시간 유효)
- **로그인**: Soft Delete·이메일 인증 완료 여부(`isActive`) 확인, 비밀번호 검증, Access/Refresh Token 발급 및 Refresh Token DB 저장, 마지막 로그인 시간 갱신
- **로그아웃**: Refresh Token을 조회·검증한 뒤 Soft Delete하여 무효화
- **토큰 갱신**: Refresh Token을 JWT·DB 양쪽으로 검증하고, 기존 토큰을 Soft Delete한 뒤 새 토큰 발급 (Rotate-on-use)
- **이메일 인증**: 만료(24시간)·중복 인증 확인 후 사용자 인증 상태 갱신
- **비밀번호 재설정**: 재설정 토큰을 메일로 발송, 검증 후 변경. 이전 비밀번호와 동일하면 거부
- **회원 탈퇴** (`DELETE /api/v1/auth/me`): 비밀번호 확인(선택 — OAuth 사용자는 비밀번호가 없어 생략), Refresh Token 전체 삭제로 남은 세션 무효화, email·username 등 unique 컬럼을 `deleted_<timestamp>_` 접두어로 익명화(재가입 시 충돌 방지), User 엔티티 Soft Delete(`deletedBy`에 본인 ID 기록). 요청 본문 `WithdrawRequest`는 선택값이며 `password`, `reason` 필드를 가집니다.

## API 엔드포인트

### 사용자 API

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

### 관리자 API

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

로그인과 토큰 갱신은 동일한 `TokenResponse`(accessToken, refreshToken, tokenType, expiresIn, refreshTokenExpiresIn)를 반환합니다. Long 필드는 전역 Jackson 설정으로 문자열로 직렬화됩니다.

## 인증/인가 플로우

JWT 인증은 `Authorization` 헤더의 `Bearer <token>`을 추출해 `JwtTokenProvider.validateToken()`으로 검증하고, 페이로드(userId·email·role)를 꺼내 `SecurityContext`에 인증 정보를 저장하는 순서로 처리합니다.

### 토큰 구성

| 토큰 | 사용자 (USER) | 관리자 (ADMIN) |
|------|-------------|---------------|
| Access Token | 60분 | 15분 |
| Refresh Token | 7일 | 1일 |

- **페이로드**: userId, email, role
- **Refresh Token 저장**: DB에 저장하며 user_id/admin_id FK를 분리
- **갱신 전략**: Rotate-on-use (갱신 시 기존 토큰 삭제 후 새 토큰 발급)
- **관리자 토큰 보안**: 전용 갱신 엔드포인트(`POST /api/v1/auth/admin/refresh`), 사용자 토큰으로 갱신 시도 시 401, 갱신 시 관리자 활성 상태(isActive/isDeleted) 재검증

## OAuth 로그인

Authorization Code Flow:

1. 로그인 시작 → State 생성·Redis 저장(CSRF 방지)
2. OAuth Provider 인증 페이지로 리다이렉트, 사용자 로그인·동의
3. 콜백으로 Authorization Code 수신 → Redis에서 State 검증·삭제
4. Code를 Access Token으로 교환 → 사용자 정보 조회
5. 사용자 조회/생성 후 JWT 토큰 발급

State 파라미터(CSRF 방지, RFC 6749 §10.12): Redis에 Key `oauth:state:{state_value}`, Value는 Provider 이름(GOOGLE/NAVER/KAKAO)으로 저장. TTL 10분(600초), 콜백 시 검증 후 즉시 삭제(일회성). State 값은 `SecureTokenGenerator`로 32바이트 난수 생성.

Provider 구현은 인터페이스 `OAuthProvider` + Google/Naver/Kakao 구현체이며, `client/feign`의 Contract 패턴 FeignClient로 Provider API를 호출합니다.

## 보안

- **로그인 잠금(관리자)**: 5회 실패 시 15분, 10회 실패 시 1시간 잠금. 잠금 상태는 Aurora MySQL에 저장되어 재시작 후에도 유지되고, 로그인 성공 시 카운터·잠금이 초기화됩니다.
- **감사 추적(관리자)**: `createdBy/updatedBy/deletedBy`로 작업자를 기록하고, Soft Delete 시 모든 Refresh Token을 무효화하며, 자기 계정 삭제를 금지합니다.
- **토큰 소유권 검증**: 로그아웃·갱신 시 Refresh Token의 `admin_id`/`user_id` FK와 인증된 ID를 교차 검증합니다.
- **비밀번호**: BCrypt(strength 12)로 해시 저장, 재설정 시 이전 값 재사용 차단
- **JWT Secret**: 환경 변수로 주입(최소 256비트), 코드에 하드코딩 금지
- **에러 메시지**: 로그인 실패는 "이메일 또는 비밀번호가 올바르지 않습니다."처럼 원인을 특정하지 않음

## 참고 자료

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
