<!-- Comment:
run-task 7단계가 `gh pr create --body`에 쓰는 템플릿.
제목 형식은 config git.pr.title_format: "Task {NN}: {task 제목}" (이슈 제목과 동일).
첫 줄 "Closes #{이슈번호}"로 merge 시 이슈가 자동으로 닫힌다 — 지우지 말 것.
안내용 주석은 본문에 넣지 않는다.
-->

Closes #{이슈번호}

## 구현 요약
<!-- 무엇을 왜 바꿨는지. 한 줄 = 한 사실. 단일 지점·확장 지점 같은 설계 핵심을 남긴다. -->
- {변경 요약 1}
- {변경 요약 2}

## 테스트·lint
<!-- 실제 실행한 커맨드와 숫자 결과. 주장 말고 실행 결과를 적는다. -->
- {예: `bin/rails test`: N runs, M assertions, 0 failures}
- {예: `bin/rubocop`: N files, no offenses}
- {완료 기준 ↔ 테스트 매핑 중 핵심 (경계값 테스트 등)}

## 리뷰
{ringle-code-reviewer 몇 개(초점)와 결과 — confidence ≥ 80 이슈 수, 수정 여부}

## 수동 확인
{사용자가 직접 확인할 절차. 없으면 "없음"과 그 이유(예: 이 task는 외부 API 미사용)}
