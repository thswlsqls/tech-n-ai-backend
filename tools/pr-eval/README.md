# eval-bot

AI 에이전트가 쓴 코드를 사람이 읽기 전에 먼저 읽는 리뷰 봇이다.
GitHub 에서 `tech-n-ai-eval-bot` 계정을 리뷰어로 지정하면, 봇이 알아서 PR 을 읽고 리뷰를 남긴다.
남기는 방식은 사람 리뷰어와 같다 — 요약 한 건과, 문제가 있는 코드 줄에 직접 붙는 인라인 코멘트들이다.

## 왜 만들었나

기능 하나를 브랜치 하나로 끊어 AI 에이전트에게 구현시키면 코드는 빨리 나온다.
남는 일은 **그 코드가 맞는지 확인하는 것**인데, 이쪽은 빨라지지 않는다.
"LLM 이 만든 결과물의 품질을 어떻게 검증했나"에 답하려면 검증하는 절차 자체를 만들어야 했다.

그래서 리뷰를 한 번에 끝내지 않고, **찾는 일 / 반박하는 일 / 저자 입장에서 읽는 일**을 서로 다른 세션에 나눠 맡겼다.
한 세션이 자기가 찾은 것을 스스로 옳다고 판정하면 그건 검사가 아니라 자기 확인이기 때문이다.

## 실제로 붙은 리뷰

**[thswlsqls/tech-n-ai-backend PR #34](https://github.com/thswlsqls/tech-n-ai-backend/pull/34)** — 신기술 다건 저장을 건별 왕복에서 일괄 왕복으로 바꾼 리팩토링.
봇이 인라인 코멘트 6건과 요약 한 건을 남겼다.

리뷰 요약. 머지를 막을 만한 것 하나를 먼저 적고, 리뷰 축을 하나씩 짚어 무엇이 걸렸고 무엇을 확인하지 못했는지 표로 정리했다.

![PR #34 에 봇이 남긴 리뷰 요약](assets/pr34-review-summary.png)

인라인 코멘트 한 건. 문제가 되는 코드 줄에 그대로 붙고, 근거로 든 파일과 줄 번호가 전부 적혀 있다.
가운데가 저자의 답이고, 그 아래는 저자가 고친 뒤 봇이 같은 스레드에 남긴 후속 판정이다.

![PR #34 의 인라인 코멘트와 후속 판정](assets/pr34-inline-comment.png)

## 무엇을 근거로 판단하나

리뷰에는 아래 여섯 단계를 통과한 지적만 남는다.

| 단계 | 하는 일 |
|---|---|
| **① 기준을 먼저 정한다** | 근거를 네 등급으로 나눈다 — 저장소 규범 문서(A) · 공식 문서 인용(B) · 코드 사실(C) · 직접 실행해 본 결과(D). 인용문이 말하는 범위를 넘어선 주장은 무효로 뺀다 |
| **② 실행할 수 있으면 실행한다** | 코드를 한 줄도 고치지 않은 채 영향 모듈의 테스트와 빌드를 다시 돌린다. 읽어서 추론한 것과 돌려서 본 것을 리뷰 안에서 섞지 않는다 |
| **③ 축을 나눠 따로 채점한다** | 리뷰 관점 아홉 축을 나눠 서로의 결과를 모르는 네 세션에 맡긴다. 각 세션은 자기 축만 보고, 찾기만 할 뿐 옳은지는 판정하지 않는다 |
| **④ 나온 지적을 반박시킨다** | 별도 세션이 근거가 가리키는 파일과 줄을 실제로 열어 본다. 코드가 이미 그것을 다루고 있지 않은지, 수치가 맞는지 다시 센다. **확실하지 않으면 반박됨으로 판정한다** |
| **⑤ 저자 입장에서 읽힌다** | 다 쓴 리뷰를 "내가 이 PR 을 쓴 사람이라면" 하는 시선으로 다시 읽어, 사실이 틀렸거나 취향을 문제로 몰아붙인 코멘트를 걸러낸다 |
| **⑥ 라운드를 이어 돈다** | 확인이 끝난 사실과 반박에 떨어진 지적을 한 줄씩 쌓아 두고 다음 라운드 프롬프트에 골라 넣는다. 리뷰뿐 아니라 리뷰를 만드는 절차도 함께 고쳐진다 |

점수는 쓰지 않는다. 총점을 매기면 저자가 코드를 고치는 대신 점수에 항변하게 된다.
그래서 판정은 **치명 · 중대 · 경미 · 사소** 네 등급뿐이다. 잘한 점도 최소 한 건은 반드시 적는다.

## 리뷰 아홉 축과 네 세션

지적이 나오는 관점을 아홉 개로 나누고, 서로 결과를 모르는 네 세션에 나눠 맡긴다.
각 세션은 자기 축의 정의와 "이 축에서는 보지 않을 것" 목록만 받는다.
한 세션이 아홉 축을 한꺼번에 보면 눈에 먼저 띄는 몇 개에서 멈추기 때문이다.

| 세션 | 맡는 축 | 무엇을 보나 |
|---|---|---|
| **J1** | `R-A` 설계 ↔ 구현 정합성<br>`R-B` 데이터 정확성 | 설계 문서와 PR 본문이 선언한 의도를 코드가 실제로 지키는가. 쓰기용 DB 와 읽기용 DB 의 값이 어긋나는가, 이벤트를 다시 처리하면 중복·누락이 생기는가 |
| **J2** | `R-C` 동시성·원자성<br>`R-F` 장애 격리 | 서버가 여러 대이거나 요청이 재시도될 때 깨지는 연산. 외부 시스템(LLM·Kafka·DB)이 죽었을 때 우리 서비스가 같이 죽는가 |
| **J3** | `R-D` 인터페이스 계약<br>`R-I` 보안<br>`R-E` 부하 적합성 | API 응답 형식과 상태 코드가 스펙과 맞는가. **남의 데이터를 id 만 바꿔 꺼낼 수 있는가, 토큰이나 키가 로그와 소스에 남는가.** 쿼리가 건수만큼 늘어나는가, 페이지네이션 없이 전건을 읽는가 |
| **J4** | `R-G` 테스트<br>`R-H` 유지보수성 | 코드가 깨졌을 때 테스트가 실제로 실패하는가. 죽은 코드·하드코딩된 값·로그 없이 조용히 실패하는 자리 |

> **`R-I` 는 조건부 축이다.** 모든 PR 이 보안 경로를 건드리지는 않는다. 인증·인가 코드가 바뀌었는지, 새 HTTP 엔드포인트가 생겼는지, 사용자별 데이터를 다루는지 같은 다섯 신호를 정해진 명령으로 세어 발동 여부를 가린다. 걸리는 것이 하나도 없으면 그 PR 에서는 여덟 축으로 돈다.
>
> 축 코드는 저장소마다 뜻이 조금 다르다. 프런트엔드에는 `R-I` 가 없고, `R-C` 가 상태 경합, `R-E` 가 클라이언트 성능,
> `R-H` 가 접근성을 포함하고 `R-G` 는 조건부 축이다. 자세한 정의는 [`profiles/`](profiles/) 에 있다.

세션 번호는 점수가 아니라 **중복 제거 순서**다. 같은 자리를 둘이 지적하면 J1 쪽을 남긴다 —
설계와 데이터가 틀렸다는 판단이 나머지 지적의 전제이기 때문이다.

## 기준의 출처

축을 어떤 순서로 두고 어디까지 문제 삼을지는 임의로 정하지 않고, 공개된 코드리뷰 기준에서 가져왔다.
인용문이 말하는 범위를 넘어선 주장은 무효로 빼는 것이 검증 단계 ①의 규칙이다.

| 하니스의 규칙 | 출처 | 근거가 된 문장 |
|---|---|---|
| 설계를 가장 먼저 본다 — `R-A` 가 첫 축이고 중복 제거에서 J1 이 우선인 이유 | Google, [What to look for in a code review](https://google.github.io/eng-practices/review/reviewer/looking-for.html) | "The most important thing to cover in a review is the overall design of the CL." |
| 동시성을 별도 축(`R-C`)으로 떼어 낸다 | 같은 문서, Functionality 절 | "…if there is some sort of **parallel programming** going on in the CL that could theoretically cause deadlocks or race conditions." |
| 테스트 축의 판정 문구 — "테스트가 있는가"가 아니라 "깨졌을 때 실패하는가" | 같은 문서, Tests 절 | "Will the tests actually fail when the code is broken?" |
| 잘한 점을 최소 한 건 반드시 적는다 | 같은 문서 Good Things 절 · [Conventional Comments](https://conventionalcomments.org/) | "If you see something nice in the CL, tell the developer…" · "Try to leave at least one of these comments per review." |
| 판정 문구가 "이상적인 설계인가"가 아니라 "코드 헬스를 악화시키는가" | Google, [The Standard of Code Review](https://google.github.io/eng-practices/review/reviewer/standard.html) | "reviewers should favor approving a CL once it is in a state where it definitely improves the overall code health of the system being worked on, even if the CL isn't perfect." |
| 근거 없는 취향은 `nitpick` 까지만 쓴다 | 같은 문서 | "On matters of style, the style guide is the absolute authority. Any purely style point (whitespace, etc.) that is not in the style guide is a matter of personal preference." |
| 코멘트 라벨 표기 — `issue (blocking)` · `suggestion` · `nitpick (non-blocking)` · `praise` | [Conventional Comments](https://conventionalcomments.org/) | 라벨 + 괄호 표시(blocking / non-blocking) 형식을 그대로 따랐다 |
| 보안 축(`R-I`)의 첫 항목이 인가 누락인 이유 | OWASP, [ASVS 5.0.0](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x17-V8-Authorization.md) V8 Authorization, 8.2.2 (Level 1) | "Verify that the application ensures that data-specific access is restricted to consumers with explicit permissions to specific data items to mitigate insecure direct object reference (IDOR) and broken object level authorization (BOLA)." |
| 토큰·자격증명이 로그에 남는 것을 결함으로 보는 근거 | [같은 문서](https://github.com/OWASP/ASVS/blob/v5.0.0/5.0/en/0x25-V16-Security-Logging-and-Error-Handling.md) V16 Security Logging and Error Handling, 16.2.5 (Level 2) | "Verify that when logging sensitive data, the application enforces logging based on the data's protection level. For example, it may not be allowed to log certain data, such as credentials or payment details. Other data, such as session tokens, may only be logged by being hashed or masked, either in full or partially." |

테스트 축에서 세게 몰아붙이지 않는 이유도 여기에 있다. 근거가 되는 문장이
*"Ask for unit, integration, or end-to-end tests **as appropriate** for the change."* 라는 선택지형이라,
"이런 테스트가 없으니 blocking" 이라고 쓰면 인용이 뒷받침하는 범위를 넘는다.

> Google eng-practices 저장소는 현재 아카이브 상태다(마지막 push 2024-09-19). 위 인용은 그 시점의 문서 기준이다.
>
> **ASVS 는 점검표로 돌리지 않는다.** 두 인용문을 보안 축 정의문의 근거로만 쓴다.
> 표준 요구사항 상당수는 설정·아키텍처·런타임 증거를 봐야 판정되는데 봇이 보는 것은 PR 의 diff 다.
> 전건 점검표로 돌리면 근거로 댈 코드 줄이 없는 지적만 잔뜩 나온다.

## 어떻게 도나

```mermaid
flowchart LR
  D[개발자<br/>봇을 리뷰어로 지정] --> W[watch.sh<br/>60초마다 확인]
  W --> CH[chain.sh<br/>스테이지를 차례로 부른다]
  CH --> O[스테이지 세션 하나<br/>오케스트레이터]

  subgraph F["① 찾는다 — 서로 결과를 모르는 네 세션"]
    J1[설계 · 데이터 정확성]
    J2[동시성 · 장애 격리]
    J3[인터페이스 계약 · 보안 · 부하]
    J4[테스트 · 유지보수성]
  end

  O --> J1 & J2 & J3 & J4
  J1 & J2 & J3 & J4 --> R["② 반박한다<br/>지적당 한 세션"]
  R --> V["③ 검증한다<br/>근거 · 인용 · 저자 시선"]
  V --> G["④ 게시 게이트<br/>PG1~PG6"]
  G --> P["pr-eval.sh<br/>GitHub 에 쓰는 유일한 경로"]
  P --> GH[("GitHub PR<br/>리뷰 · 인라인 코멘트")]
```

리뷰 한 판이 한 라운드다. 한 라운드가 끝나면 새로 나온 지적이 얼마나 줄었는지를 보고,
아직 안 닫혔으면 다음 라운드로 이어 간다. 라운드가 거듭될수록 새 지적은 줄고, 대신 앞 라운드가 쓴 글이 틀렸다는 지적이 나온다.

```mermaid
flowchart TD
  A[라운드 시작] --> B[네 세션이 채점]
  B --> C[반박 · 검증]
  C --> E{"닫혔나<br/>커버리지 · 수렴 · 검증"}
  E -- 닫혔다 --> F[게시 전 윤문] --> G[게시하고 끝]
  E -- "아직 · 5라운드 전" --> H[다음 라운드 프롬프트를 쓴다] --> A
  E -- "아직 · 5라운드째" --> I[못 닫은 항목을 요약에 적는다] --> F
```

## 리뷰는 세 번에 나눠 붙는다

한 번 리뷰하고 끝내면 "지적이 실제로 반영됐는지"와 "리뷰가 놓친 게 있는지"를 아무도 확인하지 않는다.
그래서 스테이지를 셋으로 나눴다.

```mermaid
flowchart LR
  S1["Stage 1 · review<br/>무슨 결함이 있나<br/>(리뷰어 지정 시 체인이 시작)"]
  S2["Stage 2 · followup<br/>시킨 것을 했나<br/>반영 · 부분 · 미반영 · 역행"]
  S3["Stage 3 · measured<br/>실측하면 리뷰가 버티나<br/>리뷰가 놓친 자리는 없나"]
  S1 --> S2 --> S3
```

PR #34 에서는 Stage 1 이 코멘트 6건(그중 잘한 점 1건)을 남겼고, 저자가 고친 뒤 Stage 2 가 전건을 판정했다 —
반영 5건, 부분 1건. Stage 3 은 반영한 커밋을 따로 받아 빌드와 테스트를 다시 돌려,
리뷰가 놓쳤던 테스트 사각지대 1건을 새로 찾아냈다. 저자가 그것까지 고치자 Stage 2 를 한 번 더 돌려 전건 반영을 확인했다.
그 뒤 체인이 이 PR 을 이어받아 주석 문구 하나를 더 고치고 Stage 2 를 세 번째로 돌렸고, 리스크 `low` 로 2026-10-02 에 자동 머지했다.

## 기본 동작은 머지까지다

봇을 리뷰어로 지정하면 watcher 가 `scripts/chain.sh` 를 띄우고, 체인이 세 스테이지와 저자 반영을 사람 없이 이어 돈 뒤 머지한다.
다만 머지 직전에 두 게이트를 지나야 한다.

```mermaid
flowchart LR
  C["Stage 1 → 저자 반영 → Stage 2 → Stage 3<br/>→ (저자 반영 → Stage 2)*"] --> MG{"머지 게이트<br/>blocking 0 · 역행 0 · 테스트 통과<br/>head 를 누군가 판정했나"}
  MG -- 아니다 --> B[blocked]
  MG -- "Stage 3 P 에 치명·중대" --> H
  MG -- 그렇다 --> RG{"리스크 게이트<br/>0~100 점"}
  RG -- "low (0–14)" --> M[gh pr merge]
  RG -- "medium 이상 · 점수표를 PR 에 남긴다" --> H["needs-human<br/>사람이 보고 머지한다"]
```

리뷰가 blocking 을 다 풀었어도 인증 코드나 인프라를 건드린 PR 은 사람이 봐야 한다. 리스크 게이트는 **어디를 얼마나 바꿨는지**를
파일 경로와 변경 줄 수로 점수 매겨, 문서·테스트를 동반한 작은 변경만 자동으로 넘긴다. 점수표와 근거는 [`03-risk.md`](03-risk.md) 에 있다.

스크립트는 여기까지 하지만, 지금 이 머신의 launchd watcher 설정은 마지막 `gh pr merge` 를 막아 둔다.
plist 의 PATH 맨 앞에 머지만 거절하는 `gh` 래퍼를 넣어 뒀기 때문에, 그 설정으로 돈 체인은 두 게이트를 다 지나도
`blocked`("gh pr merge 실패")로 끝나고 머지는 사람이 한다. 2026-09-30 의 #43 이 이렇게 멈췄다.
래퍼가 없는 PATH 로 띄운 체인은 그대로 머지한다. 래퍼 위치와 해제 방법은 [`CLAUDE.md`](CLAUDE.md) §5-4 에 있다.

저자 반영은 봇이 아니라 별도의 저자 세션(`/pr-eval-author`)이 맡는다. 이 세션은 사용자 gh 계정으로 PR 브랜치에만 push 하고,
force push·머지·승인은 하지 않는다. 체인에서는 사람에게 물을 수 없으므로, 세션은 규칙 문서가 권하는 쪽으로 정하고
그 결정을 `runs/<저장소>-pr<N>/decisions.md` 에 한 줄씩 남긴다.

지금까지 체인을 거친 PR 은 다섯 건이다.

| PR | 무엇 | Stage 1 코멘트 | 그 뒤 | 체인이 끝난 자리 |
|---|---|---|---|---|
| #34 | 신기술 일괄 저장 리팩토링 | 6 | Stage 3 이 놓친 테스트 1건을 더 찾았다 | 리스크 `low` → 자동 머지 |
| #38 | api-auth Mongo 설정 | 2 | Stage 3 에서 새 결함 0 | 리스크 게이트가 생기기 전에 자동 머지했다. 지금 규칙이면 `medium` 이다 |
| #43 | ECS blue/green 전환 (Terraform) | 5 + 3 | 사람이 체인 밖에서 커밋을 더해 Stage 1·2 를 새 head 로 다시 돌렸다 | 머지 게이트("head 를 아무 단계도 판정하지 않았다")에서 멈췄고, 사람이 머지했다 |
| #33 | 북마크 리포트 API | 16 | 첫 Stage 2 에서 역행 2·미반영 1, 세 번째 Stage 2 에서 전건 반영. Stage 3 이 1건을 더 찾았다 | 리스크 `medium` → `needs-human`, 열려 있다 |
| #36 | 평문 자격증명 제거 · CORS | 5 | Stage 3 에서 새 결함 0 | 리스크 `high` 이고 main 과 충돌해 머지 게이트에서 멈췄다. 열려 있다 |

## 단계를 줄일지는 비용과 통과율로 판단한다

작은 PR 에도 위원·반박자·검증자를 다 돌리니 단계를 줄이자는 얘기가 나온다. 그때 "몇 분 아끼나" 만 보지 않는다.
Anthropic 의 발표 [Tokens Should Have Jobs](https://youtu.be/PXj0p_mW9nI)(AI Engineer)는 두 가지를 말한다.
첫째, 방식끼리 비교할 때는 예산을 같게 두고 결과를 본다. 둘째, 결과가 완전히 맞아야만 쓸모 있는 일이라면
한 번 돌리는 비용을 완전히 맞을 확률로 나눈 값이 진짜 비용이다. 통과율이 40% 면 평균 2.5번, 대략 세 번을 돌려야 한다.
그래서 줄일 후보는 "아끼는 비용" 과 "그 대신 놓치는 결함" 두 칸으로 본다. 리뷰는 신뢰도가 목표라서, 비용 근거 없이 반박자·검증자를 줄이지 않는다.

**Stage 1 에서 비용이 어디에 쓰였나** — 세션 기록의 토큰 사용량을 응답 id 기준으로 한 번씩만 더했다.
한 응답이 기록에 여러 줄로 저장되므로 줄마다 더하면 두세 배로 부풀려진다. 달러는 같은 기록의 `cost-state.totalCostUSD` 값이다.

| | #38 (8줄) | #43 재실행 (809줄) |
|---|---|---|
| 비용 | $14.0 | $17.0 |
| 오케스트레이터 · 검증자 · 위원 몫 | 55% · 26% · 20% | 53% · 28% · 19% |

비중은 토큰을 입력 토큰 값으로 환산해 나눈 것이다(캐시 읽기 0.1, 캐시 쓰기 1.25, 출력 5 로 가격 비율을 어림했다).
위원을 모두 빼도 아끼는 몫은 비용의 20% 안팎이다. 두 실행 모두 치명·중대가 없어 반박자는 돌지 않았다.

**Stage 1 통과율** — 통과는 S3(검증)를 채우고 게시한 것이다.

| 실행 | 라운드 수 | 끝난 상태 |
|---|---|---|
| #33 | 2 | 미충족으로 게시 (그때 S3 정의는 표기오류도 셌다) |
| #34 | 3 | 미충족. 같은 자리 4회 재작성으로 멈춤 (그때 정의. 마지막 검출 3건은 전부 표기오류) |
| #36 | 3 | 3라운드째 충족 |
| #38 | 2 | 미충족. 2라운드에서 고친 사실오류 1건을 다시 검증하지 못한 채 게시 |
| #43 | 2 | 2라운드째 충족 |
| #43 재실행 | 2 | 미충족. 2라운드에서 고친 사실오류(V1 1건·V2 2건)를 다시 검증하지 못한 채 게시 |

2라운드 안에 통과한 것은 6번 중 1번이다. #33·#38·#43 재실행은 당시 라운드 상한이 2여서 3라운드를 돌지 못했으므로,
상한 5 에서 통과율이 얼마인지는 아직 모른다. 이후 실행을 이 표에 한 줄씩 더한다.

**PR 하나에 든 비용 보기** — 체인은 세션마다 비용을 `meta.json` 의 `chain.sessions[]` 에 남긴다.
기록을 못 남겨 다시 돌린 시도(`try` 2 이상)도 들어간다. 끝내지 못한 실행도 비용이다.
다만 세션 결과가 JSON 으로 나오지 않고 죽은 실행은 기록되지 않는다.

```bash
jq -c '.chain.sessions // [] | {total: (map(.cost_usd) | add),
  by_step: (group_by(.step) | map({step: .[0].step, runs: length, cost: (map(.cost_usd) | add)}))}' \
  tools/pr-eval/runs/<repo>-pr<N>/meta.json
```

`claude -p` 가 내는 `total_cost_usd` 가 하위 에이전트 비용까지 담는지는 아직 확인하지 않았다. 첫 체인 한 건에서 세션 기록의 값과 맞춰 본다.
사람이 연 대화형 세션의 비용은 여기에 남지 않는다.

## 봇이 못 하는 일

리뷰 봇에게 코드를 고칠 권한을 주면 봇은 리뷰어가 아니라 또 하나의 작성자가 된다. 그래서 처음부터 막아 뒀다.

- **코드를 고치지 않는다.** 봇 계정의 저장소 권한은 읽기 전용이다. 문서 약속이 아니라 GitHub 권한으로 막는다.
  체인에서 코드를 고치는 쪽은 위에서 말한 저자 세션이다.
- **승인도 변경요청도 하지 않는다.** 코멘트만 단다. 봇이 머지 게이트를 쥐면 사람이 봇을 통과시키려고 리뷰를 왜곡하게 된다.
  머지는 체인이 사용자 계정으로 한다. 머지해도 되는지는 세션의 말이 아니라 기록·GitHub 상태·리스크 점수로 판정한다.
- **봇 토큰을 세션이 갖지 않는다.** GitHub 에 쓰는 것은 스크립트 하나뿐이고, 토큰은 그 안에서만 읽힌다.
- **확인하지 못한 것은 게시하지 않는다.** 근거를 못 댄 의심은 작업 폴더에만 남는다.
- **저장소 소유자가 연 PR 만 다룬다.** 저장소가 public 이라, 남이 연 PR 에 봇을 지정해도 watcher 가 건너뛴다(`authorAssociation` 이 `OWNER` 가 아니면 skip). 그런 PR 은 사람이 `/pr-eval` 로 직접 부른다.

## 더 볼 것

| 파일 | 무엇 |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | 하니스 운영 문서 — 스크립트 계약 · 산출물 규격 · 설치 · 실측으로 확인한 것 |
| [`00-criteria.md`](00-criteria.md) | 세 스테이지 공통 규칙 — 무효 조건 · 등급 · 출처 등급 · 코멘트 규격 |
| [`01-stages.md`](01-stages.md) | 스테이지별 규격 · 게시 게이트 · 종료 조건 · 지표 |
| [`02-judges.md`](02-judges.md) | 채점 세션 · 반박자 · 검증자에게 주는 지시문 |
| [`03-risk.md`](03-risk.md) | 자동 머지 리스크 점수 — 점수표 · 등급별 처리 · 보정 기록 |
| [`profiles/`](profiles/) | 저장소별 리뷰 축 정의와 축마다 "볼 것 / 보지 않을 것" |
| [`_memory/learnings.md`](_memory/learnings.md) | PR 을 넘어 남는 학습. 라운드마다 한 줄씩 쌓인다 |
