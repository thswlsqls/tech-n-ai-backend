#!/usr/bin/env bash
# install-entrypoints.sh — .claude/ 는 gitignore 되므로 실체는 tools/pr-eval/ 에 두고
# 진입점(.claude/commands/pr-eval.md · .claude/agents/pr-eval-judge.md)만 여기서 재생성한다.
# 다른 머신에서 clone 한 뒤 한 번 돌리면 된다.
set -euo pipefail

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$HARNESS_DIR/../.." && pwd)"
CMD="$REPO_ROOT/.claude/commands/pr-eval.md"
AGENT="$REPO_ROOT/.claude/agents/pr-eval-judge.md"

mkdir -p "$(dirname "$CMD")" "$(dirname "$AGENT")"

cat > "$CMD" <<'CMDEOF'
---
description: PR eval harness 를 돌린다. 인자 — <stage> <저장소명> <PR번호> [--round N] [--approve]
---

`/pr-eval $1 $2 $3` — stage 는 `stage1`·`stage2`·`stage3` 중 하나, `$2` 는 저장소명(= 프로파일 키), `$3` 은 PR 번호.

## 맨 앞에서 지킬 것

1. **게시는 `tools/pr-eval/scripts/pr-eval.sh` 로만 한다.** `gh api` 로 직접 쓰지 않는다.
   봇 토큰은 그 스크립트가 자기 안에서 읽는다. 세션이 직접 게시하면 사용자 계정으로 리뷰가 올라간다.
2. **평가 대상을 한 글자도 고치지 않는다.** 쓰기가 허용된 곳은 `tools/pr-eval/runs/<repo>-pr<N>/` 아래와
   `tools/pr-eval/_memory/learnings.md` 뿐이다.
3. **점수를 쓰지 않는다.** 등급 넷(치명·중대·경미·사소)만 쓴다.

## 절차

### 0. 규칙을 읽는다 (순서대로, 전부)

`tools/pr-eval/00-criteria.md` → `01-stages.md` → `02-judges.md` → `profiles/<프로파일>.md`
→ `_memory/learnings.md` 전부 → `runs/<repo>-pr<N>/frozen.md` 전부.
직전 라운드가 있으면 `runs/<repo>-pr<N>/rounds/` 의 마지막 기록도 읽는다.

### 1. 락과 상태를 확인한다

```bash
tools/pr-eval/scripts/pr-eval.sh init  $2 $3
tools/pr-eval/scripts/pr-eval.sh lock  $2 $3 <1|2|3>
```

`init` 은 **어느 스테이지에서 불러도 안전하다.** Stage 1 이 이미 게시했으면 `base_sha`·`eval_sha` 를 보존한다 —
Stage 2·3 시점의 head 는 저자의 반영 커밋이라, 덮으면 Stage 1 이 무엇을 판정했는지가 사라진다.
Stage 1 을 새 head 로 다시 돌릴 때만 `init $2 $3 --reset-eval` 를 쓴다.

- `lock` 이 5 로 끝나면 다른 세션이 돌고 있다. **멈추고 사람에게 보고한다.**
- `lock` 이 3 으로 끝나면 직전 스테이지 완료 기록이 없다는 뜻이다(스크립트가 막는다).
  게시하지 않고 이유를 보고하고 멈춘다.
- Stage 1 은 `precheck` 을 돌린다. 대형 PR 컷에 걸리고 `--approve` 가 없으면
  `status` 를 `보류(대형PR)` 로 두고 PR 에 한 줄 코멘트를 **1회만** 달고 끝낸다.

### 2. 기준 SHA 를 고정한다

```bash
tools/pr-eval/scripts/pr-eval.sh sha $2 $3
```

**라운드를 시작할 때마다 직접 확인한다.** Stage 1 라운드 도중 head 가 움직였으면
그 라운드를 무효로 하고 새 `eval_sha` 로 재시작한다.

그리고 **앵커를 달 수 있는 줄 범위를 받아 위원 프롬프트에 싣는다.**

```bash
tools/pr-eval/scripts/pr-eval.sh ranges $2 $3 <기준SHA>
```

**앵커가 하나라도 이 범위 밖이면 GitHub 이 리뷰를 통째로 422 로 되돌린다**(실측).
코멘트 하나만 빠지는 게 아니라 리뷰가 아예 안 올라간다. PG1 로 사후에 걸러 내기 전에 위원에게 먼저 범위를 준다.

### 3. Phase 0 ~ 6 을 `01-stages.md` §3 표대로 돈다

- 위원 4인은 **한 메시지에서 동시에** 띄운다. 순차로 돌리면 뒤 위원이 앞 결과에 오염된다.
- 규칙·무효 조건·동결 목록·축 절은 **프롬프트 본문에 붙여 넣는다.** 링크로 주면 안 읽는다.
  평가 대상 저장소 안의 파일만 절대 경로 + "첫 행동으로 `Read` 하라" 로 갈음한다.
- **적대적 검증을 건너뛰지 않는다.** 반박을 넘긴 지적만 확정한다.
- 예산(`01-stages.md` §2)을 넘기지 않는다. 넘긴 것은 `미검증` 표기와 함께 이월한다.
- V1·V2·V3 도 한 메시지에서 동시에 띄운다.

### 4. 산출물을 쓴다

`runs/<repo>-pr<N>/outputs/<stage>/` 에 둘을 쓴다 (`<stage>` = `stage1`·`stage2`·`stage3`).
**세 스테이지가 한 run 디렉터리를 쓰므로 스테이지별 폴더에 넣는다** — 한자리에 몰아 쓰면 Stage 3 위원이
origin(P/Q/N)을 가를 때 읽는 Stage 1 산출물이 덮인다. **둘의 내용이 어긋나면 PG2 에서 걸린다.**

- `summary.md` — 리뷰 요약 본문 (총평 · 잘한 점 · blocking 순서표 · 실측/추정 구분 한 줄)
- `comments.json` — inline 코멘트 배열. 항목마다
  `{"path","line","side":"RIGHT","body","code","axis","grade"}`.
  `line` 은 **새 파일 기준 줄 번호**다.

### 4-5. 게시 전 윤문 — GitHub 에 올릴 텍스트 전부

**게시하기 직전에** 이번에 올릴 텍스트를 줄인다. 규칙은 `00-criteria.md` §6 "게시 전 윤문",
절차와 게이트는 `01-stages.md` §3-4 · PG6 에 있다.

| Stage | 언제 | 무엇을 |
|---|---|---|
| 1 | **마지막 라운드**에서 한 번 (중간 라운드에서는 하지 않는다) | `comments.json` · `summary.md` |
| 2 | `reply`·`patch` 를 부르기 직전 | `replies/*.md` · `patches/*.md` |
| 3 | `reply`·`post-review` 를 부르기 직전 | `replies/*.md` · `comments.json` · `summary.md` |

```bash
cd tools/pr-eval/runs/<repo>-pr<N>/outputs
mkdir -p pre-polish
cp -R comments.json summary.md replies patches pre-polish/ 2>/dev/null || true
```

사본을 뜬 뒤 원본을 고쳐 쓴다.

- 지운다 — 같은 말의 반복 · diff 에 보이는 코드 재인용 · `~일 수 있습니다` 류 완충어 ·
  저자가 아는 배경 설명 · 하니스 내부 용어(라운드·위원·반박자·예산) · 강조 남발
- 남긴다 — **첫 줄 · 앵커 · 등급 · 축 · 근거의 `파일:줄` · B 등급 인용문 축자 · 수치**,
  그리고 4단의 **②그래서 생기는 일**과 **④방향**. 둘이 빠지면 저자가 할 일이 사라진다
- Stage 2·3 은 **판정 단어를 바꾸지 않는다** — `반영`·`부분`·`미반영`·`역행`, `P`·`Q`·`N` 은 척도다
- **코멘트·reply 를 합치거나 지우거나 새로 만들지 않는다.** 건수는 그대로다 — 문장만 손댄다

고친 뒤 PG6 으로 대조한다. JSON 과 마크다운을 따로 본다.

```bash
jq -s 'map(map({code, path, line, side:(.side//"RIGHT"), axis, grade,
                head:(.body | split("\n")[0])})) | .[0] == .[1]' \
   pre-polish/comments.json comments.json

tok() { grep -ohE '[A-Za-z0-9_./-]+\.[a-z]+:[0-9]+|[0-9]+(\.[0-9]+)?' "$@" | sort -u; }
comm -23 <(tok pre-polish/summary.md pre-polish/replies/*.md pre-polish/patches/*.md) \
         <(tok summary.md replies/*.md patches/*.md)
```

`false` 가 나오거나 토큰이 출력되면 그 건을 사본에서 되돌리고 다시 줄인다.
이어서 문장에 기대는 게이트를 다시 본다 — Stage 1 은 PG2·PG5, Stage 2·3 은 PG3 과 산출물 간 교차 대조.
before → after 분량과 되돌린 건수를 기록에 적는다.

### 5. 게이트를 통과시키고 게시한다

```bash
tools/pr-eval/scripts/pr-eval.sh gate1 $2 $3 <기준SHA> runs/<repo>-pr<N>/outputs/$1/comments.json
tools/pr-eval/scripts/pr-eval.sh post-review $2 $3 <기준SHA> \
    runs/<repo>-pr<N>/outputs/$1/summary.md runs/<repo>-pr<N>/outputs/$1/comments.json $1
```

`post-review` 의 마지막 인자는 `stage1`·`stage2`·`stage3` 중 이번 스테이지다. 척도 밖 값은 스크립트가 거부한다.
PG1 이 되돌린 앵커는 요약 본문으로 옮긴다. PG2·PG3·PG4·PG5 는 사람 손이 아니라 세션이 대조한다.
Stage 2·3 의 스레드 reply·정정은 `reply`·`patch` 서브커맨드를 쓴다. **이때도 4-5 를 먼저 거친다** —
`reply`·`patch`·`post-review` 는 전부 GitHub 에 쓰는 호출이다.

### 6. 기록하고 락을 푼다

**`rounds/round-NN.md` 는 몰아 쓰지 말고 단계마다 이어 붙인다** — 위원 결과를 받으면 그 자리에서 적고,
반박·PG4 결과도 나오는 대로 덧붙인다. 마지막에 몰아 쓰면 세션이 죽었을 때 심사 근거가 통째로 사라진다
(실측). 들어갈 항목은 `01-stages.md` §11 에 있다.
Phase 5 신호표를 한 줄씩 대조하고, 고칠 게 없으면 **"이번 라운드에는 하니스 결함 없음"** 이라고 적는다.

```bash
tools/pr-eval/scripts/pr-eval.sh unlock $2 $3
```

## Stage 1 라운드 루프

```
라운드 NN 종료
 ├ S1~S3 충족           → 4-5 윤문 → 게시하고 세션 종료
 ├ 미충족 & NN < 2       → prompts/round-(NN+1).md 를 쓰고 같은 세션에서 이어간다
 └ 미충족 & NN = 2       → 미충족 항목을 요약에 적고 → 4-5 윤문 → 게시
```

**어느 쪽으로 끝나든 게시 직전에 4-5 를 거친다.**

**수렴을 게시 조건으로 걸지 않는다.**
CMDEOF

cat > "$AGENT" <<'AGENTEOF'
---
name: pr-eval-judge
description: PR eval harness 의 평가위원·반박자·문서 검증자. 오케스트레이터가 축 절과 규칙 전문을 프롬프트 본문으로 넘긴다. 자기 축 하나만 보고 결과를 텍스트로 반환한다.
tools: Read, Grep, Glob, Bash, mcp__context7__resolve-library-id, mcp__context7__query-docs
---

너는 PR 을 리뷰하는 리뷰어다. **자기 축 하나만 본다.**

## 불변식 — 다른 무엇보다 먼저 지킨다

> **너는 아무 파일도 만들거나 고치지 않는다.** 평가 대상 코드는 물론이고 리뷰 문서도 네가 쓰지 않는다.
> 채점 결과를 텍스트로 반환하는 것이 전부다.
>
> `Bash` 는 `grep`·`wc`·`jq`·`git diff` 같은 **조회에만** 쓴다.
> 리다이렉션(`>`·`>>`)·`sed -i`·`mv`·`rm`·`git commit`·`git push` 를 쓰지 마라.
>
> **GitHub 에 아무것도 쓰지 마라.** 게시는 오케스트레이터가 `pr-eval.sh` 로만 한다.

## 어떻게 답하나

- 프롬프트 본문에 실려 온 **무효 조건·등급·출처 등급·유형별 조정표·출력 형식**을 그대로 따른다.
  본문에 없는 규칙을 기억으로 끌어오지 마라.
- **점수·감점 숫자를 쓰지 마라.** 등급은 `치명`·`중대`·`경미`·`사소` 넷뿐이다.
  척도에 없는 등급을 만들지 마라.
- 모든 지적에 `파일:줄` 앵커를 단다. **그 줄을 실제로 열어 확인한 뒤에** 적는다.
- 숫자는 `grep -c` 로 센다. 없다는 것을 확인할 때는 넓은 패턴으로 먼저 훑고 좁혀 간다.
- 공식 문서를 근거로 들 때는 context7 로 조회해 **인용문을 그대로 옮겨 적고**,
  그 인용문이 네 결론까지 말하는지 따로 확인한다. 기억으로 인용하지 마라.
- 확신이 없으면 지적하지 말고 `미확인 우려` 에 적어라. 그건 게시되지 않는다.
- **프롬프트가 주는 "아직 안 본 각도" 는 가설이지 지시가 아니다.**
  성립하지 않으면 성립하지 않는다고 답하라. 없는 결함을 만들지 마라.
- 출력 형식의 표 컬럼을 바꾸지 마라. 빈 칸은 `-` 로 채운다.

## 반박자로 불릴 때

판정은 넷뿐이다 — `유지` / `등급 하향` / `근거 축소` / `반박됨`.
**확실하지 않으면 `반박됨` 으로 판정하라.**

## 문서 검증자(V1·V2·V3)로 불릴 때

보고서 첫 줄에 **자기 검증 범위**(앵커 전수인지 표본인지, 몇 개를 봤는지)를 적는다.
안 적으면 라운드 간 비교가 성립하지 않는다.
AGENTEOF

echo "생성:"
echo "  $CMD"
echo "  $AGENT"
