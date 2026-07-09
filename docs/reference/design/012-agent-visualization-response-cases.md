# Agent 시각화 응답 케이스 매트릭스와 렌더링 검증

**작성일**: 2026-07-09
**대상**: api-agent(응답 생성) ↔ tech-n-ai-frontend admin(렌더링)
**관련 문서**: `011-agent-statistics-chart-response.md`(차트 응답 설계), task-01 / prompt-01
**관련 프런트 브랜치**: `fix/agent-chart-value-type`(admin, #2 타입 정정)

---

## 1. 목적

admin 앱이 api-agent `/api/v1/agent/run`을 호출했을 때 받을 수 있는 모든 시각화 응답 형태를,
admin의 렌더링 분기와 하나씩 대조해 케이스로 정리한다. 각 케이스가 실제 브라우저에서 기대대로
그려지는지 확인하고, 한쪽만 처리 가능한 틈(불일치 후보)은 처리 방향을 정한다.

이 문서는 코드 전수 대조로 도출한 케이스 매트릭스(3장)와, playwright로 확인한 렌더링 결과(4~6장),
그리고 검증 환경의 한계(7장)를 담는다.

---

## 2. 응답·렌더링 분기 도출 (코드 근거)

### 2.1 백엔드가 만들 수 있는 응답 형태

`AgentExecutionResult`(record)와 그 생성 경로를 추적한 결과다.

- `success(summary, sessionId, toolCallCount, analyticsCallCount, executionTimeMs, chartData)`
  → `success=true`, `errors=[]`, `chartData`는 인자 그대로.
- `failure(summary, sessionId, errors)` → `success=false`, `errors=[msg...]`, `chartData=[]`.

`chartData`는 `EmergingTechAgentTools`가 분석 Tool 실행 중 사이드 채널로 모은다.
- `get_emerging_tech_statistics`: 정상 응답이고 `groups`가 비어있지 않을 때만 `chartType="pie"` 1건 추가.
- `analyze_text_frequency`: 정상 응답이고 `topWords`가 비어있지 않을 때만 `chartType="bar"` 1건 추가.
- 즉 백엔드는 `chartType`을 `pie`·`bar`로만 만들고, **빈 `dataPoints`인 `ChartData`는 만들지 않는다.**

`EmergingTechAgentImpl.execute`의 종료 분기:
- 정상 종료 → `success(...)`.
- `AgentLoopDetectedException`(또는 langchain4j "exceeded sequential tool invocations") →
  `success(합성 summary, ..., 그때까지 모은 chartData)`. 즉 루프 감지도 `success=true`이며,
  이미 성공한 분석 Tool의 차트가 있으면 그대로 실려 나간다.
- 그 밖의 예외 → `failure(...)`.

`ChartData.DataPoint.value`와 `ChartMeta.totalCount`는 Java `long`이다.
전역 Jackson 설정이 `Long`을 JSON 문자열로 직렬화하므로, API 경계에서 이 두 값은 **문자열**로 나간다.

### 2.2 프런트 렌더링 분기

- `agent-message-area.tsx`: `isLoadingMessages` → 스피너 / 활성 세션 없고 메시지 0 → `AgentEmptyState` /
  `isSending` → `AgentLoadingIndicator` / 그 외 → 메시지 버블 목록.
- `agent-message-bubble.tsx`: USER는 평문(+ `failed`면 "Failed to send"·Retry),
  ASSISTANT는 Markdown 본문 + `executionMeta`(있으면) + `chartData?.length>0`이면 `ChartSection`.
- `agent-execution-meta.tsx`: `success`에 따라 "Success"/"Failed" 배지, tool call 수, 실행 시간,
  `errors`가 있으면 목록으로 표시.
- `chart-section.tsx`: `chartData.length===0`이면 아무것도 안 그림. 그 외 각 항목을 `AgentChart`로.
- `agent-chart.tsx`: `chartType`이 `pie`·`bar`가 아니면 null, `dataPoints`가 비면 null,
  `pie` → `PieChart`, `bar` → `BarChart`. `value`·`totalCount`는 `Number()`로 변환해 쓴다.
- `page.tsx`의 `toDisplayMessages`: 이력 메시지를 화면용으로 바꿀 때 `content`만 옮기고
  `chartData`·`executionMeta`는 복원하지 않는다(서버 `MessageResponse`에 그 필드가 없다).

---

## 3. 케이스 매트릭스

형식: 케이스ID | 입력(요청/응답 JSON 요지) | 기대 렌더링 | 확인 방법 | 결과

### 3.1 응답 형태 (성공/실패, chartData 개수)

| 케이스ID | 입력 요지 | 기대 렌더링 | 확인 방법 | 결과 |
|---|---|---|---|---|
| R-A | 성공, 분석 Tool 미호출 → `chartData=[]` (수집·검색·단순조회) | 요약 Markdown + "Success" 메타, 차트 없음 | playwright 통제응답 | 기대값 렌더링 확인 |
| R-B | 성공, 통계 1건 → `chartData=[pie]` | pie 차트 1개 + 요약 + 메타 | playwright 통제응답(+스크린샷) | 기대값 렌더링 확인 |
| R-C | 성공, 키워드 1건 → `chartData=[bar]` | bar 차트 1개 + 요약 + 메타 | playwright 통제응답 | 기대값 렌더링 확인 |
| R-D | 성공, 통계+키워드 → `chartData=[pie,bar]` | 차트 2개(pie, bar) 순서대로 | playwright 통제응답 | 기대값 렌더링 확인 |
| R-E | 루프 감지 강제종료 → `success=true`, summary 합성, `chartData`=누적(0~n) | 성공 케이스와 동일(차트 있으면 그림) | 코드 경로 + 실제 실행 백엔드 로그 | 아래 7.2 참고 |
| R-F | 실행 예외 → `success=false`, `errors=[msg]`, `chartData=[]` | ASSISTANT 버블에 "Failed" 배지 + 에러 목록, 차트 없음 | playwright 통제응답 | 기대값 렌더링 확인 |

### 3.2 dataPoint 변주 (R-B / R-C 내부)

| 케이스ID | 입력 요지 | 기대 렌더링 | 확인 방법 | 결과 |
|---|---|---|---|---|
| DP-1 | dataPoints 1개 | 차트 1개 항목 | playwright 통제응답(R-B/EXTREME 포함) | 기대값 렌더링 확인 |
| DP-N | dataPoints 다수 | 항목 수만큼 조각/막대 | playwright 통제응답(R-B pie 3, R-C bar 5) | 기대값 렌더링 확인 |
| DP-EXTREME | 대형 정수 value(예: 1,000,000,000), totalCount 대형 | 값이 잘리지 않고 `1,000,000,001`처럼 천단위 표기 | playwright 통제응답 | 기대값 렌더링 확인 |
| DP-LONGLABEL | 긴 라벨(축 tick 넘칠 만한 길이) | 차트가 깨지지 않고 렌더 | playwright 통제응답 | 기대값 렌더링 확인 |
| DP-0 | dataPoints 0개 | (백엔드가 만들지 않음) — 아래 #1 참고 | 코드 경로 | 백엔드 미도달 |

### 3.3 프런트 상태 케이스

| 케이스ID | 입력 요지 | 기대 렌더링 | 확인 방법 | 결과 |
|---|---|---|---|---|
| U-EMPTY | 활성 세션 없음 | "Welcome to AI Agent" 빈 상태 + 예시 goal | playwright 실제 진입 | 기대값 렌더링 확인 |
| U-NETFAIL | `/run`이 HTTP 예외(500 등) | USER 버블 "Failed to send" + Retry, 차트 없음 | playwright 통제응답 + 실제 타임아웃 관측 | 기대값 렌더링 확인 |
| U-HIST | 차트 있던 세션 재열람 | 요약 텍스트만(차트·메타 없음) — 의도된 동작 | playwright 통제응답 | 기대값 렌더링 확인 (#3) |
| U-SENDING | 실행 중 | 로딩 인디케이터 | 코드 경로(짧게 지나가는 상태) | 코드 경로 확인 |
| U-LOADING | 세션 전환 메시지 로딩 | 중앙 스피너 | 코드 경로(짧게 지나가는 상태) | 코드 경로 확인 |

### 3.4 죽은 방어 분기(#1) · ID 직렬화

| 케이스ID | 입력 요지 | 기대 렌더링 | 확인 방법 | 결과 |
|---|---|---|---|---|
| DEAD-TYPE | `chartType`이 pie·bar 아님(예: line) | `agent-chart`가 null → 차트 없음 | playwright 통제응답 | 방어 분기 동작 확인(백엔드 미도달) |
| DEAD-EMPTY | `dataPoints` 빈 배열 | `agent-chart`가 null → 차트 없음 | playwright 통제응답 | 방어 분기 동작 확인(백엔드 미도달) |
| ID-STRING | sessionId(TSID)·value·totalCount가 JSON 문자열 | 문자열 ID 유지, `Number()` 변환으로 수치 정상 표기 | playwright 통제응답(값이 문자열인 상태로 렌더 확인) | 기대값 렌더링 확인 |

---

## 4. 분기 전수 대조 결과 (누락 0)

2장에서 도출한 백엔드 응답 분기(R-A~R-F)와 프런트 렌더링 분기(빈 상태, 로딩, 실패 2종,
차트 유형 판별, 빈 dataPoints, 이력 복원)가 3장 매트릭스에 각각 대응 행을 가진다.
대응 행이 없는 코드 분기는 남지 않았다.

---

## 5. 불일치 후보와 처리 방향

코드 대조에서 "한쪽만 처리 가능한 형태" 3건을 찾았다. 처리 방향은 2026-07-09 사용자와 확정했다.

### #1 — 프런트의 도달 불가 방어 분기 (유지)

`agent-chart.tsx`는 `chartType`이 pie·bar가 아니거나 `dataPoints`가 비면 렌더링하지 않는다.
백엔드는 pie·bar만 만들고 빈 `dataPoints`는 만들지 않으므로 이 두 분기는 현재 도달하지 않는다.
**처리: 유지한다.** 프런트가 백엔드 응답을 완전히 신뢰하지 않는 편이 안전하고, 제거해도 어떤
케이스의 렌더링이 달라지지 않는다(DEAD-TYPE·DEAD-EMPTY로 방어 동작만 확인).

### #2 — value·totalCount 타입 불일치 (프런트 수정 완료)

`ChartData.DataPoint.value`와 `ChartMeta.totalCount`는 백엔드에서 Java `long` → JSON 문자열로
나가는데, admin 타입은 `number`로 선언돼 있었다. `agent-chart.tsx`가 이미 `Number()`로 변환해
써서 렌더링은 맞지만, 선언 타입이 런타임과 달랐다.
**처리: admin 타입을 `string`으로 정정.** 렌더링 동작은 그대로다(ID-STRING·DP-EXTREME으로 확인).
프런트 브랜치 `fix/agent-chart-value-type`에 커밋했다.

### #3 — 이력 재열람 시 차트 소실 (휘발 유지)

`/run` 응답의 `chartData`는 실행 시점 화면에만 뜬다. 서버 `MessageResponse`에는 chartData 필드가
없고 `toDisplayMessages`도 복원하지 않아, 세션을 다시 열면 요약 텍스트만 남는다.
이력에도 차트를 되살리려면 chartData를 대화 메시지로 저장하고 조회 응답에 포함해야 하는데,
이는 common-conversation·common-kafka·datasource-aurora·datasource-mongodb·api-agent를 함께
바꾸는 큰 변경이고 공유 모듈을 쓰는 api-chatbot에도 영향을 준다.
**처리: 차트는 실행 응답에만 표시(휘발)로 두고, 이력은 요약 텍스트만 = 의도된 동작으로 확정.**
U-HIST로 실제 동작(텍스트만, 차트·메타 없음)을 확인했다. 백엔드 코드 변경 없음.

---

## 6. 검증 방법

렌더링은 실제 브라우저(admin, localhost:3001)에서 playwright로 확인했다. 코드 리딩만으로
"될 것"이라 판정하지 않았다. 각 케이스는 admin 로그인 → `/agent` 진입 후,
`/api/v1/agent/run`(과 이력용 `/sessions`, `/messages`) 응답을 케이스별 JSON으로 통제해
재현했다. 응답을 통제해도 admin의 React·recharts 컴포넌트는 실제로 실행되므로, 이는 코드 리딩이
아니라 실제 렌더링 확인이다. 응답 JSON 형태는 2장의 코드 대조로 확정한 것을 그대로 썼다.

`value`·`totalCount`는 통제응답에서도 백엔드 실제 직렬화대로 문자열로 넣어, `Number()` 변환까지
함께 확인했다.

---

## 7. 검증 환경의 한계 (정직 기록)

### 7.1 실제 LLM 실행은 이 환경에서 불안정

실제 goal("Provider별 수집 현황을 통계로 보여주세요")로 한 번 실행했을 때,
LLM이 `get_emerging_tech_statistics`를 반복 호출하다 루프 감지로 강제 종료됐고(약 37초 소요),
그 사이 프런트/프록시가 먼저 타임아웃해 브라우저는 500을 받았다(→ U-NETFAIL로 관측).
백엔드 로그상 ERROR는 없었고, 루프 감지 후 정상적으로 success 응답과 ASSISTANT 메시지 저장까지
마쳤다. 즉 백엔드는 정상 동작했고, 지연이 타임아웃을 넘긴 것이다.

이 루프는 모델 동작이라 재현마다 결과가 다르고 시간·비용이 든다. 그래서 차트가 실려 나오는
성공 응답을 브라우저에서 안정적으로 관찰하려면 통제응답 방식이 맞다(6장).

### 7.2 R-E(루프 감지) 판정 근거

R-E는 백엔드가 `success=true`로 그때까지 모은 chartData를 함께 반환하므로, 렌더링은 성공
케이스(R-B/R-C/R-D)와 같다. 7.1의 실제 실행에서 통계 Tool 첫 호출은 데이터를 받아
pie chartData가 실제로 만들어졌음을 백엔드 로그로 확인했다. 그 응답 형태의 브라우저 렌더링은
같은 형태의 통제응답(R-B)으로 확인했다.

---

## 8. 결론

- 백엔드 응답 형태(R-A~R-F)와 프런트 렌더링 분기가 매트릭스에 1:1로 대응하며, 누락은 없다.
- 모든 케이스의 렌더링을 실제 브라우저에서 확인했다(전이 상태 U-SENDING·U-LOADING과 백엔드
  미도달 분기는 코드 경로로 확인, R-E는 7.2 근거).
- 불일치 후보 3건은 #1 유지, #2 프런트 타입 정정, #3 휘발 유지로 확정했다.
- 백엔드 응답 생성 코드는 바꾸지 않았다. 이 작업의 백엔드 산출물은 이 문서 하나다.
