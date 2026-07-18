#!/bin/bash
# ============================================================================
# LangChain4j OSS Contribution v2 — tmux Session
# ============================================================================
# Usage:
#   ./oss-contrib-session.sh                          # Interactive
#   ./oss-contrib-session.sh langchain4j-open-ai      # Auto: 단일 모듈
#   ./oss-contrib-session.sh langchain4j-ollama langchain4j-mistral-ai  # 2모듈 병렬 pane
#
# 모든 경로/설정은 contrib-config.yml에서 읽는다(스크립트에 하드코딩 없음).
# Prerequisites: tmux 3.0+, claude CLI, gh(authenticated), optional fswatch
# ============================================================================
set -euo pipefail

# 이 스크립트(tmux/) 기준으로 config 위치를 잡는다 — 경로 하드코딩 회피(z-eunbin-son stale 버그 방지).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="$SCRIPT_DIR/../contrib-config.yml"
[[ -f "$CONFIG" ]] || { echo "config 없음: $CONFIG"; exit 1; }
cfg() { grep -E "^[[:space:]]*$1:" "$CONFIG" | head -1 | sed 's/^[^:]*:[[:space:]]*//' | sed 's/[[:space:]]*#.*$//' | tr -d '"'; }

SESSION="l4j-contrib-v2"
PROJECT_ROOT=$(cfg main_root)
OUTPUT_DIR=$(cfg output_dir)
PLUGIN_DIR=$(cfg plugin_dir)
MODULES=("$@")
PROMPT_SUFFIX="범위내에서 기여 후보를 발굴/검증하고 이슈와 PR 초안을 생성하세요"

CYAN="\033[0;36m"; GREEN="\033[0;32m"; YELLOW="\033[1;33m"; NC="\033[0m"
log() { echo -e "${CYAN}[l4j-v2]${NC} $1"; }

# --- Preflight ---
command -v claude >/dev/null || { echo "claude CLI not found"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh 미인증 — 'gh auth login' 먼저 실행"; exit 1; }
for m in "${MODULES[@]:-}"; do
    [[ -z "$m" ]] && continue
    [[ -d "$PROJECT_ROOT/${m%/}" ]] || { echo "모듈 디렉토리 없음: $PROJECT_ROOT/$m"; exit 1; }
done

if tmux has-session -t "$SESSION" 2>/dev/null; then
    read -p "세션 '$SESSION'이 이미 있습니다. 종료 후 재생성? [y/N] " -n 1 -r; echo
    [[ $REPLY =~ ^[Yy]$ ]] && tmux kill-session -t "$SESSION" || exit 0
fi

# run 폴더는 오케스트레이터(Phase 0)가 yyyyMMddHHmmss로 생성한다. 여기선 OUTPUT_DIR만 보장.
mkdir -p "$OUTPUT_DIR"

# claude 커맨드: 프롬프트를 CLI 인자로 직접 전달(sleep/send-keys 레이스 없음)
claude_for() {
    local module="$1"
    printf 'claude --plugin-dir %q %q' "$PLUGIN_DIR" "/oss-contrib-v2:oss-contrib ${module} ${PROMPT_SUFFIX}"
}

monitor_cmd() {
    if command -v fswatch &>/dev/null; then
        echo "echo '=== Output Monitor (live) ===' && fswatch -r --event Created --event Updated '$OUTPUT_DIR' | while read f; do echo \"\$(date '+%H:%M:%S') \${f#$OUTPUT_DIR/}\"; done"
    else
        echo "echo '=== Output Monitor ===' && watch -n 5 'find $OUTPUT_DIR -name \"*.md\" -newer $CONFIG 2>/dev/null | sort'"
    fi
}

# Layout: [0] Claude | [1] Monitor / [2] Build·Test | [3] Git·Submit
setup_single() {
    local module="${1:-}"
    local COLS LINES P0 W0 P1 P2 P3
    # base-index/pane-base-index 설정에 무관하도록 pane id(%N)·window id(@N)로 타깃한다.
    COLS="$(tput cols 2>/dev/null || echo 200)"; LINES="$(tput lines 2>/dev/null || echo 50)"
    tmux new-session -d -s "$SESSION" -c "$PROJECT_ROOT" -x "$COLS" -y "$LINES"
    P0="$(tmux display-message -p -t "$SESSION" '#{pane_id}')"
    W0="$(tmux display-message -p -t "$SESSION" '#{window_id}')"
    tmux rename-window -t "$W0" "contrib"
    if [[ -n "$module" ]]; then
        log "Auto mode: ${GREEN}${module}${NC}"
        tmux send-keys -t "$P0" "$(claude_for "$module")" Enter
    else
        log "Interactive mode"
        tmux send-keys -t "$P0" "claude --plugin-dir $PLUGIN_DIR" Enter
    fi
    P1="$(tmux split-window -h -t "$P0" -p 35 -c "$PROJECT_ROOT" -P -F '#{pane_id}')"
    tmux send-keys -t "$P1" "$(monitor_cmd)" Enter
    P2="$(tmux split-window -v -t "$P0" -p 30 -c "$PROJECT_ROOT" -P -F '#{pane_id}')"
    tmux send-keys -t "$P2" "echo '=== Build / Test ===' && echo '  ./mvnw -pl ${module:-<module>} -am clean test'" Enter
    P3="$(tmux split-window -v -t "$P1" -p 30 -c "$PROJECT_ROOT" -P -F '#{pane_id}')"
    tmux send-keys -t "$P3" "echo '=== Git / Submit (사람이 수동 제출) ===' && git -C '$PROJECT_ROOT' worktree list" Enter
    tmux select-pane -t "$P0"
}

# 2-3 모듈: 모듈당 Claude pane + monitor + shell
setup_multi_pane() {
    local COLS LINES P0 W0 P1 P2 P3 P4
    log "Parallel mode: ${GREEN}${#MODULES[@]} modules${NC} — ${MODULES[*]}"
    # base-index/pane-base-index 설정에 무관하도록 pane id(%N)·window id(@N)로 타깃한다.
    COLS="$(tput cols 2>/dev/null || echo 200)"; LINES="$(tput lines 2>/dev/null || echo 50)"
    tmux new-session -d -s "$SESSION" -c "$PROJECT_ROOT" -x "$COLS" -y "$LINES"
    P0="$(tmux display-message -p -t "$SESSION" '#{pane_id}')"
    W0="$(tmux display-message -p -t "$SESSION" '#{window_id}')"
    tmux rename-window -t "$W0" "contrib"
    tmux send-keys -t "$P0" "$(claude_for "${MODULES[0]}")" Enter
    P1="$(tmux split-window -h -t "$P0" -p 66 -c "$PROJECT_ROOT" -P -F '#{pane_id}')"
    tmux send-keys -t "$P1" "$(claude_for "${MODULES[1]}")" Enter
    P2="$(tmux split-window -h -t "$P1" -p 50 -c "$PROJECT_ROOT" -P -F '#{pane_id}')"
    tmux send-keys -t "$P2" "$(monitor_cmd)" Enter
    P3="$(tmux split-window -v -t "$P0" -p 30 -c "$PROJECT_ROOT" -P -F '#{pane_id}')"
    if [[ ${#MODULES[@]} -ge 3 ]]; then
        tmux send-keys -t "$P3" "$(claude_for "${MODULES[2]}")" Enter
    else
        tmux send-keys -t "$P3" "echo '=== Build / Test === Ready.'" Enter
    fi
    P4="$(tmux split-window -h -t "$P3" -p 50 -c "$PROJECT_ROOT" -P -F '#{pane_id}')"
    tmux send-keys -t "$P4" "echo '=== Git / Submit ===' && git -C '$PROJECT_ROOT' worktree list" Enter
    tmux select-pane -t "$P0"
}

log "Project: ${GREEN}${PROJECT_ROOT}${NC}"
log "Output:  ${GREEN}${OUTPUT_DIR}/<yyyyMMddHHmmss>/${NC}"
echo ""

if [[ ${#MODULES[@]} -ge 4 ]]; then
    echo "4개 이상 모듈 병렬은 모듈당 독립 세션을 권장합니다(worktree로 쓰기 격리)."
    exit 1
elif [[ ${#MODULES[@]} -ge 2 ]]; then
    setup_multi_pane
else
    setup_single "${MODULES[0]:-}"
fi

log "Attaching to '${SESSION}'..."
echo -e "  ${YELLOW}Keys:${NC} Ctrl-b d=detach | Ctrl-b z=zoom | Ctrl-b arrows=move | Ctrl-b [=scroll"
tmux attach-session -t "$SESSION"
