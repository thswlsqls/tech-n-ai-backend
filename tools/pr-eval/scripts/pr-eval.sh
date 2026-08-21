#!/usr/bin/env bash
# pr-eval.sh — PR eval harness 의 유일한 GitHub 게시 경로.
# 세션은 봇 토큰을 갖지 않는다. 이 스크립트가 자기 안에서 읽는다.
# 계약(서브커맨드·종료 코드)은 tools/pr-eval/README.md §3 에 있다.
set -euo pipefail

OWNER="${PR_EVAL_OWNER:-thswlsqls}"
BOT_ENV="${PR_EVAL_BOT_ENV:-$HOME/.config/pr-eval/bot.env}"
HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNS_DIR="$HARNESS_DIR/runs"
LOCK_TTL_SEC=10800   # 3시간
BIG_PR_FILES=50
BIG_PR_LINES=3000

# 종료 코드: 0 성공 · 1 사용법/인자 · 2 환경(토큰·meta 없음) · 3 게이트 위반 · 4 API 실패 · 5 락 점유
E_USAGE=1; E_ENV=2; E_GATE=3; E_API=4; E_LOCK=5

die() { echo "pr-eval: $2" >&2; exit "$1"; }

TMP=""
cleanup() { [ -n "${TMP:-}" ] && rm -rf "$TMP"; return 0; }
trap cleanup EXIT
mktmp() { cleanup; TMP="$(mktemp -d)"; }

usage() {
  cat >&2 <<'USAGE'
usage: pr-eval.sh <subcommand> <repo> <pr> [args]

  조회 (봇 토큰 불필요)
    sha        <repo> <pr>                   현재 head SHA 를 출력한다
    meta       <repo> <pr>                   meta.json 을 출력한다
    precheck   <repo> <pr>                   대형 PR 컷(파일 50 / 줄 3000) 을 판정한다
    ranges     <repo> <pr> <sha>            inline 앵커를 달 수 있는 줄 범위를 낸다 (위원 프롬프트용)\n    gate1      <repo> <pr> <sha> <comments.json>   PG1 — 앵커가 diff 안인지 검사한다

  상태
    init       <repo> <pr> [--reset-eval]    runs/<repo>-pr<N>/ 와 meta.json 을 만든다
                                             (--reset-eval: Stage 1 재실행 시에만 eval_sha 를 head 로 올린다)
    lock       <repo> <pr> <stage>           락을 획득한다 (점유 중이면 5)
    unlock     <repo> <pr>
    status     <repo> <pr> <값>
    attempt    <repo> <pr> inc|reset

  게시 (봇 토큰 필요)
    post-review <repo> <pr> <sha> <summary.md> <comments.json> [stage1|stage2|stage3]
    reply       <repo> <pr> <comment_id> <body.md>
    patch       <repo> <pr> <comment_id> <body.md>
    patch-review <repo> <pr> <review_id> <body.md>   게시한 리뷰 요약 본문을 고친다
    comment     <repo> <pr> <body.md>        PR 본문에 일반 코멘트 1건
USAGE
  exit "$E_USAGE"
}

profile_of() {
  case "$1" in
    tech-n-ai-backend)  echo backend ;;
    tech-n-ai-frontend) echo frontend ;;
    *) die "$E_USAGE" "프로파일을 모르는 저장소다: $1 (profiles/ 에 정의가 없다)" ;;
  esac
}

run_dir() { echo "$RUNS_DIR/$1-pr$2"; }
meta_path() { echo "$(run_dir "$1" "$2")/meta.json"; }

need_meta() {
  [ -f "$(meta_path "$1" "$2")" ] || die "$E_ENV" "meta.json 이 없다. 먼저 init 을 돌려라: $(meta_path "$1" "$2")"
}

# meta.json 을 jq 프로그램으로 갱신한다 (임시파일 → mv, 부분 기록 방지)
meta_update() {
  local repo="$1" pr="$2" prog="$3"; shift 3
  local m; m="$(meta_path "$repo" "$pr")"
  local tmp="$m.tmp.$$"
  jq "$@" "$prog" "$m" > "$tmp" && mv "$tmp" "$m"
}

load_bot_token() {
  [ -f "$BOT_ENV" ] || die "$E_ENV" "봇 토큰 파일이 없다: $BOT_ENV (README §2 참고)"
  # shellcheck disable=SC1090
  set +u; . "$BOT_ENV"; set -u
  local tok="${PR_EVAL_BOT_TOKEN:-${GH_TOKEN:-}}"
  [ -n "$tok" ] || die "$E_ENV" "$BOT_ENV 에 PR_EVAL_BOT_TOKEN 또는 GH_TOKEN 이 없다"
  export GH_TOKEN="$tok"
  unset GITHUB_TOKEN || true
}

api() { gh api "$@" || die "$E_API" "gh api 실패: $*"; }

now_iso() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# 락에 적을 pid — 이 스크립트는 즉시 끝나므로 자기 pid 를 적으면 다음 확인에서 바로 죽은 락이 된다.
# 조상 중 claude 세션 프로세스를 찾아 그 pid 를 쓴다. 없으면(=watcher 가 부른 경우) 직속 부모를 쓴다.
owner_pid() {
  if [ -n "${PR_EVAL_LOCK_PID:-}" ]; then echo "$PR_EVAL_LOCK_PID"; return; fi
  local p="$PPID" name
  while [ "${p:-0}" -gt 1 ]; do
    name="$(ps -o comm= -p "$p" 2>/dev/null | sed 's|.*/||' || true)"
    [ "$name" = "claude" ] && { echo "$p"; return; }
    p="$(ps -o ppid= -p "$p" 2>/dev/null | tr -d ' ')"
  done
  echo "$PPID"
}

# ---------- 조회 ----------

cmd_sha() { api "repos/$OWNER/$1/pulls/$2" --jq '.head.sha'; }

cmd_meta() { need_meta "$1" "$2"; cat "$(meta_path "$1" "$2")"; }

cmd_precheck() {
  local repo="$1" pr="$2" j
  j="$(api "repos/$OWNER/$repo/pulls/$pr")"
  local files add del
  files="$(jq -r '.changed_files' <<<"$j")"
  add="$(jq -r '.additions' <<<"$j")"
  del="$(jq -r '.deletions' <<<"$j")"
  local lines=$(( add + del ))
  echo "changed_files=$files diff_lines=$lines (cut: files>$BIG_PR_FILES or lines>$BIG_PR_LINES)"
  if [ "$files" -gt "$BIG_PR_FILES" ] || [ "$lines" -gt "$BIG_PR_LINES" ]; then
    echo "대형 PR 컷에 걸린다. status 를 보류(대형PR) 로 두고 게시하지 않는다."
    return "$E_GATE"
  fi
  echo "통과"
}

# 게시 기준 SHA 의 diff 에서 RIGHT 측 hunk 범위를 뽑아 "파일<TAB>시작<TAB>줄수" 로 낸다.
# gate1(사후 검사)과 ranges(위원 프롬프트에 실을 사전 정보)가 같은 것을 본다.
emit_ranges() {
  local repo="$1" sha="$2" out="$3" base
  base="$(jq -r '.base_sha' "$(meta_path "$repo" "$4")")"
  [ -n "$base" ] && [ "$base" != "null" ] || die "$E_ENV" "meta.json 에 base_sha 가 없다"
  # --paginate 를 붙이지 않는다 — compare 의 files[] 는 단일 응답이고, 붙이면 2페이지부터 빈 배열이 딸려 나온다.
  api "repos/$OWNER/$repo/compare/$base...$sha" > "$out/compare.json"
  jq -r '
    .files[] | select(.patch != null) | . as $f
    | ($f.patch | split("\n")[] | select(startswith("@@"))
       | capture("@@ -[0-9]+(,[0-9]+)? \\+(?<s>[0-9]+)(,(?<c>[0-9]+))?"))
    | "\($f.filename)\t\(.s)\t\(.c // "1")"
  ' "$out/compare.json" > "$out/ranges.tsv"
}

# 위원 프롬프트에 실을 "inline 코멘트를 달 수 있는 줄" 목록.
# 앵커 하나가 diff 밖이면 리뷰가 통째로 422 라 사전에 주는 편이 사후 검사보다 싸다.
cmd_ranges() {
  local repo="$1" pr="$2" sha="$3"
  need_meta "$repo" "$pr"
  mktmp; local tmp="$TMP"
  emit_ranges "$repo" "$sha" "$tmp" "$pr"
  echo "# inline 앵커 허용 범위 (기준 SHA $sha) — 파일 / 시작줄 / 끝줄(포함)"
  awk -F'\t' '{printf "%s\t%d-%d\n", $1, $2, $2+$3-1}' "$tmp/ranges.tsv"
  local n; n="$(jq -r '[.files[] | select(.patch == null)] | length' "$tmp/compare.json")"
  [ "$n" = "0" ] || { echo "# patch 가 없어 앵커를 달 수 없는 파일 $n 건:"; \
    jq -r '.files[] | select(.patch == null) | "#   " + .filename' "$tmp/compare.json"; }
}

# PG1 — 앵커가 게시 기준 SHA 의 diff 안인가
cmd_gate1() {
  local repo="$1" pr="$2" sha="$3" cfile="$4"
  [ -f "$cfile" ] || die "$E_USAGE" "코멘트 파일이 없다: $cfile"
  jq -e 'type == "array"' "$cfile" >/dev/null || die "$E_USAGE" "$cfile 은 JSON 배열이어야 한다"
  need_meta "$repo" "$pr"
  local base; base="$(jq -r '.base_sha' "$(meta_path "$repo" "$pr")")"
  [ -n "$base" ] && [ "$base" != "null" ] || die "$E_ENV" "meta.json 에 base_sha 가 없다"

  mktmp; local tmp="$TMP"
  emit_ranges "$repo" "$sha" "$tmp" "$pr"

  # patch 가 없는 파일(대용량)은 앵커 대상에서 제외한다.
  jq -r '.files[] | select(.patch == null) | .filename' "$tmp/compare.json" > "$tmp/nopatch.txt"
  if [ -s "$tmp/nopatch.txt" ]; then
    echo "주의 — patch 가 없어 앵커 대상에서 제외한 파일:" >&2
    sed 's/^/  /' "$tmp/nopatch.txt" >&2
  fi
  local nfiles; nfiles="$(jq -r '.files | length' "$tmp/compare.json")"
  [ "$nfiles" -lt 300 ] || echo "주의 — compare files 가 $nfiles 건이다. 300 에서 절단됐을 수 있다." >&2

  local bad=0 total=0
  while IFS=$'\t' read -r p l side; do
    total=$((total+1))
    if [ "$side" != "RIGHT" ]; then
      echo "PG1 위반 — side 가 RIGHT 가 아니다: $p:$l (side=$side)"; bad=$((bad+1)); continue
    fi
    if ! awk -F'\t' -v P="$p" -v L="$l" '
        $1==P && L+0 >= $2+0 && L+0 < $2+$3 { found=1 }
        END { exit !found }' "$tmp/ranges.tsv"; then
      echo "PG1 위반 — diff 밖 앵커: $p:$l"; bad=$((bad+1))
    fi
  done < <(jq -r '.[] | [.path, (.line|tostring), (.side // "RIGHT")] | @tsv' "$cfile")

  if [ "$bad" -gt 0 ]; then
    echo "PG1 실패 — $total 건 중 $bad 건이 diff 밖이다. 요약 본문으로 옮겨라."
    return "$E_GATE"
  fi
  echo "PG1 통과 — 앵커 $total 건 전부 diff 안이다 (기준 SHA $sha)"
}

# ---------- 상태 ----------

cmd_init() {
  local repo="$1" pr="$2" reset_eval=0 prof d
  case "${3:-}" in
    "")            ;;
    --reset-eval)  reset_eval=1 ;;
    *) die "$E_USAGE" "init 이 아는 플래그는 --reset-eval 뿐이다: $3" ;;
  esac
  prof="$(profile_of "$repo")"
  d="$(run_dir "$repo" "$pr")"
  mkdir -p "$d/rounds" "$d/prompts" "$d/outputs/stage1" "$d/outputs/stage2" "$d/outputs/stage3"
  [ -f "$d/frozen.md" ] || printf '# 동결 — 이 PR 에서 확정된 사실\n\n뒤집으려면 *그 확정이 틀렸다는 새 근거*를 대야 한다.\n\n| # | 확정된 사실 | 근거 | 동결한 라운드 |\n|---|---|---|---|\n' > "$d/frozen.md"

  local j base head
  j="$(api "repos/$OWNER/$repo/pulls/$pr")"
  base="$(jq -r '.base.sha' <<<"$j")"
  head="$(jq -r '.head.sha' <<<"$j")"

  if [ -f "$d/meta.json" ]; then
    # 재실행. Stage 1 이 이미 게시했으면 그 리뷰가 매달린 기준 SHA 이므로 건드리지 않는다 —
    # Stage 2 가 last_judged_sha 로 쓰는 값이고, 덮으면 무엇을 판정한 리뷰인지 알 수 없게 된다.
    # Stage 1 을 새 head 로 다시 돌릴 때만 --reset-eval 로 갱신한다.
    local posted; posted="$(jq -r '[.stage1[]?.review_id | select(. != null)] | length' "$d/meta.json")"
    if [ "$posted" -gt 0 ] && [ "$reset_eval" = 0 ]; then
      echo "meta.json 유지 — Stage 1 게시 기록 $posted 건이 있어 base_sha·eval_sha 를 보존한다."
      echo "  eval_sha=$(jq -r '.eval_sha' "$d/meta.json")  (현재 head=$head)"
      echo "  Stage 1 을 현재 head 로 다시 돌리려면: init $repo $pr --reset-eval"
    else
      meta_update "$repo" "$pr" '.base_sha=$b | .eval_sha=$h' --arg b "$base" --arg h "$head"
      echo "meta.json 갱신 (기존 기록 보존): $d/meta.json"
    fi
  else
    jq -n --arg o "$OWNER" --arg r "$repo" --argjson p "$pr" \
          --arg b "$base" --arg h "$head" --arg prof "$prof" '
      {owner:$o, repo:$r, pr:$p, base_sha:$b, eval_sha:$h, profile:$prof,
       lock:null, attempts:0, stage1:[], stage2:[], stage3:null, status:"대기"}
    ' > "$d/meta.json"
    echo "생성: $d/meta.json"
  fi
}

# 스테이지 순서 게이트 — Stage N 은 Stage N-1 완료 기록이 있어야 돈다.
# 모든 스테이지가 lock 을 지나므로 여기서 한 번만 막으면 된다(문서 규칙을 스크립트로 강제).
check_stage_order() {
  local m="$1" stage="$2" n1 n2
  n1="$(jq -r '[.stage1[]?.review_id | select(. != null)] | length' "$m")"
  n2="$(jq -r '[.stage2[]?] | length' "$m")"
  case "$stage" in
    2) [ "$n1" -gt 0 ] || die "$E_GATE" "Stage 1 완료 기록이 없다. Stage 2 는 돌지 않는다" ;;
    3) [ "$n1" -gt 0 ] || die "$E_GATE" "Stage 1 완료 기록이 없다. Stage 3 은 돌지 않는다"
       [ "$n2" -gt 0 ] || die "$E_GATE" "Stage 2 판정 기록이 없다. Stage 3 은 돌지 않는다" ;;
  esac
}

cmd_lock() {
  local repo="$1" pr="$2" stage="$3"; need_meta "$repo" "$pr"
  local m; m="$(meta_path "$repo" "$pr")"
  check_stage_order "$m" "$stage"
  local pid started
  pid="$(jq -r '.lock.pid // empty' "$m")"
  started="$(jq -r '.lock.started_at // empty' "$m")"
  if [ -n "$pid" ]; then
    local alive=0 fresh=0
    kill -0 "$pid" 2>/dev/null && alive=1
    # started_at 이 TTL 안인가
    local s_epoch now_epoch
    s_epoch="$(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$started" +%s 2>/dev/null || echo 0)"
    now_epoch="$(date -u +%s)"
    [ "$s_epoch" -gt 0 ] && [ $((now_epoch - s_epoch)) -lt "$LOCK_TTL_SEC" ] && fresh=1
    if [ "$alive" = 1 ] && [ "$fresh" = 1 ]; then
      echo "락 점유 중 — pid=$pid stage=$(jq -r '.lock.stage' "$m") started=$started"
      return "$E_LOCK"
    fi
    echo "낡은 락을 지운다 (pid alive=$alive, ttl fresh=$fresh)" >&2
  fi
  local owner; owner="$(owner_pid)"
  meta_update "$repo" "$pr" '.lock={pid:$pid, started_at:$at, stage:$st}' \
    --argjson pid "$owner" --arg at "$(now_iso)" --argjson st "$stage"
  echo "락 획득 — pid=$owner stage=$stage"
}

cmd_unlock() { need_meta "$1" "$2"; meta_update "$1" "$2" '.lock=null'; echo "락 해제"; }

cmd_status() {
  need_meta "$1" "$2"
  case "$3" in
    대기|진행|완료|"보류(대형PR)"|"보류(실패)") ;;
    *) die "$E_USAGE" "status 값이 척도 밖이다: $3" ;;
  esac
  meta_update "$1" "$2" '.status=$s' --arg s "$3"; echo "status=$3"
}

cmd_attempt() {
  need_meta "$1" "$2"
  case "$3" in
    inc)   meta_update "$1" "$2" '.attempts = (.attempts // 0) + 1' ;;
    reset) meta_update "$1" "$2" '.attempts = 0' ;;
    *) die "$E_USAGE" "attempt 는 inc 또는 reset 이다" ;;
  esac
  jq -r '.attempts' "$(meta_path "$1" "$2")"
}

# ---------- 게시 ----------

cmd_post_review() {
  local repo="$1" pr="$2" sha="$3" sfile="$4" cfile="$5" stage="${6:-stage1}"
  case "$stage" in
    stage1|stage2|stage3) ;;
    *) die "$E_USAGE" "stage 는 stage1|stage2|stage3 중 하나다: $stage" ;;
  esac
  [ -f "$sfile" ] || die "$E_USAGE" "요약 파일이 없다: $sfile"
  [ -f "$cfile" ] || die "$E_USAGE" "코멘트 파일이 없다: $cfile"
  need_meta "$repo" "$pr"

  # 게시 직전에 PG1 을 한 번 더 돌린다 — 세션이 건너뛰어도 여기서 막힌다.
  cmd_gate1 "$repo" "$pr" "$sha" "$cfile" || return "$E_GATE"

  # PG5 일부(기계로 셀 수 있는 것)
  if [ "$stage" = "stage1" ]; then
    grep -q 'praise' "$sfile" || jq -e '[.[] | select(.body | test("praise"))] | length > 0' "$cfile" >/dev/null \
      || die "$E_GATE" "PG5 위반 — praise 코멘트가 없다"
  fi
  jq -e '[.[] | select((.code|not) or (.axis|not) or (.body|not))] | length == 0' "$cfile" >/dev/null \
    || die "$E_GATE" "PG5 위반 — code/axis/body 가 빠진 코멘트가 있다"
  # 축은 코드만이 아니라 이름까지 본문에 있어야 한다 — 저자가 프로파일을 열지 않고 읽을 수 있어야 한다.
  local noname; noname="$(jq -r '[.[] | select((.body | test("R-[A-H] \\(")) | not) | .code] | join(", ")' "$cfile")"
  [ -z "$noname" ] || die "$E_GATE" "PG5 위반 — 축 이름이 없다(\`R-x (이름)\` 형식이어야 한다): $noname"
  # 요약에는 축 범례표를 한 번 싣는다 — blocking 순서표 칸에는 코드만 들어가기 때문이다.
  if [ "$stage" = "stage1" ]; then
    grep -qE '`R-[A-H]`' "$sfile" || die "$E_GATE" "PG5 위반 — 요약에 축 범례표가 없다 (00-criteria.md §6)"
  fi

  load_bot_token
  mktmp; local tmp="$TMP"

  # 게시에는 path/line/side/body 만 보낸다. code/axis/grade 는 meta 기록용이다.
  jq -n --arg cid "$sha" --rawfile body "$sfile" --slurpfile c "$cfile" '
    {commit_id:$cid, event:"COMMENT", body:$body,
     comments: ($c[0] | map({path, line, side:(.side // "RIGHT"), body}))}
  ' > "$tmp/payload.json"

  local resp; resp="$(gh api "repos/$OWNER/$repo/pulls/$pr/reviews" --input "$tmp/payload.json")" \
    || die "$E_API" "리뷰 게시 실패. 422 면 기준 SHA($sha)에 line 앵커가 안 붙는 경우를 먼저 의심하라 (01-stages.md §7)"
  local review_id; review_id="$(jq -r '.id' <<<"$resp")"

  # 게시된 inline 코멘트의 id 를 되받아 (path,line) 으로 code/axis/grade 를 붙인다.
  gh api --paginate "repos/$OWNER/$repo/pulls/$pr/comments" \
    | jq -s 'add | map(select(.pull_request_review_id == '"$review_id"'))
             | map({id, path, line, body})' > "$tmp/posted.json"

  # 본문으로 먼저 맞춘다 — 같은 자리에 코멘트가 둘이면 (path,line) 만으로는 갈리지 않고
  # id 가 교차로 붙어 Stage 2 가 엉뚱한 스레드에 답글을 단다.
  local merged; merged="$(jq -s '
      .[0] as $draft | .[1] as $posted |
      $draft | map(. as $d
        | (first($posted[] | select(.body == $d.body) | .id)
           // first($posted[] | select(.path==$d.path and .line==$d.line) | .id)) as $id
        | {id: ($id // null), code:$d.code, path:$d.path, line:$d.line,
           axis:$d.axis, grade:($d.grade // null)})
    ' "$cfile" "$tmp/posted.json")"

  # 스테이지마다 기록 자리가 다르다. stage2 를 stage1 에 붙이면 watcher 의 "이미 게시됨" 판정과
  # Stage 2 가 스레드를 찾는 stage1[].comments[].id 가 함께 어긋난다.
  case "$stage" in
    stage1)
      meta_update "$repo" "$pr" '.stage1 += [{review_id:$rid, posted_at:$at, eval_sha:$sha, comments:$c}] | .attempts=0 | .status="완료"' \
        --argjson rid "$review_id" --arg at "$(now_iso)" --arg sha "$sha" --argjson c "$merged" ;;
    stage2)
      meta_update "$repo" "$pr" '.stage2 = ((.stage2 // []) | if length == 0 then [{}] else . end)
                                 | .stage2[-1] += {review_id:$rid, posted_at:$at, stage_sha:$sha, new_comments:$c}' \
        --argjson rid "$review_id" --arg at "$(now_iso)" --arg sha "$sha" --argjson c "$merged" ;;
    stage3)
      meta_update "$repo" "$pr" '.stage3 = ((.stage3 // {}) + {review_id:$rid, posted_at:$at, stage_sha:$sha, comments:$c})' \
        --argjson rid "$review_id" --arg at "$(now_iso)" --arg sha "$sha" --argjson c "$merged" ;;
  esac

  echo "게시 완료 — review_id=$review_id, inline 코멘트 $(jq 'length' <<<"$merged") 건"
  jq -r '.[] | select(.id == null) | "주의 — id 를 못 찾은 코멘트: \(.code) \(.path):\(.line)"' <<<"$merged" >&2
}

cmd_reply() {
  local repo="$1" pr="$2" cid="$3" bfile="$4"
  [ -f "$bfile" ] || die "$E_USAGE" "본문 파일이 없다: $bfile"
  load_bot_token
  jq -n --rawfile b "$bfile" '{body:$b}' \
    | gh api "repos/$OWNER/$repo/pulls/$pr/comments/$cid/replies" --input - --jq '.id' \
    || die "$E_API" "reply 실패 (comment_id=$cid)"
}

cmd_patch() {
  local repo="$1" pr="$2" cid="$3" bfile="$4"
  [ -f "$bfile" ] || die "$E_USAGE" "본문 파일이 없다: $bfile"
  load_bot_token
  jq -n --rawfile b "$bfile" '{body:$b}' \
    | gh api -X PATCH "repos/$OWNER/$repo/pulls/comments/$cid" --input - --jq '.id' \
    || die "$E_API" "patch 실패 (comment_id=$cid). 봇은 자기 코멘트만 수정할 수 있다"
}

# 게시한 리뷰 요약 본문을 고친다. 봇은 자기 리뷰만 고칠 수 있다.
cmd_patch_review() {
  local repo="$1" pr="$2" rid="$3" bfile="$4"
  [ -f "$bfile" ] || die "$E_USAGE" "본문 파일이 없다: $bfile"
  load_bot_token
  jq -n --rawfile b "$bfile" '{body:$b}' \
    | gh api -X PUT "repos/$OWNER/$repo/pulls/$pr/reviews/$rid" --input - --jq '.id' \
    || die "$E_API" "요약 정정 실패 (review_id=$rid)"
}

cmd_comment() {
  local repo="$1" pr="$2" bfile="$3"
  [ -f "$bfile" ] || die "$E_USAGE" "본문 파일이 없다: $bfile"
  load_bot_token
  jq -n --rawfile b "$bfile" '{body:$b}' \
    | gh api "repos/$OWNER/$repo/issues/$pr/comments" --input - --jq '.id' \
    || die "$E_API" "코멘트 게시 실패"
}

# ---------- 진입점 ----------

[ $# -ge 1 ] || usage
sub="$1"; shift
case "$sub" in
  sha)         [ $# -eq 2 ] || usage; cmd_sha "$@" ;;
  meta)        [ $# -eq 2 ] || usage; cmd_meta "$@" ;;
  precheck)    [ $# -eq 2 ] || usage; cmd_precheck "$@" ;;
  ranges)      [ $# -eq 3 ] || usage; cmd_ranges "$@" ;;
  gate1)       [ $# -eq 4 ] || usage; cmd_gate1 "$@" ;;
  init)        [ $# -ge 2 ] && [ $# -le 3 ] || usage; cmd_init "$@" ;;
  lock)        [ $# -eq 3 ] || usage; cmd_lock "$@" ;;
  unlock)      [ $# -eq 2 ] || usage; cmd_unlock "$@" ;;
  status)      [ $# -eq 3 ] || usage; cmd_status "$@" ;;
  attempt)     [ $# -eq 3 ] || usage; cmd_attempt "$@" ;;
  post-review) [ $# -ge 5 ] || usage; cmd_post_review "$@" ;;
  reply)       [ $# -eq 4 ] || usage; cmd_reply "$@" ;;
  patch)       [ $# -eq 4 ] || usage; cmd_patch "$@" ;;
  patch-review) [ $# -eq 4 ] || usage; cmd_patch_review "$@" ;;
  comment)     [ $# -eq 3 ] || usage; cmd_comment "$@" ;;
  *) usage ;;
esac
