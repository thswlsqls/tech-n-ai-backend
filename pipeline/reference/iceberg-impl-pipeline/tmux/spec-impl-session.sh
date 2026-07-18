#!/bin/bash
# ============================================================================
# Spec-Driven Implementation — tmux Session
# ============================================================================
# Usage:
#   ./spec-impl-session.sh                              # Interactive
#   ./spec-impl-session.sh issue-16850                  # Auto: 단일 issue
#   ./spec-impl-session.sh issue-16850 issue-16900      # 2 issue 병렬 pane (worktree로 쓰기 격리)
#
# 작업 단위는 issue다. 각 issue의 산출물은 inputs/<issue-key>/ 에서 자동 탐색한다:
#   api.*(api 정의서) · screen.*(화면설계서) · req*/requirements.*(요구사항) · module(대상 Gradle path, 선택)
# 없는 종류는 생략한다(오케스트레이터가 N/A로 처리). issue-key는 'issue-<번호>' 형식 권장.
#
# 모든 경로/설정은 impl-config.yml에서 읽는다(스크립트에 하드코딩 없음).
# Prerequisites: tmux 3.0+, claude CLI, gh(authenticated), JDK 17 또는 21 (JAVA_HOME), ./gradlew
# ============================================================================
set -euo pipefail

CONFIG="/Users/m1/workspace/iceberg/z_ebson/impl-pipeline/impl-config.yml"

# cfg <key>: paths 섹션의 스칼라 값을 읽는다. 비면 즉시 실패(silent empty 방지).
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

SESSION="spec-impl"
PROJECT_ROOT=$(cfg main_root)
OUTPUT_DIR=$(cfg output_dir)
INPUTS_DIR=$(cfg inputs_dir)
PLUGIN_DIR=$(cfg plugin_dir)
ISSUES=("$@")

# 모델 선택: 기본은 세션/계정 기본 모델을 그대로 따른다(--model 생략).
#   서브에이전트는 frontmatter `model: inherit`로 오케스트레이터 모델을 자동 추종.
# 특정 모델 강제:  SPEC_MODEL=opus ./spec-impl-session.sh issue-16850
MODEL_FLAG=""
[[ -n "${SPEC_MODEL:-}" ]] && MODEL_FLAG="--model ${SPEC_MODEL}"

CYAN="\033[0;36m"; GREEN="\033[0;32m"; YELLOW="\033[1;33m"; NC="\033[0m"
log() { echo -e "${CYAN}[spec-impl]${NC} $1"; }

# issue-key → issue 참조(#번호). 'issue-16850'→'#16850'. 숫자 접두사 없으면 키 그대로 전달.
key_to_issue_ref() {
    local k="$1" n="${1#issue-}"
    if [[ "$n" =~ ^[0-9]+$ ]]; then echo "#${n}"; else echo "$k"; fi
}

# 첫 매치 파일 1개 경로를 echo(없으면 빈 문자열). glob 패턴 여러 개 시도.
first_match() {
    local dir="$1"; shift
    local pat f
    for pat in "$@"; do
        for f in "$dir"/$pat; do
            [[ -e "$f" ]] && { printf '%s' "$f"; return 0; }
        done
    done
    printf ''
}

# inputs/<issue-key>/ 에서 산출물을 탐색해 프롬프트 인자 문자열을 만든다.
build_args() {
    local key="$1" dir="$INPUTS_DIR/$1"
    local ref; ref=$(key_to_issue_ref "$key")
    local args="issue=${ref}"
    if [[ -d "$dir" ]]; then
        local api screen req mod
        api=$(first_match "$dir" "api.*" "openapi.*" "API*.md")
        screen=$(first_match "$dir" "screen*.*" "ui*.*" "화면*.*")
        req=$(first_match "$dir" "req*.*" "requirements.*" "요구*.*")
        [[ -n "$api" ]]    && args="$args api=${api}"
        [[ -n "$screen" ]] && args="$args screen=${screen}"
        [[ -n "$req" ]]    && args="$args req=${req}"
        if [[ -f "$dir/module" ]]; then
            mod=$(head -1 "$dir/module" | tr -d '[:space:]')
            [[ -n "$mod" ]] && args="$args module=${mod}"
        fi
    fi
    printf '%s' "$args"
}

# --- Preflight ---
command -v claude  >/dev/null || { echo "claude CLI not found"; exit 1; }
command -v gh      >/dev/null || { echo "gh not found"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh 미인증 — 'gh auth login' 먼저 실행"; exit 1; }
[[ -x "$PROJECT_ROOT/gradlew" ]] || { echo "gradlew 실행파일 없음: $PROJECT_ROOT/gradlew"; exit 1; }
[[ -d "$PROJECT_ROOT" ]] || { echo "main_root 디렉토리 없음: $PROJECT_ROOT"; exit 1; }
[[ -d "$PLUGIN_DIR"   ]] || { echo "plugin_dir 디렉토리 없음: $PLUGIN_DIR"; exit 1; }
for k in "${ISSUES[@]:-}"; do
    [[ -z "$k" ]] && continue
    # best-effort 경고만 — 산출물이 inputs/에 없어도 인자로 직접 줄 수 있으므로 hard-fail 안 함.
    [[ -d "$INPUTS_DIR/$k" ]] || echo -e "${YELLOW}WARN${NC}: 산출물 디렉터리 없음 '$INPUTS_DIR/$k' — issue 본문만으로 진행하거나 인자로 산출물 경로를 직접 주세요."
done

if tmux has-session -t "$SESSION" 2>/dev/null; then
    read -p "세션 '$SESSION'이 이미 있습니다. 종료 후 재생성? [y/N] " -n 1 -r; echo
    [[ $REPLY =~ ^[Yy]$ ]] && tmux kill-session -t "$SESSION" || exit 0
fi

# issue 폴더는 오케스트레이터(Phase 0)가 생성한다. 여기선 OUTPUT_DIR/INPUTS_DIR만 보장.
mkdir -p "$OUTPUT_DIR" "$INPUTS_DIR"

# claude 커맨드: 프롬프트를 CLI 인자로 직접 전달(sleep/send-keys 레이스 없음)
claude_for() {
    local key="$1" args
    args=$(build_args "$key")
    printf 'claude %s --plugin-dir %q %q' "$MODEL_FLAG" "$PLUGIN_DIR" "/spec-impl:spec-impl ${args}"
}

monitor_cmd() {
    if command -v fswatch &>/dev/null; then
        echo "echo '=== Output Monitor (live) ===' && fswatch -r --event Created --event Updated '$OUTPUT_DIR' | while read f; do echo \"\$(date '+%H:%M:%S') \${f#$OUTPUT_DIR/}\"; done"
    else
        echo "echo '=== Output Monitor ===' && watch -n 5 'find $OUTPUT_DIR -name \"*.md\" -newer $CONFIG 2>/dev/null | sort'"
    fi
}

# Layout: [0] Claude | [1] Monitor / [2] Build·Test | [3] Git·Validate
setup_single() {
    local key="${1:-}"
    tmux new-session -d -s "$SESSION" -c "$PROJECT_ROOT" -x "$(tput cols)" -y "$(tput lines)"
    tmux rename-window -t "$SESSION:0" "impl"
    if [[ -n "$key" ]]; then
        log "Auto mode: ${GREEN}${key}${NC}  →  $(build_args "$key")"
        tmux send-keys -t "$SESSION:0.0" "$(claude_for "$key")" Enter
    else
        log "Interactive mode"
        tmux send-keys -t "$SESSION:0.0" "claude $MODEL_FLAG --plugin-dir $PLUGIN_DIR" Enter
    fi
    tmux split-window -h -t "$SESSION:0.0" -p 35 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.1" "$(monitor_cmd)" Enter
    tmux split-window -v -t "$SESSION:0.0" -p 30 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.2" "echo '=== Build / Test ===' && echo '  ./gradlew <:module>:check   (revapi: published 모듈만)'" Enter
    tmux split-window -v -t "$SESSION:0.1" -p 30 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.3" "echo '=== Git / Validate (구현 후: /spec-impl-validate 로 검증·제출) ===' && git -C '$PROJECT_ROOT' worktree list" Enter
    tmux select-pane -t "$SESSION:0.0"
}

# 2-3 issue: issue당 Claude pane + monitor + shell
setup_multi_pane() {
    log "Parallel mode: ${GREEN}${#ISSUES[@]} issues${NC} — ${ISSUES[*]}"
    tmux new-session -d -s "$SESSION" -c "$PROJECT_ROOT" -x "$(tput cols)" -y "$(tput lines)"
    tmux rename-window -t "$SESSION:0" "impl"
    tmux send-keys -t "$SESSION:0.0" "$(claude_for "${ISSUES[0]}")" Enter
    tmux split-window -h -t "$SESSION:0.0" -p 66 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.1" "$(claude_for "${ISSUES[1]}")" Enter
    tmux split-window -h -t "$SESSION:0.1" -p 50 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.2" "$(monitor_cmd)" Enter
    tmux split-window -v -t "$SESSION:0.0" -p 30 -c "$PROJECT_ROOT"
    if [[ ${#ISSUES[@]} -ge 3 ]]; then
        tmux send-keys -t "$SESSION:0.3" "$(claude_for "${ISSUES[2]}")" Enter
    else
        tmux send-keys -t "$SESSION:0.3" "echo '=== Build / Test === Ready.'" Enter
    fi
    tmux split-window -h -t "$SESSION:0.3" -p 50 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.4" "echo '=== Git / Validate ===' && git -C '$PROJECT_ROOT' worktree list" Enter
    tmux select-pane -t "$SESSION:0.0"
}

log "Project: ${GREEN}${PROJECT_ROOT}${NC}"
log "Inputs:  ${GREEN}${INPUTS_DIR}/<issue-key>/${NC}  (api.* / screen.* / req*.* / module)"
log "Output:  ${GREEN}${OUTPUT_DIR}/<issue-key>/${NC}"
echo ""

if [[ ${#ISSUES[@]} -ge 4 ]]; then
    echo "4개 이상 issue 병렬은 issue당 독립 세션을 권장합니다(worktree로 쓰기 격리)."
    exit 1
elif [[ ${#ISSUES[@]} -ge 2 ]]; then
    setup_multi_pane
else
    setup_single "${ISSUES[0]:-}"
fi

log "Attaching to '${SESSION}'..."
echo -e "  ${YELLOW}Keys:${NC} Ctrl-b d=detach | Ctrl-b z=zoom | Ctrl-b arrows=move | Ctrl-b [=scroll"
tmux attach-session -t "$SESSION"
