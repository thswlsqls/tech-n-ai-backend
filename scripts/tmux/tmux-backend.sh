#!/bin/bash
# =============================================================================
# tech-n-ai-backend tmux 개발 환경 스크립트
#
# 세션: backend-session
# 윈도우:
#   [0] project  — 인프라/프로젝트 상태 모니터링 (3-pane)
#   [1] module   — Claude Code 모듈 작업 (1-pane)
#   [2] test     — 단위/통합 테스트 (좌우, 2-pane)
#
# 사용법:
#   ./scripts/tmux-backend.sh
# =============================================================================

SESSION="backend-session"
BASE_DIR="/Users/m1/workspace/tech-n-ai/tech-n-ai-backend"

# -----------------------------------------------------------------------------
# 기존 세션이 있으면 attach
# -----------------------------------------------------------------------------
if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "세션 '$SESSION'이(가) 이미 존재합니다. attach 합니다."
    tmux attach-session -t "$SESSION"
    exit 0
fi

# -----------------------------------------------------------------------------
# 새 세션 생성
# -----------------------------------------------------------------------------
# project-window (3-pane):
#   ┌─────────────────┬─────────────────┐
#   │                 │ 1 redis         │
#   │ 0 docker-compose├─────────────────┤
#   │                 │ 2 git           │
#   └─────────────────┴─────────────────┘
#
# module-window:
#   ┌─────────────────────────────────────┐
#   │                                     │
#   │ 0 claude                            │
#   │                                     │
#   └─────────────────────────────────────┘
#   ※ 모듈 실행은 Jenkins deploy 파이프라인이 담당한다.
#
# test-window:    unit-pane(0, 50%)   | integration-pane(1, 50%)
# -----------------------------------------------------------------------------

tmux new-session -d -s "$SESSION" -n "project" -c "$BASE_DIR"

# --- project-window: 3-pane 구성 (좌 50% | 우상+우하 각 50%) ---
tmux split-window -h -t "$SESSION:project" -c "$BASE_DIR"
tmux split-window -v -t "$SESSION:project.1" -c "$BASE_DIR"

# pane 타이틀 설정
tmux select-pane -t "$SESSION:project.0" -T "docker-compose-pane"
tmux select-pane -t "$SESSION:project.1" -T "redis-pane"
tmux select-pane -t "$SESSION:project.2" -T "git-pane"

# 초기 명령: 각 pane에 상태 확인 명령 전송
tmux send-keys -t "$SESSION:project.0" "docker compose ps" C-m
tmux send-keys -t "$SESSION:project.1" "redis-cli ping" C-m
tmux send-keys -t "$SESSION:project.2" "git status" C-m

# 포커스를 docker-compose-pane으로
tmux select-pane -t "$SESSION:project.0"

# --- module-window: claude-pane 단일 구성 (모듈 실행은 Jenkins deploy가 담당) ---
tmux new-window -t "$SESSION" -n "module" -c "$BASE_DIR"
tmux select-pane -t "$SESSION:module.0" -T "claude-pane"

# --- test-window: 좌우 분할 ---
tmux new-window -t "$SESSION" -n "test" -c "$BASE_DIR"
tmux split-window -h -t "$SESSION:test" -c "$BASE_DIR"

tmux select-pane -t "$SESSION:test.0" -T "unit-pane"
tmux select-pane -t "$SESSION:test.1" -T "integration-pane"

# 포커스를 unit-pane으로
tmux select-pane -t "$SESSION:test.0"

# -----------------------------------------------------------------------------
# 초기 윈도우를 project로 선택 후 attach
# -----------------------------------------------------------------------------
tmux select-window -t "$SESSION:project"
tmux attach-session -t "$SESSION"
