# Apache Iceberg OSS Contribution Pipeline

Apache Iceberg 기여 후보를 모듈 단위로 발굴·검증하고, git 브랜치에 테스트와 함께 변경을 커밋한 뒤,
사람이 직접 제출할 PR(필요 시 이슈) 초안을 생성하는 Claude Code 기반 파이프라인.

langchain4j v2 → jenkins v3 → opentelemetry-java v4 파이프라인을 **Apache Iceberg에 이식**한 버전이다.
선행 버전의 강점(휴먼 게이트·worktree 격리·closed-loop 학습·monoculture 교정)은 그대로 가져오고,
Iceberg 고유 환경(Gradle 모듈 path≠디렉터리·revapi·Module: Description·Jackson 금지·PMC 스펙 게이트)에 맞게 규칙을 바꿨다.

## 계보와 Iceberg 적응점

| 항목 | v2 (langchain4j) | v3 (jenkins) | v4 (otel-java) | **Iceberg** |
|------|------------------|--------------|----------------|-------------|
| 빌드 | `./mvnw` (Maven) | `mvn` (Maven) | `./gradlew` (Gradle) | **`./gradlew` (Gradle, 모듈 path≠디렉터리)** |
| 베이스 | `main` | `master` | `main` | `main` |
| JDK | 17 | 21 | 빌드 21+/산출물 Java 8 | **17 또는 21 (다른 JDK면 빌드 실패)** |
| 검증 | spotless+unit | spotless+`-Plight-test` | `{module}:check` | **`{module}:check`(test+spotless+checkstyle+errorProne)** |
| API 호환성 | 없음 | 없음 | jApiCmp→apidiff 커밋 | **revapi(빌드-실패 게이트, 커밋할 diff 없음). 대상 6개 published 모듈** |
| PR 제목 | 자유 | 기계파싱 템플릿 | 명령형 문장 | **`Module: Description`(예 "Core: Fix ...")** |
| 직렬화 | 일반 | 일반 | 일반 | **Jackson 애너테이션 금지 → 커스텀 XxxParser.toJson/fromJson** |
| 값 표현 | — | — | — | **null over Optional, CloseableIterable over Stream** |
| 스펙 게이트 | community repo | 단일 repo | OTel spec 종속 | **format/·open-api/rest-catalog* = PMC 투표(찬성 3표)** |
| AI 기여 | — | — | — | **PR/이슈 본문에 disclosure 블록 안 넣음(사용자 결정) · 커밋 Generated-by 토큰만** |
| CLA | 수동 | 수동 | CNCF EasyCLA | **ASF ICLA/CCLA** |

### 유지한 강점
config 단일화 · 사람이 제출(휴먼 게이트) · 테스트 필수 · GO/NO-GO 검증 · worktree 격리 ·
모듈 스코프 빌드 · closed-loop `_learnings.md`(§0 제출추적 + `gh pr view` 자동 폴링) ·
NPE monoculture 교정(prefer_classes vs npe-guard-fallback) · series probe-first · 프롬프트 인젝션 가드.

### Iceberg 적응으로 바꾼 것
1. **모듈 path ≠ 디렉터리** — settings.gradle이 `project(':core').name='iceberg-core'`로 재명명한다. Gradle path는 `:iceberg-core`, 디렉터리는 `core/`(spark는 `:iceberg-spark:spark-4.1_2.13`→`spark/v4.1/`). 에이전트가 settings.gradle/`properties`로 확인한다.
2. **revapi 게이트** — 공개 API 호환성은 published 6개 모듈(api/core/parquet/orc/common/data)에서 `{module}:revapi`로 검사. 깨면 빌드실패. OTel apidiff와 달리 커밋할 diff 파일이 없다.
3. **PR 제목 `Module: Description`** — OTel 명령형 문장과 다르다.
4. **코딩 컨벤션** — null over Optional, CloseableIterable over Stream, 새 인터페이스 메서드는 default, Jackson 금지(커스텀 Parser), package-private 기본, Google Java Style, Apache 라이선스 헤더(spotless 강제).
5. **거버넌스 게이트** — format/·open-api/rest-catalog* 변경은 PMC 투표(찬성 3표, lazy consensus 없음) 영역이라 자동 기여 범위 밖(spec_gate gated).
6. **AI disclosure** — AGENTS.md는 disclosure 블록을 권고하나, 사용자 결정으로 PR/이슈 본문에는 넣지 않는다(커밋 Generated-by 토큰만 유지).

## 구조

```
z_ebson/pipeline/
├── contrib-config.yml          # 단일 설정(경로·빌드·규칙) — 이식 시 이것과 에이전트 규칙만 교체
├── oss-contrib-agent/          # Claude Code 플러그인 (name: oss-contrib-iceberg)
│   ├── .claude-plugin/plugin.json
│   ├── commands/oss-contrib.md # 오케스트레이터 (Phase 0~5)
│   └── agents/
│       ├── candidate-finder.md   # 발굴 + 점수화 → candidates/
│       ├── candidate-reviewer.md # GO/CAUTION/NO-GO 검증
│       └── contributor.md        # worktree+브랜치+코드+테스트+(revapi)+커밋 → prs/[, issues/]
├── tmux/oss-contrib-session.sh # 1~3 모듈 병렬 실행
└── output/
    ├── templates/              # candidate/issue(bug·feature)/pr 템플릿 (실재 — 준수 대상)
    ├── _learnings.md           # ← closed-loop 메모리(§0 제출추적 + §2 캘리브레이션)
    └── <yyyyMMddHHmmss>/       # run마다 생성: candidates/ + prs/ (issues/는 단순 오타 수정이 아닐 때)
```

## 워크플로우

```
P0 Setup       config 로드, upstream remote 보장 + 로컬 main을 upstream/main으로 ff-only 최신화,
               gh 인증, run 폴더 생성, _learnings.md 주입 + §0 pending PR 자동 상태 폴링(closed-loop)
P1 Discover    candidate-finder ×1~2 → candidates/ (25점 척도, ≥18). prefer_classes 우선,
               NPE/null-guard는 fallback(realistic-trigger·exhausted-higher 입증 필수). spec 게이트 명시.
P2 Verify      candidate-reviewer → GO/CAUTION/NO-GO (api breaking·revapi·새 인터페이스 default·
               null over Optional·Jackson 금지·테스트성·고감도·PMC 스펙 게이트·PR 제목·probe-first)
P3 Implement   contributor: worktree→branch→코드+테스트→spotlessApply+{module}:check
               →(공개 API면 {module}:revapi)→commit(push·제출 안 함; AI면 Generated-by 토큰)
P4 Draft+Learn 단순 오타 수정이 아니면 이슈 초안을 PR보다 먼저(동일 주제 open 이슈 있으면 참조만),
               PR 초안 확정(항상; Module: Description·분량가드·규칙 grep) + _learnings.md append
P5 Handoff     요약 + 수동 제출 절차(+ CLA 안내) + closed-loop 기록 양식 안내
```

## 권위 출처 우선순위 (기술 규칙)

1. **`CONTRIBUTING.md`** (루트 스텁 → <https://iceberg.apache.org/contribute/>) — 공식 기여 규칙
2. **`AGENTS.md`**(코딩 규약·모듈 경계·고감도 영역) + **`CLAUDE.md`**(빌드·모듈 경계)
3. **실제 Iceberg PR 관행** (<https://github.com/apache/iceberg/pulls>) — PR 양식
4. `_learnings.md` — 과거 run의 실제 결과(머지/리젝)

배경 정리물: `z_ebson/apache-iceberg-기여-가이드.md`(검증된 한글 기여 가이드). 핵심:
테스트 필수(docs-only 오타·주석 변경은 제외) / api breaking 거의 불허(revapi) / 작고 집중된 PR(`Module: Description`) / main 베이스 /
format·open-api는 PMC 투표 / null over Optional / Jackson 금지 / Apache 헤더.

## Quick Start

```bash
# 1회 준비
gh auth login
export JAVA_HOME=<JDK 17 또는 21 경로>     # 다른 JDK면 build.gradle이 빌드를 실패시킨다
chmod +x tmux/*.sh

# 단일 모듈 실행 (Gradle path)
cd tmux && ./oss-contrib-session.sh :iceberg-core

# 또는 플러그인 직접 — 오케스트레이터가 고른 모델을 그대로 사용(서브에이전트는 model: inherit)
claude --plugin-dir /Users/m1/workspace/iceberg/z_ebson/pipeline/oss-contrib-agent \
  "/oss-contrib-iceberg:oss-contrib :iceberg-core 범위내에서 기여 후보를 발굴/검증하고 PR 초안을 생성하세요"

# 특정 모델 강제:  CONTRIB_MODEL=opus ./oss-contrib-session.sh :iceberg-core
```

## 사람이 하는 일 (휴먼 게이트)

파이프라인은 절대 GitHub에 직접 제출하지 않는다(`gh issue/pr create` 미실행, push 안 함).

1. `<yyyyMMddHHmmss>/prs/` 초안의 **staleness 체크**(`HEAD..upstream/main`) 실행 → 필요시 rebase
2. (단순 오타 수정이 아니라 이슈 초안이 있을 때) `issues/` 초안 검토 → (format/·open-api면 dev 메일링 리스트 제안 선행 →) `gh issue create` 제출 → 번호 확인. 단순 오타 수정이면 이 단계 없음(PR-only).
3. PR 초안의 `Closes #N` 갱신(이슈 초안을 제출했으면 받은 번호로, 기존 이슈 참조면 그 번호로) → `gh pr create --base main`로 제출(제목 `Module: Description`)
4. **CLA**: ASF ICLA/CCLA 안내 → <https://www.apache.org/licenses/contributor-agreements.html>
5. **closed-loop 기록**: 제출 후 `_learnings.md` §0에 `- [module] PR=#N branch=… status=pending run=…` 한 줄 추가
   → 다음 run의 P0가 머지/리젝 결과를 자동으로 캘리브레이션에 반영 → 반복할수록 똑똑해진다

## Requirements

- Claude Code, `gh` CLI (인증 필수), **JDK 17 또는 21** (빌드)
- iceberg 로컬 클론 (origin = fork `thswlsqls/iceberg`). upstream(`apache/iceberg`)은 파이프라인이 자동 추가
- worktree는 `/Users/m1/workspace/iceberg-worktrees/`에 생성

## 모델 (model)

3개 에이전트(finder/reviewer/contributor)는 frontmatter `model: inherit`로 **세션 모델을 그대로 따른다**.
이 파이프라인은 "머지 확률 > 양"이라 발굴·검증·구현이 전부 추론 품질에 직결되므로, 오케스트레이터를
가장 좋은 모델로 구동하면 모든 에이전트가 그 모델을 상속한다.
