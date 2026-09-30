# 자동 롤백 알람 — ALB 5xx 비율 + p95 latency
# - aws_ecs_service.this 의 alarms 블록에 연결돼 ALARM 이 되면 ECS 가 배포를 되돌린다
# - blue/green 배포 중에는 트래픽이 green 대상 그룹으로 넘어가므로 두 대상 그룹을 함께 본다

locals {
  # 알람의 CloudWatch LoadBalancer 디멘션 값(app/<alb-name>/<lb-id>) — listener ARN 에서 추출
  #   예) arn:aws:elasticloadbalancing:ap-northeast-2:123456789012:listener/app/techai-dev-alb/50dc6c495c0c9188/f2f7dc8efc522ab2
  #    → app/techai-dev-alb/50dc6c495c0c9188
  #   디멘션 형식: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-cloudwatch-metrics.html
  #   listener ARN 형식: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/alb-resources-for-blue-green.html (create-rule 예시)
  load_balancer_dimension = join("/", slice(split("/", var.alb_listener_arn), 1, 4))

  # 알람이 보는 대상 그룹 — metric_query id 접미사 => arn_suffix
  alarm_target_groups = {
    blue  = aws_lb_target_group.blue.arn_suffix
    green = aws_lb_target_group.green.arn_suffix
  }
}

# 대상 그룹별 5xx 비율 중 큰 값.
# 두 대상 그룹을 합쳐 비율을 내면 카나리 구간(새 버전이 10% 만 받음)에서 새 버전의 오류가
# 1/10 로 희석된다. 새 버전이 어느 대상 그룹에 붙는지는 배포마다 바뀌므로 green 하나만 보지 않고
# 대상 그룹마다 따로 비율을 낸다. 요청이 없는 대상 그룹은 0 으로 나누게 되어 데이터 포인트가 빠진다.
resource "aws_cloudwatch_metric_alarm" "alb_5xx_rate" {
  alarm_name          = "${local.name}-alb-5xx-rate"
  alarm_description   = "${var.service_name} ALB 5xx 비율 ${var.rollback_alarm_5xx_threshold}% 초과"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  threshold           = var.rollback_alarm_5xx_threshold
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "rate_max"
    expression  = "MAX([rate_blue, rate_green])"
    label       = "5xx 비율(%)"
    return_data = true
  }

  dynamic "metric_query" {
    for_each = local.alarm_target_groups
    content {
      id          = "rate_${metric_query.key}"
      expression  = "100 * FILL(m_5xx_${metric_query.key}, 0) / m_total_${metric_query.key}"
      return_data = false
    }
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
