# 01. RunningTaskCount 알람만 결측치를 장애로 본다 — treat_missing_data 설계 하나

> 1차 소스: [`devops/aws/{dev,beta,prod}/observability.png`](../../../../../../../devops/aws/prod/observability.png) · [`modules/observability/main.tf`](../../../../../../../devops/terraform/modules/observability/main.tf)

## 한줄 요약(Hook)

> 다이어그램 알람 평가 단계 설명에는 짧지만 눈에 띄는 문장이 있다. "running 알람은 결측치를 위반으로 처리." CPU>80이나 MEM>85 알람은 값이 없으면 보통 판단을 보류하는데, `RunningTaskCount<1` 알람만 유독 다르게 설계돼 있다. 코드를 열어 보면 그 이유가 주석 한 줄로 그대로 적혀 있다 — "태스크가 0이 되면 메트릭 송신이 끊겨 INSUFFICIENT_DATA로 빠진다. '서비스 다운'을 놓치지 않도록 결측치를 위반(ALARM)으로 처리."

## 핵심 질문

- CloudWatch 알람의 결측치 처리(`treat_missing_data`)에는 어떤 선택지가 있으며, 각각 언제 적합한가?
- 태스크가 0개가 됐을 때 메트릭 자체가 끊기는 이유는 무엇이며, 이것이 "결측치를 정상으로 처리"하는 기본 옵션과 만나면 왜 위험한가?
- CPU·메모리 알람은 그대로 두고 `RunningTaskCount` 알람만 다르게 설정한 것은, "지표의 성격에 따라 결측치 정책을 다르게 가져가야 한다"는 원칙을 어떻게 보여주는가?

## 다루는 관점

- ✅ 구현 — `aws_cloudwatch_metric_alarm`의 `treat_missing_data` 옵션과 실제 코드 값
- ✅ 운영 — 사일런트 다운타임(메트릭 자체가 사라져서 아무 알람도 안 울리는 상태)을 막는 알람 설계

## 근거

- 다이어그램: prod·dev·beta `observability.png`의 "3 표준 알람 평가" 단계 설명 — "서비스 6종 × 3종(CPU>80·MEM>85·Running<1) = 18개. 3분(60초×3). running 알람은 결측치를 위반으로 처리."
- `modules/observability/main.tf`의 `aws_cloudwatch_metric_alarm.ecs_cpu`(43~65행)·`ecs_memory`(67~89행) — `comparison_operator`, `evaluation_periods=3`, `period=60`, `treat_missing_data` 옵션 미지정(default `missing`)
- `modules/observability/main.tf`의 `aws_cloudwatch_metric_alarm.ecs_running_count`(91~117행) — `treat_missing_data = "breaching"`(106행), 바로 위 주석(104~105행): "태스크가 0이 되면 메트릭 송신이 끊겨 INSUFFICIENT_DATA로 빠진다. '서비스 다운'을 놓치지 않도록 결측치를 위반(ALARM)으로 처리."
- `modules/observability/variables.tf`(32~42행) — `service_alarms` 변수의 `cpu_threshold`(default 80)·`memory_threshold`(default 85)·`min_running_count`(default 1) 구조, 서비스 6개 × 3종 알람 = 18개라는 산식의 근거

## 타깃 독자 & 난이도

- CloudWatch 알람을 이미 여러 개 만들어 봤지만 `treat_missing_data`를 기본값 그대로 두고 있는 백엔드·인프라 엔지니어
- ★★★☆☆ (사전지식: CloudWatch 알람 평가 주기·상태 전이 기본 개념)

## 예상 분량

- 짧음 (~2,500자)

## 글 아웃라인

1. **들어가며 — 알람 18개 중 딱 하나만 다른 문장**
   - 다이어그램의 "running 알람은 결측치를 위반으로 처리"라는 짧은 예외 표기에서 출발
2. **`treat_missing_data`의 선택지들**
   - `missing`(기본, 판단 보류)·`notBreaching`·`breaching`·`ignore`가 각각 알람 상태를 어떻게 바꾸는지
3. **태스크 0개 = 메트릭도 0개, 이 조합이 만드는 사각지대**
   - ECS 서비스가 완전히 죽으면 `RunningTaskCount` 메트릭 자체가 발행되지 않는다는 사실과, 기본 옵션(`missing`)에서는 이 상태가 "판단 보류(INSUFFICIENT_DATA)"로 남아 알람이 울리지 않는다는 문제
4. **CPU·메모리는 왜 그대로 뒀는가**
   - 이 두 지표는 태스크가 살아있는 한 계속 발행되므로 결측치 자체가 드물다는 것, 즉 지표의 발행 조건에 따라 정책을 다르게 가져가야 한다는 원칙
5. **결론 — 알람 하나하나에 "이 지표가 결측되면 무엇을 뜻하는가"를 묻는 습관**
   - `treat_missing_data`를 기본값 그대로 쓰는 것이 항상 안전하지 않다는 정리

## 참고할 1차 출처

- CloudWatch 알람의 결측치 처리 구성: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/alarms-and-missing-data.html
- Amazon ECS Container Insights 메트릭: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html

## 시리즈 인용 관계

이 단편은 시리즈의 출발점이다. 같은 알람 파이프라인이 알람을 울린 "다음" 단계 — SNS까지는 도달하지만 구독자가 없는 상태 — 는 **[02 — 알람은 있는데 받는 사람이 없다](./02-sns-zero-subscribers.md)**에서 다룬다.

## 작성 메모

- "이 알람 설계가 완벽하다"는 식으로 과장하지 않는다. `treat_missing_data`를 서비스별로 다르게 가져갈 수도 있었다는 대안(예: 배치 성격의 워크로드라면 다른 정책이 맞을 수 있다)도 짧게 언급해, 이 선택이 이 시스템(상시 실행되는 ECS 서비스)의 특성에 맞는 선택이라는 맥락을 남긴다.
- 코드 주석을 그대로 인용하는 데 그치지 않고, "왜 이 주석이 맞는 설명인가"를 CloudWatch 알람 평가 메커니즘으로 다시 풀어 설명한다.
