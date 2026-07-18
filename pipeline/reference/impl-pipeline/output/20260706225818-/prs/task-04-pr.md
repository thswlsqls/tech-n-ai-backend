Closes #7

## 구현 요약
- 대화 엔드포인트 공통 게이트 `ConversationAccess` concern — 매 요청마다 `active?` && `permits?(:conversation)`를 검사하고, 통과 못하면 403과 사유 code(no_membership/expired/no_permission)를 돌려준다. 만료·권한은 `Membership#active?`·`MembershipType#permits?`를 재사용하고 재계산하지 않는다.
- 얇은 `LlmClient`(신규 gem 없이 `Net::HTTP`) — OpenAI Chat Completions를 `stream: true`로 호출하고, 청크 경계를 버퍼링해 토큰 delta만 뽑아 블록으로 넘긴다. 모델은 `gpt-4o-mini` 상수, `OPENAI_API_KEY`는 백엔드 ENV만, 키 없으면 `MissingApiKey`.
- `ActionController::Live` 기반 `POST /api/v1/conversations/messages` — 클라이언트 보관 이력에 시스템 프롬프트를 앞에 붙여 LLM에 넘기고, SSE 프레임(`data:{"delta"}`/`{"done"}`/`{"error"}`)으로 스트리밍한다. 서버는 대화 상태를 저장하지 않는다(무상태). 빈 이력이면 AI가 인사말로 시작. `GET /api/v1/conversations/access`는 게이트 확인 전용.
- 프론트 `/conversation` — 진입 시 `getConversationAccess`를 먼저 호출해 403이면 홈으로 리다이렉트(라우터 state로 사유 전달, 홈에 배너). 통과하면 빈 이력으로 자동 첫 요청을 보내 AI 인사말을 스트리밍으로 받고, 유저/AI 구분 메시지 목록에 토큰을 실시간으로 붙인다. 검증용 임시 텍스트 입력(음성은 task-05).
- `streamConversation`은 POST+이력 전송이라 `EventSource` 대신 `fetch`+`ReadableStream`으로 SSE를 직접 파싱한다.

## 테스트·lint
- `bin/rails test`: 42 runs, 116 assertions, 0 failures, 0 errors
- `bin/rubocop`: 52 files, no offenses
- `npm run test`: 4 files, 13 tests 통과
- `npm run lint`: exit 0 (기존 `currentUser.tsx` fast-refresh 경고 1건만)
- `npm run build`: 타입체크+빌드 통과
- 완료 기준 매핑: 멤버십 없음/만료/대화권한 없음 각각 403+code를 백엔드 테스트로 고정. 만료 경계는 fixture 상대시간(`1.day.ago`). LLM은 클라이언트를 주입 교체해 실 API 없이 SSE 프레임과 시스템 프롬프트 선두 배치를 검증. 프론트는 게이트 리다이렉트와 SSE 청크 경계 버퍼링(한 프레임을 두 청크로 쪼갬)을 검증.

## 리뷰
ringle-code-reviewer 3개(정확성 / 단순성·범위 / 규약·테스트) 병렬. confidence ≥ 80 지적 0건. 임계 미만 참고 2건(비정상 스트림 종료 시 sending 잔류=task-06 네트워크 복원력 범위, 스트리밍 실시간 부착 중간상태 미검증)은 범위 밖이라 미수정.

## 수동 확인
실제 OpenAI 연동은 자동 테스트가 부르지 않으므로 브라우저 확인이 필요하다.
1. `OPENAI_API_KEY`를 backend ENV(예: `backend/.env`, gitignore됨)에 넣고 Rails 서버(3000)·Vite(5173)를 함께 띄운다.
2. 대화 권한 있는 유저(seed의 프리미엄 보유 유저)로 `/conversation` 진입 → AI 인사말이 스트리밍으로 표시되는지.
3. 텍스트로 답장 → 주제(비즈니스 자기소개)를 유지하며 2~3문장으로 응답하는지, 주제 이탈 유도 입력에 부드럽게 되돌리는지.
4. 대화 권한 없는 유저·만료 유저로 진입 시 홈으로 리다이렉트되며 사유 배너가 뜨는지.
