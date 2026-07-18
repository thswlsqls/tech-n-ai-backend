# impl Pipeline — Learnings (closed-loop)

이 파이프라인이 실행할수록 똑똑해지는 단일 메커니즘이다. 별도 DB·통계엔진을 만들지 않는다(마크다운 한 장).
기록 규칙: 사실만, 한 줄씩 **append**, 한 줄 500자 이내. 기존 줄 수정 금지 —
단 §0의 pending→merged/closed 졸업(P0 폴링)만 예외.
병렬 실행 대비: 파일 전체 재작성 금지, append 직전 다시 읽고 실패하면 한 번 재시도.

읽기 규칙(비대화 대비): 전체 Read 금지. §0은 status=pending 줄만, §1~§4는 대상 모듈명
grep 매치 + 섹션별 최근 15줄만 선별 주입한다(impl-config.yml learnings.selective_injection).

- §0 제출 추적: validate 스킬이 PR 제출 후 한 줄 추가. 다음 실행 P0가 `gh pr view`로 머지/리젝을 졸업시킨다.
- §1 작업 레지스트리: 이미 구현한 작업(중복 방지). 오케스트레이터 P0가 보고 P7이 적는다.
- §2 캘리브레이션: 어떤 구현 패턴이 머지/리젝됐나, 리뷰 피드백. P0 졸업이 적는다.
- §3 모듈 메모: 구현 중 관찰한 모듈 특성·함정. explorer가 읽고 P7이 적는다.
- §4 프로세스 교훈: 빌드 실패·규약 위반·파이프라인 자체 결함. 다음 실행 전체가 참고.

---

## §0 제출 추적 (submission-tracking)
<!-- validate: `- [<modules>] <work-key> issue=#<N> PR=#<M> branch=<name> status=pending run=<RUN_ID> date=<YYYY-MM-DD>` -->
<!-- 다음 실행 P0가 status를 merged|closed로 졸업 -->
- [docs/012, frontend-admin] task-01 issue=#3 PR=#4 branch=docs/agent-visualization-cases status=pending run=20260709103227 date=2026-07-09

## §1 작업 레지스트리 (work-registry)
<!-- P7: `- [<modules>] <work-key> <요약> | branch=<name> | commit=<hash> | run=<RUN_ID>` -->
- [docs/012, frontend-admin] task-01 admin↔api-agent 시각화 응답 케이스 매트릭스 정리+브라우저 렌더링 검증. 백엔드 코드 변경 0(문서만), 프런트 #2 타입 정정 | branch=docs/agent-visualization-cases | commit=15f0842 | run=20260709103227

## §2 캘리브레이션 (calibration)
<!-- P0 졸업: `- [<modules>] <패턴 요약> → merged|rejected | <work-key>` / 리뷰 피드백도 여기 -->

## §3 모듈 메모 (module-memo)
<!-- P7: `- [<module>] <관찰 — 함정·패턴·테스트 특이점>` -->
- [api-agent] AgentExecutionResult.chartData의 DataPoint.value·ChartMeta.totalCount는 Java long → 전역 Jackson으로 JSON 문자열 직렬화. 프런트 소비 타입은 number가 아니라 string이어야 맞다.
- [api-agent] chartData는 /run 실행 응답에만 실린다. 대화 메시지 저장(ConversationMessageEntity/Document, MessageResponse)에 chartData 필드가 없어 세션 재열람 시 차트는 복원되지 않음(의도된 동작). 복원하려면 common-conversation·kafka·aurora·mongodb 관통 필요 + chatbot 영향.
- [api-agent] 실제 /run에서 LLM이 STOP 메시지 무시하고 통계 tool 반복 호출 시 루프 감지까지 30초+ 소요 → 프런트/프록시 타임아웃(500). 루프 감지는 success로 처리되나 응답이 늦어 브라우저에 미도달.

## §4 프로세스 교훈 (process-lesson)
<!-- P7: `- <빌드 실패·규약 위반·반복 실수·파이프라인 결함>` -->
- 프런트 렌더링 검증에서 실제 LLM run이 불안정할 때(루프·타임아웃), playwright page.route(browser_run_code_unsafe)로 /run·/sessions·/messages 응답을 케이스별로 통제하면 결정적으로 확인 가능. 통제응답이라도 React/recharts는 실제 실행되므로 코드 리딩이 아니다. 극단값·긴라벨·success=false처럼 LLM이 못 만드는 형태도 이 방식이라야 검증된다.
- admin 저장소에 eslint 설정 파일이 없어 `npm run lint` 실행 불가(app/에는 eslint.config.mjs 있음). 프런트 타입 검증은 `npx tsc --noEmit` + `npm run build`의 TypeScript 체크로 대체.
- playwright browser_take_screenshot은 MCP 5s 제한, page.screenshot도 폰트 로드 후 멈추는 경우 있음 → DOM 단언(locator count/textContent)을 주 증거로 쓰고 스크린샷은 보조.
- 프런트 타입(number→string) 변경 시 test-chart 같은 스캐폴딩 소비처의 리터럴도 함께 고쳐야 tsc/build 통과.
