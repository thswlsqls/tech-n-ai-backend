# Spec: admin ↔ api-agent 시각화 응답 케이스 정리와 렌더링 검증·개선 (task-01)

## 입력
- 형태: task 쌍 (task=01)
- 원문: `pipeline/inputs/tasks/task-01-agent-visualization.md` + `pipeline/inputs/prompts/prompt-01-agent-visualization.md` — 시각화 응답 케이스 정리와 브라우저 렌더링 검증·개선.

## 요구사항
- api-agent 응답(`AgentExecutionResult`·`ChartData`)의 가능한 모든 형태와 admin 렌더링 분기를 교차해, 한쪽만 처리 가능한 틈을 드러내는 케이스 매트릭스를 만든다. 행 형식: `케이스ID | 입력(요청/응답 JSON 요지) | 기대 렌더링 | 확인 방법 | 결과`.
- 케이스 변주 축: success/failure, chartData 0·1·다수 개, chartType별, dataPoints 0·1·다수·극단값·긴 라벨, TSID 문자열 ID.
- 각 케이스를 playwright MCP 브라우저(admin 로그인 → `/agent`)에서 재현·확인하고, 불일치는 원인을 백엔드(응답 데이터)/프런트(렌더링)로 갈라 최소 수정 후 재검증한다. 코드 리딩만의 통과 판정 금지.
- `run` 케이스는 LLM 실 호출 최소화, 조회 케이스는 저장된 데이터로 확인. API 키는 백엔드 ENV에만.
- 매트릭스 문서는 `docs/reference/` 하위 폴더의 3자리 번호 체계(`NNN-이름.md`)에 맞춰 추가. ls 확인: design 011까지(011이 agent 차트 응답 설계), agent-pipeline 007까지 사용 중.

## API 계약 (기존 — 변경 아님, 검증 기준)
- POST `/api/v1/agent/run` → `ApiResponse<AgentExecutionResult>`: `success`, `summary`, `sessionId`(문자열), `toolCallCount`, `analyticsCallCount`, `executionTimeMs`, `errors`(실패 시, 성공 시 빈 배열), `chartData`(실패 시 빈 배열).
- `ChartData`: `chartType`, `title`, `meta{groupBy, startDate, endDate, totalCount}`, `dataPoints[{label, value}]`.
- GET `/sessions`, `/sessions/{id}`, `/sessions/{id}/messages` — 조회 케이스 재현용.
- 프런트 분기(현행): `agent-chart.tsx`는 chartType이 `pie`·`bar`가 아니거나 dataPoints가 빈 배열이면 렌더링하지 않음. `chart-section.tsx`는 chartData 빈 배열이면 렌더링하지 않음.

## 수용 기준 (검증 가능하게)
- AC1: 케이스 매트릭스 문서가 `docs/reference/` 번호 체계에 맞는 경로에 존재하고, 모든 행에 케이스ID·입력·기대 렌더링·확인 방법·결과가 채워져 있다.
- AC2: 백엔드 응답 생성 분기(success/failure 팩토리, chartData 채움 경로)와 프런트 조건 분기(chartType 판별, 빈 dataPoints, 실패 응답, 로딩·빈 상태) 각각이 매트릭스에 대응 행을 가진다(누락 0).
- AC3: 한쪽만 처리 가능한 형태는 매트릭스에 "불일치 후보"로 표시되고, 처리 방향이 확정 기록된다.
- AC4: 모든 케이스의 결과 칸이 브라우저(playwright MCP) 확인 기반 "기대값 렌더링 확인"으로 채워진다. 확인 불가 케이스는 이유와 함께 보고.
- AC5: 백엔드 수정에는 테스트가 동반되고 영향 모듈 `./gradlew {module}:test` 전부 그린.
- AC6: 프런트 수정은 admin `npm run lint`·`npm run build` 통과, tech-n-ai-frontend 별도 브랜치에 커밋(admin/CLAUDE.md 규약).
- AC7: PR 초안에 프런트 브랜치명과 변경 요약이 기록된다.

## CQRS 영향 (impl-config.yml cqrs_checklist)
- aurora_entity: 비해당 (조회·렌더링 검증, 저장 모델 변경 없음)
- schema_sql (docs/sql): 비해당
- writer_reader: 비해당
- history: 비해당
- kafka_event / kafka_handler: 비해당
- mongodb_document: 비해당 (수정은 api-agent 응답 생성 범위 예상. P2에서 저장 계층까지 닿으면 재판정)
- id_serialization: 신규 ID 노출 없음. 기존 `sessionId` 등 TSID가 문자열로 유지되는지 케이스로 검증.

## 범위 경계
- 포함: 케이스 매트릭스 문서(백엔드 저장소), api-agent 응답 생성 수정+테스트, admin 렌더링 수정, playwright MCP 브라우저 검증.
- 제외: 새 차트 타입 추가, 인증 체계 변경, api-chatbot 등 다른 API, admin 외 앱(app/) 화면.
- git 플로우: 파이프라인 worktree·commit·push·초안은 tech-n-ai-backend 변경분에만 적용(프런트는 AC6·AC7 방식).

## 미해결 질문 → 확정
- Q1(차트 타입 방어 #1): 백엔드가 chartType을 `pie`·`bar`로만 하드코딩(`EmergingTechAgentTools`)하고 `dataPoints`가 비면 `ChartData`를 만들지 않으므로, `agent-chart.tsx` 71·72행 방어 분기는 현재 도달 불가. → **확정(2026-07-09 사용자 결정): 방어 분기는 유지하고 매트릭스에 "도달 불가 방어분기"로 기록만.** 프런트가 백엔드 응답을 완전히 신뢰하지 않는 게 안전하므로 제거하지 않는다(CLAUDE.md Surgical Changes: 죽은 코드는 언급만).
- Q2(문서 위치): → **확정: `docs/reference/design/012-*.md`** (011이 차트 응답 설계이므로 후속).
- Q3(admin 로그인): 브라우저 검증 시작 시점에 사용자에게 받음(문서·코드 기록 금지). → 유지.
- Q4(run 유도 프롬프트): 통계 pie → "provider별 통계" 계열, 키워드 bar → "키워드 빈도 분석" 계열, 미호출 → 단순 조회. → 브라우저 검증 단계에서 케이스별 최소 프롬프트 확정.
- Q5(#2 타입): `value`·`totalCount`은 Java `long` → 전역 Jackson으로 JSON 문자열. TS 타입은 `number`. → **확정: `agent.ts`의 `DataPoint.value`·`ChartMeta.totalCount`를 `string`으로 정정. `Number()` 강제변환 코드는 유지.** 런타임 동작 불변(타입 정직성 개선).
- Q6(#3 이력 차트): 이력 재열람 시 차트 소실. 복원하려면 백엔드 5개 모듈+공유 chatbot 변경 필요. → **확정(2026-07-09 사용자 결정): 차트는 run 실행 응답에만 표시(휘발). 이력 재열람은 요약 텍스트만 = 의도된 동작. 백엔드·프런트 코드 변경 없음, 매트릭스에 기록.**

## P2 확정 — 응답 형태 × 렌더링 불일치 후보 (수정 범위: 3건 전부)
- 백엔드 `/run` 응답 형태: A 정상+분석Tool 미호출(chartData=[]), B 통계성공(pie 1), C 키워드성공(bar 1), D 둘다(pie+bar), E 루프감지 강제종료(success=true, summary는 chartData로 합성, chartData 누적), F 예외(success=false, errors=[msg], chartData=[]).
- #1: 프런트 `agent-chart.tsx` 71·72행 방어 분기가 죽은 코드(백엔드 미도달) → 정리.
- #2: `value`·`totalCount`(Java long)가 전역 Jackson Long→String 직렬화로 JSON 문자열인데 TS 타입은 `number`. 프런트가 `Number()`로 강제 변환 중 → TS 타입을 사실에 맞게 정정.
- #3: `agent/page.tsx` `toDisplayMessages`가 이력 메시지에서 chartData를 복원하지 않아(세션 재열람 시 차트 소실) → 복원. **백엔드에 chartData를 대화 메시지로 저장·조회 응답에 포함하는 설계가 필요 — P4에서 확정.**

## 영향 모듈·설계
- 영향 모듈(Gradle path): :api-agent (#3 이력 저장·조회 시 common-conversation / datasource-mongodb까지 닿을 수 있음 — P4 설계에서 확정). 별도 저장소: tech-n-ai-frontend admin(#1·#2·#3 프런트분).
- 진행 게이트: playwright MCP 등록 확인, 검증 환경(docker+gateway 8081+agent 8086+admin 3001) 실행 확인(2026-07-09).
- 확정 설계(P4 승인, 2026-07-09):
  - 백엔드: 코드 변경 없음. 산출물은 케이스 매트릭스 문서 `docs/reference/design/012-*.md` 하나(commit type=docs, 테스트 불필요).
  - 프런트(별도 저장소 tech-n-ai-frontend): #2 타입 정정만 — `admin/src/types/agent.ts`의 `DataPoint.value`·`ChartMeta.totalCount`를 `string`으로. lint·build 통과.
  - 검증: 케이스별 playwright 브라우저 렌더링 확인 → 매트릭스 결과 칸 기록. #2는 타입 전용이라 런타임 렌더링 불변(재검증 불필요, lint·build로 확인).
