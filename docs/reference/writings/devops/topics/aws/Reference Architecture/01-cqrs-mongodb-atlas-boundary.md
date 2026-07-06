# 01. 점선으로 그려진 데이터베이스 — CQRS 쓰기·읽기 분리와 MongoDB Atlas의 IaC 경계

> 1차 소스: [`devops/aws/{dev,beta,prod}/reference-architecture.png`](../../../../../../../devops/aws/prod/reference-architecture.png) · [`architecture-facts.md` §2 MongoDB Atlas·§8 데이터 흐름](../../../../../../../devops/aws/architecture-facts.md)

## 한줄 요약(Hook)

> dev·beta·prod 세 장의 다이어그램 모두, Private-data 서브넷 안에 아이콘 네 개가 나란히 서 있다. 그중 셋(Aurora, ElastiCache Valkey, MSK)은 실선 테두리의 VPC 안에 있고, 나머지 하나(MongoDB Atlas)만 "external · not managed by Terraform"이라는 설명과 함께 다르게 그려진다. 인프라 코드가 만들지 않은 데이터베이스를, 인프라 다이어그램에 굳이 그려 넣은 이유는 무엇인가.

## 핵심 질문

- CQRS의 쓰기 모델과 읽기 모델을 왜 애초에 물리적으로 다른 데이터베이스 엔진(Aurora MySQL vs MongoDB Atlas)으로 나눴는가?
- Terraform이 프로비저닝하지 않는 외부 SaaS를, 인프라 코드와 배포 파이프라인에서 어떻게 안전하게 "참조"만 하는가?
- 6개 서비스 중 어떤 서비스가 읽기 경로(MongoDB Atlas)를 타고, 어떤 서비스가 쓰기 경로(Aurora)를 타는가 — 그 경계는 무엇을 기준으로 그어지는가?

## 다루는 관점

- ✅ 설계 근거(Why) — 쓰기 모델과 읽기 모델을 하나의 엔진으로 합치지 않고 분리하는 이유
- ✅ 구현 — Secrets Manager URI + KMS로 "관리하지 않는 리소스"를 안전하게 연결하는 패턴
- ✅ 운영 — 관리형 SaaS를 IaC 경계 밖에 두었을 때 생기는 의존성 리스크

## 근거

- 다이어그램 사실: prod `reference-architecture.png` 부제 "CQRS write=Aurora / read=MongoDB Atlas", 오른쪽 "Request & data flow" 패널의 5번 "CQRS read store — api-chatbot, api-agent, api-emerging-tech read from MongoDB Atlas (external, via a Secrets Manager URI)", MongoDB Atlas 노드 라벨 "external · CQRS read store · via Secrets Manager URI (not managed by Terraform)"
- `architecture-facts.md` §2 MongoDB Atlas(106~108행) — Terraform이 직접 생성하지 않는 외부 서비스, Secrets Manager 시크릿 `{project}/{env}/mongodb-uri`로 참조(X.509 인증 권장 주석, 초기값 placeholder), Private-data 서브넷은 "MongoDB Atlas Endpoint"용으로 표기되나 Atlas endpoint 리소스 자체는 코드에 없음
- `architecture-facts.md` §8 데이터 흐름(255~262행) — 인입 경로와 서비스별 라우팅, "CQRS: 쓰기=Aurora, 읽기=MongoDB Atlas, 동기화=Kafka(MSK)로 설계됨. Terraform은 인프라만 제공하고 애플리케이션 동기화 로직은 코드 범위 밖"
- `architecture-facts.md` §5 Secrets Manager 표(191~199행) — `mongodb-uri` 시크릿의 KMS 키(`{env}-data`)
- `architecture-facts.md` §9 확인 불가 항목(271행) — MongoDB Atlas 클러스터·사양은 Terraform 관리 밖이라 연결 URI 시크릿 외에는 코드로 확인 불가

## 타깃 독자 & 난이도

- CQRS를 개념으로는 알지만, 실제 클라우드 인프라에서 이 패턴이 어떻게 물리적으로 구현되는지 궁금한 백엔드·인프라 엔지니어
- ★★★☆☆ (사전지식: CQRS 개념, RDBMS/문서 DB 차이, Secrets Manager·KMS 기본 개념)

## 예상 분량

- 보통 (~3,500자)

## 글 아웃라인

1. **들어가며 — 점선으로 그려진 리소스 하나**
   - 세 다이어그램 모두 MongoDB Atlas만 "not managed by Terraform"이라고 다르게 표기한다는 관찰에서 출발
2. **왜 쓰기와 읽기를 아예 다른 엔진으로 나누는가**
   - 정규화된 쓰기 모델(Aurora, TSID PK)과 비정규화된 읽기 모델(MongoDB, Vector Search)의 요구 차이
3. **Terraform이 만들지 않는 리소스를 안전하게 참조하는 법**
   - Secrets Manager URI + KMS(`{env}-data`) 패턴, placeholder 초기값과 `lifecycle.ignore_changes`가 만드는 안전망
4. **읽기 경로 3개 서비스, 쓰기 경로는 그보다 적다 — 경계는 무엇인가**
   - api-chatbot·api-agent·api-emerging-tech가 MongoDB Atlas를 읽는 이유(RAG·조회 중심)와, 쓰기 서비스와의 역할 차이
5. **동기화는 코드 범위 밖이라는 정직한 경계선**
   - Kafka(MSK)가 담당하는 것과, Terraform·이 인프라 코드가 보장하지 않는 것(애플리케이션 동기화 로직)을 분명히 구분
6. **결론 — 인프라 다이어그램에 "안 만든 것"을 그려 넣는 실용적 이유**
   - 관리 경계를 시각적으로 남기는 것 자체가 온콜·신규 합류자에게 주는 정보라는 정리

## 참고할 1차 출처

- AWS Secrets Manager란 무엇인가: https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html
- AWS KMS 핵심 개념(봉투 암호화): https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html
- Amazon Aurora MySQL 사용 설명서: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/CHAP_AuroraMySQL.html
- MongoDB Atlas — 데이터베이스 배포에 연결하기(공식 문서): https://www.mongodb.com/docs/atlas/connect-to-database-deployment/

## 시리즈 인용 관계

이 단편은 시리즈의 출발점이다. Aurora가 정확히 어떤 스펙으로 구성되고 환경마다 어떻게 달라지는지는 여기서 다루지 않고 **[02 — Aurora의 계단형 진화](./02-aurora-serverless-to-provisioned.md)**로 넘긴다. 또한 정확히 어떤 서비스가 Aurora·캐시·MSK 각각에 접근 권한을 갖는지는 **[03 — 서비스별 데이터 저장소 접근 매트릭스](./03-service-datastore-access-matrix.md)**에서 표로 확장한다.

## 작성 메모

- "MongoDB Atlas를 Terraform이 안 만든다"를 미완성처럼 쓰지 않는다. 관리형 SaaS를 의도적으로 IaC 경계 밖에 둔 선택이라는 톤을 유지한다.
- Secrets Manager URI 패턴을 막연히 "베스트 프랙티스"라고만 부르지 말고, 왜 안전한지(코드 저장소에 접속 문자열이 없다, 회전 시 애플리케이션 코드를 건드리지 않는다)를 구체적으로 설명한다.
- MongoDB Atlas 클러스터 자체의 사양(인스턴스 크기, 리전 등)은 코드로 확인 불가한 영역이므로 추측해서 쓰지 않는다. "확인 필요"로 남긴다.
