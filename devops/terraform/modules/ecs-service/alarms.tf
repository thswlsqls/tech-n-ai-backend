# 자동 롤백 알람 — ALB 5xx 비율 + p95 latency
# - aws_ecs_service.this 의 alarms 블록에 연결돼 ALARM 이 되면 ECS 가 배포를 되돌린다
# - blue/green 배포 중에는 트래픽이 green 대상 그룹으로 넘어가므로 두 대상 그룹을 함께 본다

locals {
  # 알람의 CloudWatch LoadBalancer 디멘션 값(app/<alb-name>/<lb-id>) — listener ARN 에서 추출
  load_balancer_dimension = join("/", slice(split("/", var.alb_listener_arn), 1, 4))

  # 알람이 보는 대상 그룹 — metric_query id 접미사 => arn_suffix
  alarm_target_groups = {
    blue  = aws_lb_target_group.blue.arn_suffix
    green = aws_lb_target_group.green.arn_suffix
  }
}

# 두 대상 그룹의 5xx 합계 / 요청 합계. 데이터가 없는 대상 그룹은 FILL 로 0 처리.
# 요청이 전혀 없으면 0 으로 나누게 되어 데이터 포인트가 빠지고 notBreaching 으로 본다.
resource "aws_cloudwatch_metric_alarm" "alb_5xx_rate" {
  alarm_name          = "${local.name}-alb-5xx-rate"
  alarm_description   = "${var.service_name} ALB 5xx 비율 ${var.rollback_alarm_5xx_threshold}% 초과"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  threshold           = var.rollback_alarm_5xx_threshold
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "rate"
    expression  = "100 * (FILL(m_5xx_blue, 0) + FILL(m_5xx_green, 0)) / (FILL(m_total_blue, 0) + FILL(m_total_green, 0))"
    label       = "5xx 비율(%)"
    return_data = true
  }

  dynamic "metric_query" {
    for_each = local.alarm_target_groups
    content {
      id = "m_5xx_${metric_query.key}"
      metric {
        namespace   = "AWS/ApplicationELB"
        metric_name = "HTTPCode_Target_5XX_Count"
        period      = 60
        stat        = "Sum"
        dimensions = {
          TargetGroup  = metric_query.value
          LoadBalancer = local.load_balancer_dimension
        }
      }
    }
  }

  dynamic "metric_query" {
    for_each = local.alarm_target_groups
    content {
      id = "m_total_${metric_query.key}"
      metric {
        namespace   = "AWS/ApplicationELB"
        metric_name = "RequestCount"
        period      = 60
        stat        = "Sum"
        dimensions = {
          TargetGroup  = metric_query.value
          LoadBalancer = local.load_balancer_dimension
        }
      }
    }
  }

  tags = local.common_tags
}

# 대상 그룹별 p95 중 큰 값
resource "aws_cloudwatch_metric_alarm" "target_response_time" {
  alarm_name          = "${local.name}-latency-p95"
  alarm_description   = "${var.service_name} Target Response Time p95 ${var.rollback_alarm_latency_p95_seconds}s 초과"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  threshold           = var.rollback_alarm_latency_p95_seconds
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "p95_max"
    expression  = "MAX([p95_blue, p95_green])"
    label       = "p95 응답 시간(초)"
    return_data = true
  }

  dynamic "metric_query" {
    for_each = local.alarm_target_groups
    content {
      id = "p95_${metric_query.key}"
      metric {
        namespace   = "AWS/ApplicationELB"
        metric_name = "TargetResponseTime"
        period      = 60
        stat        = "p95"
        dimensions = {
          TargetGroup  = metric_query.value
          LoadBalancer = local.load_balancer_dimension
        }
      }
    }
  }

  tags = local.common_tags
}
