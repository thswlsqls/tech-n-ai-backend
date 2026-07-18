---
name: impl-validate
description: impl 파이프라인(tech-n-ai-backend/pipeline/)이 만든 run 폴더 산출물(이슈·PR 초안, push된 작업 브랜치)을 이 세션에서 최종 검증하고 GitHub에 제출한다. 사용자가 run 폴더 경로(pipeline/output/yyyyMMddHHmmss)나 초안 파일 경로를 인자로 주며 검증·제출을 맡길 때, 또는 "impl-validate 실행", "산출물 검증하고 제출해줘", "이슈·PR 올려줘"라고 할 때 사용한다. 4게이트(산출물 정확성=초안↔spec 수용 기준↔커밋 diff 일치 / 브랜치·커밋 일치=초안이 가리키는 브랜치·커밋·push 실재 / 빌드 실증=worktree에서 영향 모듈 테스트 재실행 / 제출 정합=제목 형식·Closes·분량)를 통과하면 gh issue create → 번호를 PR Closes에 채워 gh pr create → run 폴더명 끝에 '-' 마킹 → _learnings.md §0 기록. 게이트 실패면 산출물·브랜치를 건드리지 않고 근거만 보고한다(삭제 금지). 불확실하면 제출하지 않고 사람에게 결정을 받는다. 신규 구현에는 쓰지 않는다 — 그건 impl 스킬의 역할.
---

# impl 산출물 검증·제출기 실행기

이 스킬은 impl 파이프라인이 만든 run 폴더 산출물을 **이 세션에서** 최종 검증하고,
통과하면 이슈·PR을 GitHub에 제출하는 진입점이다. 새 코드를 구현하거나 스펙을 다시 만들지
않는다 — 그건 `impl` 스킬의 역할이다.

## 단일 진실 소스 (여기에 규칙을 다시 적지 않는다)

검증·제출 로직(Phase A~D, 4게이트, VALID/INVALID/불확실 판정, 제출·마감)은 아래 파일에
자급자족으로 들어 있다. 이 스킬은 그 파일을 읽어 그대로 실행할 뿐이다. 절차·게이트를 이
문서에 복사하면 드리프트가 생기므로 하지 않는다.

- **검증기**: `/Users/m1/workspace/tech-n-ai/tech-n-ai-backend/pipeline/SKILL.md`
  — Phase A(로드·역추적) ~ D(보고), 4게이트, 판정, 제출·마감, 불변 제약이 전부 이 파일에 있다.
- **설정(규칙의 단일 진실 소스)**: `/Users/m1/workspace/tech-n-ai/tech-n-ai-backend/pipeline/impl-config.yml`
  — 경로·빌드·규약·doc_limits·validate 게이트·상태.

## 실행 절차

1. 사용자 인자를 **run 폴더 경로**(`pipeline/output/<yyyyMMddHHmmss>`)로 삼는다.
   초안 파일 경로를 받으면 그 부모의 부모가 run 폴더다. 못 찾으면 추측하지 말고 묻는다.
   폴더명이 이미 `-`로 끝나면 기제출본이므로 보고만 하고 중단한다.
2. `pipeline/SKILL.md`를 읽는다. 그 파일의 인자 파싱과 Phase A~D를 **그 문서에 적힌 그대로** 수행한다.
3. Phase A에서 `impl-config.yml`을 로드해 이후 모든 경로·규칙을 그 값으로 쓴다.
4. 서브에이전트를 쓰면 `model`을 지정하지 말고 세션 모델을 상속시킨다(검증 품질 직결).

## 경계

- **제출 권한 = VALID 한정.** INVALID·불확실은 이슈·PR 제출·run 폴더 마킹을 하지 않고 근거만 보고한다.
- **main 직접 push·force-push 금지.** force-with-lease는 rebase한 자기 작업 브랜치 한정, main 금지.
- **산출물 불변**: INVALID여도 초안·브랜치·worktree를 삭제하지 않는다.
- **정직성**: 빌드를 못 돌리면 통과 위장 금지 — 코드/빌드로 결론 못 내면 불확실로 보고한다.
- **`pipeline/` 자기 보호**: 고치는 것은 초안 텍스트(분량·placeholder)와 상태 기록뿐,
  파이프라인 인프라·프로젝트 코드는 건드리지 않는다.
- main merge와 worktree 정리는 사용자 수동이다.
