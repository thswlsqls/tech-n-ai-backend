# MSK Provisioned (prod 용) — Kafka 3.9.x KRaft
# - 05 §1.2 spec 구현
# - IAM SASL + TLS 클라이언트 인증
# - Open Monitoring (Prometheus JMX/Node Exporter)
# - CloudWatch Logs 로 브로커 로그

locals {
  name = coalesce(
    var.cluster_name,
    "${var.project}-${var.environment}-msk",
  )

  log_group_name = coalesce(
    var.log_group_name,
    "/aws/msk/${local.name}",
  )

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "Terraform"
      Module      = "msk-provisioned"
    },
    var.tags,
  )
}

# ----------------------------------------------------------------------------
# CloudWatch Log Group (브로커 로그)
# ----------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "broker" {
  name              = local.log_group_name
  retention_in_days = var.log_retention_days

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# Security Group
# ----------------------------------------------------------------------------

resource "aws_security_group" "this" {
  name        = "${local.name}-sg"
  description = "MSK Provisioned 9098 (IAM SASL) and 9094 (TLS) inbound from workload SGs"
  vpc_id      = var.vpc_id

  tags = merge(local.common_tags, {
    Name = "${local.name}-sg"
  })
}

resource "aws_security_group_rule" "ingress_iam_sasl" {
  for_each = toset(var.allowed_security_group_ids)

  type                     = "ingress"
  description              = "Kafka IAM SASL 9098 from ${each.value}"
  from_port                = 9098
  to_port                  = 9098
  protocol                 = "tcp"
  source_security_group_id = each.value
  security_group_id        = aws_security_group.this.id
}

resource "aws_security_group_rule" "ingress_tls" {
  for_each = toset(var.allowed_security_group_ids)

  type                     = "ingress"
  description              = "Kafka TLS 9094 from ${each.value}"
  from_port                = 9094
  to_port                  = 9094
  protocol                 = "tcp"
  source_security_group_id = each.value
  security_group_id        = aws_security_group.this.id
}

# Broker → Broker (클러스터 내부)
resource "aws_security_group_rule" "intra_cluster" {
  type              = "ingress"
  description       = "Broker-to-Broker (intra-cluster replication)"
  from_port         = 0
  to_port           = 65535
  protocol          = "tcp"
  self              = true
  security_group_id = aws_security_group.this.id
}

# Open Monitoring 포트 (Prometheus 스크레이프)
resource "aws_security_group_rule" "open_monitoring_jmx" {
  count = var.enable_open_monitoring ? length(var.allowed_security_group_ids) : 0

  type                     = "ingress"
  description              = "JMX Exporter 11001 from ${var.allowed_security_group_ids[count.index]}"
  from_port                = 11001
  to_port                  = 11002
  protocol                 = "tcp"
  source_security_group_id = var.allowed_security_group_ids[count.index]
  security_group_id        = aws_security_group.this.id
}

# ----------------------------------------------------------------------------
# Configuration — KRaft 친화 + 베스트 프랙티스
# ----------------------------------------------------------------------------

resource "aws_msk_configuration" "this" {
  name           = "${local.name}-config"
  kafka_versions = [var.kafka_version]

  server_properties = <<-PROPERTIES
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=6
    log.retention.hours=168
    log.segment.bytes=1073741824
    delete.topic.enable=true
    unclean.leader.election.enable=false
    num.replica.fetchers=4
    transaction.state.log.replication.factor=3
    transaction.state.log.min.isr=2
    offsets.topic.replication.factor=3
  PROPERTIES

  lifecycle {
    create_before_destroy = true
  }
}

# ----------------------------------------------------------------------------
# MSK Cluster
# ----------------------------------------------------------------------------

resource "aws_msk_cluster" "this" {
  cluster_name           = local.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.private_subnet_ids
    security_groups = [aws_security_group.this.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.ebs_volume_size

        dynamic "provisioned_throughput" {
          for_each = [] # gp3 기본값으로 충분 — 필요 시 enabled=true + volume_throughput
          content {
            enabled           = true
            volume_throughput = 250
          }
        }
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  encryption_info {
    encryption_at_rest_kms_key_arn = var.kms_key_arn

    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  client_authentication {
    sasl {
      iam = true
    }
    tls {}
  }

  enhanced_monitoring = var.enhanced_monitoring

  dynamic "open_monitoring" {
    for_each = var.enable_open_monitoring ? [1] : []
    content {
      prometheus {
        jmx_exporter {
          enabled_in_broker = true
        }
        node_exporter {
          enabled_in_broker = true
        }
      }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.broker.name
      }
    }
  }

  tags = local.common_tags

  lifecycle {
    ignore_changes = [
      # AWS 가 자동 패치하는 마이너 버전 무시
      kafka_version,
    ]
  }
}

# ----------------------------------------------------------------------------
# Storage Auto Scaling (옵션)
# ----------------------------------------------------------------------------

resource "aws_appautoscaling_target" "storage" {
  count = var.enable_storage_autoscaling ? 1 : 0

  max_capacity       = var.storage_autoscaling_max_size
  min_capacity       = 1
  resource_id        = aws_msk_cluster.this.arn
  scalable_dimension = "kafka:broker-storage:VolumeSize"
  service_namespace  = "kafka"
}

resource "aws_appautoscaling_policy" "storage" {
  count = var.enable_storage_autoscaling ? 1 : 0

  name               = "${local.name}-storage-autoscaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.storage[0].resource_id
  scalable_dimension = aws_appautoscaling_target.storage[0].scalable_dimension
  service_namespace  = aws_appautoscaling_target.storage[0].service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "KafkaBrokerStorageUtilization"
    }
    target_value = var.storage_autoscaling_target_utilization
  }
}
