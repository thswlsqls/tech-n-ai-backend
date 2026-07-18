# State: Task 06 — 지연 단축·프롬프트 정교화·오남용 방지·네트워크 오류 대응

- **task 문서**: docs/tasks/task-06-latency-abuse-resilience.md
- **prompt 문서**: docs/prompts/prompt-06-latency-abuse-resilience.md
- **영역**: backend+frontend
- **현재 상태**: implemented
- **run 폴더**: pipeline/output/20260707095318
- **브랜치**: task/06-latency-abuse-resilience
- **worktree**: ../ringle-fullstack-worktrees/task-06
- **이슈**: #(설계 승인 후 초안 → validate가 제출)
- **PR**: #(검증 통과 후 초안 → validate가 제출)

## 단계 이력
| 시각 | 단계 | 결과 |
|------|------|------|
| 07-07 09:53 | loaded | 문서·코드 로드, 완료 기준 4개 확인 |
| 07-07 10:05 | designed | rate limit=Rails8 내장, 문장TTS 큐(동시2, 조각순차재생), GET 자동재시도, 낙관적UI. 사용자 승인 후 진행(이후 설계 승인 게이트 제거) |
| 07-07 10:10 | implemented | backend 43458d2(rate limit+프롬프트, 55 runs 0F), frontend 2cc454d(문장TTS·오남용·오류, 37 tests·lint0·build ok) |
| 07-07 10:35 | verified | 리뷰어 3종(버그·단순성·규약) 모두 confidence≥80 지적 0. worktree 전체 테스트·lint·build 그린 재확인 |

## 수용 기준 (task 완료 기준 + prompt 성공 기준)
1. AI 첫 오디오가 응답 완성 전에 재생되기 시작한다(문장 단위 TTS 동작).
2. 마이크를 계속 열어두면 제한 길이에서 자동 종료된다. 연타·폭주 요청이 클라이언트와 서버 양쪽에서 막힌다(오남용 4종: 장시간 녹음·연타·폭주·무발화).
3. 백엔드를 잠시 죽였다 살려도 앱이 에러 안내 → 재시도로 회복된다(데이터 손실 없이).
4. 전체 테스트·lint 통과.

## 가정·설계 결정 (task-07 재료)
- **Rate limit**: Rails 8.1 내장 `rate_limit` 매크로 사용(gem·미들웨어 없이 관용적). 유저별(`X-User-Id`) 분당 한도, 초과 시 429 `{ error }`. test/dev는 `Rails.cache`가 필요하므로 캐시 스토어를 `:memory_store`로 지정.
- **문장 단위 TTS 재생**: 어시스턴트 메시지에 오디오 Blob 배열(`audioParts`)을 보관. 자동재생은 문장 인덱스 순서를 보장하는 재생 큐, 재생 버튼은 조각을 처음부터 순차 재생.
- **TTS 병렬 동시성**: 최대 2개 동시 요청, 나머지는 큐 대기. 요청 큐와 재생 순서 큐를 분리.
- **한도값(상수/ENV, README 기록)**: 최대 녹음 60초, 최소 발화 ~300ms(이하 STT 생략+안내). rate/분: 대화 20, STT 30, TTS 60.

## 검증 결과
- 자동: (구현 후)
- 수동: (실제 키 검증 절차 정리 예정)

## 남은 수작업
- 실제 OPENAI_API_KEY로 체감 지연 비교, 오남용 4종 재현, 백엔드 kill→복구 재현
