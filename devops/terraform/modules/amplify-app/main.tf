# Amplify Hosting App + Branch
# - Next.js 16 SSR (platform = WEB_COMPUTE)
# - 자동 빌드 비활성 — GitHub Actions 가 start-job 으로 트리거 (D-1 불변 아티팩트, 03 §3.4)
# - 환경변수는 SSM Parameter Store 에서 pre-build phase 에 주입 (build_spec 에 정의)

locals {
  app_full_name = "${var.project}-${var.environment}-${var.app_name}"

  # 서비스 Role ARN 을 안 받으면 모듈이 IAM Role 을 직접 만든다 (아래 IAM 리소스 3개의 count 조건)
  create_iam_role = var.iam_service_role_arn == null

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "Terraform"
      Module      = "amplify-app"
      AppName     = var.app_name
    },
    var.tags,
  )

  # Next.js 16 기본 build spec — 환경별 차이는 SSM 에서 pre-build 에 가져옴
  default_build_spec = <<-EOT
    version: 1
    applications:
      - frontend:
          phases:
            preBuild:
              commands:
                - echo "Loading SSM parameters for ${var.environment}"
                - export NEXT_PUBLIC_API_BASE=$(aws ssm get-parameter --name "/${var.project}/${var.environment}/${var.app_name}/api-base" --query 'Parameter.Value' --output text)
                - npm ci --prefer-offline --no-audit --fund=false
            build:
              commands:
                - npm run build
          artifacts:
            baseDirectory: .next
            files:
              - '**/*'
          cache:
            paths:
              - node_modules/**/*
              - .next/cache/**/*
        appRoot: ${var.app_name}
  EOT

  effective_build_spec = coalesce(var.build_spec, local.default_build_spec)
}

# ----------------------------------------------------------------------------
# Amplify 서비스 Role (모듈 자동 생성 모드)
# ----------------------------------------------------------------------------

resource "aws_iam_role" "amplify" {
  count = local.create_iam_role ? 1 : 0

  name = "${local.app_full_name}-amplify"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "amplify.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "amplify_managed" {
  count = local.create_iam_role ? 1 : 0

  role       = aws_iam_role.amplify[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AdministratorAccess-Amplify"
}

# SSM Parameter Store 읽기 (환경변수 주입용)
resource "aws_iam_role_policy" "amplify_ssm" {
  count = local.create_iam_role ? 1 : 0

  name = "ssm-read"
  role = aws_iam_role.amplify[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath",
      ]
      Resource = "arn:aws:ssm:*:*:parameter/${var.project}/${var.environment}/${var.app_name}/*"
    }]
  })
}

locals {
  service_role_arn = coalesce(
    var.iam_service_role_arn,
    try(aws_iam_role.amplify[0].arn, null),
  )
}

# ----------------------------------------------------------------------------
# Amplify App
# ----------------------------------------------------------------------------

resource "aws_amplify_app" "this" {
  name        = local.app_full_name
  repository  = var.repository_url
  platform    = var.platform   # WEB_COMPUTE — Next.js 16 SSR
  iam_service_role_arn = local.service_role_arn

  # Personal Access Token (PAT) 으로 GitHub 연동 — Secrets Manager 의 ARN 을 직접 박지 않고 fetch
  access_token = var.github_access_token_secret_arn != null ? data.aws_secretsmanager_secret_version.github_token[0].secret_string : null

  build_spec = local.effective_build_spec

  # 자동 브랜치 생성 차단 — main/develop 만 명시 등록
  enable_auto_branch_creation = false
  enable_branch_auto_build    = false   # GitHub Actions 가 start-job 으로 트리거 (CI 일관성)
  enable_branch_auto_deletion = false

  environment_variables = merge(
    {
      AMPLIFY_DIFF_DEPLOY      = "false"
      AMPLIFY_MONOREPO_APP_ROOT = var.app_name
      _LIVE_UPDATES = jsonencode([
        { pkg = "next-version", type = "internal", version = "latest" },
      ])
    },
    var.environment_variables,
  )

  custom_rule {
    source = "/<*>"
    target = "/index.html"
    status = "404-200"
  }

  tags = local.common_tags

  lifecycle {
    ignore_changes = [
      access_token,   # Secrets Manager 회전 시 plan 노이즈 방지
    ]
  }
}

# ----------------------------------------------------------------------------
# Branch
# ----------------------------------------------------------------------------

resource "aws_amplify_branch" "this" {
  app_id      = aws_amplify_app.this.id
  branch_name = var.branch_name
  stage       = var.stage
  framework   = var.framework

  enable_auto_build         = false   # GitHub Actions 트리거만
  enable_pull_request_preview = false

  enable_basic_auth      = var.enable_basic_auth
  basic_auth_credentials = var.enable_basic_auth ? var.basic_auth_credentials : null

  environment_variables = var.branch_environment_variables

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# GitHub PAT fetch (Secrets Manager 에 보관, 모듈은 ARN 만 받음)
# ----------------------------------------------------------------------------

data "aws_secretsmanager_secret_version" "github_token" {
  count = var.github_access_token_secret_arn != null ? 1 : 0

  secret_id = var.github_access_token_secret_arn
}
