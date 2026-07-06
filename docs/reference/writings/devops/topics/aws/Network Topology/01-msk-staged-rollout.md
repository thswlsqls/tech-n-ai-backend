# 01. 없음 → Serverless → Provisioned — 다이어그램 세 장에 그려진 MSK 도입의 3단계

> 1차 소스: [`devops/aws/{dev,beta,prod}/network-topology.png`](../../../../../aws/prod/network-topology.png) · [`architecture-facts.md` §2 MSK](../../../../../aws/architecture-facts.md)

## 한줄 요약(Hook)

> dev 다이어그램의 Private-Data 구역에는 MSK 아이콘이 아예 없다. beta에는 있지만 Provisioned가 아니라 Serverless다. prod에만 3-브로커 Provisioned 클러스터가 그려진다 — 이 3장의 차이는 우연이 아니라 `enable_msk`·`use_msk_provisioned` 두 변수로 코드화된 단계적 도입 전략이다.

## 핵심 질문

- 왜 dev 환경은 Kafka 인프라 자체가 없는가 — "우선 만들어두고 안 쓰는" 대신 "아예 안 만든다"를 택한 근거는 무엇인가?
- MSK Serverless와 Provisioned의 기능 차이 중, beta·prod의 선택을 가르는 결정적 항목은 무엇인가?
- 세 환경이 하나의 Terraform 모듈 세트(`modules/msk-serverless`, `modules/msk-provisioned`)를 공유하면서도 다른 인프라를 만들어내는 스위치는 어떻게 짜여 있는가?

## 다루는 관점

- ✅ 설계 근거(Why) — 환경별 Kafka 필요성과 운영 부담의 회계
- ✅ 구현(Terraform 코드) — `enable_msk`/`use_msk_provisioned` 토글과 모듈 분기
- ✅ 운영 — 클러스터 형태 전환(beta→prod) 시 마주치는 재구축 비용

## 근거

- 다이어그램 사실: dev network-topology.png 부제 "no MSK" + Private-Data에 MSK 아이콘 없음 / beta 부제 "MSK Serverless" + MSK 아이콘 있음 / prod 부제 "MSK Provisioned" + Private-Data 3곳 모두 MSK 아이콘
- `architecture-facts.md` §2 MSK(99~104행) — `enable_msk`/`use_msk_provisioned` 값과 파일:라인 출처, Provisioned 상세 스펙(`kafka.m7g.large`×3, `3.9.x.kraft`, RF=3, min.insync.replicas=2)
- `architecture-facts.md` §7 환경 차이 매트릭스(244행), §8 데이터 흐름(260행) — "MSK는 prod=Provisioned, beta=Serverless, dev=없음"
- `devops/results/05-messaging-kafka.md` §1.1 MSK Provisioned vs Serverless 비교표·선정 근거(42~48행), 부록 B ADR-005-002

## 타깃 독자 & 난이도

- 이벤트 기반 아키텍처를 처음 환경별로 굴려보는 백엔드/인프라 엔지니어
- ★★★☆☆ (사전지식: Kafka 기본 개념, VPC/Private 서브넷)

## 예상 분량

- 보통 (~3,500자)

## 글 아웃라인

1. **들어가며 — 다이어그램 세 장 중 하나에만 없는 아이콘**
   - dev 다이어그램을 열었을 때 Private-Data 구역에 MSK가 빠져 있다는 관찰에서 출발
2. **dev: 왜 "일단 만들어둔다"가 아니라 "아예 안 만든다"인가**
   - `enable_msk=false`가 기본값인 이유 — 아직 실사용되지 않는 이벤트 파이프라인에 클러스터 비용·운영 부담을 지지 않는 결정
   - `05-messaging-kafka.md`가 밝히는 현재 IaC 갭(일부 서비스의 MSK IAM 권한 미적용)과의 정합성
3. **beta: Serverless가 "축소판 prod"가 아니라 다른 운영 모델인 이유**
   - 파티션 자동 할당, IAM 인증 전용, 무관리 운영이라는 Serverless의 특성이 트래픽이 낮은 검증 환경에 맞는 이유
4. **prod: 왜 beta의 연장이 아니라 Provisioned로 갈아타는가**
   - Prometheus Open Monitoring 기반 상세 SLI 관측, KRaft 신규 구축·파라미터 튜닝, 향후 mTLS/SASL 혼합 인증 확장성이라는 세 근거
   - 최대 부하(6 MB/s) 기준 요금 비교가 필요하다는 단서까지 정직하게 남기기
5. **하나의 모듈, 세 가지 결과 — 코드로 보는 전환 스위치**
   - `enable_msk`/`use_msk_provisioned` 두 불리언이 어떻게 서로 다른 모듈(`msk-serverless`/`msk-provisioned`) 호출로 갈라지는지
6. **결론 — 클러스터 형태 전환은 "설정값 하나"가 아니다**
   - Serverless→Provisioned 전환은 클러스터 재생성을 수반하므로, beta에서 검증한 토픽·컨슈머 그룹 설계를 prod에 그대로 이식할 수 없는 지점을 짚기

## 참고할 1차 출처

- Amazon MSK 개요(Serverless vs Provisioned 소개): https://docs.aws.amazon.com/msk/latest/developerguide/what-is-msk.html
- MSK Serverless: https://docs.aws.amazon.com/msk/latest/developerguide/serverless.html
- MSK Provisioned: https://docs.aws.amazon.com/msk/latest/developerguide/msk-provisioned.html

## 시리즈 인용 관계

이 단편은 **[series-01 — 다이어그램 세 장을 나란히 읽기](./series-01-three-diagrams-maturity-signal.md)** 의 "관리형 서비스 commitment 단계" 신호로 인용된다. 시리즈 글은 본 단편이 답한 "왜 beta/prod가 각각 Serverless/Provisioned인가"를 반복하지 않고, 이 선택이 NAT·TLS 선택과 같은 방향(dev < beta < prod)으로 움직인다는 사실만 더한다.

## 작성 메모

- "Serverless가 항상 더 간단하고 좋다"는 식으로 단순화하지 말 것. prod가 오히려 Provisioned로 "퇴보"하는 것처럼 보일 수 있으므로, Serverless의 제약(IAM 인증 전용, 파티션 상한)이 prod 요구사항과 안 맞는다는 인과를 분명히 짚는다.
- dev에 MSK가 아예 없다는 사실은 "미완성"이 아니라 "의도된 생략"이라는 톤을 유지한다.
