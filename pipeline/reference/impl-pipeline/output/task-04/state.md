# State: Task 04 — 대화 화면 진입 게이트 + LLM 스트리밍 대화

- **task 문서**: docs/tasks/task-04-conversation-gate-llm.md
- **prompt 문서**: docs/prompts/prompt-04-conversation-gate-llm.md
- **영역**: backend+frontend
- **현재 상태**: verified
- **브랜치**: task/04-conversation-gate-llm
- **이슈**: #7
- **PR**: #8
- **run 폴더**: 20260706225818

## 수용 기준 (task 완료 기준 + prompt 성공 기준)
- [ ] 대화 권한 없는 유저는 화면에 못 들어가고, 만료 멤버십도 403을 받는 것이 테스트로 확인된다.
- [ ] 실제 OpenAI 키로: 진입 → AI 인사말 스트리밍 표시 → 텍스트로 답장 → AI가 주제를 유지하며 응답 (수동 검증)
- [ ] AI가 먼저 말을 시작하고, 주제를 유지한다.
- [ ] `bin/rails test`, `npm run test`, lint 모두 통과.

## 단계 이력
| 시각 | 단계 | 결과 |
|------|------|------|
| 07-06 22:58 | loaded | task-03 origin/main merge 확인 후 로컬 main 최신화. 문서 로드, 완료 기준 4개 확인 |
| 07-06 23:02 | designed | Net::HTTP LlmClient + ConversationAccess concern(3사유) + Live SSE 컨트롤러 + 프론트 getConversationAccess/streamConversation + Conversation 화면. 3갈림길 사용자 승인(Net::HTTP/gpt-4o-mini/3사유). 이슈 #7, 브랜치 생성 |
| 07-06 23:15 | implemented | BE 커밋 a46d7b5(8파일, Minitest 6 stub 부재로 with_llm_client 주입 헬퍼), FE 커밋 db1fba1(6파일, 게이트 리다이렉트 403 한정). BE test 42 runs 0 fail·rubocop clean, FE test 13 pass·lint exit 0(경고 1)·build 성공. 직접 재검증 완료 |
| 07-06 23:20 | verified | 리뷰어 3(정확성/단순성·범위/규약·테스트) 병렬, confidence≥80 지적 0건. 임계 미만 2건(비정상 스트림 종료 시 sending 잔류=task-06, 실시간 부착 중간상태 미검증)은 범위 밖이라 미수정 |
| 07-06 23:28 | pushed | 지식 문서·state·run 초안 커밋 05954f3(amend로 PR 초안 포함), push. PR #8 생성. merge는 사용자 승인 대기 |
| 07-06 23:33 | validated | run-task-validate 4게이트 통과: 초안 정확·범위 정합·검증 실증(rails 42 runs 0 fail, rubocop 52 files clean, vitest 13 pass, lint 경고1, build OK)·제출 정합(이슈 #7·PR #8 OPEN, 드리프트 없음). run 폴더 20260706225818- 마킹 |

## 가정·설계 결정 (task-07 재료)
- Provider는 OpenAI 하나로 LLM·STT·TTS를 커버(키 1개). chat 모델은 `gpt-4o-mini`(지연·비용 낮음)를 코드 상수로 고정. 근거는 platform.openai.com/docs. (사용자 승인)
- LLM 클라이언트는 신규 gem 없이 얇은 `Net::HTTP` 클라이언트(`LlmClient#stream_chat`)로 만든다. 주입/stub 이음새로 테스트는 실제 API를 안 부른다. task-05 오디오 멀티파트는 그때 별도. (사용자 승인)
- 게이트 거절 사유를 세 가지 code로 구분: no_membership / expired / no_permission. 만료·권한 판정은 `Membership#active?` + `MembershipType#permits?(:conversation)` 재사용. (사용자 승인)
- 대화 세션은 서버 무상태 — 이력은 클라이언트가 보관하고 매 요청 전체 이력을 전송한다(스펙상 세션 관리 API optional).
- 게이트 확인용 `GET /api/v1/conversations/access`를 별도로 둔다(프론트가 진입 전 호출). 대화는 `POST /api/v1/conversations/messages`.
- SSE 프레임은 JSON data 라인으로 통일: `{"delta":...}` / `{"done":true}` / `{"error":...}`. POST+이력 전송이라 EventSource가 아니라 fetch+ReadableStream으로 수신.
- OpenAI 키는 `OPENAI_API_KEY` 백엔드 ENV만. 자동 테스트는 LLM stub, 실연동은 수동 검증에서만.

## 검증 결과
- 자동: (구현 후 기입)
- 수동: (구현 후 기입)

## 남은 수작업
- 실제 OpenAI 키로 브라우저 end-to-end 확인, task-07용 스크린샷 캡처
