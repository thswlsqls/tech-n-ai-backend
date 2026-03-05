# 관리자 인증 보안 강화 프롬프트

## 프롬프트 목적

이 프롬프트는 `api/auth` 모듈의 **관리자(Admin) 인증 보안 갭을 개선**하기 위한 구현을 지시합니다.
현재 User 인증에는 존재하지만 Admin 인증에는 누락된 기능을 보완하고, 관리자 계정의 인증/인가에 대한 **업계 표준 베스트 프랙티스**를 적용합니다.

---

## 현재 상태 분석

### Admin vs User 인증 비교표

| 기능 | User (현재) | Admin (현재) | 보안 갭 |
|------|-------------|-------------|---------|
| 로그인 | `POST /api/v1/auth/login` | `POST /api/v1/auth/admin/login` | - |
| 로그아웃 | `POST /api/v1/auth/logout` | **없음** | **Critical** |
| 토큰 갱신 | `POST /api/v1/auth/refresh` | **없음** | **High** |
| Refresh Token 삭제 | logout 시 hard delete | 삭제 수단 없음 | **Critical** |
| Refresh Token 소유권 검증 | `userId` 일치 확인 | 검증 로직 없음 | **High** |
| 로그인 실패 제한 | 없음 | 없음 | **High** (관리자에 더 치명적) |
| Access Token 유효기간 | 60분 | 60분 (동일) | **Medium** (관리자는 더 짧아야 함) |
| Refresh Token 유효기간 | 7일 | 7일 (동일) | **Medium** (관리자는 더 짧아야 함) |
| 다중 세션 관리 | 없음 | 없음 | **Medium** |

### 식별된 보안 갭 상세

#### 1. Admin 로그아웃 엔드포인트 부재 (Critical)
- `AdminController`에 logout 매핑 없음
- `AdminFacade`에 logout 메서드 없음
- 관리자가 로그아웃하면 Refresh Token이 DB에 남아 만료 전까지 유효
- **공격 시나리오**: 관리자 기기 분실/도난 시 세션 무효화 불가

#### 2. Admin 전용 토큰 갱신 부재 (High)
- Admin 전용 `/api/v1/auth/admin/refresh` 엔드포인트 없음
- User의 `/api/v1/auth/refresh`가 공유 테이블(`refresh_tokens`)을 조회하므로 Admin Refresh Token도 기술적으로 갱신 가능
- `UserAuthenticationService.refreshToken()`에서 role 기반 분기 없음 — Admin 토큰이 User 경로에서 갱신되는 의도하지 않은 동작

#### 3. Refresh Token 소유권 검증 미비 (High)
- User 로그아웃: `entity.getUserId().toString().equals(userId)` 검증 있음
- Admin: 소유권 검증 로직 자체가 없음
- `/api/v1/auth/refresh` 경로에서도 소유권 검증 없이 token claims만으로 새 토큰 발급

#### 4. 로그인 실패 제한 없음 (High)
- `AdminService.login()`에 실패 횟수 추적/제한 없음
- Brute-force 공격에 무방비
- 관리자 계정은 높은 권한을 가지므로 더 엄격한 보호 필요

#### 5. Admin 삭제 시 Refresh Token 미처리 (Medium)
- `AdminService.deleteAdmin()`에서 soft delete만 수행
- 삭제된 Admin의 Refresh Token이 DB에 남아 만료 전까지 갱신 가능
- User 탈퇴(`AuthService.withdraw()`)에서는 Refresh Token 삭제 수행

---

## 구현 요구사항

### Phase 1: Admin 로그아웃 구현

#### 1.1 AdminController에 로그아웃 엔드포인트 추가

```
POST /api/v1/auth/admin/logout
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "refreshToken": "..."
}
```

- `@AuthenticationPrincipal UserPrincipal principal`로 인증된 관리자 식별
- `principal.userId()`와 Refresh Token의 `adminId` 일치 확인 (소유권 검증)
- 검증 통과 시 해당 Refresh Token hard delete

#### 1.2 AdminFacade, AdminService에 logout 메서드 추가

기존 `UserAuthenticationService.logout()` 패턴을 참고하되, Admin 전용 로직 적용:

```java
// UserAuthenticationService.logout() 참고 (현재 구현)
public void logout(String userId, String refreshToken) {
    RefreshTokenEntity tokenEntity = findAndValidateRefreshToken(refreshToken, userId);
    refreshTokenService.deleteRefreshToken(tokenEntity);
}
```

Admin 버전에서는:
- `RefreshTokenEntity.getAdminId()`로 소유권 검증 (userId 대신 adminId)
- `RefreshTokenService`에 `findAdminRefreshToken()` 또는 기존 `findRefreshToken()` 재사용

#### 1.3 SecurityConfig 확인

현재 설정에서 `POST /api/v1/auth/admin/logout`은 `/api/v1/auth/admin/**` → `hasRole("ADMIN")` 규칙에 의해 이미 보호됨. 추가 설정 불필요.

---

### Phase 2: Admin 전용 토큰 갱신 구현

#### 2.1 AdminController에 토큰 갱신 엔드포인트 추가

```
POST /api/v1/auth/admin/refresh
Content-Type: application/json

{
  "refreshToken": "..."
}
```

- JWT 서명 검증 → DB 조회 → 만료 확인 → 소유권 확인 → 기존 토큰 삭제 → 새 토큰 쌍 발급 (rotate-on-use)
- `role` claim이 `"ADMIN"`인지 검증 — User Refresh Token으로 Admin 토큰 발급 방지

#### 2.2 SecurityConfig 수정

```java
// 추가: admin refresh도 permitAll (refresh token 자체가 인증 수단)
.requestMatchers("/api/v1/auth/admin/login").permitAll()
.requestMatchers("/api/v1/auth/admin/refresh").permitAll()   // 추가
.requestMatchers("/api/v1/auth/admin/**").hasRole("ADMIN")
```

#### 2.3 User의 /auth/refresh 경로에서 Admin 토큰 거부

`UserAuthenticationService.refreshToken()`에 role 검증 추가:

```java
JwtTokenPayload payload = tokenService.getPayloadFromToken(request.refreshToken());
if ("ADMIN".equals(payload.role())) {
    throw new UnauthorizedException("관리자 토큰은 관리자 전용 갱신 경로를 사용하세요.");
}
```

---

### Phase 3: Admin 삭제 시 Refresh Token 무효화

#### 3.1 AdminService.deleteAdmin()에 토큰 정리 추가

```java
// 삭제 대상 Admin의 모든 활성 Refresh Token 삭제
refreshTokenService.deleteAllAdminRefreshTokens(adminId);

// 기존 soft delete 수행
admin.setIsActive(false);
admin.setDeletedBy(currentAdminId);
adminWriterRepository.delete(admin);
```

#### 3.2 RefreshTokenService에 bulk 삭제 메서드 추가

```java
@Transactional
public void deleteAllAdminRefreshTokens(Long adminId) {
    refreshTokenWriterRepository.deleteAllByAdminId(adminId);
}
```

#### 3.3 RefreshTokenWriterRepository에 쿼리 추가

```java
void deleteAllByAdminId(Long adminId);
```

---

### Phase 4: 로그인 실패 제한 (Rate Limiting)

#### 4.1 AdminEntity에 실패 추적 필드 추가

```sql
ALTER TABLE admins
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수',
    ADD COLUMN account_locked_until DATETIME(6) NULL COMMENT '계정 잠금 해제 시각';
```

#### 4.2 AdminService.login()에 제한 로직 추가

- 계정 잠금 상태 확인 (`accountLockedUntil`이 현재 시각 이후이면 잠금)
- 로그인 실패 시 `failedLoginAttempts` 증가
- **5회 연속 실패**: 15분 계정 잠금
- **10회 연속 실패**: 1시간 계정 잠금
- 로그인 성공 시 `failedLoginAttempts = 0`, `accountLockedUntil = null` 초기화

#### 4.3 잠금 임계값 상수 정의

```java
// AdminService 또는 별도 상수 클래스
private static final int LOCK_THRESHOLD_FIRST = 5;
private static final int LOCK_THRESHOLD_SECOND = 10;
private static final Duration LOCK_DURATION_FIRST = Duration.ofMinutes(15);
private static final Duration LOCK_DURATION_SECOND = Duration.ofHours(1);
```

---

### Phase 5: Admin 토큰 유효기간 분리 (선택 사항)

관리자 토큰은 더 짧은 유효기간을 적용하여 탈취 시 피해 범위를 축소합니다.

#### 5.1 application.yml에 admin 전용 설정 추가

```yaml
jwt:
  secret-key: ${JWT_SECRET_KEY}
  access-token-validity-minutes: 60
  refresh-token-validity-days: 7
  admin:
    access-token-validity-minutes: 15    # 관리자: 15분
    refresh-token-validity-days: 1       # 관리자: 1일
```

#### 5.2 JwtTokenProvider에 admin 전용 메서드 추가

```java
public String generateAdminAccessToken(JwtTokenPayload payload) {
    return buildToken(payload, adminAccessTokenValidity);
}

public String generateAdminRefreshToken(JwtTokenPayload payload) {
    return buildToken(payload, adminRefreshTokenValidity);
}
```

#### 5.3 TokenService에서 role 기반 분기

```java
if ("ADMIN".equals(role)) {
    accessToken = jwtTokenProvider.generateAdminAccessToken(payload);
    refreshToken = jwtTokenProvider.generateAdminRefreshToken(payload);
} else {
    accessToken = jwtTokenProvider.generateAccessToken(payload);
    refreshToken = jwtTokenProvider.generateRefreshToken(payload);
}
```

---

## 기존 코드 참고 (반드시 확인)

### 수정 대상 파일

| 파일 | 변경 내용 |
|------|-----------|
| `api/auth/.../controller/AdminController.java` | logout, refresh 엔드포인트 추가 |
| `api/auth/.../facade/AdminFacade.java` | logout, refreshToken 메서드 추가 |
| `api/auth/.../service/AdminService.java` | logout, refreshToken 로직, 로그인 실패 제한, deleteAdmin 토큰 정리 |
| `api/auth/.../service/UserAuthenticationService.java` | refreshToken()에 ADMIN role 거부 로직 추가 |
| `api/auth/.../service/RefreshTokenService.java` | deleteAllAdminRefreshTokens() 추가 |
| `common/security/.../config/SecurityConfig.java` | `/api/v1/auth/admin/refresh` permitAll 추가 |
| `common/security/.../jwt/JwtTokenProvider.java` | (Phase 5) admin 전용 토큰 유효기간 |
| `datasource/aurora/.../entity/auth/AdminEntity.java` | failedLoginAttempts, accountLockedUntil 필드 |
| `datasource/aurora/.../entity/auth/RefreshTokenEntity.java` | 변경 없음 (이미 admin 지원) |
| `datasource/aurora/.../repository/writer/auth/RefreshTokenWriterRepository.java` | deleteAllByAdminId() 추가 |

### 수정하지 않을 파일

| 파일 | 이유 |
|------|------|
| `RefreshTokenEntity.java` | 이미 `createForAdmin()`, `adminId` FK 지원 |
| `TokenService.java` | 이미 role 기반 분기 구현 완료 |
| `JwtAuthenticationFilter.java` | 이미 `ROLE_` 접두사로 권한 부여 |
| `JwtTokenPayload.java` | 이미 `userId`, `email`, `role` 포함 |

---

## 기존 패턴 준수 사항

### Controller → Facade → Service 패턴

```
AdminController.logout()
  → AdminFacade.logout(principal.userId(), request.refreshToken())
    → AdminService.logout(userId, refreshToken)
      → RefreshTokenService.findRefreshToken(refreshToken)
      → 소유권 검증 (adminId == userId)
      → RefreshTokenService.deleteRefreshToken(entity)
```

### User 인증 코드 참고 패턴

`UserAuthenticationService`의 기존 패턴을 Admin에 대칭적으로 적용:

| User 메서드 | Admin 대응 메서드 |
|-------------|-------------------|
| `UserAuthenticationService.login()` | `AdminService.login()` (이미 존재) |
| `UserAuthenticationService.logout()` | `AdminService.logout()` (신규) |
| `UserAuthenticationService.refreshToken()` | `AdminService.refreshToken()` (신규) |
| `findAndValidateRefreshToken()` | Admin 버전 구현 (adminId 검증) |

### DTO 재사용

- `LoginRequest`: Admin/User 공통 사용 (이미 공유 중)
- `LogoutRequest`: Admin/User 공통 사용 가능
- `RefreshTokenRequest`: Admin/User 공통 사용 가능
- `TokenResponse`: Admin/User 공통 사용 (이미 공유 중)

---

## 업계 표준 베스트 프랙티스 근거

### 참고 공식 출처

| 출처 | 관련 권고사항 | URL |
|------|---------------|-----|
| **OWASP Authentication Cheatsheet** | 로그인 실패 제한, 계정 잠금 정책, 세션 무효화 | https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html |
| **OWASP Session Management Cheatsheet** | 로그아웃 시 서버 측 세션 무효화, 토큰 폐기 | https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html |
| **OWASP JSON Web Token Cheatsheet** | Refresh Token Rotation, 서버 측 저장 및 무효화 | https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html |
| **NIST SP 800-63B** | 인증자 보증 수준(AAL), 관리자 계정 강화 인증 | https://pages.nist.gov/800-63-4/sp800-63b.html |
| **RFC 6749 (OAuth 2.0)** | Token Revocation, Refresh Token 수명 관리 | https://datatracker.ietf.org/doc/html/rfc6749 |
| **RFC 7009 (Token Revocation)** | 토큰 무효화 엔드포인트 표준 | https://datatracker.ietf.org/doc/html/rfc7009 |
| **Spring Security Reference** | SecurityFilterChain 설정, hasRole 패턴 | https://docs.spring.io/spring-security/reference/ |
| **CWE-307** | Improper Restriction of Excessive Authentication Attempts | https://cwe.mitre.org/data/definitions/307.html |

### 적용 매핑

| 베스트 프랙티스 | 출처 | 이 프롬프트의 구현 |
|----------------|------|-------------------|
| 로그아웃 시 서버 측 토큰 무효화 | OWASP Session Management | Phase 1: Admin 로그아웃 + Refresh Token hard delete |
| Refresh Token Rotation (사용 시 교체) | OWASP JWT Cheatsheet | Phase 2: rotate-on-use 패턴 (User와 동일) |
| 관리자 계정은 일반 사용자보다 엄격한 인증 | NIST SP 800-63B AAL2+ | Phase 4: 로그인 실패 제한, Phase 5: 짧은 토큰 유효기간 |
| Brute-force 방지 (계정 잠금/Rate Limiting) | OWASP Auth, CWE-307 | Phase 4: 5회/10회 실패 시 계정 잠금 |
| 계정 비활성화 시 모든 세션 즉시 무효화 | OWASP Session Management | Phase 3: Admin 삭제 시 Refresh Token 전체 삭제 |
| 높은 권한 토큰은 짧은 유효기간 | NIST SP 800-63B | Phase 5: Admin Access Token 15분, Refresh Token 1일 |
| 토큰 경로 분리 (Admin/User 갱신 경로 혼용 방지) | RFC 6749 Scope 분리 원칙 | Phase 2: Admin 전용 refresh 경로 + User 경로에서 Admin 토큰 거부 |

---

## 구현 순서

1. **Phase 1**: Admin 로그아웃 — `AdminController`, `AdminFacade`, `AdminService`에 logout 추가
2. **Phase 2**: Admin 토큰 갱신 — Admin 전용 refresh 경로 + User refresh에서 Admin 거부
3. **Phase 3**: Admin 삭제 시 토큰 정리 — `deleteAdmin()`에 Refresh Token 삭제 추가
4. **Phase 4**: 로그인 실패 제한 — `AdminEntity` 필드 추가, `AdminService.login()` 수정
5. **Phase 5**: (선택) Admin 토큰 유효기간 분리 — `JwtTokenProvider`, `TokenService` 수정

---

## 검증 기준

### 기능 검증

- [ ] Admin 로그아웃 시 Refresh Token이 DB에서 삭제됨
- [ ] Admin 로그아웃 시 소유권 검증 (다른 Admin의 토큰 삭제 불가)
- [ ] Admin Refresh Token 갱신 시 rotate-on-use 동작 (기존 삭제 → 신규 발급)
- [ ] Admin Refresh Token이 User `/auth/refresh` 경로에서 거부됨
- [ ] User Refresh Token이 Admin `/auth/admin/refresh` 경로에서 거부됨
- [ ] Admin 삭제 시 해당 Admin의 모든 Refresh Token 삭제됨
- [ ] 5회 로그인 실패 시 15분 계정 잠금
- [ ] 10회 로그인 실패 시 1시간 계정 잠금
- [ ] 로그인 성공 시 실패 카운터 초기화

### 보안 검증

- [ ] 삭제된 Admin의 Refresh Token으로 토큰 갱신 불가
- [ ] 만료된 Refresh Token으로 갱신 불가
- [ ] 이미 사용(rotate)된 Refresh Token 재사용 불가
- [ ] Admin 로그아웃 경로에 `hasRole("ADMIN")` 적용 확인
- [ ] Admin 토큰 갱신 경로는 `permitAll` (Refresh Token 자체가 인증 수단)

### 기존 코드 정합성

- [ ] User 인증 흐름에 영향 없음
- [ ] Controller → Facade → Service 패턴 준수
- [ ] 기존 `RefreshTokenEntity`, `TokenService` 구조 유지
- [ ] `LogoutRequest`, `RefreshTokenRequest` DTO 재사용
- [ ] Soft Delete 패턴 유지 (Admin 계정 삭제)
- [ ] 기존 예외 클래스 재사용 (`UnauthorizedException`, `ForbiddenException`)

### 오버엔지니어링 방지

- [ ] Role enum 생성 금지 (String 유지)
- [ ] 새로운 예외 클래스 생성 금지
- [ ] 불필요한 추상화 계층 추가 금지
- [ ] Phase 5는 선택 사항으로 분리

---

## 테스트 전략

### 단위 테스트

- `AdminService.logout()` 정상/실패 케이스
- `AdminService.refreshToken()` 정상/만료/소유권 불일치
- `AdminService.login()` 실패 횟수 증가/계정 잠금/잠금 해제
- `AdminService.deleteAdmin()` 호출 시 Refresh Token 삭제 확인
- `UserAuthenticationService.refreshToken()`에서 ADMIN role 거부

### HTTP Client 테스트

기존 `api/bookmark/src/test/http/` 패턴 참고:

```http
### Admin 로그아웃
POST {{host}}/api/v1/auth/admin/logout
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json

{
  "refreshToken": "{{adminRefreshToken}}"
}

### Admin 토큰 갱신
POST {{host}}/api/v1/auth/admin/refresh
Content-Type: application/json

{
  "refreshToken": "{{adminRefreshToken}}"
}

### Admin 로그인 실패 제한 테스트 (5회 실패 후 잠금)
POST {{host}}/api/v1/auth/admin/login
Content-Type: application/json

{
  "email": "admin@example.com",
  "password": "wrong-password"
}
```

---

## 제한 사항

1. **외부 자료 참고 시 위에 명시된 공식 문서만 사용** (OWASP, NIST, RFC, Spring 공식 문서)
2. **오버엔지니어링 금지** — 요구사항에 명시되지 않은 기능 추가하지 않음
3. **기존 코드 패턴 준수** — Controller → Facade → Service, Reader/Writer Repository 분리
4. **기존 DTO 및 예외 클래스 최대한 재사용**

---

**작성일**: 2026-03-05
**버전**: 1.0
**대상 모듈**: api-auth, common-security, datasource-aurora
**선행 프롬프트**: `prompts/reference/admin-role-based-auth-design-prompt.md` (관리자 RBAC 설계 — 이미 구현 완료)
