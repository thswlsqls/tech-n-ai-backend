# CodeDeploy Application + Deployment Group
# - Hook 람다 미사용 (D-2)
# - 자동 롤백: 배포 실패 + 알람 트리거 (5xx, latency)

locals {
  # 알람의 CloudWatch LoadBalancer 디멘션 값 — listener ARN 에서 추출
  load_balancer_dimension = split("/", var.alb_listener_arn)[1]
}

resource "aws_codedeploy_app" "this" {
  count = var.enable_blue_green ? 1 : 0

  name             = local.name
  compute_platform = "ECS"

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# 자동 롤백 알람 — ALB 5xx 비율 + p95 latency
# ----------------------------------------------------------------------------

resource "aws_cloudwatch_metric_alarm" "alb_5xx_rate" {
  count = var.enable_blue_green ? 1 : 0

  alarm_name          = "${local.name}-alb-5xx-rate"
  alarm_description   = "${var.service_name} ALB 5xx 비율 ${var.rollback_alarm_5xx_threshold}% 초과"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  threshold           = var.rollback_alarm_5xx_threshold
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "rate"
    expression  = "100 * (m_5xx / m_total)"
    label       = "5xx 비율(%)"
    return_data = true
  }

  metric_query {
    id = "m_5xx"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "HTTPCode_Target_5XX_Count"
      period      = 60
      stat        = "Sum"
      dimensions = {
        TargetGroup  = aws_lb_target_group.blue.arn_suffix
        LoadBalancer = local.load_balancer_dimension
      }
    }
  }

  metric_query {
    id = "m_total"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "RequestCount"
      period      = 60
      stat        = "Sum"
      dimensions = {
        TargetGroup  = aws_lb_target_group.blue.arn_suffix
        LoadBalancer = local.load_balancer_dimension
      }
    }
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "target_response_time" {
  count = var.enable_blue_green ? 1 : 0

  alarm_name          = "${local.name}-latency-p95"
  alarm_description   = "${var.service_name} Target Response Time p95 ${var.rollback_alarm_latency_p95_seconds}s 초과"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  threshold           = var.rollback_alarm_latency_p95_seconds
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  extended_statistic  = "p95"
  treat_missing_data  = "notBreaching"

  dimensions = {
    TargetGroup  = aws_lb_target_group.blue.arn_suffix
    LoadBalancer = local.load_balancer_dimension
  }

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# Deployment Group
# ----------------------------------------------------------------------------

# CodeDeploy 서비스 Role (모듈 자체에서 만들지 않고 envs 에서 공유 Role 을 받는 게 정석.
# 다만 모듈 자족성 위해 자체 생성. 변경 시 외부 주입으로 전환 가능.)
resource "aws_iam_role" "codedeploy" {
  count = var.enable_blue_green ? 1 : 0

  name = "${local.name}-codedeploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "codedeploy.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "codedeploy_ecs" {
  count = var.enable_blue_green ? 1 : 0

  role       = aws_iam_role.codedeploy[0].name
  policy_arn = "arn:aws:iam::aws:policy/AWSCodeDeployRoleForECS"
}

resource "aws_codedeploy_deployment_group" "this" {
  count = var.enable_blue_green ? 1 : 0

  app_name               = aws_codedeploy_app.this[0].name
  deployment_group_name  = "${local.name}-bg"
  service_role_arn       = aws_iam_role.codedeploy[0].arn
  deployment_config_name = var.deployment_config_name

  deployment_style {
    deployment_type   = "BLUE_GREEN"
    deployment_option = "WITH_TRAFFIC_CONTROL"
  }

  ecs_service {
    cluster_name = local.ecs_cluster_name
    service_name = aws_ecs_service.this.name
  }

  load_balancer_info {
    target_group_pair_info {
      prod_traffic_route {
        listener_arns = [var.alb_listener_arn]
      }
      target_group {
        name = aws_lb_target_group.blue.name
      }
      target_group {
        name = aws_lb_target_group.green.name
      }
    }
  }

  blue_green_deployment_config {
    deployment_ready_option {
      action_on_timeout = "CONTINUE_DEPLOYMENT"
    }

    terminate_blue_instances_on_deployment_success {
      action                           = "TERMINATE"
      termination_wait_time_in_minutes = 5
    }
  }

  auto_rollback_configuration {
    enabled = true
    events  = ["DEPLOYMENT_FAILURE", "DEPLOYMENT_STOP_ON_ALARM"]
  }

  alarm_configuration {
    enabled = true
    alarms = [
      aws_cloudwatch_metric_alarm.alb_5xx_rate[0].alarm_name,
      aws_cloudwatch_metric_alarm.target_response_time[0].alarm_name,
    ]
  }

  tags = local.common_tags
}
