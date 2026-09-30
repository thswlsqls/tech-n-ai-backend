# bootstrap — state 인프라 + GitHub OIDC

> **본 모듈은 1회성**이다. 적용 후 출력된 ARN 들을 GitHub Secrets / envs 의 backend.tf 에 등록하고, 이후 일상적인 변경에서는 손대지 않는다.

## 무엇을 만드는가

| 카테고리 | 자원 | 용도 |
|---|---|---|
| State | S3 버킷 (`{project}-tfstate-{account}-apne2`) | 모든 Terraform state 저장 |
| State | KMS CMK (`alias/{project}/tfstate`) | state SSE-KMS, DynamoDB SSE |
| State | DynamoDB Lock 테이블 (`techai-tflock`) | terraform 동시 실행 잠금 |
| OIDC | IAM OIDC Provider (`token.actions.githubusercontent.com`) | GitHub Actions 신뢰 기반 |
| OIDC | `gha-deploy-{env}` × 3 | ECR push, ECS update-service, ECS 서비스 배포 조회·중지 |
| OIDC | `gha-terraform-readonly` × 1 | PR plan 단계 (ReadOnlyAccess + state read) |
| OIDC | `gha-terraform-apply-{env}` × 3 | apply 단계 (PowerUserAccess + IAM + state RW) |
| OIDC | `gha-security-scan` × 1 | 주간 ECR/Inspector 스캔 |
| ECR | KMS CMK (`alias/{project}/ecr`) | ECR 이미지 레이어 암호화 |
| ECR | 7개 리포지토리 (`{project}/{module}`) | 단일 리포 정책 (D-1), IMMUTABLE 태그, scan_on_push, lifecycle 정책 |

## 적용 절차 (순환 의존 회피)

### 1단계: local backend 로 첫 적용

```bash
cd devops/terraform/bootstrap
terraform init   # local state — backend 미지정
terraform apply \
  -var="github_org=<your-github-org>" \
  -var="github_repo=tech-n-ai"
```

이 시점에 S3 버킷·DynamoDB·KMS·OIDC Provider·8개 Role(gha-deploy ×3 + gha-terraform-readonly ×1 + gha-terraform-apply ×3 + gha-security-scan ×1) 이 생성된다.

### 2단계: state 를 방금 만든 S3 로 마이그레이션

`backend.tf` 를 다음과 같이 추가:

```hcl
# devops/terraform/bootstrap/backend.tf
terraform {
  backend "s3" {
    bucket         = "techai-tfstate-<ACCOUNT_ID>-apne2"
    key            = "bootstrap/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "techai-tflock"
    encrypt        = true
    kms_key_id     = "alias/techai/tfstate"
  }
}
```

후 `terraform init -migrate-state` 실행. 로컬 state 가 S3 로 이동한다.

### 3단계: 출력 ARN 을 GitHub Secrets 에 등록

```bash
terraform output -json
```

다음 secrets/variables 를 GitHub 리포에 등록:

| GitHub 위치 | 키 | 값 (terraform output) |
|---|---|---|
| Repo Secrets | `AWS_DEPLOY_ROLE_ARN` | `gha_deploy_role_arns.dev` (Environment `dev` 에) |
| Repo Secrets | `AWS_DEPLOY_ROLE_ARN` | `gha_deploy_role_arns.beta` (Environment `beta` 에) |
| Repo Secrets | `AWS_DEPLOY_ROLE_ARN` | `gha_deploy_role_arns.prod` (Environment `prod` 에) |
| Repo Secrets | `AWS_TERRAFORM_READONLY_ROLE_ARN` | `gha_terraform_readonly_role_arn` |
| Repo Secrets | `AWS_TERRAFORM_APPLY_ROLE_ARN` | 환경별로 (Environment `tf-{env}` 에) |
| Repo Secrets | `AWS_SECURITY_SCAN_ROLE_ARN` | `gha_security_scan_role_arn` |

GitHub Environments(`dev`, `beta`, `prod`, `tf-dev`, `tf-beta`, `tf-prod`)도 함께 생성하고 Required Reviewers 를 prod·tf-prod 에 강제.

### 4단계: envs/<env> 의 backend.tf 작성

본 부트스트랩 출력을 사용해 `envs/dev/backend.tf` 등에 동일한 backend 블록을 작성한다 (key 만 환경별로 구분).

## 갱신 정책

- 본 모듈의 변경은 **드물어야 한다**. 새 환경 추가, OIDC sub 정책 변경, KMS 회전 정책 변경 정도.
- `prevent_destroy = true` 로 S3 버킷·DynamoDB 테이블의 사고성 destroy 를 차단했다.
- KMS 키 정책 변경은 다른 환경의 terraform apply 가 진행 중이지 않은 정시(深夜)에 수행.

## 보안 결정

- **gha-terraform-apply-* 는 PowerUserAccess + IAM 추가**: 광범위하지만 PR plan 의 SARIF·OPA 게이트가 1차 방어. 향후 Permission Boundary(`variables.permissions_boundary_managed`)로 제한.
- **state 버킷 Object Lock GOVERNANCE 30일**: state 사고성 삭제 보호. 변경 시 `s3:BypassGovernanceRetention` 권한 필요.
- **DynamoDB PITR**: state 잠금이 깨졌을 때 시점 복구 가능.
