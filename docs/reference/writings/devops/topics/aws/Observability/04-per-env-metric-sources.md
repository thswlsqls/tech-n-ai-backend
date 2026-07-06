# 04. 세 환경, 같은 알람 개수, 다른 관측 대상 — dev에는 없는 "메트릭 소스" 상자

> 1차 소스: [`devops/aws/{dev,beta,prod}/observability.png`](../../../../../../../devops/aws/prod/observability.png) · [`architecture-facts.md` §2 Aurora·MSK](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> prod와 beta 다이어그램에는 왼쪽 위에 "메트릭 소스"라는 보라색 상자가 있다. prod는 Aurora(Performance Insights 켜짐)와 MSK Provisioned, beta는 MSK Serverless의 브로커 메트릭이 그 안에 있다. 그런데 dev 다이어그램에는 이 상자 자체가 없다 — "Compute(ECS 서비스 6개)" 상자 하나만 남는다. 알람 18개, 표준 대시보드, SNS 파이프라인은 세 환경이 완전히 동일한데, "무엇을 관측하고 있는가"의 범위만 환경마다 다르다.

## 핵심 질문

- 다이어그램이 "메트릭 소스"(인프라 메트릭)와 "Compute"(애플리케이션 메트릭)를 왜 별도 상자로 분리해서 그리는가?
- dev 다이어그램에 메트릭 소스 상자가 아예 없는 것은, 관측 스택의 설계가 부족해서인가 아니면 애초에 관측할 인프라 자체가 없어서인가?
- 관측 스택 자체(알람 18개, 로그, 대시보드)가 세 환경에서 완전히 동일하게 유지되는 것은, 이 스택이 "어떤 데이터가 있든 상관없이 재사용 가능하게" 설계됐다는 것을 어떻게 보여주는가?

## 다루는 관점

- ✅ 구현 — CloudWatch 네임스페이스 구분(`AWS/ECS`·`ECS/ContainerInsights` vs `AWS/RDS`·MSK Open Monitoring)과 다이어그램이 이를 상자로 시각화하는 방식
- ✅ 설계 근거(Why) — 관측 스택(수집·평가·통지 파이프라인)과 관측 대상(무엇을 수집하는가)을 분리해서 설계하는 이유

## 근거

- 다이어그램: prod `observability.png` 부제 — "ECS 서비스 메트릭·로그를 CloudWatch로 모아 표준 알람 18개를 평가하고 SNS로 통지한다. prod는 Aurora Performance Insights·MSK(Provisioned) 메트릭이 추가된다(관측 스택 구성은 dev·beta와 동일)." beta 부제 — "beta는 MSK(Serverless) 메트릭이 추가된다(관측 스택 구성은 dev·prod와 동일)." dev는 "메트릭 소스" 상자 없이 "Compute" 상자만 존재, 부제에 인프라 메트릭 언급 없음
- `architecture-facts.md` §2 Aurora MySQL(71행) — "Performance Insights: prod만 활성"
- `architecture-facts.md` §2 MSK(99~101행) — dev는 `enable_msk` default false → MSK 미생성 / beta는 MSK Serverless / prod는 MSK Provisioned(Open Monitoring — Prometheus JMX/Node Exporter 활성)
- `modules/observability/main.tf`(43~117행) — `ecs_cpu`·`ecs_memory`·`ecs_running_count` 세 알람 모두 네임스페이스가 `AWS/ECS` 또는 `ECS/ContainerInsights`로 고정돼 있어, Aurora·MSK 메트릭과 무관하게 이 모듈 자체는 항상 같은 18개 알람만 만든다는 사실

## 타깃 독자 & 난이도

- 여러 환경에 같은 관측 모듈을 재사용하면서 "이 환경에는 무엇을 더 봐야 하는가"를 구분해서 설계하려는 인프라·SRE 엔지니어
- ★★★☆☆ (사전지식: CloudWatch 네임스페이스 개념, MSK Open Monitoring·RDS Performance Insights 기본 개념)

## 예상 분량

- 보통 (~3,000자)

## 글 아웃라인

1. **들어가며 — 상자 하나가 통째로 사라진 다이어그램**
   - dev만 "메트릭 소스" 상자가 없다는 시각적 관찰에서 출발
2. **"관측 스택"과 "관측 대상"을 나눠서 읽기**
   - 알람·로그·대시보드·SNS(관측 스택)는 18개 알람이라는 고정된 산식으로 항상 동일하게 동작하고, Aurora·MSK 메트릭(관측 대상)만 환경마다 다르다는 구조
3. **dev에 메트릭 소스가 없는 이유 — 이전에 확인한 사실과의 연결**
   - Aurora가 dev에서도 Serverless v2로 존재하기는 하지만 Performance Insights가 prod에서만 켜진다는 사실, MSK는 dev에 아예 존재하지 않는다는 사실을 다시 확인(단, 이 근거를 처음 설명하는 자리가 아니라 짧게 인용)
4. **알람 18개라는 숫자가 세 환경에서 흔들리지 않는 이유**
   - `modules/observability/main.tf`가 Aurora·MSK를 전혀 참조하지 않고 ECS 서비스 6개에만 기대어 알람을 만든다는 사실 — 관측 모듈이 데이터 계층의 존재 여부와 완전히 독립적으로 설계됐다는 것
5. **결론 — 재사용 가능한 관측 스택을 설계하는 법**
   - "이 서비스가 있으면 이 알람"이라는 고정 관계를 만들어 두면, 환경마다 리소스 구성이 달라져도 관측 모듈 자체는 손대지 않아도 된다는 정리

## 참고할 1차 출처

- Amazon RDS Performance Insights: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PerfInsights.html
- Amazon MSK Open Monitoring(Prometheus): https://docs.aws.amazon.com/msk/latest/developerguide/open-monitoring.html
- Amazon CloudWatch Container Insights: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html

## 시리즈 인용 관계

**[03 — 설계된 관측성과 켜진 관측성 사이](./03-spec-vs-deployed-observability.md)**와 함께 "관측 스택의 범위(scope)"라는 주제를 이룬다. 03이 "3 Pillars 중 무엇이 켜져 있는가"를 다뤘다면, 이 단편은 "같은 스택이 환경마다 무엇을 보고 있는가"를 다룬다. `../Reference Architecture/02-aurora-serverless-to-provisioned.md`와 `../Security Architecture/03-orphaned-kafka-permission-in-dev.md`가 이미 다룬 Aurora·MSK의 환경별 차이 자체는 반복하지 않고, 그 차이가 관측 다이어그램에 "상자의 유무"로 나타난다는 사실만 새로 더한다.

## 작성 메모

- Aurora·MSK가 왜 환경마다 다른지(계단형 진화, 단계적 도입 근거)는 이 단편에서 다시 설명하지 않는다. `../Reference Architecture/`와 `../Security Architecture/`가 이미 상세히 다뤘으므로, 링크로 인용하고 넘어간다.
- "dev는 관측이 부실하다"는 인상으로 흐르지 않는다. dev에 없는 것은 관측 대상(인프라 자체)이지 관측 능력이 아니라는 점을 분명히 한다 — ECS 서비스에 대한 알람 18개는 dev도 beta·prod와 동일하게 완전히 가동 중이다.
