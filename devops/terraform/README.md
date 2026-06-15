# Tech-N-AI Terraform 모듈 레포

> **본 디렉토리는** [09-iac-terraform.md](../results/09-iac-terraform.md)의 모듈 spec을 실 코드로 구현한 산출물이다. 사용자는 본 트리를 `git clone`하거나 그대로 모노레포 안에 두고 `envs/<env>` 디렉토리에서 `terraform init` → `apply`를 실행한다.

## 디렉토리 구조

```
devops/terraform/
├── README.md                     # 본 문서
├── .pre-commit-config.yaml       # fmt / validate / tflint / tfsec / checkov
├── .tflint.hcl
├── .terraform-docs.yml
├── bootstrap/                    # 0회성: state 인프라 + GitHub OIDC Role
├── modules/                      # 환경 독립 재사용 모듈
│   ├── network/                  # VPC, 서브넷, NAT, VPCE
│   ├── iam-role-workload/        # ECS Task Role · 일반 워크로드 Role
│   ├── s3-bucket/                # 표준 S3 (KMS/Versioning/BPA/Lifecycle)
│   ├── ecs-service/              # Fargate + ALB Listener Rule + Blue/Green TG + CodeDeploy + 자동 롤백 알람
│   ├── aurora-mysql/             # Aurora MySQL (Serverless v2 / Provisioned 분기, IAM DB Auth)
│   ├── elasticache-valkey/       # Valkey (Multi-AZ + auth_token/RBAC)
│   ├── msk-serverless/           # MSK Serverless (dev/beta, IAM SASL only)
│   ├── msk-provisioned/          # MSK Provisioned (prod, KRaft + IAM SASL + TLS)
│   ├── amplify-app/              # Amplify Hosting (Next.js SSR app·admin, GitHub 연동 + SSM 환경변수)
│   ├── cloudfront-spa/           # CloudFront + OAC + 보안 헤더 정책 (SPA 라우팅)
│   └── observability/            # CloudWatch Log Groups + 표준 알람 + 대시보드 (configs: ADOT/FireLens)
└── envs/
    ├── dev/                      # 완성 (network·s3·IAM·Aurora·Valkey·MSK·6 ECS Service·Amplify·observability + 데이터 SG cross-ref)
    ├── beta/                     # 완성 (dev 동일 구조 + tfvars 차이만)
    └── prod/                     # 완성 (Aurora Provisioned 3 instance + MSK Provisioned + AZ별 NAT)
```

## 작업 진척

| 모듈 | 상태 | 세션 |
|---|---|---|
| `bootstrap` | ✅ 완료 | 2a |
| `modules/network` | ✅ 완료 | 2a |
| `modules/iam-role-workload` | ✅ 완료 | 2a |
| `modules/s3-bucket` | ✅ 완료 | 2a |
| `envs/dev` 골격 | ✅ 완료 | 2a |
| `modules/aurora-mysql` | ✅ 완료 | 2b |
| `modules/elasticache-valkey` | ✅ 완료 | 2b |
| `modules/msk-{serverless,provisioned}` | ✅ 완료 | 2b |
| `modules/ecs-service` (워크로드 SG 포함) | ✅ 완료 | 2c |
| `modules/amplify-app` | ✅ 완료 | 2c |
| `modules/cloudfront-spa` | ✅ 완료 | 2c |
| `modules/observability` | ✅ 완료 | 2c |
| `envs/beta`·`envs/prod` 완성 | ✅ 완료 | 2c |

## 부트스트랩 → 환경 적용 흐름

```
[1] bootstrap (한 번만, 관리 계정 또는 환경별 한 번씩)
    └── S3 state 버킷, DynamoDB Lock, KMS 키 생성
    └── GitHub OIDC Provider + 4 Role 생성
        ↓ (출력 ARN을 GitHub Secrets에 등록)
[2] envs/<env>
    └── backend.tf 가 [1]에서 만든 S3·DynamoDB·KMS를 가리킴
    └── module.network → module.s3 → … 순서로 호출
```

자세한 절차는 `bootstrap/README.md` 참조.

## 컨벤션

- **언어**: 본 README·주석은 한국어, 식별자·리소스 이름·변수는 영어.
- **태그**: 모든 리소스에 `local.common_tags` 적용 (Project, Environment, ManagedBy=Terraform, Module — 모듈에 따라 AppName·Service 등 추가).
- **명명**: `{project}-{env}-{resource}` (예: `techai-dev-vpc`).
- **CIDR**: dev `10.10.0.0/16`, beta `10.20.0.0/16`, prod `10.30.0.0/16` (02 §1.1.1).
- **AZ**: 서울 리전 3 AZ (`ap-northeast-2a/b/c`).
- **버전 고정**: Terraform CLI는 루트(`envs/*`·`bootstrap`)에서 `~> 1.15.0`으로 고정하고, 재사용 모듈(`modules/*`)은 하한만 `>= 1.9`로 둬서 루트가 버전을 정하게 한다. AWS Provider는 `~> 5.100` (5.x 마지막 라인, 6.0 미만 — 6.x는 별도 마이그레이션 작업으로 분리), random `~> 3.6` (aurora-mysql·elasticache-valkey·bootstrap).

## 외부 참조

- [00-cross-cutting-matrix.md](../results/00-cross-cutting-matrix.md) — KMS·IAM·SG 단일 정의 매트릭스
- [02-network-vpc.md](../results/02-network-vpc.md) — CIDR/서브넷/SG 설계
- [06-security-and-iam.md](../results/06-security-and-iam.md) — IAM·KMS·Trust Policy
- [09-iac-terraform.md](../results/09-iac-terraform.md) — 모듈 spec 본문

## 공식 출처

- Terraform — <https://developer.hashicorp.com/terraform/docs>
- AWS Provider — <https://registry.terraform.io/providers/hashicorp/aws/latest/docs>
- AWS Well-Architected Framework — <https://docs.aws.amazon.com/wellarchitected/>
