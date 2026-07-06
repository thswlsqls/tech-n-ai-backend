# 기술 블로그 주제 인덱스 — observability 다이어그램으로 읽는 설계와 실제 가동 상태

> 1차 소스: [`devops/aws/{dev,beta,prod}/observability.drawio`·`.png`](../../../../../../../devops/aws/README.md)
> 보조 컨텍스트: [`architecture-facts.md`](../../../../../../../devops/aws/architecture-facts.md) · [`devops/results/08-observability.md`](../../../../../../../devops/results/08-observability.md)(3 Pillars 설계 명세) · [`modules/observability/`](../../../../../../../devops/terraform/modules/observability/README.md)(실 구현 코드) · [`modules/observability/configs/README.md`](../../../../../../../devops/terraform/modules/observability/configs/README.md)(ADOT·FireLens 배포 상태)
>
> 본 문서는 `devops/aws/dev·beta·prod/observability.png`(와 대응 `.drawio`)를 1차 소스로 도출한 블로그 주제 후보 모음이다. `../Reference Architecture/`가 발견 당시 이 다이어그램의 존재를 이유로 "Observability 사이드카(ADOT/FireLens)" 주제를 폐기하며 남겨둔 바로 그 소재다. 이 다이어그램의 핵심은 **08-observability.md가 그린 정교한 설계와, 실제로 배포된 스택 사이의 거리**다 — 다이어그램 자체가 회색 점선으로 "설계됨·미가동" 요소를 정직하게 표시한다.

## 단편과 시리즈의 관계 — 두 주제 쌍(알람 파이프라인 01↔02, 관측 범위 03↔04)

별도 메타 글(`series-*.md`)은 두지 않는다. 네 단편은 다이어그램이 보여주는 두 개의 질문 — "가동 중인 알람 파이프라인은 정확히 어떻게 동작하는가"와 "이 관측 스택은 무엇을 보고, 무엇을 아직 못 보는가" — 을 각각 두 편씩으로 나눠 다룬다.

```
[알람 파이프라인]  01 RunningTaskCount의 결측치 정책 ──→ 02 SNS 구독 0건
[관측 범위]        03 설계 vs 실제 가동 상태          ↔  04 환경별 관측 대상 차이
```

- 01→02는 **누적** 구조다. 알람이 평가되는 단계(01)에서 그 알람이 통지되는 단계(02)로 이어진다.
- 03↔04는 짝을 이루는 독립 단편이다. 03은 "3 Pillars 중 무엇이 켜져 있는가"(수직 범위), 04는 "같은 스택이 환경마다 무엇을 보는가"(수평 범위)를 다룬다.

### 단편 사이 인용 관계

| 단편 | 앞 단편 전제 | 뒤 단편으로 넘기는 질문 |
|---|---|---|
| 01 RunningTaskCount 결측치 정책 | — (출발점) | "알람이 울린 다음엔 어디로 가나" → 02 |
| 02 SNS 구독 0건 | 01의 알람 평가 파이프라인 | — (알람 파이프라인 마무리) |
| 03 설계 vs 실제 가동 상태 | — (독립) | "그럼 켜져 있는 스택은 환경마다 뭘 보나" → 04 |
| 04 환경별 관측 대상 차이 | 03의 "가동 중인 CloudWatch 스택" | — (관측 범위 마무리) |

## 1. 단편 글 후보

| # | 제목 | Why | 구현 | 운영 | 근거 | 분량 |
|---|---|:-:|:-:|:-:|---|---|
| [01](./01-running-task-count-missing-data.md) | RunningTaskCount 알람만 결측치를 장애로 본다 — treat_missing_data 설계 하나 | — | ✅ | ✅ | 다이어그램 + `modules/observability/main.tf` | 짧음 |
| [02](./02-sns-zero-subscribers.md) | 알람은 있는데 받는 사람이 없다 — SNS 구독 0건이 남기는 신뢰성 공백 | — | ✅ | ✅ | 다이어그램 + `envs/prod/sns.tf`·`variables.tf` | 보통 |
| [03](./03-spec-vs-deployed-observability.md) | 설계된 관측성과 켜진 관측성 사이 — ADOT·FireLens·X-Ray가 모두 꺼져 있는 이유 | ✅ | ✅ | — | 다이어그램 + `08-observability.md` + `configs/README.md` + `architecture-facts.md` §1 | 김 |
| [04](./04-per-env-metric-sources.md) | 세 환경, 같은 알람 개수, 다른 관측 대상 — dev에는 없는 "메트릭 소스" 상자 | ✅ | ✅ | — | 다이어그램 + `architecture-facts.md` §2 + `modules/observability/main.tf` | 보통 |

## 2. 폐기·병합 로그(투명성)

- 🔁 **"Aurora Performance Insights가 prod에서만 켜지는 이유"·"MSK가 dev에 없는 이유"** — `../Reference Architecture/02-aurora-serverless-to-provisioned.md`와 `../Security Architecture/03-orphaned-kafka-permission-in-dev.md`가 각각 이미 근거를 도출했다. 04는 이 사실을 반복 도출하지 않고 "관측 다이어그램에 상자의 유무로 나타난다"는 사실만 인용한다.
- 🔁 **"PII 마스킹 파이프라인의 정규식 패턴 상세(이메일·JWT·카드번호·주민번호)"** — `08-observability.md` §2.6과 `masking.lua`에 근거가 있지만, 사이드카 자체가 꺼져 있어 실제로 동작하지 않는 코드를 상세히 해설하면 "가동 중인 기능"처럼 오해될 위험이 있다. 03의 "설계 vs 실제" 서사 안에서 배경 사실로만 짧게 인용하고, 독립 글로는 세우지 않는다.
- 🔁 **"SLO Burn Rate 알람·Deploy Freeze 자동화·PagerDuty 온콜"** — `08-observability.md` §5.5~5.7에 정교하게 설계돼 있지만, 부록 A 구현 체크리스트 기준 전부 ⬜(미착수)다. 실제 다이어그램에는 전혀 나타나지 않는 항목이라(현재 배포된 것은 CloudWatch Alarm→SNS→Email뿐), 다이어그램을 1차 소스로 삼는 본 시리즈 범위를 벗어난다고 판단해 폐기. 별도 "관측성 로드맵" 카테고리 후보로 남긴다.
- 🔁 **"알람 카탈로그 42개 vs 실제 알람 18개의 격차"** — `08-observability.md` §5.2는 서비스별로 가용성·지연·포화·트래픽 4개 신호를 각각 설계했지만(사실상 서비스당 여러 알람), 실제 구현은 서비스당 CPU·메모리·RunningCount 3종뿐이다. 03(설계 vs 실제)과 소재가 겹쳐 별도 단편으로 쪼개면 중복이 커 03의 한 단락으로 흡수하고 독립 단편에서는 제외.

## 3. 작성 가이드

- **인용 정책**: 기술적 사실의 근거는 `devops/terraform/modules/observability/`·`envs/prod/`의 파일:라인 출처, `architecture-facts.md`, `devops/results/08-observability.md`, 또는 `devops/aws/*/observability.png` 다이어그램 라벨, 그리고 aws-docs MCP로 확인한 공식 AWS 문서만 사용한다. 블로그·AI 생성 콘텐츠 인용 금지(`tech-n-ai-backend/CLAUDE.md` 외부 자료 참조 원칙).
- **본문 언어**: 한국어. 고유명사·기술 용어는 영문 유지(CloudWatch, SNS, ADOT, FireLens, X-Ray, Container Insights 등).
- **설계 문서와 실제 코드를 구분해서 인용**: `08-observability.md`는 설계 명세(spec)이고 `modules/observability/`·`envs/*/observability.tf`가 실제 구현이다. 이 둘을 섞어서 "이미 이렇게 동작한다"처럼 쓰지 않고, 어느 쪽 근거인지 항상 구분해서 표기한다.
- **미가동 기능을 다룰 때**: 코드가 존재해도 토글이 꺼져 있으면(`enable_otel_sidecar=false` 등) "설계됨" 또는 "준비됨"으로 표기하고 "가동 중"으로 쓰지 않는다.
- **다이어그램 인용**: 각 단편 도입부에 해당 환경의 `observability.png` 캡처 또는 라벨 인용 박스를 둔다.
- **단편 작성 시**: 글 마지막의 "시리즈 인용 관계" 섹션을 유지해 두 트랙(알람 파이프라인/관측 범위) 안에서 앞뒤 단편이 어떤 질문을 주고받는지 신호를 남긴다.
- **분량·SEO**: 완성 글은 `write-tech-blog`에서 7,000자 이상·SEO 제목 후보 3개+·번호 없는 소제목으로 다듬는다. 설계도의 아웃라인 번호는 기획용이다.

## 공식 출처 (단편 공통 보강)

- CloudWatch 알람의 결측치 처리 구성: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/alarms-and-missing-data.html
- Amazon ECS Container Insights 메트릭: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html
- Amazon SNS 이메일 알림: https://docs.aws.amazon.com/sns/latest/dg/sns-email-notifications.html
- Amazon SNS 구독 생성: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Notify_Users_Alarm_Changes.html
- AWS Distro for OpenTelemetry(ADOT): https://aws-otel.github.io/docs/
- AWS X-Ray Developer Guide: https://docs.aws.amazon.com/xray/latest/devguide/
- OpenTelemetry Documentation: https://opentelemetry.io/docs/
- Amazon RDS Performance Insights: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PerfInsights.html
- Amazon MSK Open Monitoring(Prometheus): https://docs.aws.amazon.com/msk/latest/developerguide/open-monitoring.html

> 위 공식 출처 외의 블로그/AI 생성 문서는 본 시리즈의 근거로 인용하지 않는다.
