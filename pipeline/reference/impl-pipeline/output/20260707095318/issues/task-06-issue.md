## 목표
동작하는 음성 대화를 실서비스 가정에 맞게 다듬는다. 응답 대기를 줄이고, 마이크 열어두기·요청 폭주 같은 오남용을 막고, 네트워크 오류에서 데이터 손실 없이 회복되게 한다.

- task 문서: [docs/tasks/task-06-latency-abuse-resilience.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/tasks/task-06-latency-abuse-resilience.md)
- prompt 문서: [docs/prompts/prompt-06-latency-abuse-resilience.md](https://github.com/thswlsqls/ringle-fullstack/blob/main/docs/prompts/prompt-06-latency-abuse-resilience.md)

## 완료 기준
- [ ] AI 첫 오디오가 응답 완성 전에 재생되기 시작한다(문장 단위 TTS 동작)
- [ ] 마이크를 계속 열어두면 제한 길이(60초)에서 자동 종료된다
- [ ] 연타·폭주 요청이 클라이언트와 서버 양쪽에서 막힌다(오남용 4종: 장시간 녹음·연타·폭주·무발화)
- [ ] 백엔드를 잠시 죽였다 살려도 앱이 에러 안내 → 재시도로 회복된다(데이터 손실 없이)
- [ ] 주제 이탈 유도 입력에도 시나리오로 복귀한다(프롬프트 정교화)
- [ ] 전체 테스트·lint 통과

## 범위 제외
문서화·제출 준비(task-07).
