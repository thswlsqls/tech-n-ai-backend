# VPC Endpoint 풀세트
# - Gateway: S3, DynamoDB (라우팅 테이블 attach)
# - Interface: ECR.api / ECR.dkr / Logs / KMS / STS / SecretsManager / SSM / SSM Messages / EC2 Messages
# - 모든 Interface Endpoint 는 Private-App 서브넷에 ENI 생성
# - 02 §1.4 VPC Endpoint 단일 정의

data "aws_region" "current" {}

# ----------------------------------------------------------------------------
# VPCE 전용 SG
#   - Private-App 의 SG 들이 443 으로 인바운드 허용 (sg id 는 ecs-service 모듈이 outbound 로 참조)
#   - sg-prod-vpce 매트릭스 (00-매트릭스 §3)
# ----------------------------------------------------------------------------

resource "aws_security_group" "vpce" {
  count = var.enable_vpc_endpoints ? 1 : 0

  name        = "${local.name_prefix}-sg-vpce"
  description = "Allow 443/TCP from Private-App subnets to VPC Endpoint ENIs"
  vpc_id      = aws_vpc.this.id

  # Private-App 서브넷 CIDR 에서 443 허용
  ingress {
    description = "HTTPS from Private-App subnets"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = local.private_app_subnet_cidrs
  }

  # Endpoint ENI 는 outbound 가 필요하지 않음
  egress {
    description = "(None) - Endpoint ENI does not initiate connections"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["127.0.0.1/32"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-sg-vpce"
  })
}

# ----------------------------------------------------------------------------
# Gateway Endpoints — S3, DynamoDB
# ----------------------------------------------------------------------------

resource "aws_vpc_endpoint" "s3" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"

  route_table_ids = concat(
    aws_route_table.private_app[*].id,
    [aws_route_table.private_data.id],
  )

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-vpce-s3"
  })
}

resource "aws_vpc_endpoint" "dynamodb" {
  count = var.enable_vpc_endpoints ? 1 : 0

  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.dynamodb"
  vpc_endpoint_type = "Gateway"

  route_table_ids = concat(
    aws_route_table.private_app[*].id,
    [aws_route_table.private_data.id],
  )

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-vpce-dynamodb"
  })
}

# ----------------------------------------------------------------------------
# Interface Endpoints — ECR / Logs / KMS / STS / SecretsManager / SSM / SSM Messages / EC2 Messages
# ----------------------------------------------------------------------------

locals {
  interface_endpoints = var.enable_vpc_endpoints ? {
    ecr_api        = "ecr.api"
    ecr_dkr        = "ecr.dkr"
    logs           = "logs"
    kms            = "kms"
    sts            = "sts"
    secretsmanager = "secretsmanager"
    ssm            = "ssm"
    ssmmessages    = "ssmmessages"
    ec2messages    = "ec2messages"
  } : {}
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoints

  vpc_id              = aws_vpc.this.id
  service_name        = "com.amazonaws.${data.aws_region.current.name}.${each.value}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true

  subnet_ids         = aws_subnet.private_app[*].id
  security_group_ids = [aws_security_group.vpce[0].id]

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-vpce-${each.key}"
  })
}
