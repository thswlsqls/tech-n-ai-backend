# 기술 블로그 주제 인덱스 — reference-architecture 다이어그램으로 읽는 애플리케이션 계층 설계

> 1차 소스: [`devops/aws/{dev,beta,prod}/reference-architecture.drawio`·`.png`](../../../../../../../devops/aws/README.md)
> 보조 컨텍스트: [`architecture-facts.md`](../../../../../../../devops/aws/architecture-facts.md)(Terraform 기준 line-cited 사실 모음)
>
> 본 문서는 `devops/aws/dev·beta·prod/reference-architecture.png`(와 대응 `.drawio`)를 1차 소스로 도출한 블로그 주제 후보 모음이다. 형제 폴더 [`../Network Topology/`](../Network%20Topology/README.md)가 `network-topology.png`를 소스로 NAT·MSK 단계·ALB HTTPS·ALB 라우팅을 이미 다뤘으므로, 본 시리즈는 **reference-architecture 다이어그램에서만 직접 확인되는, 애플리케이션 계층(CQRS·데이터 저장소·컴퓨트 스케일링)의 사실**에 집중한다.

## 단편과 시리즈의 관계 — 부분 선형(01→02→03) + 독립 단편(04)

`../Network Topology/`의 "빌딩블록 × 메타 글" 모델과 달리, 본 시리즈는 별도 메타 글(`series-*.md`)을 두지 않는다. 01~03은 CQRS·데이터 저장소라는 한 서사 위에서 순서대로 시야를 넓히고, 04는 컴퓨트 스케일링이라는 별도 축을 다루는 독립 단편이다.

```
01 CQRS·MongoDB Atlas 경계 ──→ 02 Aurora 계단형 진화 ──→ 03 서비스별 저장소 접근 매트릭스
   (쓰기/읽기 물리 분리)         (Aurora 스펙 심화)          (Aurora+캐시+MSK 전체 확장)

04 ECS 오토스케일링 이중 구조  (독립 — 위 세 편과 무관한 컴퓨트 축)
```

- 각 단편은 **독립 출판이 가능**하다(한 편에 한 결정/한 관찰).
- 01→02→03은 **누적** 구조다. 뒤 단편은 앞 단편이 답한 "왜"를 반복하지 않고 다음 질문으로 넘어간다.
- 04는 데이터 계층과 무관한 컴퓨트 계층을 다루므로 **시리즈 외 독립 자산**이다.

### 단편 사이 인용 관계

| 단편 | 앞 단편 전제 | 뒤 단편으로 넘기는 질문 |
|---|---|---|
| 01 CQRS·MongoDB Atlas 경계 | — (출발점) | "Aurora는 정확히 어떻게 구성되나" → 02 |
| 02 Aurora 계단형 진화 | 01의 CQRS 쓰기 모델 | "이 인스턴스에 실제로 접근하는 서비스는 몇 개인가" → 03 |
| 03 서비스별 저장소 접근 매트릭스 | 01·02의 Aurora·MongoDB 그림 | — (데이터 계층 시리즈 마무리) |
| 04 ECS 오토스케일링 이중 구조 | — (독립) | — (인용 없음) |

## 1. 단편 글 후보

| # | 제목 | Why | 구현 | 운영 | 근거 | 분량 |
|---|---|:-:|:-:|:-:|---|---|
| [01](./01-cqrs-mongodb-atlas-boundary.md) | 점선으로 그려진 데이터베이스 — CQRS 쓰기·읽기 분리와 MongoDB Atlas의 IaC 경계 | ✅ | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §2·§5·§8·§9 | 보통 |
| [02](./02-aurora-serverless-to-provisioned.md) | Aurora MySQL의 계단형 진화 — Serverless v2에서 Provisioned로 | ✅ | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §2 | 보통 |
| [03](./03-service-datastore-access-matrix.md) | SG가 허락한 접근과 실제로 쓰는 접근 — 6개 서비스 × 3개 데이터 저장소 매트릭스 | — | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §1·§5 + 저장소 코드(확인 필요) | 보통 |
| [04](./04-ecs-autoscaling-two-tier.md) | 다이어그램 라벨 하나, 숨은 정책 두 개 — ECS 오토스케일링은 절반만 환경을 따라간다 | — | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §1·§7 | 짧음 |

## 2. 폐기·병합 로그(투명성)

- ❌ **"MSK 없음→Serverless→Provisioned 3단계"** — `../Network Topology/01-msk-staged-rollout.md`가 이미 상세히 다룸. reference-architecture 다이어그램에도 같은 사실이 그려지지만 새 관점을 못 만들어 제외.
- ❌ **"ALB HTTP/HTTPS 토글"** — `../Network Topology/02-alb-https-toggle.md`가 이미 다룸. reference-architecture 다이어그램의 "Client reaches the ALB" 플로우 박스도 같은 사실을 반복할 뿐이라 제외.
- ❌ **"path-based 라우팅으로 6개 서비스를 하나의 ALB 뒤에 두기"** — `../Network Topology/03-alb-path-based-routing-vs-gateway.md`가 이미 다뤘고, 실제 인증 우회 발견까지 담아 대체 불가능한 깊이를 가짐. 중복 제외.
- 🔁 **"Frontend(Amplify/CloudFront) 모듈은 정의됐지만 배포되지 않았다"** — 세 다이어그램 모두 동일한 노트 박스로 표기하는 흥미로운 사실이지만, `architecture-facts.md` §3·§9 기준으로 "왜 아직 안 켰는가"에 대한 확인 가능한 근거가 없다(코드는 "off"라는 상태만 보여줄 뿐 이유는 밝히지 않음). 독립 글로 세우면 추측에 의존하게 되므로 폐기하고, 01의 "관리 경계" 논의에 배경 사실로만 짧게 인용.
- 🔁 **"CodeDeploy Blue/Green·카나리 롤백"** — `architecture-facts.md` §1에 근거가 풍부하지만(배포 컨트롤러, 자동 롤백 알람 임계 등), reference-architecture 다이어그램에는 아이콘·라벨로 나타나지 않는다. 다이어그램을 1차 소스로 삼는 본 시리즈 범위를 벗어나므로 폐기. 별도 CI/CD 카테고리 후보로 남긴다.
- 🔁 **"Observability 사이드카(ADOT/FireLens)와 CloudWatch Logs 경로"** — 다이어그램에 아이콘은 있으나 모든 env에서 default false로 동일하고, 별도의 `observability.png` 다이어그램이 이미 존재해 그쪽 시리즈가 다룰 주제로 남긴다. 본 시리즈에서는 폐기.

## 3. 작성 가이드

- **인용 정책**: 기술적 사실의 근거는 `architecture-facts.md`의 파일:라인 출처 또는 `devops/aws/*/reference-architecture.png` 다이어그램 라벨, 그리고 aws-docs MCP로 확인한 공식 AWS 문서(MongoDB Atlas는 공식 제품 문서)만 사용한다. 블로그·AI 생성 콘텐츠 인용 금지(`tech-n-ai-backend/CLAUDE.md` 외부 자료 참조 원칙).
- **본문 언어**: 한국어. 고유명사·기술 용어는 영문 유지(CQRS, Aurora, MongoDB Atlas, Security Group, ECS 등).
- **숫자·산술**: `architecture-facts.md`에 명시된 수치만 사용한다. 추정값은 "추정:" 표기.
- **코드 재검증 우선**: 03 단편처럼 SG 인가 목록과 다이어그램 화살표 사이 간극을 다루는 글은, 완성 글 작성 전 실제 애플리케이션 코드(리포지토리·엔티티)로 재확인한다. 다이어그램은 특정 시점의 스냅샷일 수 있다.
- **다이어그램 인용**: 각 단편 도입부에 해당 환경의 `reference-architecture.png` 캡처 또는 라벨 인용 박스를 둔다.
- **단편 작성 시**: 글 마지막의 "시리즈 인용 관계" 섹션을 유지해 앞뒤 단편이 어떤 질문을 주고받는지 신호를 남긴다.
- **분량·SEO**: 완성 글은 `write-tech-blog`에서 7,000자 이상·SEO 제목 후보 3개+·번호 없는 소제목으로 다듬는다. 설계도의 아웃라인 번호는 기획용이다.

## 공식 출처 (단편 공통 보강)

- Amazon Aurora Serverless v2 사용 설명서: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.html
- Aurora Capacity Unit(ACU) 동작 방식: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.how-it-works.html
- Amazon Aurora 읽기 복제본: https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.Replication.html
- Amazon RDS Performance Insights: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PerfInsights.html
- AWS Secrets Manager란 무엇인가: https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html
- AWS KMS 핵심 개념(봉투 암호화): https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html
- MongoDB Atlas — 데이터베이스 배포에 연결하기(공식 문서): https://www.mongodb.com/docs/atlas/connect-to-database-deployment/
- Amazon VPC 보안 그룹 규칙: https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html
- Amazon VPC 보안 그룹 기본 개념: https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html
- AWS IAM 모범 사례 — 최소 권한 부여: https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html
- Amazon ECS 서비스 오토스케일링: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html
- Amazon ECS 대상 추적 오토스케일링 정책 만들기: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/target-tracking-create-policy.html

> 위 공식 출처 외의 블로그/AI 생성 문서는 본 시리즈의 근거로 인용하지 않는다.
