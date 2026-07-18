# docs: admin ↔ api-agent 시각화 응답 케이스 매트릭스와 렌더링 검증

## 목표
admin이 api-agent `/run`을 호출했을 때 받는 모든 시각화 응답 형태를 admin 렌더링 분기와 대조해
케이스 매트릭스로 정리하고, 각 케이스를 브라우저에서 실제로 확인한다. 한쪽만 처리 가능한 틈은
처리 방향을 정한다.

- 입력 문서: pipeline/inputs/tasks/task-01-agent-visualization.md (+ prompt-01)
- spec: pipeline/output/task-01/spec.md

## 완료 기준
- [ ] 케이스 매트릭스 문서가 `docs/reference/design/012`에 있고, 각 케이스에 입력·기대 렌더링·확인 결과가 있다
- [ ] 백엔드 응답 분기와 프런트 렌더링 분기가 매트릭스에 누락 없이 대응한다
- [ ] 한쪽만 처리 가능한 형태(불일치 후보)는 처리 방향이 확정 기록된다
- [ ] 모든 케이스의 렌더링을 브라우저에서 확인한다(확인 불가 케이스는 사유 기록)
- [ ] 프런트 타입 정정(#2)은 tsc·next build를 통과하고 별도 브랜치에 커밋된다

## 범위 제외
새 차트 타입 추가, 인증 체계 변경, api-chatbot 등 다른 API, admin 외 앱(app/) 화면.

<!-- ── 파이프라인 제출 보조 (GitHub 본문 제외, validate 스킬이 사용) ──
work-key: task-01
branch: docs/agent-visualization-cases
제출(실행 금지 — /impl-validate만 실행한다):
  gh issue create -R thswlsqls/tech-n-ai-backend --title "docs: admin ↔ api-agent 시각화 응답 케이스 매트릭스와 렌더링 검증" --body-file <본문 추출 tmp>
-->
