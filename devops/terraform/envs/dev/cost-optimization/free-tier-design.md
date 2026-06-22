# envs/dev — RDS·Kafka·Redis 무료(또는 최소 비용) 설계서

이 문서는 dev 환경에서 데이터·메시징 계층(RDS·Kafka·Redis)을 무료 또는 최소 비용으로 돌리도록 바꾸는 설계안이다. 실제 `.tf` 변경은 담지 않고, 어떤 선택지가 있는지·무엇을 트레이드오프로 보고 골랐는지·어디를 고쳐야 하는지를 정리한다. 수치와 무료 한도는 모두 공식 출처로 확인했고, 확인 못 한 것은 끝에 따로 적었다.

> **전제: AWS 신규 계정(2025-07-15 이후 가입)을 새로 만든다.** 이 경우 옛 12개월 무료 한도는 적용되지 않고, 크레딧 기반 Free plan을 받는다. 가장 중요한 제약은 **계정이 가입 6개월 뒤 또는 크레딧 소진 시 자동으로 닫힌다**는 점이다(출처 S-AWS-FREE). 따라서 "AWS 안에서 무료"는 길어야 6개월이고, 그보다 오래 유지할 것은 계정 수명과 무관한 외부 무기한 무료 서비스에 두는 쪽이 안전하다. 이 전제 때문에 아래 권장은 **외부 무료 SaaS를 기본**으로 둔다.

## 목적과 범위

- 대상: `envs/dev`의 Aurora MySQL(`aurora-mysql`), Kafka(`msk-serverless`), Redis(`elasticache-valkey`) 세 계층.
- 목표: dev에서 이 셋을 무료(가능하면)·아니면 최소 비용으로 운영.
- 범위 밖이지만 반드시 짚어둘 것: NAT Gateway·VPC Interface Endpoint·ALB·ECS Fargate는 이 세 계층과 별개로 dev 비용의 대부분을 차지한다. 아래 "무료를 진짜로 만들려면" 절에서 따로 다룬다. 이걸 빼고 "RDS·Kafka·Redis만 무료"로 만들어도 전체 청구서는 무료가 아니다.

## 전제 — 신규 계정의 크레딧 Free plan

신규 계정은 크레딧 기반 Free plan을 받는다. 핵심 사실은 셋이다 (출처 S-AWS-FREE).

- 가입 시 $100, 주요 서비스 활성화로 최대 $100 추가 — 합쳐 **최대 $200 크레딧**.
- **가입 6개월 뒤 또는 크레딧 소진 시 계정이 자동으로 닫힌다.** 둘 중 먼저 오는 시점이다.
- Free plan에는 `db.t3.micro`·`db.t4g.micro`의 표준 RDS(MySQL 등)와 Aurora PostgreSQL Serverless 무료 제공이 포함되지만, 이 모두 위의 6개월 창 안에서만 의미가 있다.

따라서 이 전제에서 "무료"는 두 종류다. **AWS 내부 무료(= 6개월 한정, 계정과 함께 사라짐)**와 **외부 무기한 무료 SaaS(= 계정 수명과 무관)**. dev를 6개월 넘게 유지할 생각이면 데이터를 계정과 함께 날리지 않도록 외부 무료 SaaS에 두는 편이 낫다.

## 지금 dev가 만드는 것과 비용 (요약)

기존 `envs/dev/README.md`의 추정 비용표 기준, `enable_msk=false`라도 dev는 24/7 약 $175/월이다. 데이터 계층만 보면 Aurora Serverless v2 ~$43, ElastiCache `cache.t4g.micro` ~$16이고, 나머지(NAT·VPC Endpoint·KMS)가 큰 몫이다. 세 계층은 모두 AWS 프리티어가 없거나(현재 설정 기준) 무료 한도를 벗어나 있다.

## RDS (쓰기 측 MySQL)

**현재**: `aurora-mysql` 모듈은 `aws_rds_cluster` + `aws_rds_cluster_instance`만 쓰는 **Aurora 전용**이다(`modules/aurora-mysql/main.tf:69,131`). 단독 RDS(`aws_db_instance`)를 만들 수 없다.

**제약**: 신규 Free plan의 무료 DB는 `db.t3.micro`·`db.t4g.micro`의 **표준 RDS(MySQL 등)**이다. Aurora는 공식 페이지에 **Aurora PostgreSQL Serverless만** 무료로 명시돼 있고 Aurora MySQL은 없다 (출처 S-RDS-FREE). 즉 이 프로젝트의 Aurora MySQL은 신규 Free plan에서도 "무료"로 확정되지 않는다. 그리고 어느 AWS 내부 경로든 6개월 뒤 계정과 함께 사라진다.

**설계 결정**:

- **(R-A, 권장) AWS 내부 — 표준 RDS `db.t3.micro` MySQL (6개월 한정)**: `aws_db_instance` 기반의 작은 신규 모듈(`modules/rds-mysql` 같은)을 추가하고, dev에서 `enable_aurora=false`로 Aurora를 끈 뒤 이 모듈을 켠다. `db.t3.micro` Single-AZ, `storage 20GB gp2`, `multi_az=false`, `backup_retention=0~1`로 두면 신규 Free plan의 무료 DB 제공 대상과 맞는다 (출처 S-RDS-FREE). Aurora 클러스터 기능(리더 엔드포인트·Serverless 스케일링)을 dev에서 안 쓰므로 손실이 작다. 단 6개월 뒤 계정 종료 시 함께 사라지므로 스키마·데이터는 Flyway 마이그레이션과 별도 백업으로 재현 가능하게 둔다.
- **(R-B) Aurora 유지 + 크레딧 절약**: Aurora를 유지하되 `aurora_min_acu=0`으로 두어 미사용 시 자동 일시정지가 걸리게 한다(기존 README가 인용한 공식 동작). 무료가 아니라 크레딧을 아끼는 선택이다. baseline 과금을 없애는 대신 첫 요청에 재개 지연이 생긴다.
- **(R-C) 외부 무기한 무료 MySQL — 계정 수명 무관**: 6개월 넘게 같은 DB를 유지하고 싶으면 외부 무료 MySQL(예: Aiven 무료 플랜의 MySQL — 무기한·신용카드 불필요, 출처 S-AIVEN-FREE)을 쓰고 `enable_aurora=false`로 둔다. 용량·커넥션 한도와 인터넷 지연은 감수한다.

신규 계정 dev라면 (R-A)가 가장 단순하고 무료다. 단 6개월 한정이라는 점을 받아들이거나, 더 오래 갈 거면 (R-C)를 고른다. (R-B)는 Aurora 고유 동작을 dev에서 검증해야 할 때만.

## Kafka (CQRS 동기화 이벤트)

**현재**: `msk-serverless`/`msk-provisioned` 모듈이 IAM SASL(9098)/TLS를 강제한다(`modules/msk-serverless/main.tf`, `msk-provisioned/main.tf:173`). dev는 `enable_msk` 기본 false다(`envs/dev/variables.tf:52`). 백엔드 클라이언트는 `bootstrap-servers`만 설정하고 SASL 항목이 없다(`common/kafka/.../application-kafka.yml:3`). 로컬은 단일 브로커 KRaft + PLAINTEXT다(`docker-compose.yml`).

**제약**: MSK는 Serverless·Provisioned 어느 모드도 AWS 프리티어가 없다 (출처 S-MSK). 무료로 자주 거론되던 Upstash Kafka는 2025-03-11 서비스 종료됐다 (출처 S-UPSTASH-KAFKA).

**설계 결정**:

- **(K-A, 권장) 외부 무기한 무료 — Aiven 무료 플랜**: Aiven 무료 플랜에 Apache Kafka가 포함되며 무기한·신용카드 불필요다(계정 6개월 종료의 영향을 안 받는다). 한도는 수신·송신 각 250 kb/s, 보존 3일이다 (출처 S-AIVEN-FREE, S-AIVEN-TIER). 이 프로젝트의 동기화 이벤트는 양이 적고 보존이 짧아도 되는 성격이라 dev엔 맞는다. 단 미사용 시 사전 통지 후 종료될 수 있다.
- **(K-B) self-host — EC2 단일 브로커**: 로컬 docker-compose의 단일 브로커 KRaft 구성을 EC2 인스턴스 한 대에 그대로 올린다. VPC 내부 PLAINTEXT면 백엔드 설정 변경이 거의 없다. Kafka API 호환인 Redpanda는 JVM·ZooKeeper가 없어 작은 인스턴스에 더 맞는다 (출처 S-REDPANDA). 단 EC2 자체가 신규 계정에선 크레딧 차감·6개월 한정이다.

신규 계정 dev는 `enable_msk=false`를 유지하고 (K-A) Aiven 무료를 기본으로 둔다. 보존·처리량을 늘려야 하거나 인터넷 지연을 피하고 싶을 때만 (K-B) self-host를 본다.

**공통 구현 주의**: MSK 모듈은 IAM SASL을 쓰지만 백엔드엔 SASL 설정이 없다. 외부 SaaS(Aiven)는 보통 SASL_SSL+SCRAM이라 Spring Kafka에 `security.protocol`·`sasl.*`·트러스트스토어를 추가해야 한다. self-host PLAINTEXT(같은 VPC 내부 한정)면 현재 설정 그대로 붙는다.

## Redis (rate-limit·멱등성·세션·캐시)

**현재**: `elasticache-valkey` 모듈이 Valkey 복제그룹을 만든다(6379, auth_token). dev 기본 노드는 `cache.t4g.micro`다(`envs/dev/variables.tf:84`). 백엔드는 `REDIS_HOST/PORT/PASSWORD`와 `REDIS_SSL_ENABLED` 환경변수로 붙고(Lettuce), 사용처는 게이트웨이 rate limiter(요청마다), Kafka 멱등성, OAuth state, 챗봇 캐시, 슬랙 rate limiter다(`common/core/.../application-common-core.yml`, `api/gateway`, `common/kafka/.../IdempotencyService.java` 등).

**제약**: 신규 계정에서 ElastiCache에는 별도 무료 제공이 없고 크레딧에서 차감된다 (출처 S-ELASTICACHE — 2025-07-15 이후 가입은 크레딧 모델). `cache.t3.micro` 750시간 무료는 레거시 계정 전용이라 신규 계정엔 해당이 없다. 모듈 기본 노드도 `cache.t4g.micro`다. rate-limit·멱등성은 요청 핫패스라 외부 인터넷 Redis는 매 요청 왕복 지연이 더해진다.

**설계 결정**:

- **(C-A, 권장) 외부 무기한 무료 — Upstash Redis 또는 Redis Cloud**: 계정 수명과 무관하게 무기한 무료다(6개월 종료의 영향을 안 받는다). Upstash 무료는 256MB·월 50만 커맨드·월 10GB (출처 S-UPSTASH-REDIS), Redis Cloud 무료는 30MB forever (출처 S-REDIS-CLOUD). 백엔드의 `REDIS_SSL_ENABLED=true` + host/port/password 주입만으로 코드 변경 없이 붙는다. 멱등성·OAuth state·캐시엔 용량이 충분하다. 다만 게이트웨이 rate-limit는 요청마다 도는 핫패스라 서울 리전 지연을 실측해 보고, 너무 느리면 그 용도만 (C-B)로 옮긴다.
- **(C-B) AWS 내부 — ElastiCache (크레딧 차감, 6개월)**: VPC 내부라 핫패스 지연이 가장 작다. `enable_elasticache=true`, `cache_node_type="cache.t4g.micro"`(또는 더 작은 것), `replicas=0`, `snapshot_retention=0`으로 최소화하되, 무료가 아니라 크레딧에서 빠지고 6개월 뒤 사라진다는 점을 받아들인다.

신규 계정 dev라면 (C-A) 외부 무료로 하나로 합치는 게 무료이면서 오래간다. rate-limit 지연이 문제가 될 때만 그 부분을 (C-B) ElastiCache로 분리한다.

## 무료를 진짜로 만들려면 (범위 밖이지만 결정에 직결)

세 계층을 무료로 바꿔도 dev 전체는 무료가 아니다. 신규 계정에서 이들은 별도 무료 제공이 없어 $200 크레딧에서 빠지고, 6개월 뒤 계정과 함께 사라진다 (출처 S-VPC, S-FARGATE, S-ALB).

- NAT Gateway: 시간당 + 데이터 처리 과금, 별도 프리티어 없음 → 크레딧 차감.
- VPC Interface Endpoint 9개: AZ·시간당 과금 → `enable_vpc_endpoints=false`로 끄고 NAT 폴백을 쓰는 게 1차 절감(모듈 주석도 권장).
- ECS Fargate 6개 서비스: 별도 프리티어 없음 → 크레딧 차감.
- ALB: 신규 고객도 월 750시간 + 15 LCU 무료가 안내되며, $200 크레딧이 ELB에도 적용된다 (출처 S-ALB). 다만 6개월 창 안에서다.

이 비용이 $200 크레딧을 6개월 안에 빠르게 소진시키는 주범이다(크레딧이 먼저 떨어지면 계정도 그때 닫힌다). 크레딧을 아끼려면 `enable_vpc_endpoints=false`로 엔드포인트부터 끈다.

크레딧을 거의 안 쓰고 dev를 오래 끌고 가려면 **NAT 없는 단일 EC2(퍼블릭 서브넷)에 docker-compose로 백엔드를 올리는 구조**가 필요하다. 그 경우 (R-A)·(K-B)·self-host Redis를 한 EC2 안에서 같이 돌릴 수 있다. 다만 EC2도 신규 계정에선 크레딧·6개월 한정이고, 현재의 ECS/Fargate 기반 dev와는 다른 토폴로지라 별도 설계·구현이 필요하다. 이 토폴로지의 구현 설계는 별도 문서로 분리했다 → **[single-ec2-design.md](./single-ec2-design.md)** (별도 환경 `envs/dev-ec2`로 구축, 월 ~$34~61). 6개월을 넘겨 유지할 데이터·메시징은 결국 외부 무기한 무료 SaaS((R-C)·(K-A)·(C-A))로 빼는 게 핵심이다.

## 권장 조합 (신규 계정 전제)

**시나리오 1 — 외부 무기한 무료 SaaS (기본 권장)**
- RDS: (R-C) 외부 무료 MySQL(Aiven 무료 플랜) + `enable_aurora=false`
- Kafka: (K-A) Aiven 무료 Kafka + `enable_msk=false`
- Redis: (C-A) Upstash 또는 Redis Cloud 무료 + `enable_elasticache=false`
- 세 계층 모두 계정 6개월 종료와 무관하게 무기한 무료다. AWS 쪽 데이터·메시징 비용이 0이라 $200 크레딧을 컴퓨트(EC2/Fargate)·네트워크에만 쓴다. 대신 SaaS 한도(용량·보존)와 인터넷 지연을 감수하고, 게이트웨이 rate-limit 지연은 실측한다.

**시나리오 2 — AWS 내부 무료/크레딧 (6개월 단기 검증)**
- RDS: (R-A) 표준 RDS `db.t3.micro` MySQL(신규 Free plan 무료 제공) + `enable_aurora=false`
- Kafka: `enable_msk=false` 유지(MSK는 크레딧만 빠름), 필요 시 (K-B) self-host
- Redis: (C-B) ElastiCache 최소 노드(크레딧 차감)
- 같은 VPC 안이라 지연·인증이 단순하다. 단 6개월 뒤 계정과 함께 사라지므로, 6개월 안에 끝낼 검증·데모용으로만. 데이터는 Flyway·백업으로 재현 가능하게 둔다.

신규 계정이라면 **시나리오 1을 기본**으로 한다. 6개월 안에 끝나는 단기 검증이고 같은 VPC 저지연이 꼭 필요하면 시나리오 2를 섞는다.

## 적용 시 건드릴 곳 (구현 가이드)

이 설계서는 코드를 바꾸지 않는다. 적용할 때 손댈 지점만 적는다.

- 시나리오 1(기본): `terraform apply -var="enable_aurora=false" -var="enable_msk=false" -var="enable_elasticache=false"`로 AWS 데이터 계층을 모두 끄고, 외부 SaaS 연결 정보를 워크로드 환경변수로 주입한다.
  - Redis: `REDIS_HOST/PORT/PASSWORD`, `REDIS_SSL_ENABLED=true` (Upstash/Redis Cloud).
  - Kafka: `KAFKA_BOOTSTRAP_SERVERS`와 SASL_SSL 설정. Spring Kafka에 `security.protocol`/`sasl.*`/트러스트스토어 추가 필요.
  - MySQL(R-C): 외부 MySQL endpoint를 워크로드 DB 환경변수로 연결.
- 표준 RDS 경로(R-A, 시나리오 2): `modules/rds-mysql`(신규, `aws_db_instance` 기반)을 만들고 `envs/dev`에서 호출. `db.t3.micro`·`multi_az=false`·`storage 20GB gp2`로 두고 출력 endpoint를 워크로드 DB 환경변수로 연결.
- ElastiCache 최소화(C-B, 시나리오 2): `enable_elasticache=true` 유지, `cache_node_type`은 최소 노드, `replicas=0`·`snapshot_retention=0`. 무료가 아니라 크레딧 차감임을 전제.
- 크레딧 절약 공통: `enable_vpc_endpoints=false`로 인터페이스 엔드포인트 9개부터 끈다(NAT 폴백).

## 검증

- `terraform plan`이 의도한 자원만 추가/삭제하는지 확인(데이터 계층 토글 on/off 차이).
- 무료 한도 적용 여부는 적용 후 AWS Billing의 Free Tier 사용량과 콘솔의 Free Tier 자격 표시로 확인.
- 외부 SaaS 연결은 워크로드에서 실제 연결·인증·지연을 확인(특히 게이트웨이 rate-limit 핫패스).

## 확인 못 한 것 / 직접 확인 필요

- 신규 Free plan의 `db.t3.micro` MySQL이 "무료 제공"인지 "크레딧 차감"인지: 공식 페이지가 둘을 또렷이 구분하지 않는다. 가입 후 콘솔 Free Tier 자격·비용 화면으로 확인 필요.
- 신규 Free plan에서 Aurora MySQL이 무료로 잡히는지: 공식 페이지는 Aurora PostgreSQL Serverless만 명시. 콘솔 Free Tier 자격으로 확인 필요.
- 계정 자동 종료(6개월) 시 데이터·리소스 처리 방식과 사전 통지: 가입 약관·콘솔로 확인. 종료 전 백업·이관 계획 필요.
- NAT·MSK·ElastiCache·Fargate 서울(ap-northeast-2) 단가: 공식 예시가 US 리전 기준이라 서울은 다름. Pricing Calculator로 재산정.
- Upstash Redis "forever" 명시·TLS 무료 포함·최대 커넥션, Aiven MySQL/Kafka 구체 한도: 서비스별 문서·콘솔로 확인.
- 외부 SaaS(Aiven·Upstash·Redis Cloud)의 서울 리전 지연: 실측 필요(특히 게이트웨이 rate-limit).

## 공식 출처

- **S-AWS-FREE** — AWS Free Tier, <https://aws.amazon.com/free/> : 신규 크레딧 모델($100~200, 6개월), 레거시 분기.
- **S-RDS-FREE** — Amazon RDS Free Tier, <https://aws.amazon.com/rds/free/> : 신규 Free plan은 `db.t3.micro`·`db.t4g.micro`의 표준 RDS(MySQL 등) 무료, Aurora는 PostgreSQL Serverless만 무료 명시.
- **S-MSK** — Amazon MSK Pricing, <https://aws.amazon.com/msk/pricing/> : 프리티어 없음.
- **S-UPSTASH-KAFKA** — Upstash Blog, <https://upstash.com/blog/workflow-kafka> : Kafka 2025-03-11 종료.
- **S-AIVEN-FREE** — Aiven Free Plan, <https://aiven.io/docs/platform/concepts/free-plan> : 무료 플랜에 Kafka·Valkey 포함, 무기한·카드 불필요.
- **S-AIVEN-TIER** — Aiven Free Tier, <https://aiven.io/free-tier> : 무료 Kafka 250 kb/s·보존 3일.
- **S-REDPANDA** — Redpanda Pricing, <https://www.redpanda.com/pricing> : Kafka API 호환, Serverless.
- **S-ELASTICACHE** — Amazon ElastiCache Pricing, <https://aws.amazon.com/elasticache/pricing/> : `cache.t3.micro` 750h·12개월 무료는 레거시(2025-07-15 이전) 전용, 신규 가입은 크레딧 모델.
- **S-UPSTASH-REDIS** — Upstash Pricing, <https://upstash.com/pricing> : Redis 무료 256MB·월 50만 커맨드·10GB.
- **S-REDIS-CLOUD** — Redis Cloud Pricing, <https://redis.io/pricing/> : 무료 30MB forever.
- **S-VPC** — Amazon VPC Pricing, <https://aws.amazon.com/vpc/pricing/> : NAT Gateway·PrivateLink 과금, 프리티어 없음.
- **S-FARGATE** — AWS Fargate Pricing, <https://aws.amazon.com/fargate/pricing/> : 프리티어 없음.
- **S-ALB** — Elastic Load Balancing Pricing, <https://aws.amazon.com/elasticloadbalancing/pricing/> : 신규 고객 월 750h + 15 LCU 무료.
- 저장소 코드(직접 확인): `modules/aurora-mysql/main.tf`, `modules/msk-serverless/main.tf`, `modules/msk-provisioned/main.tf`, `modules/elasticache-valkey/main.tf`, `envs/dev/variables.tf`, `common/core/.../application-common-core.yml`, `common/kafka/.../application-kafka.yml`, `docker-compose.yml`.
