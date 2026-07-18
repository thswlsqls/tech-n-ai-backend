## 목표
대화 화면 진입을 유효한(미만료 + 대화 권한) 멤버십으로 막고, 실제 OpenAI LLM과 SSE 스트리밍으로 텍스트 대화가 오가는 골격을 만든다. AI가 먼저 말을 시작한다. 음성 입출력은 task-05에서 얹는다.

- task 문서: [docs/tasks/task-04-conversation-gate-llm.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/tasks/task-04-conversation-gate-llm.md)
- prompt 문서: [docs/prompts/prompt-04-conversation-gate-llm.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/prompts/prompt-04-conversation-gate-llm.md)

## 완료 기준
- [ ] 대화 권한 없는 유저는 화면에 못 들어가고, 만료 멤버십도 403(사유 code 포함)을 받는 것이 테스트로 확인된다.
- [ ] 백엔드 대화 엔드포인트가 매 요청마다 `active?` && `permits?(:conversation)`을 검사한다.
- [ ] `POST /api/v1/conversations/messages`가 클라이언트 보관 이력을 받아 SSE로 스트리밍한다(빈 이력이면 AI 인사말로 시작).
- [ ] 프론트 `/conversation` 진입 시 권한 확인 API를 먼저 호출하고, 실패하면 홈으로 돌려보내며 사유를 안내한다.
- [ ] 실제 OpenAI 키로: 진입 → AI 인사말 스트리밍 → 텍스트 답장 → 주제 유지 응답 (수동 검증으로 기록).
- [ ] `bin/rails test`, `npm run test`, lint 모두 통과.

## 범위 제외
음성 입출력·재생(task-05), 오남용 방지와 재시도(task-06).
