# Task 01: admin ↔ api-agent 시각화 응답 케이스 정리와 렌더링 검증·개선

## 목표
tech-n-ai-frontend `admin` 앱이 tech-n-ai-backend `api-agent`를 호출했을 때 기대되는
**모든 시각화 응답을 케이스별로 정리**하고, 각 케이스를 **브라우저에서 실제로 렌더링 확인**하면서
기대값이 나올 때까지 admin 앱과 api-agent를 개선한다.

## 배경 — 근거 위치 (여기서 출발하되, 전체 분기는 코드에서 다시 확인할 것)
- 백엔드: `api/agent/.../controller/AgentController.java` — `/api/v1/agent/run`(POST),
  `/sessions`, `/sessions/{id}`, `/sessions/{id}/messages` 등.
  `run`의 응답 `AgentExecutionResult`: `success`, `summary`, `sessionId`, 실행 시간,
  `errors`(실패 시), `chartData`(`List<ChartData>`).
- 프런트: `admin/src/components/agent/` — `agent-chart.tsx`(recharts,
  `chartType`이 `pie`·`bar`가 아니면 렌더링하지 않음), `chart-section.tsx`,
  `agent-message-area.tsx`, `agent-execution-meta.tsx`, `agent-empty-state.tsx`.
  타입은 `admin/src/types/agent`(`ChartData`, `ChartMeta`).

## 요구사항
1. **케이스 정리**: 응답 스키마의 변주(성공/실패, chartData 0·1·다수 개,
   chartType별, 데이터 포인트 0·1·다수·극단값·긴 라벨, TSID 문자열 ID)와
   admin 렌더링 분기를 대조해 기대 렌더링을 케이스 매트릭스로 만든다.
   정리 문서는 tech-n-ai-backend `docs/reference/` 아래 기존 번호 체계에 맞춰 추가한다.
2. **렌더링 검증·개선**: 각 케이스를 로컬 환경(브라우저)에서 재현해 기대 렌더링과
   대조하고, 불일치는 원인(백엔드 데이터 형태 / 프런트 렌더링)을 갈라 수정한 뒤
   재검증한다. 모든 케이스가 기대값으로 렌더링될 때까지 반복한다.

## 완료 기준
- [ ] 케이스 매트릭스 문서가 존재하고, 각 케이스에 입력(요청·응답 형태)·기대 렌더링·확인 결과가 적혀 있다.
- [ ] 코드의 응답·렌더링 분기 중 매트릭스에 빠진 케이스가 없다(분기 전수 대조).
- [ ] 모든 케이스가 브라우저에서 기대값으로 렌더링됨을 확인했다(케이스별 확인 기록).
- [ ] 백엔드 수정에는 테스트가 동반되고 영향 모듈 테스트가 통과한다.
- [ ] 프런트 수정은 admin의 lint·build를 통과한다.

## 범위 제외
- 스펙에 없는 새 차트 타입 추가, 인증 체계 변경, api-chatbot 등 다른 API.
- admin 외 앱(app/)의 화면.

## 제약 — 두 저장소에 걸친 작업
- 이 파이프라인의 git 플로우(worktree·commit·push·이슈/PR 초안)는 **tech-n-ai-backend 변경분에만** 적용된다.
- tech-n-ai-frontend는 **별도 git 저장소**다. admin 수정은 그 저장소에서 브랜치를 따로 만들어
  커밋하되(admin/CLAUDE.md 규약 준수), 파이프라인 산출물(PR 초안)에는 프런트 브랜치명과
  변경 요약을 기록만 한다.
- LLM 실 호출 비용: `run` 검증은 최소 호출로 설계하고, 세션·메시지 조회 케이스는
  저장된 데이터로 확인한다. API 키는 백엔드 ENV에만 둔다.
- 브라우저 검증은 playwright MCP로 수행한다(상세는 prompt-01의 2단계).
  admin 로그인 자격은 실행 시 사용자에게 받는다.
