# Amplify Hosting — Next.js 16 SSR (app + admin)
# 시드 단계 default 비활성. GitHub PAT secret 입력 후 enable_amplify=true.
# 03 §3.4 + 07b 워크플로 정합

# GitHub PAT secret stub — Amplify 가 리포 클론·웹훅 등록에 사용
# 실제 값은 보안팀이 별도로 secret_string 입력 (lifecycle.ignore_changes 로 보호)
resource "aws_secretsmanager_secret" "github_pat" {
  count = var.enable_amplify ? 1 : 0

  name        = "${var.project}/${var.environment}/github-pat-amplify"
  description = "GitHub PAT for Amplify (scopes: repo, admin:repo_hook)."
  kms_key_id  = aws_kms_key.auth.arn

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "github_pat_initial" {
  count = var.enable_amplify ? 1 : 0

  secret_id     = aws_secretsmanager_secret.github_pat[0].id
  secret_string = "ghp_PLACEHOLDER_REPLACE_WITH_REAL_PAT"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

# ----------------------------------------------------------------------------
# app — 공개 사용자 대상 (Next.js 16 SSR)
# ----------------------------------------------------------------------------

module "amplify_app" {
  count  = var.enable_amplify ? 1 : 0
  source = "../../modules/amplify-app"

  project     = var.project
  environment = var.environment
  app_name    = "app"

  repository_url                 = var.frontend_repository_url
  github_access_token_secret_arn = aws_secretsmanager_secret.github_pat[0].arn

  branch_name = var.frontend_branch_name
  stage       = "DEVELOPMENT"
  platform    = "WEB_COMPUTE"

  environment_variables = {
    NEXT_PUBLIC_APP_NAME = "tech-n-ai"
  }

  branch_environment_variables = {
    NEXT_PUBLIC_BUILD_ENV = var.environment
  }
}

# ----------------------------------------------------------------------------
# admin — 내부 관리자 (Basic Auth 옵션)
# ----------------------------------------------------------------------------

module "amplify_admin" {
  count  = var.enable_amplify ? 1 : 0
  source = "../../modules/amplify-app"

  project     = var.project
  environment = var.environment
  app_name    = "admin"

  repository_url                 = var.frontend_repository_url
  github_access_token_secret_arn = aws_secretsmanager_secret.github_pat[0].arn

  branch_name = var.frontend_branch_name
  stage       = "DEVELOPMENT"
  platform    = "WEB_COMPUTE"

  enable_basic_auth      = false # dev 는 미적용. prod admin 은 true 권장
  basic_auth_credentials = null
}
