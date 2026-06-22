# Aurora MySQL 클러스터 + 인스턴스
# - engine_mode 에 따라 Serverless v2 와 Provisioned 분기
# - Managed Master User Password (AWS 가 Secrets Manager 시크릿 자동 생성·회전)
# - IAM DB Authentication 활성화 (api-auth Task Role 이 토큰 발급)

locals {
  cluster_name = coalesce(
    var.cluster_identifier,
    "${var.project}-${var.environment}-aurora-core",
  )

  is_serverless = var.engine_mode == "serverlessv2"
  is_prod       = var.environment == "prod"

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "Terraform"
      Module      = "aurora-mysql"
    },
    var.tags,
  )
}

# ----------------------------------------------------------------------------
# DB Subnet Group
# ----------------------------------------------------------------------------

resource "aws_db_subnet_group" "this" {
  name       = "${local.cluster_name}-subnet"
  subnet_ids = var.data_subnet_ids

  tags = merge(local.common_tags, {
    Name = "${local.cluster_name}-subnet"
  })
}

# ----------------------------------------------------------------------------
# Security Group — Aurora 3306, 워크로드 SG 인바운드
# ----------------------------------------------------------------------------

resource "aws_security_group" "this" {
  name        = "${local.cluster_name}-sg"
  description = "Aurora MySQL 3306 inbound from workload SGs"
  vpc_id      = var.vpc_id

  tags = merge(local.common_tags, {
    Name = "${local.cluster_name}-sg"
  })
}

resource "aws_security_group_rule" "ingress_3306" {
  for_each = toset(var.allowed_security_group_ids)

  type                     = "ingress"
  description              = "MySQL 3306 from ${each.value}"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = each.value
  security_group_id        = aws_security_group.this.id
}

# ----------------------------------------------------------------------------
# Aurora 클러스터
# ----------------------------------------------------------------------------

resource "aws_rds_cluster" "this" {
  cluster_identifier = local.cluster_name

  engine         = "aurora-mysql"
  engine_version = var.engine_version
  engine_mode    = "provisioned" # serverlessv2 도 engine_mode=provisioned + serverlessv2_scaling_configuration

  database_name   = var.db_name
  master_username = var.master_username

  # 비밀번호는 Managed Master User Password 로 위임 — AWS 가 Secrets Manager 시크릿 자동 생성
  manage_master_user_password   = true
  master_user_secret_kms_key_id = var.kms_key_arn

  vpc_security_group_ids = [aws_security_group.this.id]
  db_subnet_group_name   = aws_db_subnet_group.this.name

  storage_encrypted = true
  kms_key_id        = var.kms_key_arn
  storage_type      = var.storage_type

  iam_database_authentication_enabled = var.iam_database_authentication_enabled

  backup_retention_period      = var.backup_retention_period
  preferred_backup_window      = var.preferred_backup_window
  preferred_maintenance_window = var.preferred_maintenance_window

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${local.cluster_name}-final-${formatdate("YYYYMMDDhhmm", timestamp())}"

  enabled_cloudwatch_logs_exports = ["error", "slowquery", "audit"]

  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.this.name

  # Serverless v2 스케일링 (engine_mode=serverlessv2 인 경우만 의미 있음)
  dynamic "serverlessv2_scaling_configuration" {
    for_each = local.is_serverless ? [1] : []
    content {
      min_capacity = var.serverlessv2_min_capacity
      max_capacity = var.serverlessv2_max_capacity
    }
  }

  apply_immediately = !local.is_prod

  tags = local.common_tags

  lifecycle {
    ignore_changes = [
      # final_snapshot_identifier 의 timestamp 가 매 plan 마다 변경되어 노이즈 — 무시
      final_snapshot_identifier,
      # AWS 가 자동 패치하는 마이너 버전 변경 무시
      engine_version,
    ]
  }
}

# ----------------------------------------------------------------------------
# 인스턴스 — Provisioned 모드는 instance_count 만큼, Serverless v2 는 1개 + ACU 스케일링
# ----------------------------------------------------------------------------

resource "aws_rds_cluster_instance" "this" {
  count = local.is_serverless ? 1 : var.instance_count

  identifier         = "${local.cluster_name}-${count.index + 1}"
  cluster_identifier = aws_rds_cluster.this.id

  engine         = aws_rds_cluster.this.engine
  engine_version = aws_rds_cluster.this.engine_version

  # serverlessv2 모드는 db.serverless 인스턴스 클래스 강제
  instance_class = local.is_serverless ? "db.serverless" : var.instance_class

  db_subnet_group_name = aws_db_subnet_group.this.name

  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_retention_period = var.performance_insights_enabled ? var.performance_insights_retention_period : null
  performance_insights_kms_key_id       = var.performance_insights_enabled ? var.kms_key_arn : null

  monitoring_interval = local.is_prod ? 60 : 0

  auto_minor_version_upgrade = true
  apply_immediately          = !local.is_prod

  tags = merge(local.common_tags, {
    Name = "${local.cluster_name}-${count.index + 1}"
    Role = count.index == 0 ? "writer" : "reader"
  })
}
