<!-- title: Task 01: 멤버십 도메인 모델 (Backend) → https://github.com/thswlsqls/ringle-fullstack/pull/2 -->

Closes #1

## 구현 요약
- User(name, admin) / MembershipType(name, duration_days, price, 기능 boolean 3개) / Membership(user, membership_type, expires_at) — 마이그레이션 3개와 모델, seed(베이직·프리미엄 상품, 유저 3명).
- 만료 판정은 `Membership#active?`(`expires_at > Time.current`, 정각=만료) 한 곳에 두고, 같은 규칙의 쿼리판을 `active` scope로 제공. 이후 모든 API가 이 둘만 쓴다.
- 권한 확인은 `MembershipType#permits?(feature)` — `FEATURES` 화이트리스트 밖이면 false. 기능이 늘면 컬럼+FEATURES만 추가.
- `expires_at`은 생성 시 미지정이면 `duration_days.days.from_now` 자동 계산, 직접 지정 가능(어드민 부여·테스트용).

## 테스트·lint
- `bin/rails test`: 18 runs, 34 assertions, 0 failures
- `bin/rubocop`: 32 files, no offenses
- `db:prepare && db:seed` 후 runner 조회로 상품 2종·유저 3명 확인
- 경계값: 만료 1초 전 유효 / 정각 만료 / 1초 후 만료 — travel_to로 고정

## 리뷰
ringle-code-reviewer 2개(버그·정확성 / 범위·규약·테스트) — confidence ≥ 80 이슈 0건.
참고 사항 2건(permits? symbol 전용, has_many dependent 미지정)은 backend/MEMORY.md에 기록.

## 수동 확인
이 task는 외부 API를 쓰지 않아 실제 키 검증 없음. `bin/rails runner 'p MembershipType.pluck(:name)'`로 seed 재확인 가능.
