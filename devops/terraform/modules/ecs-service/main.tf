# ECS Fargate Service + Target Group(blue/green) + CodeDeploy + 워크로드 SG
# - 03 §2 + 09 §5.2 spec 구현
# - 단일 ECR 리포 전제 (D-1) — container_image 는 digest 형태 권장
# - CodeDeploy Hook 미사용 (D-2) — ALB readiness + CW 알람 자동 롤백

locals {
  name = "${var.project}-${var.environment}-${var.service_name}"

  # cluster ARN(arn:...:cluster/{name})에서 클러스터 이름만 추출
  ecs_cluster_name = split("/", var.cluster_arn)[1]

  log_group_name = coalesce(
    var.log_group_name,
    "/aws/ecs/${var.environment}/${var.service_name}",
  )

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "Terraform"
      Module      = "ecs-service"
      Service     = var.service_name
    },
    var.tags,
  )
}

# ----------------------------------------------------------------------------
# CloudWatch Log Group (자동 생성 모드)
# ----------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "this" {
  count = var.log_group_name == null ? 1 : 0

  name              = local.log_group_name
  retention_in_days = var.log_retention_days
  kms_key_id        = var.log_kms_key_arn

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# Security Group — 워크로드 SG
#   - 인바운드: ALB SG (8081~8086), 다른 워크로드 SG 는 envs 에서 cross-reference 추가
#   - 아웃바운드: 모두 허용 (NAT 경유 인터넷, VPCE, 데이터 SG)
# ----------------------------------------------------------------------------

resource "aws_security_group" "this" {
  name        = "${local.name}-sg"
  description = "Workload SG for ${var.service_name}"
  vpc_id      = var.vpc_id

  egress {
    description = "All outbound (NAT, VPCE, data SG)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.name}-sg"
  })
}

resource "aws_security_group_rule" "ingress_from_alb" {
  type                     = "ingress"
  description              = "${var.service_name} ${var.container_port} from ALB"
  from_port                = var.container_port
  to_port                  = var.container_port
  protocol                 = "tcp"
  source_security_group_id = var.alb_security_group_id
  security_group_id        = aws_security_group.this.id
}

# ----------------------------------------------------------------------------
# Target Group — Blue / Green
# ----------------------------------------------------------------------------

resource "aws_lb_target_group" "blue" {
  name                 = "${local.name}-blue"
  port                 = var.container_port
  protocol             = "HTTP"
  vpc_id               = var.vpc_id
  target_type          = "ip"
  deregistration_delay = var.deregistration_delay

  health_check {
    enabled             = true
    path                = var.health_check_path
    protocol            = "HTTP"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
  }

  tags = merge(local.common_tags, {
    Name      = "${local.name}-blue"
    BlueGreen = "blue"
  })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb_target_group" "green" {
  name                 = "${local.name}-green"
  port                 = var.container_port
  protocol             = "HTTP"
  vpc_id               = var.vpc_id
  target_type          = "ip"
  deregistration_delay = var.deregistration_delay

  health_check {
    enabled             = true
    path                = var.health_check_path
    protocol            = "HTTP"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
  }

  tags = merge(local.common_tags, {
    Name      = "${local.name}-green"
    BlueGreen = "green"
  })

  lifecycle {
    create_before_destroy = true
  }
}

# ----------------------------------------------------------------------------
# Listener Rule — 외부 ALB 의 listener 에 본 서비스 라우팅 추가
# ----------------------------------------------------------------------------

resource "aws_lb_listener_rule" "this" {
  listener_arn = var.alb_listener_arn
  priority     = var.listener_rule_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.blue.arn
  }

  dynamic "condition" {
    for_each = length(var.listener_path_patterns) > 0 ? [1] : []
    content {
      path_pattern {
        values = var.listener_path_patterns
      }
    }
  }

  dynamic "condition" {
    for_each = length(var.listener_host_headers) > 0 ? [1] : []
    content {
      host_header {
        values = var.listener_host_headers
      }
    }
  }

  # CodeDeploy 가 listener rule 의 forward target 을 blue↔green 으로 토글하므로 무시
  lifecycle {
    ignore_changes = [action]
  }

  tags = local.common_tags
}

data "aws_region" "current" {}

# ----------------------------------------------------------------------------
# Task Definition — 메인 컨테이너 + 옵션 sidecar (ADOT, FireLens)
# ----------------------------------------------------------------------------

locals {
  # 메인 컨테이너의 OTel 환경변수 — sidecar 활성 시 자동 주입
  otel_env = var.enable_otel_sidecar ? [
    { name = "OTEL_EXPORTER_OTLP_ENDPOINT", value = "http://localhost:4317" },
    { name = "OTEL_RESOURCE_ATTRIBUTES",    value = "service.name=${var.service_name},service.namespace=${var.project},deployment.environment=${var.environment}" },
  ] : []

  # logConfiguration — FireLens 활성 시 awsfirelens, 아니면 awslogs
  main_log_configuration = var.enable_firelens_sidecar ? {
    logDriver = "awsfirelens"
    options = {
      Name = "cloudwatch_logs"
    }
  } : {
    logDriver = "awslogs"
    options = {
      awslogs-group         = local.log_group_name
      awslogs-region        = data.aws_region.current.name
      awslogs-stream-prefix = var.service_name
    }
  }

  # dependsOn — sidecar 가 먼저 시작
  main_depends_on = concat(
    var.enable_firelens_sidecar ? [{ containerName = "log-router", condition = "START" }] : [],
    var.enable_otel_sidecar ? [{ containerName = "otel-collector", condition = "START" }] : [],
  )

  main_container = {
    name      = var.service_name
    image     = var.container_image
    essential = true

    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]

    environment = concat(var.environment_vars, local.otel_env)

    secrets = [
      for env_name, secret_arn in var.secrets_arn_map : {
        name      = env_name
        valueFrom = secret_arn
      }
    ]

    logConfiguration = local.main_log_configuration

    dependsOn = length(local.main_depends_on) > 0 ? local.main_depends_on : null

    healthCheck = {
      command     = ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:${var.container_port}${var.health_check_path} || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }

    stopTimeout            = 60
    readonlyRootFilesystem = false   # Spring Boot 는 /tmp 사용
  }

  # ADOT Collector sidecar — OTLP 4317/4318 수신, X-Ray + CloudWatch EMF 송신
  otel_sidecar = {
    name              = "otel-collector"
    image             = var.otel_sidecar_image
    essential         = false
    cpu               = 64
    memoryReservation = 128

    command = ["--config=/etc/ecs/otel-config.yaml"]

    environment = [
      { name = "AWS_REGION",   value = data.aws_region.current.name },
      { name = "ENVIRONMENT",  value = var.environment },
      { name = "SERVICE_NAME", value = var.service_name },
    ]

    secrets = var.otel_config_ssm_arn != null ? [
      { name = "AOT_CONFIG_CONTENT", valueFrom = var.otel_config_ssm_arn }
    ] : []

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = local.log_group_name
        awslogs-region        = data.aws_region.current.name
        awslogs-stream-prefix = "otel-collector"
      }
    }
  }

  # FireLens (Fluent Bit) sidecar — 메인 컨테이너 stdout 을 awsfirelens 로 라우팅
  firelens_sidecar = {
    name              = "log-router"
    image             = var.firelens_image
    essential         = true
    cpu               = 32
    memoryReservation = 64

    firelensConfiguration = var.firelens_config_s3_arn != null ? {
      type = "fluentbit"
      options = {
        config-file-type  = "s3"
        config-file-value = var.firelens_config_s3_arn
      }
    } : {
      type    = "fluentbit"
      options = null
    }

    environment = [
      { name = "AWS_REGION",   value = data.aws_region.current.name },
      { name = "ENVIRONMENT",  value = var.environment },
      { name = "SERVICE_NAME", value = var.service_name },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = local.log_group_name
        awslogs-region        = data.aws_region.current.name
        awslogs-stream-prefix = "firelens"
      }
    }
  }

  containers = concat(
    [local.main_container],
    var.enable_otel_sidecar ? [local.otel_sidecar] : [],
    var.enable_firelens_sidecar ? [local.firelens_sidecar] : [],
  )
}

resource "aws_ecs_task_definition" "this" {
  family                   = local.name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  task_role_arn            = var.task_role_arn
  execution_role_arn       = var.execution_role_arn

  runtime_platform {
    cpu_architecture        = "ARM64"   # Graviton (03 §2.3)
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode(local.containers)

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# ECS Service
# ----------------------------------------------------------------------------

resource "aws_ecs_service" "this" {
  name            = local.name
  cluster         = var.cluster_arn
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  health_check_grace_period_seconds = var.health_check_grace_period_seconds

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.this.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.blue.arn
    container_name   = var.service_name
    container_port   = var.container_port
  }

  deployment_controller {
    type = var.enable_blue_green ? "CODE_DEPLOY" : "ECS"
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # CodeDeploy 가 task_definition 토글을 관리하므로 무시
  lifecycle {
    ignore_changes = [task_definition, load_balancer, desired_count]
  }

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# Auto Scaling — CPU + 메모리 타깃 추적
# ----------------------------------------------------------------------------

resource "aws_appautoscaling_target" "this" {
  max_capacity       = var.autoscaling_max_count
  min_capacity       = var.autoscaling_min_count
  resource_id        = "service/${local.ecs_cluster_name}/${aws_ecs_service.this.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${local.name}-cpu-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.this.resource_id
  scalable_dimension = aws_appautoscaling_target.this.scalable_dimension
  service_namespace  = aws_appautoscaling_target.this.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = var.autoscaling_cpu_target
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}

resource "aws_appautoscaling_policy" "memory" {
  name               = "${local.name}-memory-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.this.resource_id
  scalable_dimension = aws_appautoscaling_target.this.scalable_dimension
  service_namespace  = aws_appautoscaling_target.this.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = var.autoscaling_memory_target
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}
