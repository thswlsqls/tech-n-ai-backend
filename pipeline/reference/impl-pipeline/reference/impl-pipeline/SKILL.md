---
name: spec-impl-validate
description: 스펙-주도 구현 파이프라인(z_ebson/impl-pipeline)이 만든 산출물(정규화 spec.md·PR 초안·작업 브랜치)을 issue 단위로 최종 검증하고 마감한다. 사용자가 spec 문서 경로와 PR 초안 경로를 인자로 주며 검증·제출을 맡길 때 사용. VALID면 산출물을 정제(≤30% 절감)하고 PR 제목·작업브랜치명을 보강한 뒤, 작업 브랜치를 fork(origin)로 push하고 gh pr create로 PR을 제출하며 state.md 상태를 submitted로 갱신한다. INVALID면 PR 초안과 브랜치(worktree 포함)를 삭제한다. 다섯 게이트(① 스펙 정합성=구현이 spec.md 수용 기준을 실제 충족하는가, ② 결함 실재성, ③ 머지 적격성=api breaking·revapi·테스트·의존성·sensitive·PMC 스펙 게이트, ④ 변경 적절성=범위 최소성·형제 일관성·교차 산출물 정합, ⑤ 빌드 실증=실제 빌드/테스트/revapi 재실행)로 검증한다. 코드/빌드 근거 있는 disqualifier만 무효 처리한다. 신규 구현이나 후보 발굴에는 쓰지 않는다.
---

# 스펙-주도 구현 산출물 검증/제출·마감기

이 스킬은 **이미 구현된** issue 단위 산출물(정규화 `spec.md` / PR 초안 / 작업 브랜치)을 입력으로 받아,
구현이 스펙을 충족하고 머지될 자격을 갖췄는지 **최종 검증**하고 그 결과로 마감한다.
새 코드를 구현하거나 스펙을 다시 만들지 않는다 — 그건 자매 파이프라인 `/spec-impl`의 역할이다.

**마감 = 세 갈래(자동)**:
- **VALID** → 산출물 정제(≤30% 절감) → PR 제목·작업브랜치명 보강 → (빌드 실증으로 그린 확인) → staleness 확인 → 브랜치 **push**(fork=origin) → **`gh pr create`로 PR 제출**(issue는 이미 있으므로 `Closes #<number>`만 채움, 새 이슈 생성 안 함) → `state.md` 상태를 **submitted**로 갱신 + `_memory.md` §0에 `status=pending` 기록. Iceberg는 draft 강제가 아니므로 일반 PR로 연다.
- **INVALID** → PR 초안과 브랜치(worktree 포함, 푸시됐다면 원격 브랜치도) **삭제(비가역)**. `state.md`에 ABANDONED 기록.
- **불확실**(코드/빌드로 결론 불가) → 제출·삭제 **둘 다 자동 수행 안 함**. 게이트별 근거와 함께 사람에게 결정을 받는다.

**위치 — 최종 게이트(over-engineering 금지)**: 산출물은 이미 파이프라인의 spec-analyst·code-reviewer를 거쳤다.
이 스킬은 그걸 처음부터 재수행하지 않는다. 머지를 실제로 막는 **disqualifier만 코드/빌드 근거로** 확인한다.
VALID 제출은 공개·비가역(PR이 apache/iceberg에 게시)이고 INVALID는 브랜치+초안을 삭제(비가역)하므로,
어느 쪽도 나이트픽·취향·약한 의심만으로 결정해 정상 기여를 폐기하거나 결함을 제출하지 않는다.

## 인자 파싱
- **spec 문서**(필수): `output/<issue-key>/spec.md`
- **PR 초안**(필수): `output/<issue-key>/prs/<file>.md`
- **state 문서**(선택, 같은 폴더의 `state.md` 자동 탐색)
경로를 못 찾으면 추측하지 말고 사용자에게 묻는다. spec 또는 PR 초안이 없으면 검증 불가이므로 중단한다.

## 고정 자산 (단일 진실 소스는 config)
- config: `/Users/m1/workspace/iceberg/z_ebson/impl-pipeline/impl-config.yml`
  → `project`(fork/upstream/default_branch), `paths`(main_root/worktree_dir/memory_file/output_dir), `build`, `conventions`, `spec_gate`, `sensitive_areas`, `doc_limits`, `validate_gates`, `issue_state`를 읽어 이후 전부 이 값을 쓴다.
  `project.fork`에서 `FORK_OWNER`(슬래시 앞)를 분리해 둔다(PR `--head`에 사용).
- 메모리: `paths.memory_file`(=`output/_memory.md`).
- 권위 출처: upstream `CONTRIBUTING.md`(→iceberg.apache.org/contribute/, 1순위) > `AGENTS.md`+`CLAUDE.md` > 실제 PR 관행 > `_memory.md`. 추측 금지.
- 격리: `z_ebson/` 아래 파일은 기여 코드가 아니다 — 절대 upstream 커밋·푸시·PR diff에 포함하지 않는다.
- git 리모트: `origin`=fork, `upstream`=원본. 브랜치는 fork(`origin`)로만 push, PR은 `--head <fork-owner>:<branch>`로 upstream에 연다.

**모델 상속**: 검증은 추론 품질에 직결되므로(잘못 valid면 리젝 PR, 잘못 invalid면 정상 기여 폐기) 서브에이전트를 쓰면 `model`을 지정하지 말고 세션 모델을 상속시킨다.

## Phase A: 로드 & 컨텍스트 복원
1. `CONFIG_FILE`을 읽어 위 값들을 추출한다. `RUN_DIR`(=`output/<issue-key>`)을 입력 경로의 부모로 확정(spec.md의 부모, prs/의 부모).
2. **spec.md**를 읽어 추출한다: **요구사항**, **API 계약**, **수용 기준(AC 목록)** — ★ spec_conformance 게이트의 검사 대상 —, **범위 경계**, 대상 모듈, spec 게이트(exempt/gated).
3. **PR 초안**을 읽어 제목·`Closes #<number>`·Summary·`Testing done`(수용 기준↔테스트 매핑)·관련 링크를 파악한다.
4. **state.md**(있으면) 읽어 현재 상태·브랜치·worktree·commit 해시를 가져온다.
5. **브랜치·worktree 역추적**(삭제·제출 안전성의 전제):
   - PR 초안 헬퍼의 `Branch:` 주석 + state.md의 브랜치 → `git -C "$MAIN_ROOT" branch --list`·`git worktree list`로 확인. ⚠️ worktree 경로 = `WORKTREE_DIR/<branch를 -로 치환>`.
   - **확정 검증**: `git -C "$MAIN_ROOT" log --oneline upstream/<default>..<branch>`의 커밋 제목이 PR 제목(`Module: Description`)과 일치하는지. 일치해야 "이 issue의 브랜치"로 확정. 불일치/불확실하면 삭제·제출 전 사용자 확인.

## Phase B: 자격 최종 검증 (다섯 게이트)
각 게이트는 **명확한 코드/빌드 근거로 disqualifier가 확정될 때만** 실패 처리한다(반증 우선, 약한 의심은 실패 아님). 주장은 믿지 말고 인용한 코드·커밋 diff·빌드 결과를 직접 연다.

### B-0. 스펙 정합성 게이트 — 구현이 spec.md 수용 기준을 실제 충족하는가 (★신규, 스펙-주도 고유)
이 게이트가 이 스킬을 "기여 검증"에서 "스펙 충족 검증"으로 끌어올린다.
- **AC 커버리지**: spec.md의 수용 기준(AC1..ACn)을 하나씩 본다. 각 AC에 대해 (a) 구현 코드가 그 동작을 실제로 하는가(커밋 diff에서 확인), (b) 그 AC를 커버하는 테스트가 커밋에 실재하는가(PR `Testing done`의 매핑이 거짓이 아닌가)를 확인한다.
- **누락**: AC 중 구현·테스트가 빠진 게 있으면 → INVALID(미완성 구현) 또는 불확실(범위 축소가 spec 미해결 질문에서 합의됐으면 통과). 어느 AC가 왜 빠졌는지 명시.
- **API 계약 일치**: spec.md의 API 계약(시그니처·입출력·에러)과 구현이 어긋나면 → INVALID.
- **범위 초과**: spec 범위 경계의 "제외"를 구현이 침범했으면 → B-3(a)로 넘겨 다룬다.

### B-1. 결함 실재성 게이트 — 풀려는 문제/요구가 실재하는가
- spec이 근거한 issue 요구가 실재하고, 현재 코드가 그 요구를 아직 충족하지 못함을 확인(이미 구현돼 있었으면 중복 → INVALID).
- 구현이 바꾼 동작이 **의도된 기존 동작**(Javadoc/주석/기존 테스트/스펙 규정)을 깨지 않는가. 기존 테스트가 옛 동작을 assert하는데 구현이 그걸 바꿨다면, spec이 그 변경을 정당화하는지 확인(아니면 INVALID).

### B-2. 머지 적격성 게이트 — CONTRIBUTING/config 위반이 없는가
코드로 확정 가능한 disqualifier만 본다(권위: `CONTRIBUTING.md` 1순위, config `conventions`/`sensitive_areas`/`spec_gate` 캐시).
- **api/ breaking** 없음 — REVAPI 대상(api/core/parquet/orc/common/data) 공개 API/동작 비호환은 거의 불허(`@Deprecated` 사이클 위임). 위반 시 INVALID(B-4에서 실제 revapi로 확정).
- **새 인터페이스 메서드 default** — 공개 인터페이스 추가 메서드가 추상이면 INVALID.
- **null over Optional** — 새 public 시그니처가 `Optional`을 도입하면 CAUTION(정렬 요구).
- **Jackson 금지** — 직렬화에 `@Json*`/`com.fasterxml` 추가면 INVALID(커스텀 Parser여야 함). `grep -nE '@Json|com\.fasterxml' <changed>`.
- **테스트 동반** — 동작 변경에 단위테스트(JUnit5+AssertJ, `Test*`) 없으면 INVALID. `integrationTest`만으로 커버되면 자격 약화(불확실).
- **새 의존성** 없음 — `gradle/libs.versions.toml`/`versions.props` 추가는 INVALID(Ask first).
- **sensitive_areas** 미접촉 — config `sensitive_areas`를 명시 승인 없이 접촉하면 INVALID. `.asf.yaml`/`LICENSE`/`NOTICE` 접촉 → INVALID.
- **spec/거버넌스 게이트** — 변경이 `pmc_vote_paths`(`format/`·`open-api/rest-catalog*`)를 건드리면 PMC 투표 영역 → 자동 범위 밖 → INVALID(programmatic-only로 축소 가능하면 불확실). `exempt`면 통과.
- **모듈 경계** — 엔진(Spark/Flink) 개념이 core/data로 누출되면 INVALID.
- **PR 제목·라이선스·커밋 토큰** — 제목이 `Module: Description`인가? 신규 파일에 Apache 라이선스 헤더(spotless 통과로 B-4 확인)? AI 보조면 커밋에 `Generated-by:` 토큰(PR 본문엔 disclosure 블록 금지 — 옛 초안에 있으면 제거).

### B-3. 변경 적절성 게이트 — 자격을 적절히 구현했는가
브랜치 커밋의 실제 diff를 읽는다(`git -C "$MAIN_ROOT" show <branch>` 또는 worktree에서 `git diff upstream/<default>`). 세 축:
- **(a) 범위 최소성** — diff가 spec TO-BE/수용 기준**만** 구현하는가. 범위를 벗어난 변경(무관 리팩터·추가 기능·대량 재포맷·불필요한 추상화·방어 코드 남발)이 섞였으면 CAUTION/INVALID(분리 요구). 단 `spotlessApply` 강제 포맷처럼 타당한 사유가 명시되면 예외. 기준: 그 줄을 빼면 수용 기준이 안 맞춰지는가.
- **(b) 직속 형제 일관성** — diff가 형제 구현/내부 선례 패턴을 미러링하는가(명명·시그니처·에러처리·테스트 스타일). 형제 엔진/포맷/카탈로그 중 한쪽만 고친 series면 나머지를 한 PR에 욱여넣지 않았는가. 사유 없는 비일관 → CAUTION(정렬 요구).
- **(c) 교차 산출물 정합** — **spec 수용 기준 ↔ 커밋 diff ↔ PR `Testing done`** 세 곳이 일치하는가. spec이 약속한 동작과 실제 diff가 다르거나, PR이 "추가했다"는 테스트가 커밋에 없으면 → INVALID(허위 보고). `git show <branch> --stat`로 변경 파일을 PR 주장과 대조.

### B-4. 빌드 실증 게이트 — 주장이 아니라 실제로 통과하는가
PR `Testing done`을 그대로 믿지 말고, **가능하면 브랜치에서 실제 빌드/테스트를 재실행**한다.
1. **환경 확인**: `java -version`이 17 또는 21인가(아니면 build.gradle이 빌드 실패). 아니면 재실행을 건너뛰고 **환경 제약을 정직히 보고**(통과 위장 금지).
2. worktree(Phase A-5 확정)에서 변경 모듈을 config `build` 커맨드로 검증(전체 빌드 금지):
   ```bash
   cd "<worktree>"
   ./gradlew {module}:spotlessCheck    # 포맷·라이선스 헤더
   ./gradlew {module}:test             # 추가 테스트 포함 통과(단일은 --tests "<FQCN>")
   ./gradlew {module}:revapi           # REVAPI 대상 공개 API 변경 시에만
   ```
   - 테스트 실패 → INVALID(허위 통과). revapi 실패 → INVALID(api breaking). spotlessCheck 실패 → CAUTION(`spotlessApply` 후 재커밋).
   - 모듈 path↔디렉터리 불일치 주의(`:iceberg-core`→`core/`). 빌드는 worktree 루트에서 Gradle path로 실행.
3. **시간·환경 비용이 크면** 부분 재실행하고 무엇을 실행/생략했는지 보고에 명시(생략은 INVALID 사유 아님 → 불확실). docs-only는 `spotlessCheck`만으로 충분.

### 최종 판정 (과필터 방지)
- **VALID** = B-0·B-1·B-2·B-3 통과 + B-4 통과(또는 환경 제약으로 정직히 부분 검증) → Phase C-VALID.
- **INVALID** = 어느 게이트든 **명확한 코드/빌드 근거로** disqualifier 확정 → Phase C-INVALID.
- **불확실** = 코드/빌드로 결론 불가 → 자동 삭제·제출 둘 다 금지. 게이트별 근거와 함께 사람에게 보고. 나이트픽·취향만으로 INVALID 처리 금지(삭제는 비가역).
게이트별 결과(통과/실패/불확실)를 한 줄씩 보고한 뒤 해당 Phase C로 진행한다.

## Phase C-VALID: 정제 → 보강 → 제출 → 마감
순서대로 수행한다. **B-4가 그린을 입증 못 했으면(빌드 실패·환경 미충족) 여기서 멈추고 Phase D에 보고한다 — 잘못된 제출은 리젝 PR이다.**

### C-VALID.1 산출물 정제 (군더더기 제거, 핵심 누락 없이 최대 30% 절감)
PR 초안(필요 시 spec.md)을 면밀히 읽고 반복·장황·군더더기를 다듬는다. **30%는 도달 할당량이 아니라 상한(ceiling)**이다 — 장황하면 핵심 누락 없이 30%까지 적극적으로 깎고, 이미 간결하면 그만큼만 깎고 멈춘다.
- 정제 규칙(config `doc_limits.rules`): one-fact-per-line(문서 내부 반복만 제거), single-code-snippet, factual-imperative(마케팅·hedging 제거), no-template-hints, link 보존.
- **절감률·상한 확인**: 정제 전후 본문 단어수를 `wc -w`로 측정(staleness·gh 블록 제외). 절감률 30% 초과는 사실을 깎았다는 신호 → 보존 대상(코드 위치·근거·수용기준↔테스트 매핑·링크)을 복원하고 재측정. `doc_limits.pr_max_words`(200) 이내 확인.
- **보존 불변식**: PR의 `Closes #<number>`·`Testing done`·staleness/gh 블록·링크는 삭제하지 않는다. 정제는 산문 본문·제목 한정.

### C-VALID.2 제목·작업브랜치명 보강
- **제목**: PR 첫 `# Module: Description` 헤딩과 끝 `gh ... --title "..."` 블록의 제목을 일치시킨다(`Module: Description` 유지). 커밋 제목과도 일치해야 한다.
- **작업브랜치명**: PR 헬퍼의 `Branch:` 주석·`git push`/`--head`의 `<type/slug>`를 Phase A-5에서 확정한 실제 브랜치로 치환(placeholder가 남았으면 교정).
- `state.md`의 브랜치·worktree·PR 초안 경로가 채워졌는지 확인(빠졌으면 보강).

### C-VALID.3 staleness 확인 → 커밋 정리 → push
worktree(`WT="$WORKTREE_DIR/<branch-dash>"`)에서 수행한다.
1. **staleness**: `git -C "$MAIN_ROOT" fetch upstream` 후 PR의 staleness 블록(`git log --oneline HEAD..upstream/<default> -- <touched>`)을 실행. 출력이 비면 안전. 있으면 `git -C "$WT" rebase upstream/<default>` — **충돌 나면 자동 해결 말고 멈춰** Phase D에 보고(사람이 rebase).
2. **커밋 정리**: `git -C "$WT" status`. 보통 clean(implementer가 이미 커밋). B-4의 `spotlessApply` 등 정당한 미커밋 변경이 남았으면 대상 파일만 `git add` 후 `git commit --amend --no-edit`로 합친다(push 전이라 안전). 커밋 제목 = PR 제목. AI 보조면 `Generated-by:` 토큰 확인. **무관 파일·`z_ebson/` 산출물은 절대 스테이징 금지**(worktree엔 z_ebson/가 없으나 재확인).
3. **push**: `git -C "$WT" push -u origin "<branch>"` — fork(`origin`)로만. upstream에는 push 금지.

### C-VALID.4 PR 제출 (이슈는 이미 있음 — 새로 만들지 않음)
- PR 본문을 임시 파일로 추출한다 — PR 초안에서 **첫 `# 제목` 다음 줄부터 첫 ```` ```bash ```` 코드펜스(staleness 블록) 직전까지**가 본문이다(본문에 `Closes #<number>` + `## Summary` + `## Testing done` 포함). 헬퍼 HTML 주석·AI Disclosure 잔여 블록은 추출에서 제외(`# 제목`은 `--title`로 전달).
- `Closes #<number>`가 입력 issue 번호로 채워졌는지 확인(이 파이프라인은 issue 입력이 전제다 — 새 이슈를 만들지 않는다).
- `gh pr create -R "$UPSTREAM" --base "$DEFAULT_BRANCH" --head "$FORK_OWNER:<branch>" --title "<Module: Description>" --body-file <tmp>` 실행(**`--draft` 강제 아님 — config `pr_draft_first: false`**) → PR URL 확보.

### C-VALID.5 상태·학습 기록
1. **state.md**: 상태를 **submitted**로 갱신. 상태 전이 이력에 `validated`(5게이트 통과)·`submitted`(push·PR URL) 줄을 append. PR 이력에 PR 번호·URL 추가.
2. **_memory.md** §0 제출 추적에 `- [<module>] issue=#<N> PR=#<M> branch=<name> status=pending finalize=<오늘 날짜>` 한 줄 append(config `memory.poll_pending_on_setup`이 다음 실행에서 머지/리젝으로 졸업). §2 캘리브레이션에 `- [<module>] <스펙-구현 패턴> → submitted | issue=#<N>` 한 줄.

## Phase C-INVALID: 삭제
1. PR 초안 파일을 삭제한다. spec.md·state.md는 남기되 state.md에 ABANDONED·사유를 기록.
2. 브랜치·worktree 정리:
   ```bash
   cd "$MAIN_ROOT"
   git worktree remove --force "$WORKTREE_DIR/<branch-dash>" 2>/dev/null || true
   git branch -D "<branch>" 2>/dev/null || true
   git worktree list   # 잔여 확인
   ```
   검증은 제출 앞이라 정상 흐름에선 fork에 push된 적이 없다. 방어적으로 원격 브랜치 존재를 확인하고 있으면만 삭제:
   ```bash
   git ls-remote --exit-code --heads origin "<branch>" >/dev/null 2>&1 && git push origin --delete "<branch>"
   ```
   upstream에는 어떤 것도 push/삭제하지 않는다. 무효 산출물의 PR이 실수로 게시된 적 있으면(정상 흐름엔 없음) 사용자에게 보고해 `gh pr close` 여부 확인 — 자동 close 금지.
3. `_memory.md` §1 구현 레지스트리의 해당 줄에 `[ABANDONED]`·사유를 append(재구현 방지). state.md 상태는 closed로 표기하되 issue_state의 정식 closed와 구분되게 비고에 "validate INVALID"라 적는다.

## Phase D: 보고
- 입력 산출물 경로, **VALID/INVALID/불확실 판정**과 **게이트별 한 줄 근거**(B-0 스펙 정합 / B-1 결함 실재 / B-2 머지 적격 / B-3 변경 적절 / B-4 빌드 실증 — 각각 어떤 코드·커밋·빌드 결과를 열어 확인했는지).
- VALID: 정제 전후 단어수·절감률(%)·≤30% 상한 준수·제목·보존 불변식, 빌드 실증 결과(`{module}:check`/`revapi` 통과 수 또는 환경 제약), **커밋 해시·push한 fork 브랜치·제출한 PR URL**, state.md 상태(submitted)·`_memory.md`에 남긴 줄. 빌드 실패·rebase 충돌로 멈췄으면 사유와 사람이 할 일. 첫 기여면 ASF ICLA/CCLA 안내.
- INVALID: disqualifier 확정 게이트와 코드/빌드 근거, 삭제한 파일·브랜치·worktree(원격 삭제 여부), `_memory.md`에 남긴 줄.
- 불확실: 결론 못 낸 게이트와 무엇이 불확실한지, 사람에게 필요한 결정 — **제출·삭제·정제 어느 것도 자동 수행 안 함**.

## 불변 제약
- **제출 권한 = VALID 한정** — VALID에서만 `git push`(fork=origin)·`gh pr create`. 불확실·INVALID는 제출 금지. **upstream으로의 push·force-push 금지.** 새 이슈는 만들지 않는다(issue가 입력이다).
- **격리** — `z_ebson/` 아래 파일은 절대 upstream 커밋·푸시·PR diff에 포함하지 않는다(커밋 전 스테이징 재확인).
- **권위 출처 순서** 준수, 추측 금지 — 스펙 충족/결함 실재/위반 여부는 코드·커밋 diff·실제 빌드 결과를 직접 열어 판단.
- **최소 재심** — spec-analyst/code-reviewer를 재수행하지 않는다. 머지를 실제로 막는 disqualifier만 코드/빌드 근거로 확인. 단 **공개 제출 전 빌드 실증(B-4)은 생략하지 않는다**.
- **정직성·반과필터** — 코드/빌드로 결론 못 내면 불확실로 보고. 빌드를 환경 제약으로 못 돌리면 통과 위장 금지. 잘못된 valid는 리젝 PR, 잘못된 invalid는 정상 기여 폐기. 나이트픽·취향으로 삭제·제출하지 않는다.
- **삭제·제출 안전성** — 브랜치-issue 매핑 미확정 또는 판정 불확실 시 삭제·제출 전 사용자 확인. rebase 충돌·빌드 실패 시 자동 진행 말고 멈춰 보고.
