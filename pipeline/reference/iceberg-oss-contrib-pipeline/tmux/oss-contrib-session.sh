#!/bin/bash
# ============================================================================
# Apache Iceberg OSS Contribution — tmux Session
# ============================================================================
# Usage:
#   ./oss-contrib-session.sh                            # Interactive
#   ./oss-contrib-session.sh :iceberg-core              # Auto: 단일 모듈(Gradle path)
#   ./oss-contrib-session.sh :iceberg-core :iceberg-data  # 2모듈 병렬 pane (worktree로 쓰기 격리)
#
# 모든 경로/설정은 contrib-config.yml에서 읽는다(스크립트에 하드코딩 없음).
# Prerequisites: tmux 3.0+, claude CLI, gh(authenticated), JDK 17 또는 21 (JAVA_HOME), ./gradlew
# ============================================================================
set -euo pipefail

CONFIG="/Users/m1/workspace/iceberg/z_ebson/pipeline/contrib-config.yml"

# cfg <key>: paths/build 섹션의 스칼라 값을 읽는다.
# 값이 비면 즉시 실패(silent empty 방지). 키는 config 내에서 유일해야 한다.
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

SESSION="iceberg-contrib"
PROJECT_ROOT=$(cfg main_root)
OUTPUT_DIR=$(cfg output_dir)
PLUGIN_DIR=$(cfg plugin_dir)
MODULES=("$@")
PROMPT_SUFFIX="범위내에서 기여 후보를 발굴/검증하고 PR 초안을 생성하세요"

# 모델 선택: 기본은 세션/계정 기본 모델을 그대로 따른다(--model 생략).
#   = 오케스트레이터가 고른 모델이 곧 파이프라인 모델. 서브에이전트는 frontmatter `model: inherit`로 자동 추종.
# 특정 모델을 강제하려면:  CONTRIB_MODEL=opus ./oss-contrib-session.sh :iceberg-core
MODEL_FLAG=""
[[ -n "${CONTRIB_MODEL:-}" ]] && MODEL_FLAG="--model ${CONTRIB_MODEL}"

CYAN="\033[0;36m"; GREEN="\033[0;32m"; YELLOW="\033[1;33m"; NC="\033[0m"
log() { echo -e "${CYAN}[iceberg-contrib]${NC} $1"; }

# ⚠️ Iceberg는 Gradle 프로젝트 path와 디렉터리가 단순 변환되지 않는다:
#    :iceberg-core → core/, :iceberg-spark:spark-4.1_2.13 → spark/v4.1/.
# 그래서 디렉터리 추정은 best-effort로만 하고, 없으면 실패시키지 않고 경고만 한다
# (정확한 디렉터리는 오케스트레이터/에이전트가 settings.gradle·`{module}:properties`로 확인한다).
mod_to_dir_guess() {
    local m="${1#:}"            # 선행 콜론 제거
    m="${m%%:*}"               # 첫 세그먼트만
    echo "${m#iceberg-}"       # iceberg- 접두사 제거(:iceberg-core→core)
}

# --- Preflight ---
command -v claude  >/dev/null || { echo "claude CLI not found"; exit 1; }
command -v gh      >/dev/null || { echo "gh not found"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh 미인증 — 'gh auth login' 먼저 실행"; exit 1; }
[[ -x "$PROJECT_ROOT/gradlew" ]] || { echo "gradlew 실행파일 없음: $PROJECT_ROOT/gradlew"; exit 1; }
[[ -d "$PROJECT_ROOT" ]] || { echo "main_root 디렉토리 없음: $PROJECT_ROOT"; exit 1; }
[[ -d "$PLUGIN_DIR"   ]] || { echo "plugin_dir 디렉토리 없음: $PLUGIN_DIR"; exit 1; }
for m in "${MODULES[@]:-}"; do
    [[ -z "$m" ]] && continue
    [[ "$m" == docs || "$m" == *.md ]] && continue       # 문서 스코프는 디렉토리 검사 생략
    d=$(mod_to_dir_guess "$m")
    # best-effort 경고만 — path↔dir 불일치(spark/flink 버전 서브모듈)에서 hard-fail 하지 않는다.
    [[ -d "$PROJECT_ROOT/$d" ]] || echo -e "${YELLOW}WARN${NC}: 디렉터리 추정 실패 '$PROJECT_ROOT/$d' (Gradle path '$m') — 에이전트가 settings.gradle로 확인합니다."
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
    printf 'claude %s --plugin-dir %q %q' "$MODEL_FLAG" "$PLUGIN_DIR" "/oss-contrib-iceberg:oss-contrib ${module} ${PROMPT_SUFFIX}"
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
    tmux new-session -d -s "$SESSION" -c "$PROJECT_ROOT" -x "$(tput cols)" -y "$(tput lines)"
    tmux rename-window -t "$SESSION:0" "contrib"
    if [[ -n "$module" ]]; then
        log "Auto mode: ${GREEN}${module}${NC}"
        tmux send-keys -t "$SESSION:0.0" "$(claude_for "$module")" Enter
    else
        log "Interactive mode"
        tmux send-keys -t "$SESSION:0.0" "claude $MODEL_FLAG --plugin-dir $PLUGIN_DIR" Enter
    fi
    tmux split-window -h -t "$SESSION:0.0" -p 35 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.1" "$(monitor_cmd)" Enter
    tmux split-window -v -t "$SESSION:0.0" -p 30 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.2" "echo '=== Build / Test ===' && echo '  ./gradlew ${module:-<:module>}:check   (revapi: published 모듈만)'" Enter
    tmux split-window -v -t "$SESSION:0.1" -p 30 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.3" "echo '=== Git / Submit (사람이 수동 제출) ===' && git -C '$PROJECT_ROOT' worktree list" Enter
    tmux select-pane -t "$SESSION:0.0"
}

# 2-3 모듈: 모듈당 Claude pane + monitor + shell
setup_multi_pane() {
    log "Parallel mode: ${GREEN}${#MODULES[@]} modules${NC} — ${MODULES[*]}"
    tmux new-session -d -s "$SESSION" -c "$PROJECT_ROOT" -x "$(tput cols)" -y "$(tput lines)"
    tmux rename-window -t "$SESSION:0" "contrib"
    tmux send-keys -t "$SESSION:0.0" "$(claude_for "${MODULES[0]}")" Enter
    tmux split-window -h -t "$SESSION:0.0" -p 66 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.1" "$(claude_for "${MODULES[1]}")" Enter
    tmux split-window -h -t "$SESSION:0.1" -p 50 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.2" "$(monitor_cmd)" Enter
    tmux split-window -v -t "$SESSION:0.0" -p 30 -c "$PROJECT_ROOT"
    if [[ ${#MODULES[@]} -ge 3 ]]; then
        tmux send-keys -t "$SESSION:0.3" "$(claude_for "${MODULES[2]}")" Enter
    else
        tmux send-keys -t "$SESSION:0.3" "echo '=== Build / Test === Ready.'" Enter
    fi
    tmux split-window -h -t "$SESSION:0.3" -p 50 -c "$PROJECT_ROOT"
    tmux send-keys -t "$SESSION:0.4" "echo '=== Git / Submit ===' && git -C '$PROJECT_ROOT' worktree list" Enter
    tmux select-pane -t "$SESSION:0.0"
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
