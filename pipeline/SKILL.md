---
name: impl-validate
description: impl 파이프라인(pipeline/)이 만든 run 폴더 산출물(이슈·PR 초안, push된 작업 브랜치)을 최종 검증하고 GitHub에 제출한다. 사용자가 run 폴더 경로(pipeline/output/yyyyMMddHHmmss)를 인자로 주며 검증·제출을 맡길 때 사용. 4게이트(산출물 정확성=초안↔spec 수용 기준↔커밋 diff 일치 / 브랜치·커밋 일치=초안이 가리키는 브랜치·커밋·push 실재 / 빌드 실증=worktree에서 영향 모듈 테스트 재실행 / 제출 정합=제목 형식·Closes·분량)를 통과하면 gh issue create → 번호를 PR Closes에 채워 gh pr create → run 폴더명 끝에 '-' 마킹 → _learnings.md §0 기록. 게이트 실패면 산출물·브랜치를 건드리지 않고 근거만 보고한다(삭제 금지). 신규 구현에는 쓰지 않는다 — 그건 /tech-n-ai-impl:impl의 역할.
---

# impl 산출물 검증·제출기

이 스킬은 **이미 구현·push된** run 폴더 산출물을 입력으로 받아, 초안이 실제 코드와 일치하고
제출할 자격이 있는지 **최종 검증**하고, 통과하면 이슈·PR을 GitHub에 제출한다.
새 코드를 구현하거나 스펙을 다시 만들지 않는다 — 그건 파이프라인(`/tech-n-ai-impl:impl`)의 역할이다.

**마감 = 세 갈래(자동)**:
- **VALID** → 이슈 제출 → 받은 번호를 PR 초안 `Closes #`에 채움 → PR 제출 → run 폴더명 끝에
  `-` 마킹(mv) → `state.md` 상태 `submitted` + `_learnings.md` §0에 `status=pending` 기록.
- **INVALID** → **산출물·브랜치를 건드리지 않는다**(삭제·마킹 금지). 게이트별 근거를 보고하고,
  수정은 파이프라인 재실행(P5 amend 경로)에 맡긴다. 자기 저장소 작업이라 폐기 비용보다
  수정 비용이 싸다 — 참고 구현(외부 OSS용)의 "INVALID=삭제" 정책을 의도적으로 뒤집었다.
- **불확실**(코드/빌드로 결론 불가) → 제출·마킹 둘 다 자동 수행하지 않고 게이트별 근거와 함께
  사람에게 결정을 받는다.

**위치 — 최종 게이트(과잉 재심 금지)**: 산출물은 이미 파이프라인의 spec-analyst·reviewer를 거쳤다.
그걸 재수행하지 않는다. 제출을 실제로 막는 결함만 코드/빌드 근거로 확인한다.
단, **공개 제출 전 빌드 실증은 생략하지 않는다.**

## 인자 파싱
- **run 폴더**(필수): `pipeline/output/<yyyyMMddHHmmss>` — 안의 `issues/*-issue.md`·`prs/*-pr.md`를
  찾는다. 여러 work-key가 있으면 각각 독립적으로 검증·제출한다.
- 초안 파일 경로를 직접 받아도 된다(그 부모의 부모가 run 폴더).
- 경로를 못 찾으면 추측하지 말고 사용자에게 묻는다. 폴더명이 이미 `-`로 끝나면
  기제출본이므로 보고만 하고 중단한다.

## 고정 자산 (단일 진실 소스는 config)
- config: `pipeline/impl-config.yml` → `project`(repo/default_branch), `paths`, `build`,
  `conventions`, `doc_limits`, `validate`, `learnings`, `work_state`를 읽어 이후 전부 이 값을 쓴다.
- **PR 초안** 하단 "파이프라인 제출 보조" 주석 블록에서 work-key·branch·commit·worktree를 읽는다
  (이슈 초안 블록에는 work-key·branch만 있다). 같은 work-key의
  `pipeline/output/<work-key>/spec.md`·`state.md`를 함께 연다.
- **모델 상속**: 검증은 추론 품질에 직결된다(잘못 valid면 결함 PR, 잘못 invalid면 정상 작업 지연).
  서브에이전트를 쓰면 `model`을 지정하지 말고 세션 모델을 상속시킨다.

## Phase A: 로드 & 역추적
1. config·초안(이슈/PR)·spec.md·state.md를 읽는다.
2. **브랜치·커밋 역추적**: 초안 헬퍼 블록의 branch·commit →
   `git -C "$MAIN_ROOT" branch --list <branch>`·`git worktree list`·
   `git log --oneline origin/main..<branch>`로 실재 확인. 커밋 제목이 PR 초안 제목과
   같은 형식·내용인지 대조해야 "이 작업의 브랜치"로 확정한다. 불일치면 제출 전 사용자 확인.
3. push 여부 확인: `git ls-remote --heads origin <branch>`. 안 돼 있으면
   `git -C <worktree> push -u origin <branch>`를 먼저 수행한다(파이프라인 P7 실패 복구).

## Phase B: 4게이트 (config `validate.gates`)
각 게이트는 **명확한 코드/빌드 근거로 결함이 확정될 때만** 실패 처리한다(약한 의심·취향은 실패 아님).
주장은 믿지 말고 인용된 코드·커밋 diff·빌드 결과를 직접 연다.

### B-1. artifact_accuracy — 초안 ↔ spec ↔ diff 세 곳이 일치하는가
- spec.md 수용 기준 각각에 대해 (a) 커밋 diff에 그 동작의 구현이 실재하는가,
  (b) 그 기준을 커버하는 테스트가 커밋에 실재하는가(PR "테스트" 절의 매핑이 거짓이 아닌가).
- 이슈 초안의 완료 기준이 spec 수용 기준과 어긋나거나, PR이 "추가했다"는 테스트가 커밋에 없으면
  → INVALID(허위 보고). `git show <branch> --stat`으로 변경 파일을 PR 주장과 대조.
- CQRS 영향 절이 "해당"인데 diff에 해당 산출(이벤트·Document·docs/sql)이 없으면 → INVALID(미완성).
  단 범위 축소가 spec "미해결 질문 → 확정"에서 합의됐으면 통과.

### B-2. branch_commit_consistency — 초안이 가리키는 것이 실재하는가
- Phase A의 역추적 결과 확정. 브랜치에 초안이 모르는 추가 커밋이 있으면 사유 확인(리뷰 수정
  amend는 정상). `pipeline/` 파일이 diff에 섞였으면 → INVALID(인프라 혼입).
- **staleness**: PR 초안 헬퍼의 staleness 블록을 **worktree 안에서** 실행
  (`git -C <worktree> log --oneline HEAD..origin/main -- <files>` — 본 트리에서 실행하면
  HEAD가 main이라 항상 비어 보이는 거짓 안전이 된다). 출력이 비면 안전.
  있으면 worktree에서 `git rebase origin/main` — **충돌 나면 자동 해결하지 말고
  멈춰** 사람에게 보고. rebase했으면 `git push --force-with-lease`로 브랜치를 갱신한다
  (이 스킬의 유일한 force 계열 허용 — 자기 작업 브랜치 한정, main 금지).

### B-3. build_proven — 주장이 아니라 실제로 통과하는가
worktree(Phase A 확정) 안에서 영향 모듈 각각 재실행한다(본 트리 checkout 금지):
```bash
cd "<worktree>"
./gradlew {module}:test     # 영향 모듈 전부, config build.unit_test
```
- 테스트 실패 → INVALID(허위 통과). 문서만 바꾼 작업이면 생략 가능(생략 사실을 보고에 명시).
- 환경 제약(JDK 등)으로 못 돌리면 통과로 위장하지 말고 **불확실**로 보고.

### B-4. submission_consistency — 제출물 형식이 규약에 맞는가
- 이슈 제목 `{type}: {제목}`, PR 제목 `{type} : [main] {설명}`(커밋 제목과 일치),
  PR 본문에 `Closes #{이슈번호}` placeholder 실재.
- 분량 가드: 본문 `wc -w` ≤ config `doc_limits`(헬퍼 블록 제외). 초과분은 여기서 다듬는다
  (사실 삭제 금지 — 중복·군더더기만. 상투어·번역투는 commit-message 스킬 어휘 규칙으로 걸러낸다).
- placeholder(`{...}`) 잔재가 본문에 남았으면 실값으로 치환(치환 불가 정보면 INVALID).

### 판정
- **VALID** = 4게이트 전부 통과 → Phase C. **INVALID** = 어느 게이트든 코드/빌드 근거로 확정 →
  근거 보고 후 종료(산출물 불변). **불확실** = 결론 불가 → 사람에게. 게이트별 결과를 한 줄씩 보고한다.

## Phase C: 제출 & 마감 (VALID 한정)
1. **이슈 제출**: 초안에서 본문 추출(첫 `# 제목` 다음부터 헬퍼 주석 블록 직전까지, 제목은 `--title`로):
   ```bash
   gh issue create -R <repo> --title "<type>: <제목>" --body-file <tmp>
   ```
   → 이슈 번호 확보. 같은 주제의 open 이슈가 이미 있으면 새로 만들지 말고 그 번호를 쓴다
   (`gh issue list --search`로 확인).
2. **PR 제출**: PR 초안의 `Closes #{이슈번호}`를 실번호로 치환 → 본문 추출 →
   ```bash
   gh pr create -R <repo> --base main --head "<branch>" --title "<type> : [main] <설명>" --body-file <tmp>
   ```
   → PR URL 확보.
3. **마감 기록**:
   - `state.md`: 상태 `submitted`, 이슈#·PR#·URL을 이력에 append.
   - `_learnings.md` §0에 한 줄 append(파일 전체 재작성 금지 — append 직전 다시 읽고
     실패하면 한 번 재시도, 병렬 실행 대비):
     `- [<modules>] <work-key> issue=#<N> PR=#<M> branch=<name> status=pending run=<RUN_ID> date=<오늘>`
   - run 폴더 마킹: `mv pipeline/output/<RUN_ID> pipeline/output/<RUN_ID>-`
     (초안 파일은 삭제하지 않는다 — 이력·회고 재료).
4. **안내**: main merge와 worktree 정리는 사용자 수동 —
   `gh pr merge <pr> --merge --delete-branch` 후 `git worktree remove <path>`.
   merge/리젝 결과는 다음 파이프라인 실행의 P0가 자동 폴링해 §2에 반영한다.

## Phase D: 보고
- run 폴더, work-key별 **VALID/INVALID/불확실 판정**과 **게이트별 한 줄 근거**(어떤 코드·커밋·
  빌드 결과를 열어 확인했는지).
- VALID: 제출한 이슈#·PR# URL, 빌드 재실행 결과(모듈별 통과 수), 마킹된 폴더명, §0에 남긴 줄.
- INVALID: 결함 확정 게이트와 근거, 파이프라인 재실행으로 고칠 방법.
- 불확실: 결론 못 낸 게이트와 사람에게 필요한 결정.

## 불변 제약
- **제출 권한 = VALID 한정.** 불확실·INVALID는 제출·마킹 금지.
- **main 직접 push·force-push 금지.** force-with-lease는 rebase한 자기 작업 브랜치 한정.
- **산출물 불변**: INVALID여도 초안·브랜치·worktree를 삭제하지 않는다.
- **정직성**: 빌드를 못 돌리면 통과 위장 금지. 코드/빌드로 결론 못 내면 불확실로 보고.
- **`pipeline/` 자기 보호**: 이 스킬이 고치는 것은 초안 텍스트(분량·placeholder)와 상태 기록뿐,
  파이프라인 인프라·프로젝트 코드는 건드리지 않는다.
