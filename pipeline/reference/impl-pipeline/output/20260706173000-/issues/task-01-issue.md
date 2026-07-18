<!-- title: Task 01: 멤버십 도메인 모델 (Backend) → https://github.com/thswlsqls/ringle-fullstack/issues/1 -->

## 목표
멤버십 핵심 규칙 — 기능 3종(학습/대화/분석) 권한 조합 + 이용 기한 — 을 Rails 모델로 만들고, 만료 판정 로직을 테스트로 고정한다.

- task 문서: [docs/tasks/task-01-membership-domain.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/tasks/task-01-membership-domain.md)
- prompt 문서: [docs/prompts/prompt-01-membership-domain.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/prompts/prompt-01-membership-domain.md)

## 완료 기준
- [ ] `bin/rails db:prepare && bin/rails db:seed` 성공, 콘솔에서 베이직/프리미엄 상품과 유저 조회
- [ ] 만료 멤버십의 `active?`가 false이고 `active` scope에서 빠짐 (테스트)
- [ ] 만료 경계값 테스트 존재·통과 (직전 유효 / 정각·직후 만료)
- [ ] `permits?`가 세 기능 각각에 올바른 값 (테스트)
- [ ] `bin/rails test`, `bin/rubocop` 통과

## 범위 제외
API 엔드포인트·결제(task-02), UI(task-03)
