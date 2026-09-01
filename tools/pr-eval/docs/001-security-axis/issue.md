# 이슈 초안 — 커밋된 평문 자격증명과 `*` CORS

> `gh issue create` 로 올릴 본문이다. 제목은 아래 한 줄을 쓴다.
> **제목**: `fix: 저장소에 커밋된 평문 JWT·비밀번호를 빼고 CORS 오리진을 설정으로 받기`

---

## 무엇이 문제인가

IntelliJ HTTP Client 환경 파일 세 개에 **실제로 발급받은 JWT 와 비밀번호가 평문으로 커밋돼 있다.**
그리고 모든 서비스가 공유하는 `SecurityConfig` 의 CORS 허용 오리진이 `*` 로 고정돼 있다.

## 어디에 있나

| 파일 | 들어 있는 것 |
|---|---|
| `api/auth/src/test/http/http-client.env.json` | ADMIN 계정의 access·refresh 토큰 쌍, `adminPassword`(`admin`, dev/prod 는 `Admin123!`), `testPassword`(`Password123!`), 개인 이메일 주소 |
| `api/agent/src/test/java/com/tech/n/ai/api/agent/http/http-client.env.json` | ADMIN 계정의 access·refresh 토큰 쌍 |
| `api/bookmark/src/test/java/com/tech/n/ai/http/http-client.private.env.json` | 사용자 계정의 access·refresh 토큰 쌍 |
| `common/security/src/main/java/com/tech/n/ai/common/security/config/SecurityConfig.java:58` | `configuration.setAllowedOrigins(List.of("*"))` — `// TODO: 운영 환경에서는 특정 도메인 지정 필요` 주석만 달려 있다 |

**토큰은 전부 만료됐다.** 가장 늦게 끝나는 refresh 토큰이 2026-02-13 이다. 그래서 지금 그 값을 그대로
써서 로그인되지는 않는다. 다만 세 가지가 남는다.

1. 비밀번호는 만료되지 않는다. `admin` 은 계정 하나의 실제 값이고 dev·prod 항목에도 값이 적혀 있다.
2. 토큰 페이로드에 계정 id 와 이메일 주소가 들어 있어 그대로 읽힌다.
3. **다음에 발급받은 토큰이 같은 자리에 다시 커밋되는 것을 막는 장치가 없다.** `.gitignore` 에
   `http-client.private.env.json` 규칙이 없고, `api/auth`·`api/bookmark` 에는 자리표시자 템플릿도 없다.

CORS 쪽은 `setAllowCredentials(false)` 라 브라우저가 쿠키를 실어 보내지는 않는다. 그래서 지금 당장
세션이 새는 구조는 아니다. 하지만 `Authorization` 헤더를 쓰는 API 라 **아무 오리진의 스크립트나
토큰만 있으면 이 API 를 부를 수 있다.** 운영에서 도메인을 좁히라는 TODO 가 코드에 남아 있는데,
좁힐 수단(설정 키)이 없어서 좁히려면 코드를 고쳐야 한다.

## 왜 지금인가

`tools/pr-eval` 리뷰 하니스에 보안 축 `R-I` 를 넣으면서(`docs/001-security-axis.md`) §8-2 검증을 돌렸다.
그때 `R-I` 위원이 **이 둘을 찾아 놓고 지적하지 않았다.** 무효 조건 I-8("이 PR 이 만든 것인가")에 걸려
`미확인 우려` 로만 남겼기 때문이다. 축은 제대로 동작했지만, 접힌 결함은 접힌 채로 main 에 남았다.

함께 드러난 것이 하나 더 있다. **`R-I` 의 발동 신호4(자격증명·시크릿)가 이 세 파일을 하나도 못 잡는다.**
신호4 명령이 확장자를 화이트리스트로 좁혔는데 거기에 `.json` 이 없다. 각 파일을 처음 추가한 커밋에
신호4 를 돌려 보면 이렇다.

| 파일을 추가한 커밋 | 현행 신호4 | `.json` 을 넣었을 때 |
|---|---|---|
| `ade9de9` (bookmark, 평문 JWT 2개) | **0** | **4** |
| `1bb20e8` (auth, ADMIN 토큰 쌍·비밀번호) | 94 | 97 |
| `e306db6` (agent, ADMIN 토큰 쌍) | 6 | 8 |

`ade9de9` 가 결정적이다. 평문 JWT 를 커밋하는 자리를 보안 축의 시크릿 신호가 통째로 놓쳤다.

## 무엇을 하면 닫히나

- 세 파일에서 토큰·비밀번호·이메일을 걷어내고, 값이 필요한 사람이 채워 쓸 `.template` 을 남긴다.
- `.gitignore` 에 `**/http-client.private.env.json` 을 넣어 다음 값이 다시 커밋되지 않게 한다.
- CORS 허용 오리진을 설정 키로 받는다. 기본값은 로컬 프런트엔드 두 개로 둔다.
- `R-I` 신호4 확장자 목록에 `.json` 을 넣는다. 거짓 매치가 늘지 않는지 실측으로 확인한다.

## 범위 밖

- **git 히스토리에 남은 값의 제거.** 히스토리를 다시 쓰는 일은 협업자 전원의 clone 을 깨뜨리므로
  따로 판단할 문제다. 이 이슈는 앞으로 들어오는 것을 막는 데까지만 한다.
- **`admin` 비밀번호의 실제 교체.** 저장소 밖의 일이다. 값이 노출됐다는 사실만 여기 적어 둔다.
- **frontend 프로파일의 같은 축 반영.** `docs/001-security-axis.md` §9 가 backend 를 한 번 돌린 뒤로 미뤄 뒀다.
