# run-task Pipeline — ringle-fullstack 전용 task 실행 파이프라인

`docs/tasks/task-NN`(무엇을) + `docs/prompts/prompt-NN`(어떤 순서·기준으로) 문서 쌍을
입력으로 받아 **탐색 → 질문 → 설계 → 구현 → 리뷰 → 기록**을 오케스트레이션한다.
task마다 작업 브랜치·GitHub 이슈·PR을 만들고, 사용자 승인 후 main에 merge한다.
진입점은 `.claude/skills/run-task` 스킬이다.

## 계보와 무엇이 다른가

| 항목 | feature-dev (참조) | impl-pipeline (참조) | **run-task (이것)** |
|------|--------------------|----------------------|---------------------|
| 입력 | 자연어 기능 설명 | GitHub issue + 설계 산출물 | **docs/tasks·prompts 문서 쌍 (번호 하나)** |
| 스펙 정규화 | 없음 | spec-analyst가 spec.md 생성 | **불필요 — prompt 문서가 이미 정규화된 스펙** |
| 설정 외부화 | 없음 | impl-config.yml | **run-task-config.yml** |
| 학습 메모리 | 없음 | 전용 _memory.md | **기존 MEMORY.md/ARCHITECTURE.md 재사용 (새 파일 안 만듦)** |
| 쓰기 격리 | 없음(현재 트리) | worktree (issue 병렬) | **task 브랜치 (task는 순차 의존이라 worktree 불필요)** |
| 상태 관리 | todo | issue별 state.md | **task별 state.md → task-07 문서화 재료** |
| 제출 | 없음 | validate 스킬이 fork push·PR | **push·이슈·PR까지 파이프라인, main merge는 사용자 승인 후** |

참조 원본 두 개는 `reference/` 아래 그대로 보존한다(읽기 전용).

## 구조
```
pipeline/
├── run-task-config.yml        # 단일 진실 소스: 경로·커맨드·규약·git 플로우·에이전트 fan-out
├── run-task-agent/            # Claude Code 플러그인 (name: ringle-run-task)
│   ├── .claude-plugin/plugin.json
│   ├── commands/run-task.md   # 얇은 진입 명령 — 스킬 문서를 따른다
│   └── agents/                # 에이전트 4종 원본 (.claude/agents/가 여기로 링크)
│       ├── ringle-code-explorer.md    # 읽기 전용 탐색, 핵심 파일 5~10개 반환
│       ├── ringle-code-architect.md   # prompt 1단계 설계 항목 → 청사진 (큰 변경에만)
│       ├── ringle-implementer.md      # 코드+테스트+검증 커맨드 그린+커밋 (push 금지)
│       └── ringle-code-reviewer.md    # confidence ≥ 80만 보고
├── output/
│   ├── templates/             # state / issue / pr 템플릿
│   ├── task-NN/state.md       # task 단위 상태 — 단계 이력·가정·이슈/PR 번호 (재실행 기준점)
│   └── yyyyMMddHHmmss/        # 실행(run) 단위 산출물
│       ├── issues/task-NN-issue.md   # 이슈 초안 → gh issue create --body-file
│       └── prs/task-NN-pr.md         # PR 초안 → gh pr create --body-file
└── reference/                 # 참조 파이프라인 원본 (feature-dev 커스텀 2종)
```

## 실행
```bash
# 저장소 세션에서 (기본 진입점)
/run-task 01

# 플러그인을 직접 로드하는 새 세션
claude --plugin-dir pipeline/run-task-agent "/ringle-run-task:run-task 01"

# run 산출물 최종 검증·제출·마킹 (검증 성공 시 폴더명에 '-' 마킹)
/run-task-validate pipeline/output/20260706173000
```
에이전트는 `.claude/agents/`의 심볼릭 링크로 저장소 세션에 로드된다.
에이전트는 세션 시작 시 읽히므로, 링크를 만든 직후에는 세션을 새로 열어야 보인다.

## task 단위 git 플로우
```
설계 승인 → gh issue create + git checkout -b task/NN-<slug>
→ 구현·커밋 (implementer, push 금지)
→ 검증·리뷰 → 지식 문서 갱신 커밋
→ git push + gh pr create (Closes #이슈)
→ [사용자 승인] → gh pr merge --merge --delete-branch → main 동기화
```

## 휴먼 게이트 (사람이 답하는 지점)
1. 3단계 질문 목록에 답한다.
2. 4단계 설계안을 승인한다 — 승인 전에는 코드를 만들지 않는다.
3. 6단계 리뷰 지적의 수정 여부를 결정한다.
4. 7단계 PR의 main merge를 승인한다. 실제 키가 필요한 수동 검증(브라우저·음성)을 수행한다.
