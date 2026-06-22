# Valkey (Redis OSS 호환, ElastiCache 신규 엔진) 클러스터
# - 04 §3 spec 구현
# - dev: 단일 노드 (replicas=0, multi_az=false)
# - beta/prod: 샤드 1 + 복제본 1, Multi-AZ

locals {
  name = coalesce(
    var.replication_group_id,
    "${var.project}-${var.environment}-cache",
  )

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "Terraform"
      Module      = "elasticache-valkey"
    },
    var.tags,
  )

  # AUTH 토큰 모드일 때만 토큰 자동 생성
  use_auth_token = var.auth_mode == "auth_token"

  # replicas=0 이면 자동 페일오버·Multi-AZ 의미 없음
  has_replicas       = var.replicas_per_node_group > 0
  effective_failover = var.automatic_failover_enabled && local.has_replicas
  effective_multi_az = var.multi_az_enabled && local.has_replicas
}

# ----------------------------------------------------------------------------
# Subnet Group
# ----------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "this" {
  name       = "${local.name}-subnet"
  subnet_ids = var.data_subnet_ids

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# Security Group
# ----------------------------------------------------------------------------

resource "aws_security_group" "this" {
  name        = "${local.name}-sg"
  description = "Valkey 6379 inbound from workload SGs"
  vpc_id      = var.vpc_id

  tags = merge(local.common_tags, {
    Name = "${local.name}-sg"
  })
}

resource "aws_security_group_rule" "ingress_6379" {
  for_each = toset(var.allowed_security_group_ids)

  type                     = "ingress"
  description              = "Valkey 6379 from ${each.value}"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = each.value
  security_group_id        = aws_security_group.this.id
}

# ----------------------------------------------------------------------------
# AUTH 토큰 (auth_mode=auth_token 인 경우만)
#   - 토큰을 Secrets Manager 에 저장하고 워크로드가 GetSecretValue
#   - 매트릭스 §4 — `tech-n-ai/{env}/elasticache-auth-token`
# ----------------------------------------------------------------------------

resource "random_password" "auth_token" {
  count = local.use_auth_token ? 1 : 0

  length  = 64
  special = false # ElastiCache AUTH 토큰은 영숫자만
  upper   = true
  lower   = true
  numeric = true
}

# Secrets Manager 시크릿은 매트릭스 정의처가 06 이라 본 모듈은 토큰만 생성하고,
# 시크릿 저장(GetSecretValue 대상)은 envs/* 에서 명시적으로 한다.

# ----------------------------------------------------------------------------
# Replication Group
# ----------------------------------------------------------------------------

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = local.name
  description          = "Valkey replication group for ${local.name}"

  engine         = "valkey"
  engine_version = var.engine_version

  node_type                  = var.node_type
  num_node_groups            = var.num_node_groups
  replicas_per_node_group    = var.replicas_per_node_group
  automatic_failover_enabled = local.effective_failover
  multi_az_enabled           = local.effective_multi_az

  port = 6379

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.this.id]

  at_rest_encryption_enabled = var.at_rest_encryption_enabled
  kms_key_id                 = var.at_rest_encryption_enabled ? var.kms_key_arn : null
  transit_encryption_enabled = var.transit_encryption_enabled

  # 인증 — auth_token 또는 RBAC (User Group)
  auth_token     = local.use_auth_token ? random_password.auth_token[0].result : null
  user_group_ids = var.auth_mode == "rbac" ? var.rbac_user_group_ids : null

  snapshot_retention_limit = var.snapshot_retention_limit
  snapshot_window          = var.snapshot_retention_limit > 0 ? var.snapshot_window : null
  maintenance_window       = var.maintenance_window

  apply_immediately = var.environment != "prod"

  tags = local.common_tags

  lifecycle {
    ignore_changes = [
      auth_token, # 토큰 회전은 별도 워크플로
    ]
  }
}
