# docs : [main] agent 시각화 응답 케이스 매트릭스와 렌더링 검증 문서 추가

Closes #{이슈번호}

## 구현 요약
- `docs/reference/design/012-agent-visualization-response-cases.md` 추가. api-agent 응답 형태(R-A~R-F)와 admin 렌더링 분기를 대조한 케이스 매트릭스, 불일치 후보 3건의 처리 방향, 검증 방법·환경 한계를 담았다.
- 백엔드 응답 생성 코드는 바꾸지 않았다. 코드 대조 결과 응답 형태가 이미 매트릭스를 덮었다.
- 불일치 후보 처리: #1 도달 불가 방어 분기 유지, #2 프런트 타입 정정, #3 이력 차트 휘발 유지(의도된 동작).
- 프런트 #2는 별도 저장소 tech-n-ai-frontend 브랜치 `fix/agent-chart-value-type`에 커밋(commit 502c394). admin `ChartData.DataPoint.value`·`ChartMeta.totalCount`를 백엔드 실제 직렬화(long→JSON string)에 맞춰 `number`→`string`으로 정정. 렌더링은 `Number()` 변환으로 그대로.

## 테스트
- 백엔드: 문서만 추가 — 코드 변경 없어 gradle 테스트 대상 아님.
- 프런트(admin): `npx tsc --noEmit` 통과, `npm run build` 통과(12페이지 생성). admin lint는 저장소에 eslint 설정이 없어 실행 불가(기존 상태) — build의 TypeScript 체크로 대체.
- 렌더링: admin 브라우저(playwright)에서 R-A·B·C·D·F, DP-EXTREME/LONGLABEL, DEAD-TYPE/EMPTY, U-EMPTY/NETFAIL/HIST를 통제응답으로 재현해 전부 기대값 확인. 실제 LLM run은 모델 루프로 타임아웃(문서 7장에 기록).

## 리뷰
자체 점검 1회: 변경 최소(문서 1 + 프런트 타입 2줄), 오버엔지니어링 없음, 고감도 영역 미접촉.

## 수동 확인
차트 렌더링을 직접 보려면 docker compose up -d 후 api-gateway·api-agent 실행, admin(3001) 로그인 → `/agent`.

<!-- ── 파이프라인 제출 보조 (GitHub 본문 제외, validate 스킬이 사용) ──
work-key: task-01
branch: docs/agent-visualization-cases
commit: 15f0842
worktree: /Users/m1/workspace/tech-n-ai/tech-n-ai-backend-worktrees/docs-agent-visualization-cases
frontend-branch: fix/agent-chart-value-type (tech-n-ai-frontend, commit 502c394 — 미push, 별도 PR 대상)
staleness 체크(제출 전 worktree 안에서 실행):
  git -C /Users/m1/workspace/tech-n-ai/tech-n-ai-backend-worktrees/docs-agent-visualization-cases fetch origin
  git -C /Users/m1/workspace/tech-n-ai/tech-n-ai-backend-worktrees/docs-agent-visualization-cases log --oneline HEAD..origin/main -- docs/reference/design/012-agent-visualization-response-cases.md
제출(실행 금지 — /impl-validate만 실행한다):
  gh pr create -R thswlsqls/tech-n-ai-backend --base main --head docs/agent-visualization-cases \
    --title "docs : [main] agent 시각화 응답 케이스 매트릭스와 렌더링 검증 문서 추가" --body-file <본문 추출 tmp>
-->
