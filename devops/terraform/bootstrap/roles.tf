# GitHub Actions OIDC 가 신뢰하는 4종 Role
# 매트릭스 §2.1 단일 정의처
#
#   ┌─────────────────────────────────┬─────────────────────────────────────────┐
#   │ Role                            │ sub 조건                                │
#   ├─────────────────────────────────┼─────────────────────────────────────────┤
#   │ gha-deploy-{env}                │ repo:{org}/{repo}:environment:{env}     │
#   │ gha-terraform-readonly          │ repo:{org}/{repo}:pull_request          │
#   │ gha-terraform-apply-{env}       │ repo:{org}/{repo}:environment:tf-{env}  │
#   │ gha-security-scan               │ repo:{org}/{repo}:ref:refs/heads/main   │
#   └─────────────────────────────────┴─────────────────────────────────────────┘

locals {
  oidc_provider_arn = aws_iam_openid_connect_provider.github.arn
  oidc_provider_url = trimprefix(aws_iam_openid_connect_provider.github.url, "https://")
  github_repo_full  = "${var.github_org}/${var.github_repo}"
  account_id        = data.aws_caller_identity.current.account_id
}

# ----------------------------------------------------------------------------
# 1. gha-deploy-{env}: 워크로드 배포 (ECR push, ECS update, ECS 서비스 배포 조회·중지)
# ----------------------------------------------------------------------------

data "aws_iam_policy_document" "gha_deploy_trust" {
  for_each = toset(var.environments)

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:sub"
      values   = ["repo:${local.github_repo_full}:environment:${each.value}"]
    }
  }
}

data "aws_iam_policy_document" "gha_deploy_inline" {
  # ECR push (단일 리포 `techai/<module>` 전용 — D-1 결정 반영)
  statement {
    sid = "EcrAuth"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    sid = "EcrPushPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = ["arn:aws:ecr:*:${local.account_id}:repository/techai/*"]
  }

  # ECS update-service + RegisterTaskDefinition (특정 클러스터·서비스 패턴 한정)
  statement {
    sid = "EcsUpdate"
    actions = [
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:DescribeTasks",
      "ecs:ListTasks",
      "ecs:RegisterTaskDefinition",
      "ecs:UpdateService",
    ]
    resources = ["*"]
  }

  # ECS 서비스 배포(blue/green·canary) 진행 상황 조회와 수동 중지
  statement {
    sid = "EcsServiceDeployment"
    actions = [
      "ecs:DescribeServiceDeployments",
      "ecs:DescribeServiceRevisions",
      "ecs:ListServiceDeployments",
      "ecs:StopServiceDeployment",
    ]
    resources = ["*"]
  }

  # PassRole — Task Role / Execution Role 을 ECS Task 에 전달
  statement {
    sid     = "PassRoleToEcs"
    actions = ["iam:PassRole"]
    resources = [
      "arn:aws:iam::${local.account_id}:role/${var.project}-*-task-*",
      "arn:aws:iam::${local.account_id}:role/${var.project}-*-task-execution-*",
    ]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  # SSM Parameter / Secrets Manager read (taskdef 렌더링용)
  statement {
    sid = "SsmAndSecretsRead"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      "arn:aws:ssm:*:${local.account_id}:parameter/${var.project}/*",
      "arn:aws:secretsmanager:*:${local.account_id}:secret:${var.project}/*",
    ]
  }

  # Amplify start-job (프론트엔드 배포)
  statement {
    sid = "AmplifyDeploy"
    actions = [
      "amplify:GetApp",
      "amplify:GetBranch",
      "amplify:GetJob",
      "amplify:ListJobs",
      "amplify:StartJob",
      "amplify:StopJob",
    ]
    resources = ["*"]
  }

  # AWS Signer 서명 (Notation)
  statement {
    sid = "SignerSign"
    actions = [
      "signer:SignPayload",
      "signer:GetSigningProfile",
      "signer:DescribeSigningJob",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role" "gha_deploy" {
  for_each = toset(var.environments)

  name                 = "${var.project}-gha-deploy-${each.value}"
  assume_role_policy   = data.aws_iam_policy_document.gha_deploy_trust[each.value].json
  max_session_duration = 3600
}

resource "aws_iam_role_policy" "gha_deploy_inline" {
  for_each = toset(var.environments)

  name   = "deploy-permissions"
  role   = aws_iam_role.gha_deploy[each.value].id
  policy = data.aws_iam_policy_document.gha_deploy_inline.json
}

# ----------------------------------------------------------------------------
# 2. gha-terraform-readonly: PR 단계 plan 전용 (read-only)
# ----------------------------------------------------------------------------

data "aws_iam_policy_document" "gha_tf_readonly_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "${local.oidc_provider_url}:sub"
      values = [
        "repo:${local.github_repo_full}:pull_request",
        "repo:${local.github_repo_full}:pull_request:*",
      ]
    }
  }
}

resource "aws_iam_role" "gha_terraform_readonly" {
  name               = "${var.project}-gha-terraform-readonly"
  assume_role_policy = data.aws_iam_policy_document.gha_tf_readonly_trust.json
}

# ReadOnlyAccess 는 너무 광범위하지만 plan 단계 read 전반에 필요. tfstate 접근은 별도 인라인.
resource "aws_iam_role_policy_attachment" "gha_terraform_readonly_managed" {
  role       = aws_iam_role.gha_terraform_readonly.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

resource "aws_iam_role_policy" "gha_terraform_readonly_state" {
  name   = "tfstate-read"
  role   = aws_iam_role.gha_terraform_readonly.id
  policy = data.aws_iam_policy_document.tfstate_read.json
}

data "aws_iam_policy_document" "tfstate_read" {
  statement {
    actions   = ["s3:ListBucket", "s3:GetBucketVersioning"]
    resources = [aws_s3_bucket.tfstate.arn]
  }
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.tfstate.arn}/*"]
  }
  statement {
    actions   = ["dynamodb:DescribeTable", "dynamodb:GetItem"]
    resources = [aws_dynamodb_table.tflock.arn]
  }
  statement {
    actions   = ["kms:Decrypt", "kms:DescribeKey"]
    resources = [aws_kms_key.tfstate.arn]
  }
}

# ----------------------------------------------------------------------------
# 3. gha-terraform-apply-{env}: apply 단계 (환경별)
# ----------------------------------------------------------------------------

data "aws_iam_policy_document" "gha_tf_apply_trust" {
  for_each = toset(var.environments)

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:sub"
      values   = ["repo:${local.github_repo_full}:environment:tf-${each.value}"]
    }
  }
}

resource "aws_iam_role" "gha_terraform_apply" {
  for_each = toset(var.environments)

  name                 = "${var.project}-gha-terraform-apply-${each.value}"
  assume_role_policy   = data.aws_iam_policy_document.gha_tf_apply_trust[each.value].json
  max_session_duration = 3600
}

# Terraform apply 는 광범위 권한이 필요. PowerUserAccess + IAM 한정 권한 + tfstate RW.
# 더 강한 격리 필요 시 Permission Boundary 부착 (variables.permissions_boundary_managed).
resource "aws_iam_role_policy_attachment" "gha_terraform_apply_power" {
  for_each = toset(var.environments)

  role       = aws_iam_role.gha_terraform_apply[each.value].name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

resource "aws_iam_role_policy" "gha_terraform_apply_iam" {
  for_each = toset(var.environments)

  name   = "iam-management"
  role   = aws_iam_role.gha_terraform_apply[each.value].id
  policy = data.aws_iam_policy_document.tf_apply_iam.json
}

data "aws_iam_policy_document" "tf_apply_iam" {
  statement {
    sid = "IamManagement"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:UpdateRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:PassRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:CreatePolicy",
      "iam:DeletePolicy",
      "iam:GetPolicy",
      "iam:ListPolicyVersions",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:GetInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:CreateOpenIDConnectProvider",
      "iam:DeleteOpenIDConnectProvider",
      "iam:GetOpenIDConnectProvider",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "gha_terraform_apply_state" {
  for_each = toset(var.environments)

  name   = "tfstate-rw"
  role   = aws_iam_role.gha_terraform_apply[each.value].id
  policy = data.aws_iam_policy_document.tfstate_rw.json
}

data "aws_iam_policy_document" "tfstate_rw" {
  statement {
    actions   = ["s3:ListBucket", "s3:GetBucketVersioning"]
    resources = [aws_s3_bucket.tfstate.arn]
  }
  statement {
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.tfstate.arn}/*"]
    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-server-side-encryption"
      values   = ["aws:kms"]
    }
  }
  statement {
    actions = [
      "dynamodb:DescribeTable",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:DeleteItem",
    ]
    resources = [aws_dynamodb_table.tflock.arn]
  }
  statement {
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]
    resources = [aws_kms_key.tfstate.arn]
  }
}

# ----------------------------------------------------------------------------
# 4. gha-security-scan: 주간 보안 스캔 (ECR describe·pull, Inspector findings)
# ----------------------------------------------------------------------------

data "aws_iam_policy_document" "gha_sec_scan_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "${local.oidc_provider_url}:sub"
      values   = ["repo:${local.github_repo_full}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "gha_security_scan" {
  name               = "${var.project}-gha-security-scan"
  assume_role_policy = data.aws_iam_policy_document.gha_sec_scan_trust.json
}

data "aws_iam_policy_document" "gha_sec_scan_inline" {
  statement {
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
      "ecr:DescribeImageScanFindings",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:ListImages",
      "inspector2:ListFindings",
      "inspector2:GetFinding",
      "inspector2:ListCoverage",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "gha_sec_scan_inline" {
  name   = "security-scan"
  role   = aws_iam_role.gha_security_scan.id
  policy = data.aws_iam_policy_document.gha_sec_scan_inline.json
}
