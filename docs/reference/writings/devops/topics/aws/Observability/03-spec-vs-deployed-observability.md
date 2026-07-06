# 03. 설계된 관측성과 켜진 관측성 사이 — ADOT·FireLens·X-Ray가 모두 꺼져 있는 이유

> 1차 소스: [`devops/aws/{dev,beta,prod}/observability.png`](../../../../../../../devops/aws/prod/observability.png) · [`devops/results/08-observability.md`](../../../../../../../devops/results/08-observability.md) · [`modules/observability/configs/README.md`](../../../../../../../devops/terraform/modules/observability/configs/README.md)

## 한줄 요약(Hook)

> `08-observability.md`는 로그 7년 Object Lock 보존, PII 마스킹 Lambda, SLO Burn Rate 알람, PagerDuty 온콜, Deploy Freeze 자동화까지 그리는 정교한 관측성 설계 문서다. 그런데 실제 배포된 다이어그램을 열어 보면 회색 점선 상자 세 개가 "설계됨·미가동"이라고 적혀 있다 — ADOT Collector, FireLens PII 마스킹, X-Ray 트레이스 백엔드. 화려한 설계 문서와, 실제로 켜져 있는 스위치 사이의 거리를 다이어그램이 정직하게 그려 넣었다.

## 핵심 질문

- ADOT Collector·FireLens PII 마스킹·X-Ray 분산 추적이 "설계됨·미가동" 상태라는 것은 코드에서 정확히 무엇을 의미하는가 — 리소스 자체가 없는가, 변수만 꺼져 있는가?
- 3 Pillars(로그·메트릭·트레이스) 중 지금 실제로 켜져 있는 것은 무엇이고, 무엇이 빠져 있는가?
- 사이드카를 아직 켜지 않은 이유를 코드·문서에서 확인할 수 있는 근거(비용, 모듈 미지원)는 무엇인가?

## 다루는 관점

- ✅ 설계 근거(Why) — 3 Pillars를 한 번에 다 켜지 않고 단계적으로 도입하는 전략
- ✅ 구현 — `enable_otel_sidecar`·`enable_firelens_sidecar` 토글과 설정 파일(ADOT YAML, FireLens conf, masking.lua)의 현재 배포 상태

## 근거

- 다이어그램: dev·beta·prod `observability.png`의 "설계됨·미가동(enable_otel_sidecar=false, enable_firelens_sidecar=false)" 점선 상자 — "로그 마스킹(미가동)"(FireLens, masking.lua 미적용), "분산추적(미가동)"(ADOT Collector, OTLP 4317/4318), "트레이스 백엔드(미가동)"(X-Ray, 트레이스 미수집), 하단 범례: "회색 점선 = 설계됨·미가동: ADOT(분산추적→X-Ray)·FireLens(PII 마스킹) 사이드카가 전 환경 off. 트레이스 미수집, 로그는 마스킹 없이 적재."
- `architecture-facts.md` §1 Sidecar(ADOT/FireLens)(56~60행) — "둘 다 옵션이며 default false(모든 env에서 tfvars가 켜지 않음 → 비활성)", ADOT 활성 시 사양(image, cpu 64/memReservation 128, `OTEL_EXPORTER_OTLP_ENDPOINT` 자동 주입), FireLens 활성 시 메인 logDriver가 `awsfirelens`로 전환된다는 사실, 미사용 시 메인 로그는 `awslogs` 드라이버로 CloudWatch 직행
- `modules/observability/configs/README.md`(5~10행) — "현재 상태 — 미가동: dev·beta·prod 모두 ADOT/FireLens 사이드카가 꺼져 있다... 그래서 분산 추적이 수집되지 않고, `masking.lua`의 PII 마스킹도 적용되지 않는다 — 앱 로그가 awslogs 드라이버로 CloudWatch에 마스킹 없이 그대로 적재된다."
- `modules/observability/configs/README.md`(21~23행, 115~122행) — "본 ecs-service 모듈은 sidecar 자동 추가가 아직 미지원(세션 5 보강 예정)"이라는 구현 제약, ADOT+FireLens 사이드카를 켰을 때 예상 추가 비용(dev 기준 약 $14/월)
- `08-observability.md` §1.2(87~126행) — 옵션 A(CloudWatch+X-Ray 네이티브) vs 옵션 B(AMP+AMG+OTel) 비교와 "하이브리드: 옵션 A(기본) + 옵션 B(트레이싱/대시보드 일부)" 선정안, 부록 A 구현 체크리스트(⬜/🟡/✅ 상태 표)

## 타깃 독자 & 난이도

- 관측성 설계 문서를 이미 작성했지만 "이 중 실제로 무엇이 켜져 있는지"를 다이어그램·코드로 다시 확인해야 하는 팀의 백엔드·인프라 엔지니어, SRE
- ★★★☆☆ (사전지식: OpenTelemetry·사이드카 패턴 기본 개념, ECS Task Definition 구조)

## 예상 분량

- 김 (~4,500자)

## 글 아웃라인

1. **들어가며 — 회색 점선 상자 세 개**
   - 설계 문서의 야심찬 그림과, 다이어그램에 명시적으로 표시된 "미가동" 상태 사이의 대비에서 출발
2. **지금 실제로 가동 중인 것 — CloudWatch 메트릭·로그·알람**
   - ECS 서비스가 CPU·메모리·태스크 수를 CloudWatch로 발행하고, 컨테이너 stdout이 `awslogs` 드라이버로 그대로 적재되는 현재 경로
3. **꺼져 있는 세 조각 — ADOT, FireLens 마스킹, X-Ray**
   - `enable_otel_sidecar`/`enable_firelens_sidecar` 토글이 왜 아직 false인지(모듈의 sidecar 자동 추가 미지원, 추가 비용)와, 그 결과 로그가 PII 마스킹 없이 적재된다는 구체적 결과
4. **왜 처음부터 다 켜지 않았는가 — 단계적 도입이라는 선택**
   - `08-observability.md`가 제시한 옵션 A/B 하이브리드 전략과, "OTel을 1급 인터페이스로 앞단에 둬 언제든 전환 가능한 구조"라는 설계 의도가 지금의 "일부만 가동" 상태와 어떻게 연결되는지
5. **결론 — 설계 문서와 배포 상태를 같은 자리에 남겨두는 것의 가치**
   - 다이어그램이 "미가동"이라고 정직하게 표시하는 것 자체가, 다음에 이 시스템을 맡을 사람에게 남기는 가장 유용한 정보라는 정리

## 참고할 1차 출처

- AWS Distro for OpenTelemetry(ADOT): https://aws-otel.github.io/docs/
- AWS X-Ray Developer Guide: https://docs.aws.amazon.com/xray/latest/devguide/
- Amazon CloudWatch Container Insights: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html
- OpenTelemetry Documentation: https://opentelemetry.io/docs/

## 시리즈 인용 관계

이 단편은 시리즈 외 독립 자산이다. 01·02가 다루는 "가동 중인 CloudWatch 알람 파이프라인"의 세부 설계와 달리, 이 단편은 전체 관측성 스택에서 "무엇이 켜져 있고 무엇이 꺼져 있는가"라는 더 넓은 시야를 다루므로 다른 단편을 전제하지 않는다. **[04 — 세 환경, 같은 알람 개수, 다른 관측 대상](./04-per-env-metric-sources.md)**과 함께 "관측 스택의 범위(scope)"라는 주제를 이룬다.

## 작성 메모

- "마스킹 없이 로그가 쌓인다"는 사실을 자극적으로 다루지 않는다. `08-observability.md`의 구조화 로그 스키마(§2.1)가 애초에 이메일·전화·평문 userId를 원천에서 제외하고 `user_id_hash`(SHA-256)만 남기도록 설계했다는 점을 함께 언급해, "마스킹 사이드카가 없다"가 "PII가 무방비로 쌓인다"와 동일하지 않을 수 있다는 균형을 유지한다. 다만 이 구조화 로그 스키마가 실제 애플리케이션 코드에 구현됐는지는 이 설계도 단계에서 확인하지 못했으므로, 완성 글 작성 전 재확인이 필요하다.
- "미완성 프로젝트"로 읽히지 않도록, 단계적 도입 자체가 08 문서가 명시한 전략이라는 근거를 아웃라인 4번에서 분명히 제시한다.
