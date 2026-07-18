Closes #3

## 구현 요약
- 유저 식별·인가를 concern `UserIdentification` 한 곳으로 모음: `X-User-Id` 헤더로 유저를 찾고, 헤더 없음 400 / 없는 유저 404 / 어드민 아님 403. 에러는 `{ "error": "메시지" }`로 통일.
- 멤버십 응답 형태는 `MembershipSerialization#membership_json` 단일 지점. `active`는 task-01의 `Membership#active?`를 재사용해 만료를 재계산하지 않음.
- 엔드포인트: `GET /membership_types`, `GET /users`, `GET /me/memberships`, `GET /admin/users`, `POST /admin/users/:user_id/memberships`, `DELETE /admin/users/:user_id/memberships/:id`, `POST /payments`.
- 결제는 `PaymentService`가 `PaymentGateway`(기본 `MockPaymentGateway`)를 주입받아 처리. 성공 시 Membership + Payment를 한 트랜잭션으로 생성, 실패 시 502. 실제 PG로 바꿀 때 gateway 구현만 교체.
- Payment 모델·마이그레이션 추가(컬럼 최소: amount, status, membership 참조).

## 테스트·lint
- `bin/rails test`: 31 runs, 84 assertions, 0 failures, 0 errors (기존 18 + 신규 13).
- `bin/rubocop`: 47 files, no offenses.
- 핵심 매핑: 만료 멤버십 `active:false`(bob_expired fixture), 결제 `expires_at`이 상품 기간과 일치(`assert_in_delta 60.days.from_now`), gateway 호출 검증(SpyGateway 주입으로 `charge` 호출 금액 확인), 어드민 부여→반영→삭제→제거 흐름.

## 리뷰
- ringle-code-reviewer 2개(버그·정확성·보안 / 단순성·범위·규약·테스트) 병렬 실행. confidence ≥ 80 이슈 0건.
- 임계값 미만 참고: gateway `currency` 인자·`transaction_id` 미사용(실 PG 응답 흉내), `users`/`admin/users`의 동일 map 중복(task가 두 엔드포인트를 명시 요구), 목록 N+1(데이터 규모상 정확성 무관). 범위 최소화 원칙에 따라 수정하지 않음.

## 수동 확인
dev 서버(`bin/rails db:seed` 후 `bin/rails server`)에서 curl로 재현 완료:
- 어드민 부여 → `GET /me/memberships` 반영 → 삭제 → 목록 제거
- 결제(프리미엄) → 멤버십(60일, active) + Payment(29900, paid) 생성
- 비어드민 403 / 헤더 없음 400 / 없는 유저 404 / 공개 목록 조회

외부 API 미사용 task라 실제 키 검증은 없음.
