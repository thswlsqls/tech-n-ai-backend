# Observability — Log Groups + 표준 Alarms + Dashboard
# - 08 spec 의 일부 구현 (ADOT Collector / Fluent Bit YAML 은 세션 4 에서)
# - ECS 모듈이 자체 Log Group 을 만들면 본 모듈은 추가 Log Group 만 만들면 됨

locals {
  name = "${var.project}-${var.environment}-observability"

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "Terraform"
      Module      = "observability"
    },
    var.tags,
  )

  # SNS 토픽이 없으면 알람만 만들고 알림은 연결하지 않음
  alarm_actions = var.alarm_sns_topic_arn == null ? [] : [var.alarm_sns_topic_arn]
}

# ----------------------------------------------------------------------------
# Log Groups (옵션)
# ----------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "extra" {
  for_each = { for lg in var.log_groups : lg.name => lg }

  name              = each.value.name
  retention_in_days = each.value.retention_days
  kms_key_id        = var.log_kms_key_arn

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# 서비스별 표준 알람
#   - CPU 사용률 초과
#   - 메모리 사용률 초과
#   - RunningTaskCount < min_running_count
# ----------------------------------------------------------------------------

resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  for_each = { for s in var.service_alarms : "${s.cluster_name}/${s.service_name}" => s }

  alarm_name          = "${var.project}-${var.environment}-${each.value.service_name}-cpu-high"
  alarm_description   = "${each.value.service_name} CPU > ${each.value.cpu_threshold}% (5분)"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  threshold           = each.value.cpu_threshold
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"

  dimensions = {
    ClusterName = each.value.cluster_name
    ServiceName = each.value.service_name
  }

  alarm_actions = local.alarm_actions
  ok_actions    = local.alarm_actions

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "ecs_memory" {
  for_each = { for s in var.service_alarms : "${s.cluster_name}/${s.service_name}" => s }

  alarm_name          = "${var.project}-${var.environment}-${each.value.service_name}-mem-high"
  alarm_description   = "${each.value.service_name} 메모리 > ${each.value.memory_threshold}% (5분)"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  threshold           = each.value.memory_threshold
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"

  dimensions = {
    ClusterName = each.value.cluster_name
    ServiceName = each.value.service_name
  }

  alarm_actions = local.alarm_actions

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "ecs_running_count" {
  for_each = { for s in var.service_alarms : "${s.cluster_name}/${s.service_name}" => s }

  alarm_name          = "${var.project}-${var.environment}-${each.value.service_name}-running-low"
  alarm_description   = "${each.value.service_name} 실행 태스크 < ${each.value.min_running_count} (3분)"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 3
  threshold           = each.value.min_running_count
  metric_name         = "RunningTaskCount"
  namespace           = "ECS/ContainerInsights"
  period              = 60
  statistic           = "Average"

  dimensions = {
    ClusterName = each.value.cluster_name
    ServiceName = each.value.service_name
  }

  alarm_actions = local.alarm_actions

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# Overview Dashboard
# ----------------------------------------------------------------------------

resource "aws_cloudwatch_dashboard" "overview" {
  count = var.dashboard_enabled ? 1 : 0

  dashboard_name = "${local.name}-overview"

  dashboard_body = jsonencode({
    widgets = concat(
      [
        {
          type   = "text"
          x      = 0
          y      = 0
          width  = 24
          height = 2
          properties = {
            markdown = "# ${var.project} ${var.environment} — Overview\n\n서비스별 CPU·메모리·태스크 수, ALB 5xx 비율, 데이터 계층 메트릭."
          }
        },
      ],
      # 서비스별 CPU 라인
      [
        for idx, s in var.service_alarms : {
          type   = "metric"
          x      = (idx % 3) * 8
          y      = 2 + floor(idx / 3) * 6
          width  = 8
          height = 6
          properties = {
            view    = "timeSeries"
            stacked = false
            region  = data.aws_region.current.name
            title   = "${s.service_name} CPU%"
            metrics = [
              ["AWS/ECS", "CPUUtilization", "ClusterName", s.cluster_name, "ServiceName", s.service_name, { stat = "Average" }]
            ]
            period = 60
          }
        }
      ],
    )
  })
}

data "aws_region" "current" {}
