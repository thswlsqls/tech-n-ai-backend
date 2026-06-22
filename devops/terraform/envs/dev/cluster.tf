# ECS Cluster + 공유 ALB (외부)
# - ALB 1개 + path-based routing 으로 모든 API 라우팅
# - HTTPS 전환은 var.alb_certificate_arn 으로 토글한다. 값이 있으면 HTTPS(443) 리스너를
#   만들고 HTTP(80)는 443 으로 리다이렉트한다. dev/beta 는 빈 값이라 HTTP 80 단독,
#   prod 는 ap-northeast-2 ACM 인증서 ARN 을 받아 HTTPS 로 동작한다.

locals {
  # ACM 인증서 ARN 이 주어지면 HTTPS 리스너를 만들고 HTTP 는 리다이렉트로 전환.
  alb_https_enabled = var.alb_certificate_arn != ""

  # 서비스 path 라우팅 규칙이 붙는 리스너 — HTTPS 활성 시 443, 아니면 80.
  app_listener_arn = local.alb_https_enabled ? one(aws_lb_listener.https[*].arn) : aws_lb_listener.http.arn
}

resource "aws_ecs_cluster" "main" {
  name = "${var.project}-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  configuration {
    execute_command_configuration {
      logging = "DEFAULT"
    }
  }

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# ALB SG — 80 은 항상 연다(HTTPS 활성 시 80→443 리다이렉트를 받기 위함).
# HTTPS 활성 시 443 인바운드를 추가한다.
# ----------------------------------------------------------------------------

resource "aws_security_group" "alb" {
  name        = "${var.project}-${var.environment}-sg-alb-public"
  description = "Public ALB — HTTP 80 (+ HTTPS 443 when ACM cert set)."
  vpc_id      = module.network.vpc_id

  ingress {
    description = "HTTP from internet (redirect to HTTPS when cert set)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  dynamic "ingress" {
    for_each = local.alb_https_enabled ? [1] : []
    content {
      description = "HTTPS from internet"
      from_port   = 443
      to_port     = 443
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
    }
  }

  egress {
    description = "All outbound to VPC"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.project}-${var.environment}-sg-alb-public"
  })
}

# ----------------------------------------------------------------------------
# ALB
# ----------------------------------------------------------------------------

resource "aws_lb" "main" {
  name               = "${var.project}-${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  subnets            = module.network.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]

  drop_invalid_header_fields = true
  enable_deletion_protection = var.alb_enable_deletion_protection

  tags = local.common_tags
}

# HTTP(80) — HTTPS 활성 시 443 으로 301 리다이렉트, 아니면 path 라우팅의 기본 404.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # HTTPS 활성: 모든 요청을 443 으로 리다이렉트
  dynamic "default_action" {
    for_each = local.alb_https_enabled ? [1] : []
    content {
      type = "redirect"
      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }

  # HTTPS 비활성(dev/beta): 어떤 path 도 매칭 안 되면 404
  dynamic "default_action" {
    for_each = local.alb_https_enabled ? [] : [1]
    content {
      type = "fixed-response"
      fixed_response {
        content_type = "application/json"
        message_body = jsonencode({ error = "not found" })
        status_code  = "404"
      }
    }
  }

  tags = local.common_tags
}

# HTTPS(443) — ACM 인증서가 있을 때만 생성. 서비스 path 라우팅 규칙이 이 리스너에 붙는다.
resource "aws_lb_listener" "https" {
  count = local.alb_https_enabled ? 1 : 0

  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = var.alb_ssl_policy
  certificate_arn   = var.alb_certificate_arn

  # 기본 동작: 어떤 path 도 매칭 안 되면 404
  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = jsonencode({ error = "not found" })
      status_code  = "404"
    }
  }

  tags = local.common_tags
}
