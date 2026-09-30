#!/usr/bin/env bash
# watch.sh — 봇에게 리뷰 요청이 걸린 PR 을 찾아 Stage 1 세션을 띄운다.
# 자동으로 도는 것은 Stage 1 뿐이다. Stage 2·3 은 사람이 /pr-eval 을 부를 때만 돈다.
# launchd StartInterval 60 으로 상시 실행한다. --once 는 디버깅 경로다.
set -euo pipefail

OWNER="${PR_EVAL_OWNER:-thswlsqls}"
# 봇 로그인은 bot.env 의 BOT_LOGIN 하나에서 읽는다 — 여기와 토큰 파일이 어긋나면
# watcher 가 엉뚱한 계정으로 폴링해 트리거가 조용히 안 돈다. (토큰은 읽지 않는다)
BOT_ENV="${PR_EVAL_BOT_ENV:-$HOME/.config/pr-eval/bot.env}"
BOT="${PR_EVAL_BOT:-$(sed -n 's/^BOT_LOGIN=//p' "$BOT_ENV" 2>/dev/null | tr -d "\"'" | head -1)}"
[ -n "$BOT" ] || { echo "봇 로그인을 못 찾았다: $BOT_ENV 의 BOT_LOGIN 또는 PR_EVAL_BOT 을 설정하라" >&2; exit 2; }
HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$HARNESS_DIR/../.." && pwd)"
WS_ROOT="$(cd "$REPO_ROOT/.." && pwd)"
PR_EVAL="$HARNESS_DIR/scripts/pr-eval.sh"
MAX_ATTEMPTS=2

log() { echo "[$(date -u +%H:%M:%S)] $*"; }

known_profile() { case "$1" in tech-n-ai-backend|tech-n-ai-frontend) return 0 ;; *) return 1 ;; esac; }

# meta.lock 이 유효한가 — 시작 후 3시간 이내이고 pid 가 살아 있으면 유효하다.
lock_valid() {
  local m="$1" pid started s_epoch
  pid="$(jq -r '.lock.pid // empty' "$m")"
  [ -n "$pid" ] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  started="$(jq -r '.lock.started_at // empty' "$m")"
  s_epoch="$(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$started" +%s 2>/dev/null || echo 0)"
  [ "$s_epoch" -gt 0 ] || return 1
  [ $(( $(date -u +%s) - s_epoch )) -lt 10800 ]
}

run_once() {
  local prs n
  # watcher 가 GitHub 에 던지는 질의는 이것 하나뿐이다. PR 코멘트를 폴링하지 않는다.
  prs="$(gh search prs --owner "$OWNER" --review-requested="$BOT" --state=open \
          --json number,repository,isDraft 2>/dev/null || echo '[]')"
  n="$(jq 'length' <<<"$prs")"
  [ "$n" -gt 0 ] || { log "리뷰 요청 없음"; return 0; }

  local i
  for (( i=0; i<n; i++ )); do
    local pr repo draft m
    pr="$(jq -r ".[$i].number" <<<"$prs")"
    repo="$(jq -r ".[$i].repository.name" <<<"$prs")"
    draft="$(jq -r ".[$i].isDraft" <<<"$prs")"

    # 1) 프로파일을 모르는 저장소 — 질의가 --owner 단위라 밖의 PR 도 걸려 온다.
    known_profile "$repo" || { log "skip $repo#$pr — 프로파일 없음"; continue; }
    # 2) draft
    [ "$draft" = "false" ] || { log "skip $repo#$pr — draft"; continue; }

    m="$HARNESS_DIR/runs/$repo-pr$pr/meta.json"
    if [ -f "$m" ]; then
      # 3) 유효한 락
      lock_valid "$m" && { log "skip $repo#$pr — 락 점유 중"; continue; }
      # 4) 보류 상태 — 재개는 사람만 한다
      case "$(jq -r '.status' "$m")" in
        보류*) log "skip $repo#$pr — $(jq -r '.status' "$m")"; continue ;;
      esac
      # 5) 이미 Stage 1 리뷰를 게시했다
      [ "$(jq -r '[.stage1[]?.review_id | select(. != null)] | length' "$m")" = "0" ] \
        || { log "skip $repo#$pr — 이미 게시됨"; continue; }
      # 실패 반복 차단
      if [ "$(jq -r '.attempts // 0' "$m")" -ge "$MAX_ATTEMPTS" ]; then
        log "$repo#$pr — 연속 실패 $MAX_ATTEMPTS 회. 보류(실패) 로 멈춘다"
        "$PR_EVAL" status "$repo" "$pr" "보류(실패)" >/dev/null
        continue
      fi
    fi

    log "Stage 1 시작 — $repo#$pr"
    "$PR_EVAL" init "$repo" "$pr" >/dev/null
    "$PR_EVAL" attempt "$repo" "$pr" inc >/dev/null

    # Stage 1 부터 머지까지 체인 하나가 끝까지 돈다(scripts/chain.sh). 끝날 때까지 기다린다 —
    # 그래야 launchd 가 StartInterval 회차를 건너뛰어 이중 실행이 이중으로 막힌다.
    "$HARNESS_DIR/scripts/chain.sh" "$repo" "$pr" || log "체인이 멈췄다 — $repo#$pr (chain.state=$(jq -r '.chain.state // "?"' "$m" 2>/dev/null))"

    log "체인 종료 — $repo#$pr (status=$(jq -r '.status' "$m" 2>/dev/null || echo '?'))"
  done
}

case "${1:-}" in
  --once|"") run_once ;;
  *) echo "usage: watch.sh [--once]" >&2; exit 1 ;;
esac
