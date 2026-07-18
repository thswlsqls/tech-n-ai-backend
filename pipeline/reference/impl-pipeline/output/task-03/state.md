# State: Task 03 — 홈 화면(멤버십 현황·구매) + 어드민 UI (Frontend)

- **task 문서**: docs/tasks/task-03-home-admin-ui.md
- **prompt 문서**: docs/prompts/prompt-03-home-admin-ui.md
- **영역**: frontend
- **현재 상태**: verified
- **브랜치**: task/03-home-admin-ui
- **이슈**: #5
- **PR**: #6
- **run 폴더**: pipeline/output/20260706221820

## 수용 기준 (task 완료 기준 + prompt 성공 기준)
- [ ] 브라우저: 유저 전환 → 홈에서 상품 구매 → 멤버십 카드에 나타남 → 어드민에서 삭제 → 홈에서 사라짐 (end-to-end)
- [ ] 홈: 보유 멤버십(상품명·기능 뱃지·만료일·만료 여부) + 구매 상품 카드(이름·기간·가격·기능·구매 버튼) 렌더
- [ ] 유저 전환 드롭다운(GET /users), 선택값 localStorage 유지
- [ ] 어드민: 유저별 보유 멤버십 표시, 부여/삭제, 비어드민 접근 시 안내 문구
- [ ] 구매·부여·삭제 후 목록 즉시 갱신, 로딩/에러 표시
- [ ] `npm run test`, `npm run lint`, `npm run build` 통과

## 단계 이력
| 시각 | 단계 | 결과 |
|------|------|------|
| 07-06 22:18 | loaded | 문서 로드, 선행조건(task-02 API merge·응답 형태) 검증, 완료 기준 6개 확인 |
| 07-06 22:25 | designed | 라우터 도입 + api.ts 래퍼 + currentUser Context + useResource 훅 + Layout/cards + Home/Admin/Conversation. 어드민 조회는 /me/memberships 재사용, react-query 미도입. 사용자 승인. 이슈 #5, 브랜치 생성 |
| 07-06 22:40 | implemented | 커밋 c88f989, 19파일(frontend만). test 3 files 10 tests pass, lint exit 0(경고 1: currentUser only-export-components), build 성공. adminGrant/Remove는 admin id(헤더)+대상 id(경로) 분리로 3인자 |
| 07-06 22:45 | verified | test/lint/build 직접 재통과 확인. 리뷰어 2(버그·정확성 / 단순성·규약·테스트) 병렬. confidence≥80 지적 1건(Home 초기 로드 X-User-Id 누락) → 사용자 승인 후 수정. 커밋 e209032, test 11 pass |
| 07-06 22:50 | pushed | 지식 문서·state·run 초안 커밋 6e5881c, push. PR #6 생성. merge는 사용자가 **보류** — PR 열어 둠 |
| 07-06 22:52 | validated | run-task-validate 4게이트 통과: 초안=GitHub 실물(이슈 #5·PR #6) 일치, diff는 frontend+허용 인프라 문서만, 비밀값 없음. test 11 pass·lint exit 0(경고 1)·build 성공 재확인. run 폴더 20260706221820 마킹. merge는 미수행(보류) |

## 가정·설계 결정 (task-07 재료)
- 어드민의 "유저별 보유 멤버십"은 전용 admin 조회 엔드포인트가 없어 `GET /me/memberships`를 대상 유저 id를 X-User-Id에 실어 재사용한다. 인증이 없는 과제의 X-User-Id 전제와 일관되며 백엔드를 수정하지 않는다. (사용자 승인)
- 서버 상태는 react-query 등 라이브러리 없이 플레인 훅(useEffect/useState) + fetch 래퍼로 처리한다. 엔드포인트가 적고 뮤테이션 후 수동 refetch면 충분 — 오버엔지니어링 금지 원칙. (사용자 승인)
- 현재 유저 상태는 전역 상태 라이브러리 없이 React Context + localStorage로 유지한다. 첫 로드 시 localStorage가 비어 있으면 유저 목록의 첫 유저를 기본 선택한다.
- 어드민 게이팅은 서버 왕복 없이 `/users` 응답의 `admin` boolean으로 판단한다.
- 라우터는 react-router-dom을 도입한다(라우팅 요구사항). 유일한 신규 런타임 의존성.
- 기존 health 연결 확인 전용 화면(App.tsx)은 홈 라우트로 대체된다 — 실데이터를 부르는 홈 자체가 연동 증명이 된다.

## 검증 결과
- 자동: (구현 후 기입)
- 수동: (구현 후 기입)

## 남은 수작업
- 브라우저 end-to-end 흐름 확인, task-07용 스크린샷 캡처
