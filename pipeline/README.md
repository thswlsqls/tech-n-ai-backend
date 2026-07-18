# impl Pipeline — tech-n-ai-backend 전용 구현 파이프라인

요구사항 입력(문서 경로 / task·prompt 쌍 / GitHub issue)을 정규화 스펙으로 옮기고,
**탐색 → 질문 → 설계 승인 → worktree 구현 → 리뷰 → push → 초안** 순서로 오케스트레이션하는
Claude Code 기반 파이프라인. 이슈·PR 초안은 `output/<yyyyMMddHHmmss>/`에 저장되고,
실제 제출은 `/impl-validate` 스킬이, main merge는 사용자가 한다.

규칙의 단일 진실 소스는 **`impl-config.yml`**이다. 이 README는 구조와 실행법만 담는다
(참고 구현에서 README↔config 드리프트가 실제로 발생한 교훈 — 규칙을 두 곳에 적지 않는다).

## 계보와 무엇이 다른가

`pipeline/reference/impl-pipeline`(run-task, ringle-fullstack용)과 그 안의 이전 세대
(iceberg spec-impl / oss-contrib)를 참고했다. 유지한 강점과 보완한 약점:

| 항목 | 참고 구현 | **impl (이것)** |
|------|-----------|-----------------|
| 오케스트레이터 로직 | 외부 SKILL.md 위임 → 복사 시 유실 사고 | **commands/impl.md에 자급자족** |
| 입력 | 세대마다 한 형태 고정 | **3형태(docs/task쌍/issue) → spec.md로 정규화** |
| 학습 루프 | run-task에서 자동 폴링 소실 | **_learnings.md 5섹션 + P0 pending PR 자동 폴링 복원, grep 선별 주입 상시** |
| 빌드 검증 | 단일 모듈 전제 | **CQRS 영향 모듈 전부 개별 테스트** |
| INVALID 처리 | 브랜치·초안 삭제(비가역) | **불변 보고 — 수정은 재실행으로(자기 저장소라 폐기보다 수정이 싸다)** |
| git 모델 | fork+upstream(OSS) | **단일 origin. push까지 파이프라인, 제출은 validate, merge는 사용자** |

## 구조
```
pipeline/
├── impl-config.yml            # 단일 진실 소스 — 경로·빌드·규약·CQRS 체크리스트·게이트·메모리 정책
├── README.md                  # 이 파일 (구조·실행법만)
├── SKILL.md                   # impl-validate 스킬: 4게이트 검증 → 이슈·PR 제출 → run 폴더 '-' 마킹
├── impl-agent/                # Claude Code 플러그인 (name: tech-n-ai-impl)
│   ├── .claude-plugin/plugin.json
│   ├── commands/impl.md       # 오케스트레이터 P0~P7 (로직 전부 이 파일에)
│   └── agents/
│       ├── impl-spec-analyst.md   # 입력 3형태 → 정규화 spec.md (비신뢰 입력 가드)
│       ├── impl-explorer.md       # 읽기 전용 탐색 + CQRS 영향 모듈 판별
│       ├── impl-architect.md      # 설계 청사진 (큰 변경에만)
│       ├── impl-implementer.md    # worktree→코드+테스트→모듈별 그린→커밋 (push 금지)
│       └── impl-reviewer.md       # confidence ≥ 80만 보고
├── tmux/impl-session.sh       # 1~3 작업 병렬 런처 (worktree로 쓰기 격리)
├── inputs/                    # (선택) task·prompt 쌍을 둘 위치: tasks/task-NN*.md, prompts/prompt-NN*.md
├── output/
│   ├── templates/             # spec / state / issue / pr
│   ├── _learnings.md          # closed-loop 메모리 — 실행할수록 똑똑해지는 단일 메커니즘
│   ├── <work-key>/            # 작업 단위: spec.md + state.md (재실행 기준점)
│   └── <yyyyMMddHHmmss>/      # 실행 단위: issues/ + prs/ (검증·제출 완료 시 '-' 마킹)
└── reference/                 # 참고 파이프라인 원본 (읽기 전용)
```

## 워크플로우
```
P0 Setup      config 로드 · main ff 최신화 · run 폴더 생성 · _learnings.md §0 pending PR을
              gh로 폴링해 merged/rejected를 §2 캘리브레이션에 자동 반영 (closed-loop)
P1 Ingest     impl-spec-analyst: 입력 3형태 → spec.md (수용 기준 + CQRS 영향 표시)
P2 Explore    impl-explorer ×1~3 병렬 → 핵심 파일은 오케스트레이터가 직접 읽음 · 영향 모듈 확정
P3 Clarify    미해결 질문을 사용자에게 (스킵 금지)
P4 Design     작은 변경은 인라인 / 큰 변경은 impl-architect → 사용자 승인 (승인 없이 구현 금지)
P5 Implement  impl-implementer: worktree({type}/{slug} 브랜치)→코드+테스트→영향 모듈별
              :test 그린→커밋({type} : [main] {설명})
P6 Review     impl-reviewer ×1~3 (confidence≥80) → 수정 여부는 사용자 결정
P7 Record     git push -u origin <branch> → 이슈·PR 초안 저장 → state.md·_learnings.md 갱신
              → /impl-validate 핸드오프 안내
```

## Quick Start
```bash
# 1회 준비
gh auth login
chmod +x pipeline/tmux/impl-session.sh
# validate 스킬 설치(심볼릭 링크)
mkdir -p ~/.claude/skills/impl-validate
ln -sf /Users/m1/workspace/tech-n-ai/tech-n-ai-backend/pipeline/SKILL.md ~/.claude/skills/impl-validate/SKILL.md

# (A) 플러그인 직접 호출
claude --plugin-dir pipeline/impl-agent \
  "/tech-n-ai-impl:impl docs=docs/reference/design/001-foo.md modules=:api-bookmark"
claude --plugin-dir pipeline/impl-agent "/tech-n-ai-impl:impl task=01"
claude --plugin-dir pipeline/impl-agent "/tech-n-ai-impl:impl issue=#12"

# (B) tmux 런처 — 인자 하나 = 작업 하나, 2~3개면 병렬 pane
cd pipeline/tmux
./impl-session.sh "issue=#12"
./impl-session.sh "task=01" "docs=path/to/req.md"      # 병렬 (worktree 격리)
IMPL_MODEL=opus ./impl-session.sh "issue=#12"          # 특정 모델 강제

# (C) 구현 완료 후 — 검증·제출
/impl-validate pipeline/output/<yyyyMMddHHmmss>
```

## 사람이 하는 일 (휴먼 게이트 — 상세는 config `human_gates`)
1. P3 질문 목록에 답한다.
2. P4 설계안을 승인한다 — 승인 전에는 코드를 만들지 않는다.
3. P6 리뷰 지적의 수정 여부를 결정한다.
4. P7 후 `/impl-validate`로 검증·제출을 맡긴다.
5. 제출된 PR을 확인하고 merge한다: `gh pr merge <pr> --merge --delete-branch`,
   이후 `git worktree remove <path>`. merge/리젝 결과는 다음 실행이 자동 학습한다.

## 다른 repo로 이식
`impl-config.yml`의 `[REPO-SPECIFIC]` 블록(project·build·cqrs_checklist·conventions·
sensitive_areas·authority)만 교체하고, 에이전트 프롬프트의 repo 고유 규칙(CQRS 체크리스트·
Jackson 3·docs/sql 관행)을 대상 repo 규칙으로 바꾼다. `[PIPELINE-GENERIC]` 블록은 그대로 둔다.

## 모델
에이전트 5종은 frontmatter `model: inherit`로 세션 모델을 그대로 따른다.
오케스트레이터를 가장 좋은 모델로 구동하면 전부 상속한다.
