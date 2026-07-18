## 목표
멤버십이 할당되는 두 경로(어드민 강제 부여/삭제, 유저 결제)를 API로 만든다. 결제의 PG 호출은 mock object를 거치게 하고, 만료 여부(`active`)를 응답에 담는다.

- task 문서: [docs/tasks/task-02-membership-api.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/tasks/task-02-membership-api.md)
- prompt 문서: [docs/prompts/prompt-02-membership-api.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/prompts/prompt-02-membership-api.md)

## 완료 기준
- [ ] curl: 어드민 부여 → `GET /me/memberships` 반영 → 어드민 삭제 → 목록에서 제거가 재현된다
- [ ] curl: 결제 → 멤버십과 결제 기록(Payment)이 생성된다
- [ ] 결제 시 주입된 gateway mock 호출이 테스트로 검증된다
- [ ] 비어드민의 어드민 API 접근 → 403, 없는 유저/상품 → 404
- [ ] 만료된 멤버십이 `active: false`로 내려온다 (`Membership#active?` 재사용)
- [ ] 결제 성공 시 Payment + Membership 생성이 한 트랜잭션으로 묶인다
- [ ] `bin/rails test`, `bin/rubocop` 통과

## 설계 확정 사항
- 유저 식별: `X-User-Id` 헤더. 헤더 없음 → 400, 없는 유저 → 404. 어드민 아님 → 403.
- 에러 응답 형태: `{ "error": "메시지" }`.
- 같은 유저·같은 상품의 멤버십 중복 허용.

## 범위 제외
- 결제 실패의 정교한 처리(스펙이 "성공 가정"이라 실패는 단순 502 에러까지만), UI(task-03), 실제 PG 연동·인증.
