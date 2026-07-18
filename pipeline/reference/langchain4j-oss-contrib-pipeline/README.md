# LangChain4j OSS Contribution Pipeline v2

LangChain4j 기여 후보를 모듈 단위로 발굴·검증하고, git 브랜치에 테스트와 함께 변경을 커밋한 뒤,
사람이 직접 제출할 이슈/PR 초안을 생성하는 Claude Code 기반 파이프라인.

v1(`../../v1/pipeline`)을 **단순화·강화**한 버전이다. 골격은 유지하되 과잉 부분을 덜어냈다.

## v1 대비 변경점

| 항목 | v1 | v2 |
|------|----|----|
| 출력 구조 | `oss-contrib-output/01-…07-` 7개 번호 디렉토리 | `output/<yyyyMMddHHmmss>/{candidates,issues,prs}` run 단위 3폴더 |
| 에이전트 수 | 6 (analyzer/finder/reviewer/implementer/issue-writer/pr-writer) | **3** (finder/reviewer/contributor) |
| 피드백 루프 | `oss-track` 커맨드 + GitHub 폴링 + `stats.md` 엔진 | **단일 `_learnings.md`** — run이 append, 다음 run이 입력으로 읽음 |
| 커맨드 수 | 2 (oss-contrib + oss-track) | **1** (oss-contrib) |
| 학습 방식 | open→closed loop(별도 추적) | 마크다운 한 장에 누적(외부 의존성 0) |

유지된 v1의 핵심: config 단일화, 사람이 제출, 테스트 필수, GO/NO-GO 검증, worktree 격리, 모듈 스코프 빌드.

## 구조

```
v2/pipeline/
├── contrib-config.yml          # 단일 설정(경로·빌드·규칙) — 이식 시 이것만 교체
├── oss-contrib-agent/          # Claude Code 플러그인
│   ├── .claude-plugin/plugin.json
│   ├── commands/oss-contrib.md # 오케스트레이터 (Phase 0~5)
│   └── agents/
│       ├── candidate-finder.md   # 발굴 + 점수화 → candidates/
│       ├── candidate-reviewer.md # GO/CAUTION/NO-GO 검증
│       └── contributor.md        # worktree+브랜치+코드+테스트+커밋 → issues/, prs/
├── tmux/oss-contrib-session.sh # 1~3 모듈 병렬 실행
└── output/
    ├── templates/              # candidate/issue/pr 템플릿(준수 대상)
    ├── _learnings.md           # ← 반복할수록 똑똑해지는 메모리
    └── <yyyyMMddHHmmss>/       # run마다 생성: candidates/ issues/ prs/
```

## 워크플로우

```
P0 Setup       config 로드, upstream remote 보장 + 로컬 main을 upstream/main으로 ff-only 최신화, gh 인증, <yyyyMMddHHmmss> 폴더 생성, _learnings.md 주입
P1 Discover    candidate-finder ×1~2 → candidates/ (25점 척도, ≥18). 고가치 클래스(prefer_classes) 우선, NPE/null-guard는 fallback(realistic-trigger·exhausted-higher 입증 필수) — config candidate_quality
P2 Verify      22점 이상 후보 각각 candidate-reviewer → GO/CAUTION/NO-GO. 강한 후보(22점 ∧ GO)가 2건 이상이면 전부 구현(아래)
P3 Implement   contributor: worktree→branch→코드+테스트→모듈빌드→commit (push·제출 안 함). 강한 후보 여럿이면 순차 구현
P4 Draft+Learn 구현한 각 기여의 issues/·prs/ 초안 확정 + _learnings.md append
P5 Handoff     요약 + 수동 제출 절차 + near-miss 후보 리마인드
```

## 권위 출처 우선순위 (기술 규칙)

1. **`CONTRIBUTING.md`** (langchain4j 루트) — 불변 규칙의 유일한 권위 출처
2. `CONTRIBUTION_GUIDE.md` — 실측 기반 컨벤션 요약
3. `_learnings.md` — 과거 run의 실제 결과(머지/리젝)

CONTRIBUTING.md에서 강제하는 핵심: *no tests, no review* / draft PR 우선 / breaking change 금지(@Deprecated) / 신규 통합은 community 레포 / 새 의존성 회피 / 작고 집중된 PR.

## Quick Start

```bash
# 1회 준비
gh auth login
chmod +x tmux/*.sh

# 단일 모듈 실행
cd tmux && ./oss-contrib-session.sh langchain4j-open-ai

# 또는 플러그인 직접
claude --plugin-dir /Users/m1/workspace/langchain4j/z-ebson/v2/pipeline/oss-contrib-agent \
  "/oss-contrib-v2:oss-contrib langchain4j-open-ai 범위내에서 기여 후보를 발굴/검증하고 이슈와 PR 초안을 생성하세요"
```

## 사람이 하는 일 (휴먼 게이트)

파이프라인은 절대 GitHub에 직접 제출하지 않는다(`gh issue/pr create` 미실행, push 안 함).

1. `<yyyyMMddHHmmss>/prs/` 초안의 **staleness 체크** 실행 → 필요시 rebase
2. `issues/` 초안 검토 → `gh issue create`로 제출 → 이슈 번호 확인
3. PR 초안의 `Closes #N` 갱신 → `gh pr create --draft`로 제출 (**draft 필수**)
4. 머지 결과를 `_learnings.md` 캘리브레이션 섹션에 한 줄 기록 → 다음 run이 더 똑똑해짐

## Requirements

- Claude Code, `gh` CLI (인증 필수)
- LangChain4j 로컬 클론 (origin = fork). upstream remote는 파이프라인이 자동 추가
- worktree는 `/Users/m1/workspace/langchain4j-worktrees/`에 생성

## 모델 (model)

3개 에이전트(finder/reviewer/contributor)는 frontmatter `model: inherit`로 **세션 모델을 그대로 따른다**.
이 파이프라인은 "머지 확률 > 양"이라 발굴·검증·구현이 전부 추론 품질에 직결되므로, 에이전트를
mid-tier로 다운그레이드하지 않고 오케스트레이터와 동일한 최고 모델을 쓰게 한다.
따라서 `claude`/`oss-contrib-session.sh`를 **가장 좋은 모델(예: Opus)로 구동**하면 모든 에이전트가
그 모델을 상속한다. 특정 티어를 강제하려면 각 에이전트 frontmatter의 `model:`을 `opus` 등으로 바꾸면 된다.
