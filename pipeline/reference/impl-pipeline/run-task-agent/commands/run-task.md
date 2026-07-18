---
description: docs/tasks의 계획된 task 하나를 run-task 파이프라인(탐색→질문→설계→구현→리뷰→기록, task별 worktree 브랜치·커밋·push·이슈/PR 초안. 제출은 run-task-validate)으로 실행
argument-hint: "<task 번호 또는 문서 경로> (예: 01, task-03)"
---

# run-task (플러그인 진입 명령)

이 명령은 저장소 스킬의 얇은 진입점이다. 오케스트레이션 절차의 단일 진실 소스는
`.claude/skills/run-task/SKILL.md`다 (여기 절차를 복사해 두지 않는다 — 한 출처 유지).

1. 저장소 루트에서 `.claude/skills/run-task/SKILL.md`를 읽는다.
2. `$ARGUMENTS`를 스킬의 인자로 삼아 스킬 문서의 단계(0~7)를 그대로 수행한다.
3. 서브에이전트는 이 플러그인의 4종을 쓴다:
   ringle-code-explorer / ringle-code-architect / ringle-implementer / ringle-code-reviewer.
