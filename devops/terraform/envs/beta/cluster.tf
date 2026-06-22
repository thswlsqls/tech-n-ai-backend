# ECS Cluster + 공유 ALB (외부)
# - dev 비용 최소화: ALB 1개 + path-based routing 으로 모든 API 라우팅

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
# ALB SG — 외부에서 0.0.0.0/0 :80 (dev). prod 는 ACM + 443 이 표준.
# ----------------------------------------------------------------------------

resource "aws_security_group" "alb" {
  name        = "${var.project}-${var.environment}-sg-alb-public"
  description = "Public ALB — HTTP (dev). prod 는 443 + ACM."
  vpc_id      = module.network.vpc_id

  ingress {
    description = "HTTP from internet (dev)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
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
  enable_deletion_protection = false # dev — true 권장은 prod

  tags = local.common_tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

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
