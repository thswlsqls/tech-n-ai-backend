#!/bin/bash
# ============================================================================
# tech-n-ai-backend impl Pipeline — tmux Session
# ============================================================================
# Usage:
#   ./impl-session.sh                                  # Interactive
#   ./impl-session.sh "issue=#12"                      # Auto: 단일 작업
#   ./impl-session.sh "task=01"
#   ./impl-session.sh "docs=docs/reference/design/001-foo.md modules=:api-bookmark"
#   ./impl-session.sh "issue=#12" "task=02"            # 2 작업 병렬 pane (worktree로 쓰기 격리)
#
# 인자 하나 = 작업 하나. 인자 문자열이 그대로 /tech-n-ai-impl:impl 의 입력이 된다
# (docs= / task=NN / issue=#N + 선택 modules= type=).
#
# 모든 경로는 impl-config.yml에서 읽는다(스크립트에 경로 하드코딩 없음).
# Prerequisites: tmux 3.0+, claude CLI, gh(authenticated), JDK 21, ./gradlew
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="$SCRIPT_DIR/../impl-config.yml"

# cfg <key>: config의 스칼라 값을 읽는다. 비면 즉시 실패(silent empty 방지).
cfg() {
    local key="$1" val
    val=$(grep -E "^[[:space:]]*${key}:" "$CONFIG" | head -1 \
          | sed 's/^[^:]*:[[:space:]]*//' | sed 's/[[:space:]]*#.*$//' | tr -d '"')
    if [[ -z "$val" ]]; then
        echo "config 키 '${key}' 를 $CONFIG 에서 찾지 못함" >&2
        exit 1
    fi
    printf '%s' "$val"
}

SESSION="tech-impl"
PROJECT_ROOT=$(cfg main_root)
OUTPUT_DIR=$(cfg output_dir)
PLUGIN_DIR=$(cfg plugin_dir)
WORKS=("$@")

# 모델 선택: 기본은 세션/계정 기본 모델(--model 생략). 에이전트는 model: inherit로 자동 추종.
# 특정 모델 강제:  IMPL_MODEL=opus ./impl-session.sh "issue=#12"
MODEL_FLAG=""
[[ -n "${IMPL_MODEL:-}" ]] && MODEL_FLAG="--model ${IMPL_MODEL}"

CYAN="\033[0;36m"; GREEN="\033[0;32m"; YELLOW="\033[1;33m"; NC="\033[0m"
log() { echo -e "${CYAN}[impl]${NC} $1"; }

# --- Preflight ---
command -v claude >/dev/null || { echo "claude CLI not found"; exit 1; }
command -v gh     >/dev/null || { echo "gh not found"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh 미인증 — 'gh auth login' 먼저 실행"; exit 1; }
[[ -d "$PROJECT_ROOT" ]] || { echo "main_root 디렉토리 없음: $PROJECT_ROOT"; exit 1; }
[[ -x "$PROJECT_ROOT/gradlew" ]] || { echo "gradlew 실행파일 없음: $PROJECT_ROOT/gradlew"; exit 1; }
[[ -d "$PLUGIN_DIR" ]] || { echo "plugin_dir 디렉토리 없음: $PLUGIN_DIR"; exit 1; }

if tmux has-session -t "$SESSION" 2>/dev/null; then
    read -p "세션 '$SESSION'이 이미 있습니다. 종료 후 재생성? [y/N] " -n 1 -r; echo
    [[ $REPLY =~ ^[Yy]$ ]] && tmux kill-session -t "$SESSION" || exit 0
fi

mkdir -p "$OUTPUT_DIR"

# claude 커맨드: 프롬프트를 CLI 인자로 직접 전달(send-keys 레이스 없음)
claude_for() {
    printf 'claude %s --plugin-dir %q %q' "$MODEL_FLAG" "$PLUGIN_DIR" "/tech-n-ai-impl:impl $1"
}

monitor_cmd() {
    if command -v fswatch &>/dev/null; then
        echo "echo '=== Output Monitor (live) ===' && fswatch -r --event Created --event Updated '$OUTPUT_DIR' | while read f; do echo \"\$(date '+%H:%M:%S') \${f#$OUTPUT_DIR/}\"; done"
    else
        echo "echo '=== Output Monitor ===' && watch -n 5 'find $OUTPUT_DIR -name \"*.md\" -newer $CONFIG 2>/dev/null | sort'"
    fi
}

# pane ID(%N)를 캡처해 base-index 설정과 무관하게 타깃팅한다.
# Layout(단일): [Claude | Monitor] / [Build·Test | Git·Validate]
setup_single() {
    local work="${1:-}" p0 p1 p2 p3
    p0=$(tmux new-session -d -s "$SESSION" -c "$PROJECT_ROOT" -x "$(tput cols)" -y "$(tput lines)" -P -F '#{pane_id}')
    tmux rename-window -t "$SESSION:" "impl"
    if [[ -n "$work" ]]; then
        log "Auto mode: ${GREEN}${work}${NC}"
        tmux send-keys -t "$p0" "$(claude_for "$work")" Enter
    else
        log "Interactive mode"
        tmux send-keys -t "$p0" "claude $MODEL_FLAG --plugin-dir $PLUGIN_DIR" Enter
    fi
    p1=$(tmux split-window -h -t "$p0" -p 35 -c "$PROJECT_ROOT" -P -F '#{pane_id}')
    tmux send-keys -t "$p1" "$(monitor_cmd)" Enter
    p2=$(tmux split-window -v -t "$p0" -p 30 -c "$PROJECT_ROOT" -P -F '#{pane_id}')
    tmux send-keys -t "$p2" "echo '=== Build / Test ===' && echo '  ./gradlew :<module>:test  (영향 모듈 각각)'" Enter
    p3=$(tmux split-window -v -t "$p1" -p 30 -c "$PROJECT_ROOT" -P -F '#{pane_id}')
    tmux send-keys -t "$p3" "echo '=== Git / Validate (push 후: /impl-validate pipeline/output/<run>) ===' && git -C '$PROJECT_ROOT' worktree list" Enter
    tmux select-pane -t "$p0"
}

# 2~3 작업: 작업당 Claude pane + monitor + shell (worktree로 쓰기 격리)
setup_multi_pane() {
    local p0 p1 p2 p3 p4
    log "Parallel mode: ${GREEN}${#WORKS[@]} works${NC}"
    p0=$(tmux new-session -d -s "$SESSION" -c "$PROJECT_ROOT" -x "$(tput cols)" -y "$(tput lines)" -P -F '#{pane_id}')
    tmux rename-window -t "$SESSION:" "impl"
    tmux send-keys -t "$p0" "$(claude_for "${WORKS[0]}")" Enter
    p1=$(tmux split-window -h -t "$p0" -p 66 -c "$PROJECT_ROOT" -P -F '#{pane_id}')
    tmux send-keys -t "$p1" "$(claude_for "${WORKS[1]}")" Enter
    p2=$(tmux split-window -h -t "$p1" -p 50 -c "$PROJECT_ROOT" -P -F '#{pane_id}')
    tmux send-keys -t "$p2" "$(monitor_cmd)" Enter
    p3=$(tmux split-window -v -t "$p0" -p 30 -c "$PROJECT_ROOT" -P -F '#{pane_id}')
    if [[ ${#WORKS[@]} -ge 3 ]]; then
        tmux send-keys -t "$p3" "$(claude_for "${WORKS[2]}")" Enter
    else
        tmux send-keys -t "$p3" "echo '=== Build / Test === Ready.'" Enter
    fi
    p4=$(tmux split-window -h -t "$p3" -p 50 -c "$PROJECT_ROOT" -P -F '#{pane_id}')
    tmux send-keys -t "$p4" "echo '=== Git / Validate ===' && git -C '$PROJECT_ROOT' worktree list" Enter
    tmux select-pane -t "$p0"
}

log "Project: ${GREEN}${PROJECT_ROOT}${NC}"
log "Output:  ${GREEN}${OUTPUT_DIR}/<yyyyMMddHHmmss>/${NC} (issues/ prs/) + ${GREEN}<work-key>/${NC} (spec/state)"
echo ""

if [[ ${#WORKS[@]} -ge 4 ]]; then
    echo "4개 이상 병렬은 작업당 독립 세션을 권장합니다(worktree로 쓰기 격리, _learnings.md append 경합 최소화)."
    exit 1
elif [[ ${#WORKS[@]} -ge 2 ]]; then
    setup_multi_pane
else
    setup_single "${WORKS[0]:-}"
fi

log "Attaching to '${SESSION}'..."
echo -e "  ${YELLOW}Keys:${NC} Ctrl-b d=detach | Ctrl-b z=zoom | Ctrl-b arrows=move | Ctrl-b [=scroll"
tmux attach-session -t "$SESSION"
