# Auth API 설계서

**작성일**: 2026-02-06
**대상 모듈**: api-auth
**버전**: v2

---

## 1. 개요

| 항목 | 내용 |
|------|------|
| 모듈 | api-auth |
| Base URL | `/api/v1/auth` |
| 포트 | 8083 (via Gateway: 8081) |
| 설명 | 사용자 인증 및 OAuth 로그인, 관리자 인증 및 계정 관리 API |

### 인증 방식

| API 유형 | 인증 방식 |
|---------|----------|
| 공개 API | 불필요 |
| 사용자 API | Bearer Token (JWT) |
| 관리자 API | Bearer Token (ADMIN) |

### 토큰 만료 시간

| 토큰 유형 | 사용자 | 관리자 |
|----------|--------|--------|
| Access Token | 3600초 (1시간) | 900초 (15분) |
| Refresh Token | 604800초 (7일) | 86400초 (1일) |

---

## 2. 공통 응답 형식

### ApiResponse<T>

```json
{
  "code": "2000",
  "messageCode": {
    "code": "SUCCESS",
    "text": "성공"
  },
  "message": "success",
  "data": {...}
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| code | String | O | 응답 코드 |
| messageCode | MessageCode | O | 메시지 코드 객체 |
| message | String | X | 응답 메시지 |
| data | T | X | 응답 데이터 |

---

## 3. 사용자 인증 API

### 3.1 회원가입

**POST** `/api/v1/auth/signup`

**인증**: 불필요

새로운 사용자 계정을 생성합니다. 이메일 인증이 필요합니다.

**Request Body**

```json
{
  "email": "user@example.com",
  "username": "사용자명",
  "password": "Password123!"
}
```

**SignupRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| email | String | O | 이메일 형식 | 사용자 이메일 |
| username | String | O | 3~50자 | 사용자명 |
| password | String | O | 최소 8자, 대소문자/숫자/특수문자 중 2가지 이상 | 비밀번호 |

**Response** (200 OK) `ApiResponse<AuthResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "userId": 12345,
    "email": "user@example.com",
    "username": "사용자명",
    "message": "이메일 인증 링크가 발송되었습니다."
  }
}
```

**AuthResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | Long | O | 사용자 ID |
| email | String | O | 이메일 |
| username | String | O | 사용자명 |
| message | String | O | 안내 메시지 |

**Errors**
- `400` - 비밀번호 정책 위반, 유효성 검증 실패
- `409` - 이메일 중복, 사용자명 중복

---

### 3.2 로그인

**POST** `/api/v1/auth/login`

**인증**: 불필요

이메일/비밀번호로 로그인합니다.

**Request Body**

```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

**LoginRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| email | String | O | 이메일 형식 | 사용자 이메일 |
| password | String | O | - | 비밀번호 |

**Response** (200 OK) `ApiResponse<TokenResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "refreshTokenExpiresIn": 604800
  }
}
```

**TokenResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| accessToken | String | O | JWT 액세스 토큰 |
| refreshToken | String | O | 리프레시 토큰 |
| tokenType | String | O | 토큰 타입 (항상 "Bearer") |
| expiresIn | Long | O | 액세스 토큰 만료 시간 (초) |
| refreshTokenExpiresIn | Long | O | 리프레시 토큰 만료 시간 (초) |

**Errors**
- `401` - 이메일 또는 비밀번호 불일치, 이메일 미인증

---

### 3.3 로그아웃

**POST** `/api/v1/auth/logout`

**인증**: 필요

현재 세션을 로그아웃합니다. 리프레시 토큰이 무효화됩니다.

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` |

**Request Body**

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

**LogoutRequest 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| refreshToken | String | O | 리프레시 토큰 |

**Response** (200 OK) `ApiResponse<Void>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

**Errors**
- `401` - 인증 실패, Refresh Token 불일치

---

### 3.4 회원탈퇴

**DELETE** `/api/v1/auth/me`

**인증**: 필요

현재 사용자 계정을 탈퇴합니다. Soft delete 처리되며, 동일 이메일로 재가입이 가능하도록 이메일과 사용자명이 익명화됩니다.

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` |

**Request Body** (선택)

```json
{
  "password": "Password123!",
  "reason": "서비스를 더 이상 사용하지 않습니다."
}
```

**WithdrawRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| password | String | X | 8~100자 | 비밀번호 확인 (보안 강화용) |
| reason | String | X | 최대 500자 | 탈퇴 사유 |

**Response** (200 OK) `ApiResponse<Void>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

**Errors**
- `401` - 인증 실패
- `404` - 사용자 없음
- `409` - 이미 탈퇴한 사용자

---

### 3.5 토큰 갱신

**POST** `/api/v1/auth/refresh`

**인증**: 불필요

리프레시 토큰으로 새 액세스 토큰을 발급받습니다.

**Request Body**

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

**RefreshTokenRequest 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| refreshToken | String | O | 리프레시 토큰 |

**Response** (200 OK) `ApiResponse<TokenResponse>`

TokenResponse 형식 (로그인 응답과 동일)

**Errors**
- `401` - Refresh Token 만료 또는 무효

---

### 3.6 이메일 인증

**GET** `/api/v1/auth/verify-email`

**인증**: 불필요

이메일 인증 토큰을 검증합니다.

> **이메일 링크**: 회원가입 시 발송되는 인증 이메일의 링크는 프론트엔드 경로(`{MAIL_BASE_URL}/verify-email?token=xxx`)로 연결됩니다. 프론트엔드에서 토큰을 추출하여 이 API를 호출해야 합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| token | String | O | 이메일 인증 토큰 |

**Response** (200 OK) `ApiResponse<Void>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

**Errors**
- `400` - 토큰 만료, 토큰 무효, 중복 인증

---

### 3.7 비밀번호 재설정 요청

**POST** `/api/v1/auth/reset-password`

**인증**: 불필요

비밀번호 재설정 이메일을 발송합니다. 이메일의 링크는 프론트엔드 경로(`{MAIL_BASE_URL}/reset-password?token=xxx`)로 연결됩니다. 프론트엔드에서 새 비밀번호 입력 폼을 렌더링한 후, `POST /api/v1/auth/reset-password/confirm` API를 호출해야 합니다.

**Request Body**

```json
{
  "email": "user@example.com"
}
```

**ResetPasswordRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| email | String | O | 이메일 형식 | 사용자 이메일 |

**Response** (200 OK) `ApiResponse<Void>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

> 보안상 존재하지 않는 이메일도 동일한 성공 응답을 반환합니다.

---

### 3.8 비밀번호 재설정 확인

**POST** `/api/v1/auth/reset-password/confirm`

**인증**: 불필요

토큰으로 비밀번호를 재설정합니다.

**Request Body**

```json
{
  "token": "reset-password-token-string",
  "newPassword": "NewPassword123!"
}
```

**ResetPasswordConfirmRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| token | String | O | - | 비밀번호 재설정 토큰 |
| newPassword | String | O | 최소 8자, 대소문자/숫자/특수문자 중 2가지 이상 | 새 비밀번호 |

**Response** (200 OK) `ApiResponse<Void>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

**Errors**
- `400` - 토큰 만료, 토큰 무효, 비밀번호 정책 위반, 이전 비밀번호와 동일

---

## 4. OAuth 인증 API

### 4.1 OAuth 로그인 시작

**GET** `/api/v1/auth/oauth2/{provider}`

**인증**: 불필요

OAuth 인증 페이지로 리다이렉트합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| provider | String | O | OAuth 제공자 (`google`, `kakao`, `naver`) |

**Response** (302 Redirect)

OAuth 제공자 인증 페이지로 리다이렉트합니다.

**Errors**
- `400` - 지원하지 않는 OAuth 제공자

---

### 4.2 OAuth 콜백

**GET** `/api/v1/auth/oauth2/{provider}/callback`

**인증**: 불필요

OAuth 인증 완료 후 토큰을 발급합니다.

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| provider | String | O | OAuth 제공자 (`google`, `kakao`, `naver`) |

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| code | String | O | OAuth 인증 코드 |
| state | String | X | CSRF 방지용 state 값 |

**Response** (200 OK) `ApiResponse<TokenResponse>`

TokenResponse 형식 (로그인 응답과 동일)

**Errors**
- `401` - State 토큰 불일치, OAuth 인증 실패

---

## 5. 관리자 API

### 5.1 관리자 로그인

**POST** `/api/v1/auth/admin/login`

**인증**: 불필요

관리자 계정으로 로그인합니다. 로그인 실패 시 계정 잠금 정책이 적용됩니다.

**Request Body**

LoginRequest 형식 (일반 로그인과 동일)

**Response** (200 OK) `ApiResponse<TokenResponse>`

TokenResponse 형식 (관리자 토큰: Access 900초, Refresh 86400초)

**계정 잠금 정책**

| 연속 실패 횟수 | 잠금 시간 |
|--------------|----------|
| 5회 이상 | 15분 |
| 10회 이상 | 1시간 |

> 로그인 성공 시 실패 횟수가 초기화됩니다.

**Errors**
- `401` - 이메일 또는 비밀번호 불일치, 비활성화 또는 삭제된 관리자 계정, 계정 잠금 상태

---

### 5.2 관리자 로그아웃

**POST** `/api/v1/auth/admin/logout`

**인증**: 필요 (ADMIN)

관리자 세션을 로그아웃합니다. 해당 리프레시 토큰이 무효화됩니다.

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` (ADMIN) |

**Request Body**

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

**LogoutRequest 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| refreshToken | String | O | 리프레시 토큰 |

**Response** (200 OK) `ApiResponse<Void>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

**Errors**
- `401` - 인증 실패, Refresh Token 불일치, 관리자 ID 불일치

---

### 5.3 관리자 토큰 갱신

**POST** `/api/v1/auth/admin/refresh`

**인증**: 불필요

관리자 리프레시 토큰으로 새 액세스 토큰을 발급받습니다. 사용자(USER) 역할의 토큰은 사용할 수 없습니다.

**Request Body**

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

**RefreshTokenRequest 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| refreshToken | String | O | 관리자 리프레시 토큰 |

**Response** (200 OK) `ApiResponse<TokenResponse>`

TokenResponse 형식 (관리자 토큰: Access 900초, Refresh 86400초)

**Errors**
- `401` - Refresh Token 만료 또는 무효, 사용자 토큰 사용 시도, 비활성화된 관리자 계정, 관리자 ID 불일치

---

### 5.4 관리자 계정 생성

**POST** `/api/v1/auth/admin/accounts`

**인증**: 필요 (ADMIN)

새 관리자 계정을 생성합니다. 생성자 정보가 감사 추적됩니다.

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` (ADMIN) |

**Request Body**

```json
{
  "email": "admin@example.com",
  "username": "관리자",
  "password": "AdminPassword123!"
}
```

**AdminCreateRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| email | String | O | 이메일 형식 | 관리자 이메일 |
| username | String | O | 2~50자 | 관리자명 |
| password | String | O | 최소 8자 | 비밀번호 |

**Response** (200 OK) `ApiResponse<AdminResponse>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": {
    "id": 1,
    "email": "admin@example.com",
    "username": "관리자",
    "role": "ADMIN",
    "isActive": true,
    "createdAt": "2025-01-15T10:00:00",
    "lastLoginAt": null
  }
}
```

**AdminResponse 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| id | Long | O | 관리자 ID |
| email | String | O | 이메일 |
| username | String | O | 관리자명 |
| role | String | O | 권한 (`ADMIN`) |
| isActive | Boolean | O | 활성화 여부 |
| createdAt | String (ISO 8601) | O | 생성일시 |
| lastLoginAt | String (ISO 8601) | X | 마지막 로그인 일시 |

**Errors**
- `400` - 유효성 검증 실패, 이메일 중복, 사용자명 중복
- `401` - 인증 실패
- `403` - 권한 없음

> **참고**: 이메일/사용자명 중복은 유효성 검증 에러 형식(코드: `4006`, `VALIDATION_ERROR`)으로 반환됩니다.

---

### 5.5 관리자 목록 조회

**GET** `/api/v1/auth/admin/accounts`

**인증**: 필요 (ADMIN)

활성 상태(`isActive=true`)이고 삭제되지 않은(`isDeleted=false`) 관리자 계정 목록을 조회합니다.

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` (ADMIN) |

**Response** (200 OK) `ApiResponse<List<AdminResponse>>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success",
  "data": [
    {
      "id": 1,
      "email": "admin@example.com",
      "username": "관리자",
      "role": "ADMIN",
      "isActive": true,
      "createdAt": "2025-01-15T10:00:00",
      "lastLoginAt": "2025-01-20T14:30:00"
    }
  ]
}
```

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음

---

### 5.6 관리자 상세 조회

**GET** `/api/v1/auth/admin/accounts/{adminId}`

**인증**: 필요 (ADMIN)

특정 관리자 계정을 조회합니다.

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` (ADMIN) |

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| adminId | Long | O | 관리자 ID |

**Response** (200 OK) `ApiResponse<AdminResponse>`

AdminResponse 형식

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음
- `404` - 관리자 없음

---

### 5.7 관리자 정보 수정

**PUT** `/api/v1/auth/admin/accounts/{adminId}`

**인증**: 필요 (ADMIN)

관리자 계정 정보를 수정합니다. 수정자 정보가 감사 추적됩니다.

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` (ADMIN) |

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| adminId | Long | O | 관리자 ID |

**Request Body**

```json
{
  "username": "수정된관리자명",
  "password": "NewPassword123!"
}
```

**AdminUpdateRequest 필드**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| username | String | X | 2~50자 | 관리자명 |
| password | String | X | 최소 8자 | 비밀번호 |

**Response** (200 OK) `ApiResponse<AdminResponse>`

AdminResponse 형식

**Errors**
- `400` - 유효성 검증 실패, 사용자명 중복
- `401` - 인증 실패
- `403` - 권한 없음
- `404` - 관리자 없음

---

### 5.8 관리자 계정 삭제

**DELETE** `/api/v1/auth/admin/accounts/{adminId}`

**인증**: 필요 (ADMIN)

관리자 계정을 삭제합니다 (Soft delete). 자기 자신은 삭제 불가. 삭제 시 해당 관리자의 모든 리프레시 토큰이 무효화되며, 삭제자 정보가 감사 추적됩니다.

**Request Headers**

| 헤더 | 타입 | 필수 | 설명 |
|------|------|------|------|
| Authorization | String | O | `Bearer {accessToken}` (ADMIN) |

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| adminId | Long | O | 관리자 ID |

**Response** (200 OK) `ApiResponse<Void>`

```json
{
  "code": "2000",
  "messageCode": { "code": "SUCCESS", "text": "성공" },
  "message": "success"
}
```

**Errors**
- `401` - 인증 실패
- `403` - 권한 없음, 자기 자신 삭제 시도
- `404` - 관리자 없음

---

## 6. 관리자 보안 정책

### 6.1 계정 잠금

| 정책 | 내용 |
|------|------|
| 1차 잠금 | 연속 5회 로그인 실패 시 15분 잠금 |
| 2차 잠금 | 연속 10회 로그인 실패 시 1시간 잠금 |
| 잠금 해제 | 로그인 성공 시 실패 횟수 초기화 |

### 6.2 감사 추적 (Audit Trail)

관리자 계정의 생성/수정/삭제 시 수행한 관리자의 ID가 기록됩니다.

| 필드 | 설명 |
|------|------|
| createdBy | 계정을 생성한 관리자 ID |
| updatedBy | 계정을 수정한 관리자 ID |
| deletedBy | 계정을 삭제한 관리자 ID |

### 6.3 관리자 토큰 분리

관리자 토큰과 사용자 토큰은 분리되어 관리됩니다.
- 관리자 토큰 갱신 경로(`/admin/refresh`)에서 사용자(USER) 역할의 토큰 사용 불가
- 사용자 토큰 갱신 경로(`/refresh`)에서 관리자(ADMIN) 역할의 토큰 사용 불가

---

## 7. 에러 코드

| HTTP 상태 | 에러 코드 | 메시지 코드 | 설명 |
|----------|---------|-----------|------|
| 400 | 4000 | BAD_REQUEST | 잘못된 요청 |
| 400 | 4006 | VALIDATION_ERROR | 유효성 검증 실패 (이메일/사용자명 중복 포함) |
| 401 | 4001 | AUTH_FAILED | 인증 실패 |
| 401 | 4002 | AUTH_REQUIRED | 인증 필요 |
| 403 | 4003 | FORBIDDEN | 권한 없음 |
| 404 | 4004 | NOT_FOUND | 리소스 없음 |
| 409 | 4005 | CONFLICT | 충돌 |
| 429 | 4029 | RATE_LIMIT_EXCEEDED | 요청 한도 초과 |
| 500 | 5000 | INTERNAL_SERVER_ERROR | 서버 에러 |

> **참고**: `ConflictException`(이메일/사용자명 중복)은 유효성 검증 에러 형식으로 처리되어 HTTP `400`, 코드 `4006`으로 반환됩니다.

### 에러 메시지 코드

| 메시지 코드 | 기본 메시지 |
|-----------|-----------|
| AUTH_FAILED | 인증에 실패했습니다. |
| AUTH_REQUIRED | 인증이 필요합니다. |
| FORBIDDEN | 권한이 없습니다. |
| NOT_FOUND | 리소스를 찾을 수 없습니다. |
| CONFLICT | 충돌이 발생했습니다. |
| VALIDATION_ERROR | 유효성 검증에 실패했습니다. |
| BAD_REQUEST | 잘못된 요청입니다. |

---

## 8. 비밀번호 정책

- **최소 길이**: 8자
- **필수 포함**: 대소문자/숫자/특수문자 중 2가지 이상

---

## 9. 엔드포인트 요약

| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| POST | `/api/v1/auth/signup` | X | 회원가입 |
| POST | `/api/v1/auth/login` | X | 로그인 |
| POST | `/api/v1/auth/logout` | O | 로그아웃 |
| DELETE | `/api/v1/auth/me` | O | 회원탈퇴 |
| POST | `/api/v1/auth/refresh` | X | 토큰 갱신 |
| GET | `/api/v1/auth/verify-email` | X | 이메일 인증 |
| POST | `/api/v1/auth/reset-password` | X | 비밀번호 재설정 요청 |
| POST | `/api/v1/auth/reset-password/confirm` | X | 비밀번호 재설정 확인 |
| GET | `/api/v1/auth/oauth2/{provider}` | X | OAuth 로그인 시작 |
| GET | `/api/v1/auth/oauth2/{provider}/callback` | X | OAuth 콜백 |
| POST | `/api/v1/auth/admin/login` | X | 관리자 로그인 |
| POST | `/api/v1/auth/admin/logout` | O (ADMIN) | 관리자 로그아웃 |
| POST | `/api/v1/auth/admin/refresh` | X | 관리자 토큰 갱신 |
| POST | `/api/v1/auth/admin/accounts` | O (ADMIN) | 관리자 계정 생성 |
| GET | `/api/v1/auth/admin/accounts` | O (ADMIN) | 관리자 목록 조회 |
| GET | `/api/v1/auth/admin/accounts/{adminId}` | O (ADMIN) | 관리자 상세 조회 |
| PUT | `/api/v1/auth/admin/accounts/{adminId}` | O (ADMIN) | 관리자 정보 수정 |
| DELETE | `/api/v1/auth/admin/accounts/{adminId}` | O (ADMIN) | 관리자 계정 삭제 |

---

**문서 버전**: 2.0
**최종 업데이트**: 2026-03-05
