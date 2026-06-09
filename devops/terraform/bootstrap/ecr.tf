# ECR 리포 — 단일 리포 정책 (D-1)
# - 리포명: techai/{module} (환경 무관)
# - 태그: {semver}-{sha} (불변, IMMUTABLE)
# - 정책: scan on push, KMS, lifecycle (untagged 7일 / 60개 이상 정리)
# - 06 §2.4 gha-deploy Role 의 ECR push 권한과 정합 (Resource: techai/*)

locals {
  ecr_repositories = [
    "api-gateway",
    "api-emerging-tech",
    "api-auth",
    "api-chatbot",
    "api-bookmark",
    "api-agent",
    "batch-source",
  ]
}

# ----------------------------------------------------------------------------
# ECR 전용 KMS CMK — 이미지 레이어 암호화
# ----------------------------------------------------------------------------

resource "aws_kms_key" "ecr" {
  description             = "ECR 이미지 레이어 SSE-KMS"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = {
    Module = "bootstrap-ecr"
  }
}

resource "aws_kms_alias" "ecr" {
  name          = "alias/${var.project}/ecr"
  target_key_id = aws_kms_key.ecr.key_id
}

# ----------------------------------------------------------------------------
# 리포 7개
# ----------------------------------------------------------------------------

resource "aws_ecr_repository" "this" {
  for_each = toset(local.ecr_repositories)

  name                 = "${var.project}/${each.value}"   # techai/api-auth
  image_tag_mutability = "IMMUTABLE"                       # 태그 재사용 차단 (불변 아티팩트)

  image_scanning_configuration {
    scan_on_push = true   # ECR Enhanced Scanning 사용 시 Inspector 가 자동 분석
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.ecr.arn
  }

  tags = {
    Module  = "bootstrap-ecr"
    Service = each.value
  }

  lifecycle {
    prevent_destroy = true   # ECR 리포 사고성 삭제 차단
  }
}

# ----------------------------------------------------------------------------
# Lifecycle Policy — untagged 7일 정리, 태그된 이미지는 최근 60개 유지
# ----------------------------------------------------------------------------

resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Untagged 이미지는 7일 후 정리"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Tagged 이미지는 최근 60개만 유지"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 60
        }
        action = { type = "expire" }
      },
    ]
  })
}

# ----------------------------------------------------------------------------
# Repository Policy — gha-deploy-* Role 들만 push 가능, 모든 ECS Task 가 pull 가능
# ----------------------------------------------------------------------------

resource "aws_ecr_repository_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowGhaDeployRolePush"
        Effect = "Allow"
        Principal = {
          AWS = [for env, role in aws_iam_role.gha_deploy : role.arn]
        }
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:GetDownloadUrlForLayer",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
          "ecr:BatchGetImage",
          "ecr:DescribeImages",
        ]
      },
      {
        Sid    = "AllowEcsTaskPull"
        Effect = "Allow"
        Principal = { Service = "ecs-tasks.amazonaws.com" }
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
      },
      {
        Sid    = "DenyDeleteImage"
        Effect = "Deny"
        Principal = "*"
        Action = [
          "ecr:BatchDeleteImage",
          "ecr:DeleteRepository",
          "ecr:DeleteRepositoryPolicy",
          "ecr:DeleteLifecyclePolicy",
        ]
        Condition = {
          StringNotEquals = {
            "aws:PrincipalArn" = "arn:aws:iam::${local.account_id}:role/${var.project}-bootstrap-admin"
          }
        }
      },
    ]
  })
}

# KMS Key 정책 갱신 — gha-deploy / ECS Task 가 키 사용
data "aws_iam_policy_document" "ecr_kms" {
  statement {
    sid    = "EnableRoot"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${local.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid    = "AllowEcrService"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["ecr.amazonaws.com"]
    }
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "AllowGhaDeployAndEcsTask"
    effect = "Allow"
    principals {
      type = "AWS"
      identifiers = concat(
        [for env, role in aws_iam_role.gha_deploy : role.arn],
        ["arn:aws:iam::${local.account_id}:root"], # ECS Task Execution Role 들
      )
    }
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]
    resources = ["*"]
  }
}

resource "aws_kms_key_policy" "ecr" {
  key_id = aws_kms_key.ecr.id
  policy = data.aws_iam_policy_document.ecr_kms.json
}
