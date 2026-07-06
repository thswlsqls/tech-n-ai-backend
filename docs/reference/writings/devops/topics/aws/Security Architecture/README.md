# 기술 블로그 주제 인덱스 — security 다이어그램으로 읽는 신뢰 경계와 암호화

> 1차 소스: [`devops/aws/{dev,beta,prod}/security.drawio`·`.png`](../../../../../../../devops/aws/README.md)
> 보조 컨텍스트: [`architecture-facts.md`](../../../../../../../devops/aws/architecture-facts.md)(Terraform 기준 line-cited 사실 모음)
>
> 본 문서는 `devops/aws/dev·beta·prod/security.png`(와 대응 `.drawio`)를 1차 소스로 도출한 블로그 주제 후보 모음이다. 이 다이어그램은 CI/CD 신뢰 경계(GitHub OIDC) · 런타임 신뢰 경계(ECS Task Role) · KMS 암호화 키 · Secrets Manager · 전송 구간 암호화를 한 장에 담는다. 형제 폴더 [`../Network Topology/`](../Network%20Topology/README.md)가 이미 SG 3단 체인을 다룬 `../prototype/05-sg-vs-nacl-defense-in-depth.md`를 인용하며 네트워크 계층 방어를 다뤘고, [`../Reference Architecture/`](../Reference%20Architecture/README.md)가 SG 인가 목록과 다이어그램 화살표의 간극(03)을 다뤘으므로, 본 시리즈는 **신원(identity)과 암호화(encryption)라는, security 다이어그램에서만 직접 확인되는 두 축**에 집중한다.

## 단편과 시리즈의 관계 — 두 트랙(신원 축 01→02→03, 암호화 축 04↔05)

별도 메타 글(`series-*.md`)은 두지 않는다. 다섯 단편은 다이어그램이 시각적으로 나눈 두 개의 큰 상자(신뢰 경계 두 개 / KMS·Secrets·전송 암호화)를 그대로 따라 두 트랙으로 묶인다.

```
[신원 트랙]     01 CI/CD OIDC 4개 역할 ──→ 02 Runtime Task Role 6개 + 시크릿 ──→ 03 dev의 고아 kafka 권한
[암호화 트랙]   04 KMS 5+2 키 구조 ←──────────── 짝 ────────────→ 05 ALB 뒤 in-transit 암호화 갈림
```

- 신원 트랙(01→02→03)은 **누적** 구조다. 배포 시점 권한(01)에서 실행 시점 권한(02)으로, 다시 02가 남긴 구체적 질문 하나(api-agent의 kafka 권한)를 03이 심화한다.
- 암호화 트랙(04, 05)은 "저장 데이터"와 "전송 구간"이라는 짝을 이루는 두 단편으로, 서로를 반복하지 않고 참조만 한다.
- 두 트랙 사이에는 직접적인 인용 관계가 없다 — 신원과 암호화는 서로 다른 통제이기 때문이다.

### 단편 사이 인용 관계

| 단편 | 앞 단편 전제 | 뒤 단편으로 넘기는 질문 |
|---|---|---|
| 01 CI/CD OIDC 4개 역할 | — (출발점) | "실행 중인 컨테이너는 어떤 권한을 쓰나" → 02 |
| 02 Runtime Task Role 6개 + 시크릿 | 01의 최소 권한 원칙 | "api-agent의 kafka 권한은 dev에서 무엇을 가리키나" → 03 |
| 03 dev의 고아 kafka 권한 | 02의 Task Role 매트릭스 | — (신원 트랙 마무리) |
| 04 KMS 5+2 키 구조 | — (독립) | "저장은 그렇다 치고, 흐르는 동안은?" → 05 |
| 05 ALB 뒤 in-transit 암호화 갈림 | 04의 저장 데이터 암호화 | — (암호화 트랙 마무리) |

## 1. 단편 글 후보

| # | 제목 | Why | 구현 | 운영 | 근거 | 분량 |
|---|---|:-:|:-:|:-:|---|---|
| [01](./01-github-actions-oidc-four-roles.md) | 장기 자격증명 없는 CI/CD — GitHub Actions OIDC 페더레이션과 역할을 4개로 쪼갠 이유 | ✅ | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §5 IAM | 보통 |
| [02](./02-ecs-task-role-secrets-least-privilege.md) | Task Role 6개, 시크릿 5개 — 런타임 신뢰 경계가 최소 권한을 코드로 강제하는 법 | — | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §5 IAM·Secrets Manager | 보통 |
| [03](./03-orphaned-kafka-permission-in-dev.md) | dev에는 없는 MSK를 향한 권한 — kafka-cluster:*가 아무것도 가리키지 않을 때 | — | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §2 MSK·§5 IAM | 짧음 |
| [04](./04-kms-five-plus-two-keys.md) | 계정에 KMS 키 하나면 충분할 텐데, 왜 환경마다 5개를 따로 만드는가 | ✅ | ✅ | — | 다이어그램 + `architecture-facts.md` §5 KMS 키 | 보통 |
| [05](./05-in-transit-encryption-split.md) | 외부는 HTTPS, 내부는 평문 HTTP — ALB 뒤에서 암호화 전략이 갈리는 지점 | ✅ | ✅ | ✅ | 다이어그램 + `architecture-facts.md` §1·§2·§5 | 보통 |

## 2. 폐기·병합 로그(투명성)

- ❌ **"Security Group 3단 체인(ALB SG → Workload SG → Data SG)"** — `../prototype/05-sg-vs-nacl-defense-in-depth.md`가 이미 SG ID 참조 원칙과 심층 방어를 다룸. security 다이어그램에는 SG 자체가 아이콘으로 그려지지 않으므로(신뢰 경계·키·시크릿 중심), 중복 여지도 적어 제외.
- ❌ **"SG 인가 목록과 다이어그램 화살표의 간극"** — `../Reference Architecture/03-service-datastore-access-matrix.md`가 이미 다룸(Aurora/Valkey/MSK 접근 매트릭스). security 다이어그램의 IAM 축(누가 무엇을 읽는가)과는 다른 질문이라 겹치지 않지만, 혼동을 피하기 위해 명시적으로 제외 처리.
- ❌ **"ALB HTTPS 토글(Client→ALB 구간)"** — `../Network Topology/02-alb-https-toggle.md`가 이미 다룸. 05는 그 뒤 구간(ALB→Fargate, Fargate→데이터)만 다루도록 범위를 분명히 좁혔다.
- 🔁 **"CI/CD 아티팩트 무결성 — ECR 이미지 불변 태그·스캔 + tfstate Object Lock·DynamoDB PITR"** — 다이어그램의 "State + boundary controls" 상자에 근거가 있고(ECR IMMUTABLE·scan_on_push, S3 Object Lock GOVERNANCE 30일, DynamoDB PITR), 독립 글로 세울 만한 밀도지만 01(CI/CD 신원)과 소재가 겹쳐 분량이 늘어질 위험이 있다. 이번 시리즈에서는 폐기하고, 별도 "공급망 무결성" 카테고리 후보로 남긴다.
- 🔁 **"Inspector 이미지 스캔 파이프라인"** — `gha-security-scan` 역할과 Inspector 노드가 다이어그램에 있지만, 근거가 "권한 부여"(01의 일부)에 그치고 스캔 결과 활용 방식은 코드로 확인되지 않아 독립 글로 세우기엔 얇다. 01의 배경 사실로만 짧게 인용.

## 3. 작성 가이드

- **인용 정책**: 기술적 사실의 근거는 `architecture-facts.md`의 파일:라인 출처 또는 `devops/aws/*/security.png` 다이어그램 라벨, 그리고 aws-docs MCP로 확인한 공식 AWS 문서와 GitHub 공식 문서만 사용한다. 블로그·AI 생성 콘텐츠 인용 금지(`tech-n-ai-backend/CLAUDE.md` 외부 자료 참조 원칙).
- **본문 언어**: 한국어. 고유명사·기술 용어는 영문 유지(OIDC, IAM, KMS, Secrets Manager, TLS 등).
- **숫자·산술**: `architecture-facts.md`에 명시된 수치만 사용한다. 추정값은 "추정:" 표기.
- **다이어그램 라벨 인용 시 주의**: 다이어그램의 요약 라벨("rotation on · 30d window" 등)을 그대로 옮기지 말고, `architecture-facts.md`의 원문 사실(rotation과 deletion window는 별개 설정)로 재확인한 뒤 정확히 구분해서 쓴다.
- **판단을 단정하지 않기**: 03처럼 "이것이 위험한가"를 코드만으로 확정할 수 없는 주제는, 결론을 흐릿하게 남기지 않되 "코드가 보여주는 사실"과 "코드만으로는 답할 수 없는 지점"을 분리해서 정직하게 쓴다.
- **다이어그램 인용**: 각 단편 도입부에 해당 환경의 `security.png` 캡처 또는 라벨 인용 박스를 둔다.
- **단편 작성 시**: 글 마지막의 "시리즈 인용 관계" 섹션을 유지해 트랙 안에서 앞뒤 단편이 어떤 질문을 주고받는지 신호를 남긴다.
- **분량·SEO**: 완성 글은 `write-tech-blog`에서 7,000자 이상·SEO 제목 후보 3개+·번호 없는 소제목으로 다듬는다. 설계도의 아웃라인 번호는 기획용이다.

## 공식 출처 (단편 공통 보강)

- IAM과 OpenID Connect 자격 증명 공급자: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html
- GitHub Actions에서 AWS용 OpenID Connect 구성(GitHub 공식 문서): https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
- Amazon ECS 태스크 IAM 역할: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html
- Amazon ECS 태스크 실행 IAM 역할: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html
- AWS Secrets Manager란 무엇인가: https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html
- IAM 정책 요소 — Resource: https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_resource.html
- Amazon MSK IAM 액세스 제어: https://docs.aws.amazon.com/msk/latest/developerguide/iam-access-control.html
- AWS KMS 핵심 개념(봉투 암호화): https://docs.aws.amazon.com/kms/latest/developerguide/concepts.html
- AWS KMS 키 자동 순환: https://docs.aws.amazon.com/kms/latest/developerguide/rotate-keys.html
- Application Load Balancer 타깃 그룹: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-target-groups.html
- Aurora MySQL의 IAM 데이터베이스 인증(`rds-db:connect`): https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/UsingWithRDS.IAMDBAuth.IAMPolicy.html
- Amazon ElastiCache 전송 중 암호화(TLS): https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/in-transit-encryption.html

> 위 공식 출처 외의 블로그/AI 생성 문서는 본 시리즈의 근거로 인용하지 않는다.
