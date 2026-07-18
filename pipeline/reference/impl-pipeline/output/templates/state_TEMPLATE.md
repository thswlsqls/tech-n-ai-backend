<!-- Comment:
run-task 파이프라인이 task 하나를 실행하며 채우는 상태 문서다.
- 단계가 끝날 때마다 "단계 이력"에 한 줄 append (기존 줄은 고치지 않는다).
- "가정·설계 결정"과 "검증 결과"는 task-07이 README와
  docs/coding_agent_interaction_history.md를 쓸 때의 재료다 — 그때 옮겨 적기 쉽게 쓴다.
- 재실행 시 오케스트레이터가 이 문서를 읽고 끝난 단계를 건너뛴다.
- 안내용 <!-- ... --> 주석은 첫 기록 후 지운다.
-->

# State: Task {NN} — {task 제목}

- **task 문서**: docs/tasks/task-{NN}-*.md
- **prompt 문서**: docs/prompts/prompt-{NN}-*.md
- **영역**: {backend | frontend | backend+frontend | docs}
- **현재 상태**: loaded
- **브랜치**: task/{NN}-{slug} (설계 승인 후 기입)
- **이슈**: #{번호} (설계 승인 후 기입)
- **PR**: #{번호} (검증 통과 후 기입)

## 단계 이력
| 시각 | 단계 | 결과 |
|------|------|------|
| {MM-DD HH:mm} | loaded | 문서 로드, 완료 기준 {N}개 확인 |
<!-- designed / implemented / verified / merged / recorded 순으로 append.
     designed: 승인된 설계 요지 한 줄. implemented: 커밋 해시·테스트 결과.
     verified: 검증 방법·리뷰 지적 수. merged: PR 번호·merge 커밋. -->

## 가정·설계 결정 (task-07 재료)
- {결정과 이유 — README 가정 목록에 그대로 옮길 수 있게}

## 검증 결과
- 자동: {테스트·lint 커맨드와 결과 요약}
- 수동: {사용자가 확인한 항목 / 아직 해야 할 항목}

## 남은 수작업
- {스크린샷 캡처, 실제 키 검증 등}
