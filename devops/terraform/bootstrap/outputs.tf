output "state_bucket_name" {
  description = "Terraform state S3 버킷 이름. envs/<env>/backend.tf 의 bucket 필드에 그대로 사용."
  value       = aws_s3_bucket.tfstate.bucket
}

output "state_bucket_arn" {
  description = "Terraform state S3 버킷 ARN."
  value       = aws_s3_bucket.tfstate.arn
}

output "state_kms_key_arn" {
  description = "Terraform state 암호화 KMS CMK ARN. backend.tf 의 kms_key_id 에 사용."
  value       = aws_kms_key.tfstate.arn
}

output "state_kms_alias" {
  description = "Terraform state KMS 별칭 (alias/{project}/tfstate)."
  value       = aws_kms_alias.tfstate.name
}

output "lock_table_name" {
  description = "DynamoDB Lock 테이블 이름. backend.tf 의 dynamodb_table 에 사용."
  value       = aws_dynamodb_table.tflock.name
}

output "github_oidc_provider_arn" {
  description = "GitHub OIDC Provider ARN. 모든 신뢰 정책의 Federated Principal."
  value       = aws_iam_openid_connect_provider.github.arn
}

output "gha_deploy_role_arns" {
  description = "환경별 워크로드 배포 Role ARN. GitHub Secrets `AWS_DEPLOY_ROLE_ARN` 에 등록."
  value       = { for env, role in aws_iam_role.gha_deploy : env => role.arn }
}

output "gha_terraform_readonly_role_arn" {
  description = "Terraform plan 전용 read-only Role ARN. GitHub Secrets `AWS_TERRAFORM_READONLY_ROLE_ARN` 에 등록."
  value       = aws_iam_role.gha_terraform_readonly.arn
}

output "gha_terraform_apply_role_arns" {
  description = "환경별 Terraform apply Role ARN. GitHub Secrets `AWS_TERRAFORM_APPLY_ROLE_ARN` 에 등록."
  value       = { for env, role in aws_iam_role.gha_terraform_apply : env => role.arn }
}

output "gha_security_scan_role_arn" {
  description = "주간 보안 스캔 Role ARN. GitHub Secrets `AWS_SECURITY_SCAN_ROLE_ARN` 에 등록."
  value       = aws_iam_role.gha_security_scan.arn
}

# ----------------------------------------------------------------------------
# ECR
# ----------------------------------------------------------------------------

output "ecr_registry_url" {
  description = "ECR 레지스트리 URL. GitHub Variables `ECR_REGISTRY` 에 등록 (`<account>.dkr.ecr.<region>.amazonaws.com`)."
  value       = "${local.account_id}.dkr.ecr.${var.region}.amazonaws.com"
}

output "ecr_repository_urls" {
  description = "모듈별 ECR 리포지토리 URL map (techai/{module})."
  value       = { for k, repo in aws_ecr_repository.this : k => repo.repository_url }
}

output "ecr_repository_arns" {
  description = "모듈별 ECR 리포지토리 ARN map."
  value       = { for k, repo in aws_ecr_repository.this : k => repo.arn }
}

output "ecr_kms_key_arn" {
  description = "ECR 이미지 암호화 KMS CMK ARN."
  value       = aws_kms_key.ecr.arn
}
