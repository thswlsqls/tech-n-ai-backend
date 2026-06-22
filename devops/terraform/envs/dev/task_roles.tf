# ECS Task Role 6개 — 매트릭스 §2.2 정의 따름
# - api-chatbot 은 Bedrock 권한 미부여 (D-12 — 코드 미도입, 별도 ADR 시 추가)
# - api-auth 는 RDS IAM 인증 (cluster_resource_id 사용)

locals {
  task_trust_conditions = [
    {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  ]
}

# ----------------------------------------------------------------------------
# api-gateway — SSM Parameter Store read 만
# ----------------------------------------------------------------------------

module "task_role_api_gateway" {
  source = "../../modules/iam-role-workload"

  project          = var.project
  environment      = var.environment
  workload_name    = "api-gateway"
  trust_service    = "ecs-tasks.amazonaws.com"
  trust_conditions = local.task_trust_conditions

  inline_policies = {
    ssm-read = data.aws_iam_policy_document.ssm_read.json
  }
}

data "aws_iam_policy_document" "ssm_read" {
  statement {
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/${var.environment}/*"]
  }
}

# ----------------------------------------------------------------------------
# api-auth — Aurora·JWT secrets, RDS IAM connect, KMS Decrypt(auth)
# ----------------------------------------------------------------------------

module "task_role_api_auth" {
  source = "../../modules/iam-role-workload"

  project          = var.project
  environment      = var.environment
  workload_name    = "api-auth"
  trust_service    = "ecs-tasks.amazonaws.com"
  trust_conditions = local.task_trust_conditions

  inline_policies = {
    secrets-and-rds = data.aws_iam_policy_document.api_auth_secrets.json
  }
}

data "aws_iam_policy_document" "api_auth_secrets" {
  # Aurora master password (Managed) + JWT signing key (active/next 듀얼)
  statement {
    sid = "ReadSecrets"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      try(module.aurora[0].master_user_secret_arn, "*"),
      "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/jwt-signing-key-*",
    ]
  }

  # RDS IAM 인증 — DB user 'api_auth' 로 연결
  statement {
    sid = "RdsIamConnect"
    actions = [
      "rds-db:connect",
    ]
    resources = [
      "arn:aws:rds-db:${var.region}:${data.aws_caller_identity.current.account_id}:dbuser:${try(module.aurora[0].cluster_resource_id, "*")}/api_auth",
    ]
  }

  # KMS — auth 키로 시크릿 복호화
  statement {
    sid = "DecryptAuthKms"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
    ]
    resources = [aws_kms_key.auth.arn, aws_kms_key.data.arn]
  }
}

# ----------------------------------------------------------------------------
# api-chatbot — OpenAI/Cohere secrets, KMS(ai). Bedrock 권한 미부여 (D-12).
# ----------------------------------------------------------------------------

module "task_role_api_chatbot" {
  source = "../../modules/iam-role-workload"

  project          = var.project
  environment      = var.environment
  workload_name    = "api-chatbot"
  trust_service    = "ecs-tasks.amazonaws.com"
  trust_conditions = local.task_trust_conditions

  inline_policies = {
    ai-secrets = data.aws_iam_policy_document.api_chatbot_secrets.json
  }
}

data "aws_iam_policy_document" "api_chatbot_secrets" {
  statement {
    sid = "ReadAiSecrets"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/openai-api-key-*",
      "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/mongodb-uri-*",
    ]
  }

  statement {
    sid = "DecryptAiKms"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
    ]
    resources = [aws_kms_key.ai.arn]
  }
}

# ----------------------------------------------------------------------------
# api-agent — MSK kafka-cluster, MongoDB secret
#   MSK 권한은 enable_msk=true 일 때만 의미. Resource ARN 은 클러스터 미생성 시 wildcard 임시.
# ----------------------------------------------------------------------------

module "task_role_api_agent" {
  source = "../../modules/iam-role-workload"

  project          = var.project
  environment      = var.environment
  workload_name    = "api-agent"
  trust_service    = "ecs-tasks.amazonaws.com"
  trust_conditions = local.task_trust_conditions

  inline_policies = {
    msk-and-mongo = data.aws_iam_policy_document.api_agent_perms.json
  }
}

data "aws_iam_policy_document" "api_agent_perms" {
  statement {
    sid = "MskClusterIam"
    actions = [
      "kafka-cluster:Connect",
      "kafka-cluster:DescribeCluster",
      "kafka-cluster:DescribeTopic",
      "kafka-cluster:DescribeGroup",
      "kafka-cluster:AlterGroup",
      "kafka-cluster:ReadData",
      "kafka-cluster:WriteData",
    ]
    # MSK Serverless ARN 은 동적. 미활성 시 wildcard.
    resources = compact([
      try(module.msk[0].cluster_arn, ""),
      try("${module.msk[0].cluster_arn}/topic/${var.project}.conversation.*", ""),
      try("${module.msk[0].cluster_arn}/group/${var.project}.*", ""),
    ])
    # 비활성 시 빈 리스트라 정책 statement 가 비어 — IAM 거부. 미활성 시 dummy 한 statement 둠.
  }

  statement {
    sid = "ReadMongoSecret"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/mongodb-uri-*",
    ]
  }
}

# ----------------------------------------------------------------------------
# api-bookmark — RDS IAM, ElastiCache RBAC token (또는 AUTH 토큰)
# ----------------------------------------------------------------------------

module "task_role_api_bookmark" {
  source = "../../modules/iam-role-workload"

  project          = var.project
  environment      = var.environment
  workload_name    = "api-bookmark"
  trust_service    = "ecs-tasks.amazonaws.com"
  trust_conditions = local.task_trust_conditions

  inline_policies = {
    rds-and-cache = data.aws_iam_policy_document.api_bookmark_perms.json
  }
}

data "aws_iam_policy_document" "api_bookmark_perms" {
  statement {
    sid     = "RdsIamConnect"
    actions = ["rds-db:connect"]
    resources = [
      "arn:aws:rds-db:${var.region}:${data.aws_caller_identity.current.account_id}:dbuser:${try(module.aurora[0].cluster_resource_id, "*")}/api_bookmark",
    ]
  }

  statement {
    sid = "ReadAuthTokenSecret"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/elasticache-auth-token-*",
    ]
  }

  statement {
    sid       = "DecryptDataKms"
    actions   = ["kms:Decrypt"]
    resources = [aws_kms_key.data.arn]
  }
}

# ----------------------------------------------------------------------------
# api-emerging-tech — OpenAI 키만 read
# ----------------------------------------------------------------------------

module "task_role_api_emerging_tech" {
  source = "../../modules/iam-role-workload"

  project          = var.project
  environment      = var.environment
  workload_name    = "api-emerging-tech"
  trust_service    = "ecs-tasks.amazonaws.com"
  trust_conditions = local.task_trust_conditions

  inline_policies = {
    openai-secret = data.aws_iam_policy_document.api_et_secrets.json
  }
}

data "aws_iam_policy_document" "api_et_secrets" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/openai-api-key-*",
    ]
  }
  statement {
    actions   = ["kms:Decrypt"]
    resources = [aws_kms_key.ai.arn]
  }
}
