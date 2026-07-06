# 02. 알람은 있는데 받는 사람이 없다 — SNS 구독 0건이 남기는 신뢰성 공백

> 1차 소스: [`devops/aws/{dev,beta,prod}/observability.png`](../../../../../../../devops/aws/prod/observability.png) · [`envs/prod/sns.tf`](../../../../../../../devops/terraform/envs/prod/sns.tf)·[`variables.tf`](../../../../../../../devops/terraform/envs/prod/variables.tf)

## 한줄 요약(Hook)

> 다이어그램 "5 이메일 구독" 단계에는 "alert_emails 목록으로 이메일 구독 생성. 현재 어느 tfvars도 미설정 → 구독 0건(수동 추가 유지)"이라고 적혀 있다. 알람 평가(3단계)부터 SNS 발행(4단계)까지 파이프라인은 세 환경 모두 완전히 가동 중인데, 마지막 구독자 목록만 비어 있다. 알람이 울려도 지금은 아무에게도 가지 않는다.

## 핵심 질문

- `aws_sns_topic_subscription`이 `alert_emails` 변수의 `for_each`로 만들어지는 구조에서, 이 리스트가 비어 있으면 정확히 무엇이 만들어지고 무엇이 안 만들어지는가?
- 코드 주석은 이 상태를 "기존 동작(구독자를 IaC 밖에서 수동 추가)을 그대로 유지한다"고 설명한다 — 왜 구독자 관리를 IaC 안으로 끌어들이지 않고 밖에 남겨뒀는가?
- 알림 파이프라인이 "발행까지는 성공하지만 아무도 받지 못하는" 상태로 존재하는 것은, 관측성 시스템 전체의 신뢰성 관점에서 어떤 위험을 남기는가?

## 다루는 관점

- ✅ 구현 — `alert_emails` 변수와 `aws_sns_topic_subscription`의 `for_each` 구조
- ✅ 운영 — 알림 파이프라인이 "조용히 비어 있는" 상태를 놓치지 않는 법

## 근거

- 다이어그램: prod·dev·beta `observability.png`의 "4 SNS 통지"·"5 이메일 구독" 단계 설명 — "알람·복구(ok_actions) 모두 alerts 토픽으로. KMS(logs 키) 암호화... alert_emails 목록으로 이메일 구독 생성. 현재 어느 tfvars도 미설정 → 구독 0건(수동 추가 유지)"
- `envs/prod/sns.tf`(1~18행) — `aws_sns_topic.alerts`(KMS `logs` 키로 암호화), `aws_sns_topic_subscription.alerts_email`(`for_each = toset(var.alert_emails)`), 상단 주석: "구독자는 tfvars의 alert_emails로 주입한다. 비우면 구독을 만들지 않아 기존 동작(구독자를 IaC 밖에서 수동 추가)을 그대로 유지한다."
- `envs/prod/variables.tf`(298~302행) — `alert_emails` 변수 선언, `default = []`, 설명: "알람 통지를 받을 이메일 목록. 비우면 SNS 구독을 만들지 않는다."
- `modules/observability/main.tf`(18~19행) — `alarm_actions = var.alarm_sns_topic_arn == null ? [] : [var.alarm_sns_topic_arn]`(SNS 토픽 자체가 없으면 알람만 만들고 알림은 연결하지 않는다는 별도 안전장치)

## 타깃 독자 & 난이도

- 알람과 SNS 토픽까지는 IaC로 배포했지만, "그래서 실제로 알림이 도착하는지"를 별도로 검증한 적 없는 백엔드·인프라 엔지니어
- ★★★☆☆ (사전지식: SNS 토픽·구독 기본 개념, Terraform `for_each` 문법)

## 예상 분량

- 보통 (~3,000자)

## 글 아웃라인

1. **들어가며 — 파이프라인의 마지막 한 칸만 비어 있다**
   - 6단계 흐름 중 5단계 "이메일 구독"만 "현재 0건"이라고 적힌 이유를 살펴보는 데서 출발
2. **`for_each`가 빈 리스트를 만나면 생기는 일**
   - `toset(var.alert_emails)`가 빈 리스트일 때 `aws_sns_topic_subscription` 리소스가 하나도 생성되지 않는다는 사실을 코드로 확인
3. **왜 구독자 관리를 IaC 밖에 남겼는가**
   - "기존 동작을 그대로 유지한다"는 주석이 뜻하는 것 — 구독자를 자주 바꾸는 운영 조직에서 Terraform 리비전을 매번 만들지 않으려는 실용적 선택
4. **"발행은 성공, 수신자는 0명"이 만드는 신뢰성 공백**
   - 알람이 `ALARM` 상태로 전이돼 SNS Publish까지는 성공하지만, 그 메시지를 읽는 사람이 아무도 없다면 모니터링 시스템 전체가 "동작하는 것처럼 보이지만 실제로는 무용지물"인 상태에 빠질 수 있다는 것
5. **결론 — 파이프라인의 끝단(구독자)까지 검증 대상에 포함하는 습관**
   - 알람이 울리는지가 아니라, 알람이 "누군가에게 실제로 도달하는지"까지 확인해야 완전한 검증이라는 정리

## 참고할 1차 출처

- Amazon SNS 이메일 알림: https://docs.aws.amazon.com/sns/latest/dg/sns-email-notifications.html
- Amazon SNS 구독 생성: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Notify_Users_Alarm_Changes.html

## 시리즈 인용 관계

**[01 — RunningTaskCount 알람만 결측치를 장애로 본다](./01-running-task-count-missing-data.md)**가 다룬 알람 평가 단계 다음의, 같은 파이프라인의 통지 단계를 다룬다. 01의 결측치 처리 로직은 반복하지 않는다.

## 작성 메모

- "구독자가 0명인 것은 실수다"라고 단정하지 않는다. 코드 주석이 이것을 의도된 설계("기존 동작 유지")라고 명시하므로, 그 의도를 존중하면서도 실무적으로 남는 리스크(문서화되지 않은 수동 구독에 의존하는 것)를 함께 짚는 균형 잡힌 톤을 유지한다.
- `tech-n-ai-backend/CLAUDE.md`의 "확인되지 않은 정보를 사실처럼 제시하지 않는다" 원칙에 따라, 실제 운영 환경에서 수동으로 구독이 추가돼 있는지는 이 저장소의 코드만으로 확인할 수 없다는 점을 "확인 필요"로 남긴다.
