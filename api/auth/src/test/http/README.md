# Auth API HTTP 테스트 파일

이 디렉토리는 **IntelliJ IDEA 내장 HTTP Client**를 사용하여 인증 API를 테스트하기 위한 `.http` 파일들을 포함하고 있습니다.

별도의 Postman이나 다른 도구 없이 **IDE 내에서 바로 API 테스트**가 가능합니다.

## 파일 구조

```
http/
├── README.md                           # 이 파일
├── http-client.env.json                # 환경별 공개 변수 (baseUrl, testEmail 등)
├── http-client.private.env.json        # 환경별 비공개 변수 (토큰 등) - Git 제외
├── 01-signup.http                      # 회원가입 API 테스트
├── 02-login.http                       # 로그인 API 테스트
├── 03-logout.http                      # 로그아웃 API 테스트
├── 04-refresh.http                     # 토큰 갱신 API 테스트
├── 05-verify-email.http                # 이메일 인증 API 테스트
├── 06-reset-password.http              # 비밀번호 재설정 요청 API 테스트
└── 07-reset-password-confirm.http      # 비밀번호 재설정 확인 API 테스트
```

## 🚀 IntelliJ HTTP Client 사용 방법

### 1. 시작하기

1. IntelliJ IDEA에서 `.http` 파일 열기
2. 파일 상단의 환경 선택 드롭다운에서 `local`, `dev`, `prod` 중 선택
3. 각 요청 옆의 **실행 버튼(▶️)** 클릭 또는 `Ctrl+Enter` (Mac: `Cmd+Enter`)

### 2. 환경 설정

환경 변수는 두 파일로 나뉩니다.

| 파일 | 무엇이 들어가나 | 커밋 |
|---|---|---|
| `http-client.env.json` | URL, 사용자명처럼 누구에게나 같은 값 | 한다 |
| `http-client.private.env.json` | 토큰·비밀번호·이메일처럼 사람마다 다른 값 | **안 한다** (`.gitignore`) |

처음 받았다면 같은 폴더의 `http-client.private.env.json.template` 을
`http-client.private.env.json` 으로 복사하고 값을 채우세요.
IntelliJ HTTP Client 가 private 파일의 값을 공용 파일 위에 덮어쓰므로, `.http` 파일이 쓰는
변수 이름(`{{testPassword}}` 등)은 그대로입니다.

공용 파일 — `http-client.env.json`:

```json
{
  "local": {
    "baseUrl": "http://localhost:8080",
    "gatewayUrl": "http://localhost:8081",
    "testUsername": "testuser"
  },
  "dev": {
    "baseUrl": "https://dev-api.example.com",
    ...
  }
}
```

개인 파일 — `http-client.private.env.json`:

```json
{
  "local": {
    "testEmail": "YOUR_TEST_EMAIL_HERE",
    "testPassword": "YOUR_TEST_PASSWORD_HERE",
    "newTestPassword": "YOUR_NEW_TEST_PASSWORD_HERE",
    "adminEmail": "YOUR_ADMIN_EMAIL_HERE",
    "adminPassword": "YOUR_ADMIN_PASSWORD_HERE",
    "adminAccessToken": "YOUR_ADMIN_ACCESS_TOKEN_HERE",
    "adminRefreshToken": "YOUR_ADMIN_REFRESH_TOKEN_HERE"
  }
}
```

**환경 전환 방법:**
- HTTP 요청 편집기 상단의 환경 드롭다운에서 선택
- 또는 `Alt+Enter` → `Switch environment` 선택

### 3. 자동 토큰 관리 (핵심 기능!)

**IntelliJ HTTP Client의 강력한 기능**: 응답 핸들러 스크립트를 사용하여 토큰을 자동으로 저장하고 재사용합니다.

#### 예시: 로그인 후 자동 토큰 저장

```http
POST {{baseUrl}}/api/v1/auth/login
Content-Type: application/json

{
  "email": "{{testEmail}}",
  "password": "{{testPassword}}"
}

> {%
    // 응답 핸들러: 토큰을 자동으로 환경 변수에 저장
    client.global.set("accessToken", response.body.data.accessToken);
    client.global.set("refreshToken", response.body.data.refreshToken);
%}
```

이후 다른 요청에서 자동으로 사용:

```http
POST {{baseUrl}}/api/v1/auth/logout
Authorization: Bearer {{accessToken}}
```

**수동 복사 불필요!** 모든 토큰이 자동으로 관리됩니다.

### 4. 응답 검증 (자동 테스트)

각 요청에는 **자동 검증 스크립트**가 포함되어 있습니다:

```http
> {%
    client.test("로그인 성공", function() {
        client.assert(response.status === 200, "응답 상태 코드가 200이어야 합니다");
        client.assert(response.body.success === true, "success 필드가 true여야 합니다");
    });
%}
```

**실행 결과 창에서 자동으로 테스트 결과 확인 가능**

### 5. 테스트 순서

#### 기본 플로우 (권장)
1. **01-signup.http** - 회원가입으로 새 계정 생성
2. **02-login.http** - 로그인 (토큰 자동 저장됨)
3. **03-logout.http** - 로그아웃 테스트
4. **04-refresh.http** - 토큰 갱신 테스트

#### 비밀번호 재설정 플로우
1. **06-reset-password.http** - 비밀번호 재설정 요청
2. **07-reset-password-confirm.http** - 새 비밀번호로 변경
3. **02-login.http** - 새 비밀번호로 로그인 확인

## API 엔드포인트 목록

| 파일 | 메서드 | 엔드포인트 | 설명 | 인증 필요 |
|------|--------|-----------|------|----------|
| 01-signup.http | POST | `/api/v1/auth/signup` | 회원가입 | ❌ |
| 02-login.http | POST | `/api/v1/auth/login` | 로그인 | ❌ |
| 03-logout.http | POST | `/api/v1/auth/logout` | 로그아웃 | ✅ |
| 04-refresh.http | POST | `/api/v1/auth/refresh` | 토큰 갱신 | ❌ |
| 05-verify-email.http | GET | `/api/v1/auth/verify-email` | 이메일 인증 | ❌ |
| 06-reset-password.http | POST | `/api/v1/auth/reset-password` | 비밀번호 재설정 요청 | ❌ |
| 07-reset-password-confirm.http | POST | `/api/v1/auth/reset-password/confirm` | 비밀번호 재설정 확인 | ❌ |

## 테스트 케이스

각 `.http` 파일은 다음과 같은 테스트 케이스를 포함합니다:

### 정상 케이스
- 유효한 입력값으로 API 호출
- 다양한 정상 시나리오 테스트

### 실패 케이스
- 필수 파라미터 누락
- 잘못된 형식의 입력값
- 유효하지 않은 토큰
- 만료된 토큰
- 권한 없는 접근

## 비밀번호 요구사항

비밀번호는 다음 조건을 만족해야 합니다:
- 최소 8자 이상
- 영문 대소문자/숫자/특수문자 중 **2가지 이상** 조합

**유효한 비밀번호 예시:**
- `Password123` (영문 + 숫자)
- `Password!@#` (영문 + 특수문자)
- `Pass123!` (영문 + 숫자 + 특수문자)

**유효하지 않은 비밀번호 예시:**
- `password` (영문만)
- `12345678` (숫자만)
- `Pass1` (8자 미만)

## 응답 형식

모든 API는 공통 응답 형식을 사용합니다:

### 성공 응답
```json
{
  "success": true,
  "data": {
    // 응답 데이터
  }
}
```

### 실패 응답
```json
{
  "success": false,
  "error": "ERROR_CODE",
  "message": "에러 메시지"
}
```

## 🎯 IntelliJ HTTP Client 고급 기능

### 1. 요청 히스토리
- `Tools` → `HTTP Client` → `Show HTTP Requests History`
- 또는 `.idea/httpRequests/` 디렉토리에서 과거 요청 확인

### 2. 실행 결과 저장
- 각 요청 실행 후 응답이 자동으로 저장됨
- `.idea/httpRequests/` 디렉토리에서 확인 가능

### 3. 환경 변수 Live Template
- `Ctrl+Space`로 자동완성 사용
- `{{` 입력 시 사용 가능한 변수 목록 표시

### 4. 여러 요청 일괄 실행
- `Tools` → `HTTP Client` → `Run All Requests in File`
- 또는 파일 상단의 실행 버튼 옆 드롭다운에서 선택

### 5. 응답 포맷팅
- 응답 창에서 JSON, XML 자동 포맷팅
- `Copy Response Body` 버튼으로 응답 복사

## 주의사항

1. **Rate Limiting**: 비밀번호 재설정 등 일부 API는 Rate Limiting이 적용될 수 있습니다.
2. **토큰 만료**: Access Token과 Refresh Token은 일정 시간 후 만료됩니다.
3. **일회용 토큰**: 이메일 인증 토큰과 비밀번호 재설정 토큰은 일회용입니다.
4. **보안**: 운영 환경에서는 반드시 HTTPS를 사용해야 합니다.
5. **Git 제외**: `http-client.private.env.json` 파일은 `.gitignore`에 추가하세요.

## 💡 유용한 팁

### 1. .gitignore 설정
```gitignore
# IntelliJ HTTP Client - 민감한 정보가 포함된 파일 제외
http-client.private.env.json
.idea/httpRequests/*
```

### 2. 단축키
- `Ctrl+Enter` (Mac: `Cmd+Enter`): 현재 요청 실행
- `Ctrl+\` (Mac: `Cmd+\`): 최근 실행한 요청 재실행
- `Alt+Enter`: 빠른 액션 (환경 전환 등)

### 3. 여러 요청 순차 실행
파일 내 모든 요청을 순서대로 실행하려면:
1. 파일 상단 실행 버튼 옆 드롭다운 클릭
2. "Run All Requests in File" 선택

### 4. 동적 변수 사용
IntelliJ HTTP Client는 동적 변수를 지원합니다:
```http
GET {{baseUrl}}/api/v1/users/{{$uuid}}
X-Request-Id: {{$timestamp}}
```

## 문제 해결

### 401 Unauthorized
- Access Token이 만료되었거나 유효하지 않은 경우
- `02-login.http`로 다시 로그인하여 새 토큰 발급

### 403 Forbidden
- 권한이 없는 리소스에 접근한 경우
- 올바른 사용자 계정으로 로그인 확인

### 400 Bad Request
- 요청 데이터 형식이 올바르지 않은 경우
- Validation 오류 메시지 확인

### 500 Internal Server Error
- 서버 오류 발생
- 서버 로그 확인 필요

## 📚 참고 자료

- [IntelliJ HTTP Client 공식 문서](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html)
- [HTTP 요청 응답 핸들러 스크립트](https://www.jetbrains.com/help/idea/exploring-http-syntax.html#response-handler-scripts)
- [환경 변수 설정 방법](https://www.jetbrains.com/help/idea/exploring-http-syntax.html#environment-variables)

## ✅ 체크리스트

시작하기 전에 확인하세요:

- [ ] IntelliJ IDEA (Community 또는 Ultimate) 설치
- [ ] Auth API 서버가 실행 중 (`http://localhost:8080`)
- [ ] `http-client.env.json` 파일의 환경 설정 확인
- [ ] 환경 선택 (local, dev, prod)
- [ ] 첫 번째 요청 실행 테스트

## 문의 및 지원

- API 문서: [링크 추가 예정]
- 이슈 리포팅: [GitHub Issues 링크]
- 문의: [담당자 이메일 또는 연락처]
