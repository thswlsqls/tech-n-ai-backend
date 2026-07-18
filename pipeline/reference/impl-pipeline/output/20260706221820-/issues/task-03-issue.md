## 목표
홈 화면에서 내 멤버십을 확인하고 구매할 수 있게 하고, 어드민이 유저에게 멤버십을 부여/삭제하는 UI를 만든다. (Frontend)

- task 문서: [docs/tasks/task-03-home-admin-ui.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/tasks/task-03-home-admin-ui.md)
- prompt 문서: [docs/prompts/prompt-03-home-admin-ui.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/prompts/prompt-03-home-admin-ui.md)

## 완료 기준
- [ ] 브라우저에서 유저 전환 → 홈에서 상품 구매 → 멤버십 카드에 나타남 → 어드민에서 삭제 → 홈에서 사라짐 흐름이 end-to-end로 동작
- [ ] 홈: 보유 멤버십(상품명·기능 뱃지·만료일·만료 여부) + 구매 상품 카드(이름·기간·가격·기능·구매 버튼) 렌더
- [ ] 유저 전환 드롭다운(GET /api/v1/users), 선택값 localStorage 유지
- [ ] 어드민: 유저별 보유 멤버십 표시, 부여/삭제, 비어드민 접근 시 안내 문구
- [ ] 구매·부여·삭제 후 목록 즉시 갱신, 로딩/에러 상태 표시
- [ ] `npm run test`, `npm run lint`, `npm run build` 통과

## 범위 제외
- 대화 화면(task-04) — 라우트 자리만 만든다.
- UI 스타일 라이브러리 도입 — 간단한 CSS로 충분.
