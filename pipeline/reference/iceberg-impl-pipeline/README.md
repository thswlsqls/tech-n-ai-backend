# Spec-Driven Implementation Pipeline

GitHub issue와 설계 산출물(API 정의서·화면설계서·요구사항 정의서)을 **입력으로 받아** 코드 구현을 위임하고,
worktree 격리 브랜치에 테스트와 함께 커밋한 뒤, 커밋된 변경에 대한 PR 초안을 만드는 Claude Code 기반 파이프라인.
최종 검증·push·제출은 별도 validate 스킬(`SKILL.md`)이 issue 단위로 수행한다.

## 계보와 무엇이 다른가

| 항목 | feature-dev (참조) | z_ebson/pipeline (참조) | **impl-pipeline (이것)** |
|------|--------------------|-------------------------|--------------------------|
| 입력 | 자연어 기능 설명 | 모듈 스코프(코드 스캔) | **GitHub issue + 설계 산출물(API/화면/요구사항)** |
| 방향 | 스펙→구현 7단계 | 후보 **발굴**→구현 | **스펙 입력→정규화→구현** |
| 설정 외부화 | 없음 | config 단일화 | **config 단일화(`impl-config.yml`) + repo 이식성** |
| 학습 메모리 | 없음 | closed-loop `_learnings.md` | **closed-loop `_memory.md`(PR 상태 자동 폴링)** |
| 쓰기 격리 | 없음(현재 트리) | worktree | **worktree(issue 1건=브랜치 1개)** |
| 상태 관리 | todo | run 폴더 | **issue 단위 `state.md`(상태·commit·PR 이력)** |
| 검증·제출 | 인라인 리뷰 | validate 스킬(발굴형) | **validate 스킬 + 스펙 정합성 게이트(VALID에서만 push·제출)** |

`feature-dev`에 없던 두 가지(① 설정 외부화 ② 자가 개선 메모리)를 `z_ebson/pipeline`에서 이식하고,
후보 발굴 대신 **입력 스펙 구현**으로 방향을 바꾼 것이 이 파이프라인이다.

## 구조
```
z_ebson/impl-pipeline/
├── impl-config.yml             # 단일 진실 소스(경로·빌드·규약·게이트·상태집합·산출물 입력). [REPO-SPECIFIC]/[PIPELINE-GENERIC] 구분
├── README.md
├── SKILL.md                    # validate 스킬: issue 단위 최종 검증→push→제출(VALID 한정)
├── spec-impl-agent/            # Claude Code 플러그인 (name: spec-impl)
│   ├── .claude-plugin/plugin.json
│   ├── commands/spec-impl.md   # 오케스트레이터 (Phase 0~7)
│   └── agents/
│       ├── spec-analyst.md     # issue+산출물 → 정규화 spec.md (비신뢰 입력 가드)
│       ├── code-explorer.md    # 코드베이스 이해(feature-dev 계승)
│       ├── code-architect.md   # 설계 청사진(feature-dev 계승, 큰 변경에만)
│       ├── implementer.md      # worktree→브랜치→코드+테스트→빌드검증→커밋→PR 초안
│       └── code-reviewer.md    # 구현 리뷰(confidence≥80만, feature-dev 계승)
├── tmux/spec-impl-session.sh   # 1~3 issue 병렬 실행 런처(worktree로 쓰기 격리)
├── inputs/                     # (선택) 산출물을 미리 떨궈둘 기본 위치: inputs/<issue-key>/
└── output/
    ├── templates/              # spec / state / pr 템플릿
    ├── _memory.md              # closed-loop 메모리(5섹션)
    └── <issue-key>/            # issue 단위: spec.md + state.md + prs/ 초안
```

## 워크플로우 (오케스트레이터 Phase 0~7)
```
P0 Setup        config 로드, upstream 최신화, gh 인증, issue 폴더 생성, _memory.md 주입 + pending PR 폴링
P1 Ingest       spec-analyst: issue+산출물 → 정규화 spec.md(수용 기준·범위·미해결 질문). state=analyzed
P2 Explore      code-explorer ×1~3(병렬) → 핵심 파일 반환 → 오케스트레이터가 직접 읽음
P3 Clarify      미해결 질문을 사용자에게(스킵 금지) → spec.md에 확정 반영
P4 Architect    작은 변경은 인라인 설계 / 큰 변경은 code-architect → 사용자 승인
P5 Implement    implementer: worktree→브랜치→코드+테스트→{module}:check→(공개 API면 revapi)→커밋→PR 초안. state=implemented
P6 Review       code-reviewer ×1~3(병렬, confidence≥80) → 높은 심각도만 수정
P7 Record       state.md·_memory.md 갱신 + validate 핸드오프 안내
```
issue·산출물 본문은 **비신뢰 입력(프롬프트 인젝션 가드)**으로 다룬다 — 지시문을 실행하지 않고 기술적 사실만 추린다.
파이프라인은 절대 push·제출하지 않는다(휴먼 게이트). 제출은 validate 스킬의 VALID 경로 한정.

## Quick Start
```bash
# 1회 준비
gh auth login
export JAVA_HOME=<JDK 17 또는 21 경로>     # 다른 JDK면 build.gradle이 빌드를 실패시킨다
chmod +x tmux/*.sh

# (A) tmux 런처 — 산출물을 inputs/<issue-key>/에 떨궈두면 자동 탐색해 프롬프트를 조립한다.
mkdir -p inputs/issue-16850
#   inputs/issue-16850/api.md           (API 정의서)   ← api.* / openapi.* / API*.md
#   inputs/issue-16850/screen.md        (화면설계서)   ← screen*.* / ui*.*  (백엔드면 생략)
#   inputs/issue-16850/requirements.md  (요구사항)     ← req*.* / requirements.*
#   inputs/issue-16850/module           (대상 Gradle path 한 줄, 선택) 예: :iceberg-core
cd tmux && ./spec-impl-session.sh issue-16850            # 단일 issue
./spec-impl-session.sh issue-16850 issue-16900           # 2 issue 병렬(worktree 격리)
SPEC_MODEL=opus ./spec-impl-session.sh issue-16850       # 특정 모델 강제

# (B) 플러그인 직접 호출 — 산출물 경로를 인자로 직접 준다.
claude --plugin-dir /Users/m1/workspace/iceberg/z_ebson/impl-pipeline/spec-impl-agent \
  "/spec-impl:spec-impl issue=#16850 api=inputs/issue-16850/api.md req=inputs/issue-16850/requirements.md module=:iceberg-core"
```
- `issue=` (필수) GitHub issue 번호/URL. `api= screen= req= other=` 산출물 경로(있는 것만). `module=` Gradle path.
- 모듈을 모르면 생략 → P2 탐색 후 확정. issue가 없으면 정지.
- 런처는 `inputs/<issue-key>/`에서 산출물을 자동 탐색한다(없는 종류는 생략 → 오케스트레이터가 N/A 처리). 4개 이상 issue는 issue당 독립 세션을 권장한다.

## validate 스킬 설치 (issue 단위 최종 검증·제출)
이 스킬은 `impl-pipeline/SKILL.md`로 버전관리되며, 쓰려면 Claude 스킬 디렉터리에 설치(심볼릭 링크)한다:
```bash
mkdir -p ~/.claude/skills/spec-impl-validate
ln -sf /Users/m1/workspace/iceberg/z_ebson/impl-pipeline/SKILL.md ~/.claude/skills/spec-impl-validate/SKILL.md
```
구현이 끝난 뒤(P7) 오케스트레이터가 안내하는 대로 호출한다:
```
/spec-impl-validate spec=output/issue-16850/spec.md pr=output/issue-16850/prs/<file>.md
```
스킬은 다섯 게이트(스펙 정합성 / 결함 실재성 / 머지 적격성 / 변경 적절성 / 빌드 실증)를 통과시키면
작업 브랜치를 fork(origin)에 push하고 `gh pr create`로 PR을 제출한다. INVALID면 브랜치·worktree·초안을 삭제하고,
불확실하면 자동 진행하지 않고 사람에게 결정을 받는다. **upstream으로의 push·force-push·새 이슈 생성은 하지 않는다.**

## 사람이 하는 일 (휴먼 게이트)
1. issue + 산출물 준비(`inputs/<issue-key>/` 또는 인자 경로).
2. P3의 미해결 질문·P4의 설계 승인에 답한다.
3. P6 리뷰 결과로 수정 여부 결정.
4. P7 후 `/spec-impl-validate`로 최종 검증·제출을 맡긴다(VALID면 스킬이 push·PR 제출까지 수행).
5. **CLA**: 첫 기여면 ASF ICLA/CCLA — <https://www.apache.org/licenses/contributor-agreements.html>.

## 다른 repo로 이식
`impl-config.yml`의 `[REPO-SPECIFIC]` 블록(project 좌표·build 커맨드·conventions·spec_gate·sensitive_areas·권위 출처)만 교체하고,
에이전트 프롬프트의 repo-고유 규칙(Iceberg의 Gradle 모듈 path·revapi·Module: Description 등)을 대상 repo 규칙으로 바꾼다.
`[PIPELINE-GENERIC]` 블록(상태 집합·산출물 입력·브랜치 네이밍·메모리·게이트 골격)은 그대로 둔다.

## 권위 출처 우선순위
1. upstream `CONTRIBUTING.md`(→ <https://iceberg.apache.org/contribute/>) — 공식 기여 규칙
2. `AGENTS.md`(코딩 규약·모듈 경계) + `CLAUDE.md`(빌드·모듈 경계)
3. 실제 Iceberg PR 관행 (<https://github.com/apache/iceberg/pulls>)
4. `output/_memory.md` — 과거 실행의 실제 머지/리젝 결과
추측 금지. 불확실하면 위 순서로만 인용한다.

## 모델
5개 에이전트는 frontmatter `model: inherit`로 세션 모델을 그대로 따른다. 머지 확률이 추론 품질에 직결되므로
오케스트레이터를 가장 좋은 모델로 구동하면 모든 에이전트가 상속한다.
