# PR 초안 — 커밋된 평문 자격증명 제거와 CORS 오리진 설정화

> `gh pr create` 로 올릴 본문이다. 제목과 브랜치는 아래를 쓴다.
> **제목**: `fix : [main] 커밋된 평문 자격증명을 빼고 CORS 오리진을 설정으로 받는다`
> **브랜치**: `fix/security-secrets-and-cors` → `main`
> 본문 맨 아래 `Closes #N` 의 `N` 은 이슈를 만든 뒤 채운다.

---

## 무엇을 바꿨나

IntelliJ HTTP Client 환경 파일 세 개에 실제 JWT 와 비밀번호가 평문으로 커밋돼 있었고,
같은 비밀번호 문자열과 개인 이메일이 `.http` 파일과 문서에도 흩어져 있었다.
그 값을 전부 걷어내고, 값이 필요한 사람이 자기 로컬에서 채워 쓰도록 템플릿과 `.gitignore` 규칙을 붙였다.
함께 CORS 허용 오리진 `*` 를 두 곳 모두 설정으로 받게 바꾸고, 이 PR 의 리뷰에서 드러난
하니스 게이트 버그를 고쳤다.

### 1. 자격증명

| 파일 | 무엇을 했나 |
|---|---|
| `api/auth/src/test/http/http-client.env.json` | ADMIN 토큰 쌍·`adminPassword`·`testPassword`·이메일을 뺐다. `baseUrl`·`gatewayUrl`·`testUsername`·`testAdminId` 만 남는다 |
| `api/auth/src/test/http/http-client.private.env.json.template` | 새로 만들었다. 뺀 값들의 자리표시자다 |
| `api/agent/.../http-client.env.json` | ADMIN 토큰 쌍을 뺐다 |
| `api/agent/.../http-client.private.env.json.template` | 이미 있던 템플릿에 토큰 두 자리를 더했다 |
| `api/bookmark/.../http-client.private.env.json` | 저장소에서 뺐다(삭제) |
| `api/bookmark/.../http-client.private.env.json.template` | 새로 만들었다 |
| `.gitignore` | `**/http-client.private.env.json` 을 넣었다 |

**토큰은 전부 만료된 값이다** — 가장 늦게 끝나는 것이 2026-02-13 이다. 그래서 이 PR 은 살아 있는
자격증명을 막는 것이 아니라, **다음 값이 같은 자리에 다시 커밋되는 것을 막는다.**
비밀번호는 만료되지 않으므로 저장소 밖에서 따로 교체해야 한다.

환경 파일만 비우면 절반이다. **같은 비밀번호 문자열이 `.http` 파일과 문서에도 그대로 있었다.**
대부분 실패 케이스 요청 본문이라 눈에 덜 띈다 — 이메일을 일부러 빼고 400 을 확인하는 자리인데
비밀번호 칸에는 진짜 값이 들어가 있다. 문자열이 노출된다는 점에서는 성공 케이스와 다르지 않다.

| 파일 | 무엇을 했나 |
|---|---|
| `01-signup.http` · `02-login.http` | 실패 케이스에 박힌 `Password123!` 7곳을 `{{testPassword}}` 로 바꿨다 |
| `07-reset-password-confirm.http` | `NewPassword123!` 4곳을 `{{newTestPassword}}` 로 바꿨다. 재설정 뒤 §8 이 이 값으로 실제 로그인하므로 살아 있는 값이다 |
| `12-admin-login.http` | `Admin123!` 을 `{{adminPassword}}` 로 바꿨다 |
| `13-admin-create.http` | 개인 이메일 2곳을 `newadmin@example.com` 으로 바꿨다 — 이 파일이 이미 응답에서 단언하는 값이라 요청과 단언이 오히려 맞아떨어진다. 중복 케이스의 `Admin123!` 은 1번 케이스와 같은 샘플값 `admin123!` 으로 맞췄다 |
| `api/auth/src/test/http/README.md` | 환경 설정 절이 아직 `http-client.env.json` 하나만 설명하고 예시에 실제 비밀번호가 있었다. 두 파일로 나뉜 구조를 설명하도록 다시 썼다 |
| `docs/reference/api-specifications/002-api-auth.md` · `docs/reference/design/002-admin-role-based-auth.md` | 예시 본문의 실제 값을 이 문서들이 이미 쓰는 자리표시자 관례(`"username": "사용자명"`)에 맞춰 `"비밀번호"`·`"새 비밀번호"`·`"관리자 비밀번호"` 로 바꿨다 |
| `contents/20260719091109/README.md` | 캡처 계정으로 적힌 개인 이메일을 뺐다 |
| `api/auth/.../http-client.private.env.json.template` | 위에서 새로 쓴 `newTestPassword` 자리를 더했다 |

`.http` 의 변수 이름은 하나도 안 바꿨다. IntelliJ HTTP Client 는 `http-client.private.env.json` 을
`http-client.env.json` 위에 덮어쓰는 방식이라, 쓰는 사람은 같은 폴더의 `.template` 을
`http-client.private.env.json` 으로 복사하고 값을 채우면 된다.

**두 곳은 일부러 그대로 뒀다.** `02-login.http:111` 의 `WrongPassword123!` 은 인증 실패를 확인하려고
일부러 틀리게 넣은 값이라 자격증명이 아니다. `AuthControllerIntegrationTest.java:66` 의 `"Password123!"` 은
같은 문자열이지만, 이 테스트의 `obtainAccessToken()` 이 `"test-access-token"` 을 그대로 돌려주는
미완성 스텁이라 어디에도 인증하지 않는다.

### 2. CORS

```java
// as-is
// TODO: 운영 환경에서는 특정 도메인 지정 필요
configuration.setAllowedOrigins(List.of("*"));

// to-be
@Value("${security.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
private List<String> allowedOrigins;
...
configuration.setAllowedOrigins(allowedOrigins);
```

기본값은 로컬에서 띄우는 프런트엔드 둘(`app` 3000, `admin` 3001)이다.
**서비스별 `application.yml` 은 안 고쳤다.** 기본값이 있어서 설정을 안 넣어도 로컬은 그대로 돌고,
환경별 도메인은 필요한 곳에서 `security.cors.allowed-origins` 로 덮으면 된다.
코드에 남아 있던 TODO 는 지웠다 — 이제 좁히는 수단이 코드 밖에 있다.

`setAllowCredentials(false)` 는 그대로 뒀다. 지금 인증은 `Authorization` 헤더로 하고 쿠키를 안 쓴다.

**`api-emerging-tech` 는 이 파일을 안 쓴다.** 이 서비스만 `common-security` 대신 자기 `WebConfig` 로
CORS 를 걸고 있어서, `SecurityConfig` 를 고쳐도 `allowedOrigins("*")` 가 그대로 남았다.
같은 설정 키를 같은 기본값으로 읽게 해서 두 곳이 한 키로 움직이게 했다.

```java
// api/emerging-tech/.../config/WebConfig.java
@Value("${security.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
private String[] allowedOrigins;
...
registry.addMapping("/api/**").allowedOrigins(allowedOrigins)
```

### 3. 리뷰 하니스의 보안 신호

`tools/pr-eval` 의 `R-I`(보안) 축은 다섯 신호로 발동 여부를 가린다. 그중 신호4(자격증명·시크릿)가
**이 PR 이 고치는 파일 세 개를 하나도 못 잡았다.** 확장자 화이트리스트에 `.json` 이 없어서다.

각 파일을 처음 추가한 커밋에 신호4 를 두 판으로 돌린 결과다.

| 파일을 추가한 커밋 | 현행 | `.json` 포함 |
|---|---|---|
| `ade9de9` (bookmark, 평문 JWT 2개) | **0** | **4** |
| `1bb20e8` (auth, ADMIN 토큰 쌍·비밀번호) | 94 | 97 |
| `e306db6` (agent, ADMIN 토큰 쌍) | 6 | 8 |

거짓 매치가 늘지 않는지도 같이 쟀다. **최근 main 커밋 60건에 두 판을 나란히 돌렸더니 매치 수가
달라진 커밋이 하나도 없었다.** 이 저장소의 `.json` 은 대부분 잠금 파일과 설정 스냅샷이라 신호4
키워드를 안 쓴다. 그래서 `.json` 을 넣었다 — 놓치던 자리를 잡고 거짓 매치는 안 늘어난다.

고친 곳은 `profiles/backend.md` §1 의 신호4 명령 한 줄과 그 각주, 그리고 같은 명령을 적어 둔
`docs/001-security-axis.md` §3-2 다. 판단 근거는 그 문서 §11 에 남겼다.

**게시 게이트에도 같은 성격의 구멍이 있었다.** 이 PR 에 eval-bot 리뷰를 걸었더니 게시가 PG5 에서 막혔다 —
`scripts/pr-eval.sh:322` 의 축 이름 검사가 `test("R-[A-H] \\(")` 라서, 규격대로 `R-I (보안)` 을 적은
코멘트를 "축 이름이 없다" 로 튕겼다. `R-I` 를 넣을 때 규칙 문서(`00-criteria.md`·`profiles/backend.md`)는
갱신됐는데 게이트 스크립트가 안 따라간 것이다. **조건부 축이라 실제로 발동해 게시까지 가야 드러나는데,
그런 판이 이 PR 이 처음이었다.** `R-[A-I]` 로 넓혔고, 학습 두 건(L0-31·L0-32)을 `_memory/learnings.md` 에 남겼다.

## 왜 이 PR 인가

`R-I` 축은 §8 에서 **이미 끝난 PR 의 동결 SHA** 에 위원 세션을 따로 띄워 검증했다. 실제 하니스가
Phase 0 부터 게시까지 도는 라운드에서 돈 적은 없다. 이 PR 이 그 첫 번째다.

그리고 고치는 대상이, §8-2 에서 `R-I` 위원이 **찾아 놓고 무효 조건 I-8("이 PR 이 만든 것인가")에 걸려
접어 둔 바로 그 둘**이다. 축은 제대로 동작했는데 접힌 결함은 main 에 남아 있었다.

이 PR 의 `main…head` 에 다섯 신호를 돌린 결과다.

| 신호1 | 신호2 | 신호3 | 신호4 | 신호5 | 판정 |
|---|---|---|---|---|---|
| 9 | 0 | 2 | 0 | 2 | **해당** |

> 신호4 는 `.json` 을 넣어도 0 이다. **고치는 방향의 diff 라서 그렇다** — 자격증명이 든 줄은 전부
> `-` 쪽이고 `+` 쪽에 남는 것은 `.template` 의 자리표시자인데, 그 파일은 확장자가 `.template` 이라
> `*.json` 글롭에도 안 걸린다. 그래서 위 §3 의 근거는 이 PR 의 diff 가 아니라 과거 커밋 셋이다.
> 이 PR 로 `.json` 추가를 정당화하면 하니스가 금지한 상수 검사(`01-stages.md` §10)가 된다.

## 검증

| 무엇 | 결과 |
|---|---|
| `./gradlew :common-security:compileJava` | 통과 |
| `./gradlew :api-emerging-tech:compileJava` | 통과 |
| `./gradlew :api-auth:test --rerun` | 181건 중 **178건 통과 · 3건 skipped**, 실패 0 · 오류 0. skipped 3건은 이 PR 이전부터 있던 `@Disabled` 다 |
| 저장소 전체에 남은 값 재검색 | `git grep` 으로 `Password123!` · `Admin123!` · 개인 이메일 두 개를 다시 훑었다. 남은 것은 아래 「남는 것」에 적은 셋뿐이다 |
| 신호4 두 판 대조 (최근 main 60커밋) | 차이 난 커밋 0건 |
| 신호4 두 판 대조 (문제 파일 추가 커밋 3건) | 0→4 · 94→97 · 6→8 |

**테스트 건수를 고쳐 적었다.** 이 PR 의 앞선 초안은 "181건 전부 통과" 라고 썼는데, 실제로는 3건이
skipped 다. eval-bot 리뷰가 짚어 준 것을 재실행으로 확인했다.

**`common/security` 에는 테스트 소스셋이 없다.** 그래서 CORS 변경은 컴파일과, 이 모듈을 쓰는
`api-auth` 의 테스트로만 확인했다. 오리진 목록이 실제로 응답 헤더에 어떻게 실리는지는 확인하지 않았다.
`api-emerging-tech` 쪽도 컴파일까지만 확인했다.

## 남는 것

- **git 히스토리에는 값이 그대로 있다.** 히스토리를 다시 쓰면 협업자 clone 이 깨지므로 따로 판단할 문제다.
- **비밀번호 교체는 저장소 밖의 일이다.** 이 PR 은 값이 노출됐다는 사실을 적어 두는 데까지만 한다.
- **`docker/init/auth/02-init-data.sql` 은 안 고쳤다.** 로컬 docker 컨테이너에만 들어가는 시드 데이터다.
  비밀번호는 BCrypt 해시로 들어가 있고 평문은 주석(`Admin1234!`)뿐이지만, 개인 이메일이 계정 식별자로 박혀 있다.
  이 값을 바꾸면 로컬에서 로그인하는 계정 자체가 바뀌어 `.http` 흐름이 같이 흔들리므로 따로 판단할 문제로 뒀다.
- **CORS 설정 키를 채우는 곳은 아직 없다.** 네 서비스에 비-local 프로필 파일 자체가 없어서 이 키만의 문제는
  아니다. beta·prod 를 처음 띄우는 시점에 `security.cors.allowed-origins` 를 넣어야 한다.
- **frontend 프로파일의 보안 축은 손대지 않았다.** `docs/001-security-axis.md` §9 가 미뤄 둔 그대로다.

Closes #N
