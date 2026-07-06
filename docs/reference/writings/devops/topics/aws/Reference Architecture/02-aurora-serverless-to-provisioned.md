# 02. Aurora MySQL의 계단형 진화 — Serverless v2에서 Provisioned로

> 1차 소스: [`devops/aws/{dev,beta,prod}/reference-architecture.png`](../../../../../../../devops/aws/prod/reference-architecture.png) · [`architecture-facts.md` §2 Aurora MySQL](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> dev와 beta 다이어그램의 Aurora 아이콘 아래엔 똑같이 "Serverless v2"라고 적혀 있다. 용량 범위만 0.5–2.0 ACU에서 0.5–4.0 ACU로 두 배 늘었을 뿐이다. 그런데 prod로 넘어가면 라벨이 통째로 바뀐다 — "Provisioned · 3x db.r7g.large · writer + 2 readers". 자동으로 늘고 줄던 서버리스 인스턴스가, 왜 prod에서는 고정된 3대의 인스턴스로 바뀌는가.

## 핵심 질문

- Aurora Serverless v2와 Provisioned는 운영 모델 관점에서 근본적으로 무엇이 다른가?
- 어떤 조건에서 서버리스가 프로비저닝드보다 불리해지는가 — prod가 갈아탄 이유는 무엇인가?
- Terraform 코드 한 세트가 `engine_mode` 값 하나로 완전히 다른 두 아키텍처(서버리스 스케일링 vs 고정 3대 인스턴스)를 만들어내는 방식은 무엇인가?

## 다루는 관점

- ✅ 설계 근거(Why) — 환경별 트래픽 예측 가능성과 운영 부담의 회계
- ✅ 구현(Terraform 코드) — `engine_mode`/`instance_count`/`instance_class` 변수 전환과 리소스 분기
- ✅ 운영 — 백업 보존 기간·삭제 보호·Performance Insights 활성 여부의 환경별 차이

## 근거

- 다이어그램 사실: dev·beta `reference-architecture.png` Aurora 노드 라벨 "Serverless v2 (0.5–2.0 ACU)" / "Serverless v2 (0.5–4.0 ACU)" · prod 라벨 "Provisioned 3x db.r7g.large · writer + 2 readers · :3306"
- `architecture-facts.md` §2 Aurora MySQL 공통 사실(67~71행) — engine `aurora-mysql`, `engine_mode="provisioned"`(serverlessv2도 provisioned 타입 + scaling block 사용), Managed Master User Password, `storage_encrypted=true`, Multi-AZ 명시 플래그 없음, Performance Insights는 prod만 활성, `monitoring_interval` prod=60/그 외 0
- `architecture-facts.md` §2 환경별 비교표(73~84행) — engine_mode, serverless ACU 범위, instance_count/instance_class, storage_type, backup_retention, deletion_protection, skip_final_snapshot, performance_insights의 dev/beta/prod 값과 file:line 출처

## 타깃 독자 & 난이도

- RDS·Aurora를 서버리스로 시작해 언젠가 프로비저닝드로 옮길지 고민하는 백엔드·DBA 엔지니어
- ★★★☆☆ (사전지식: RDBMS 복제 구조, Aurora 기본 개념)

## 예상 분량

- 보통 (~3,500자)

## 글 아웃라인

1. **들어가며 — 라벨 두 줄이 통째로 바뀌는 지점**
   - dev·beta는 "Serverless v2" 한 줄로 같고, prod만 완전히 다른 문장으로 바뀐다는 관찰
2. **Serverless v2가 dev·beta에 맞는 이유**
   - ACU 자동 조정, 관리 부담 최소화, 낮고 예측 어려운 트래픽에서의 비용 이점
3. **prod가 Provisioned로 갈아타는 세 가지 근거**
   - Performance Insights 활성화(prod 전용), 고정 3대(writer+2reader)로 읽기 부하 분산, backup 30일·삭제 보호까지 포함한 신뢰성 강화
4. **코드로 보는 전환 스위치**
   - `engine_mode`/`instance_count`/`instance_class` 변수가 어떻게 서로 다른 리소스 블록(serverless scaling config vs 고정 인스턴스 3개)을 만들어내는지
5. **결론 — "설정값 하나"가 아니라 재구축을 수반하는 전환**
   - Serverless v2에서 Provisioned로의 전환이 클러스터 형태 자체를 바꾸는 작업이라는 실무적 경고

## 참고할 1차 출처

- Amazon Aurora Serverless v2 사용 설명서: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.html
- Aurora Capacity Unit(ACU) 동작 방식: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.how-it-works.html
- Amazon Aurora 읽기 복제본: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Replication.html
- Amazon RDS Performance Insights: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PerfInsights.html

## 시리즈 인용 관계

이 단편은 **[01 — CQRS 쓰기·읽기 분리와 MongoDB Atlas의 IaC 경계](./01-cqrs-mongodb-atlas-boundary.md)**가 예고한 "Aurora가 정확히 어떻게 구성되는가"를 심화한다. 01이 다룬 CQRS 쓰기/읽기 경계 자체는 반복하지 않고, Aurora 내부의 환경별 스펙 전환에만 집중한다. 이 단편이 짚은 "고정 3대 인스턴스"라는 사실은 **[03 — 서비스별 데이터 저장소 접근 매트릭스](./03-service-datastore-access-matrix.md)**에서 "이 인스턴스에 실제로 접근하는 서비스가 몇 개인가"라는 질문으로 이어진다.

## 작성 메모

- "서버리스가 항상 더 간단하고 좋다"는 식으로 단순화하지 않는다. prod가 Provisioned로 "퇴보"하는 것처럼 보이지 않도록, 고정 스펙이 주는 예측 가능한 성능과 Performance Insights 가시성이라는 명확한 트레이드오프를 짚는다.
- 최대 부하(예: MSK 6 MB/s 같은 구체 수치) 기준의 Serverless vs Provisioned 요금 비교는 공식 문서로 직접 산정하지 않는 한 단정하지 말고 "확인 필요"로 남긴다.
- Multi-AZ가 명시 플래그로 보장되지 않는다는 사실(§2 공통 사실)을 "3 AZ subnet group + prod instance_count 3"이 실제로 서로 다른 AZ 배치를 보장하는 것과 혼동해서 쓰지 않는다(`architecture-facts.md` §9 확인 불가 항목 참고).
