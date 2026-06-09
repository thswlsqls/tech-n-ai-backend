# 02-network-vpc.md 의 4티어 서브넷 구조를 모듈화
#   /16 VPC ┬─ Public      /24 × 3 AZ  (ALB, NAT GW)
#           ├─ Private-App /20 × 3 AZ  (ECS Fargate Task ENI)
#           ├─ Private-Data /24 × 3 AZ (Aurora, ElastiCache, MongoDB Atlas Endpoint)
#           └─ Private-TGW /26 × 3 AZ  (향후 TGW 연결용)
#
# CIDR 분할은 cidrsubnet() 으로 결정적으로 계산 — /16 → /20·/24·/26.

locals {
  name_prefix = "${var.project}-${var.environment}"

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "Terraform"
      Module      = "network"
    },
    var.tags,
  )

  # 서브넷 CIDR 결정적 산출 — 02 §1.1.2 표와 호환
  # /16 기준 cidrsubnet(cidr, newbits, netnum):
  #   public      /24  : newbits=8,  netnum 0..2  → x.x.0.0/24, x.x.1.0/24, x.x.2.0/24
  #   private-app /20  : newbits=4,  netnum 1..3  → x.x.16.0/20, x.x.32.0/20, x.x.48.0/20
  #                                                 (netnum 0 은 public 과 겹치므로 1 부터 시작)
  #   private-data /24 : newbits=8,  netnum 64..66 → x.x.64.0/24, x.x.65.0/24, x.x.66.0/24
  #   private-tgw  /26 : newbits=10, netnum 280..282 → x.x.70.0/26, x.x.70.64/26, x.x.70.128/26 (private-data /24 대역과 충돌 회피)
  public_subnet_cidrs       = [for i in range(length(var.azs)) : cidrsubnet(var.cidr_block, 8, i)]            # /24
  private_app_subnet_cidrs  = [for i in range(length(var.azs)) : cidrsubnet(var.cidr_block, 4, i + 1)]        # /20
  private_data_subnet_cidrs = [for i in range(length(var.azs)) : cidrsubnet(var.cidr_block, 8, i + 64)]       # /24
  private_tgw_subnet_cidrs  = [for i in range(length(var.azs)) : cidrsubnet(var.cidr_block, 10, i + 280)]     # /26 (CIDR 충돌 회피)
}

# ----------------------------------------------------------------------------
# VPC + IGW
# ----------------------------------------------------------------------------

resource "aws_vpc" "this" {
  cidr_block           = var.cidr_block
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-vpc"
  })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-igw"
  })
}

# ----------------------------------------------------------------------------
# 서브넷
# ----------------------------------------------------------------------------

resource "aws_subnet" "public" {
  count = length(var.azs)

  vpc_id                  = aws_vpc.this.id
  cidr_block              = local.public_subnet_cidrs[count.index]
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = false  # ALB·NAT 만 IP 받음, 일반 EC2 자동 IP 부여 차단

  tags = merge(local.common_tags, {
    Name                     = "${local.name_prefix}-public-${var.azs[count.index]}"
    Tier                     = "public"
    "kubernetes.io/role/elb" = "1"
  })
}

resource "aws_subnet" "private_app" {
  count = length(var.azs)

  vpc_id            = aws_vpc.this.id
  cidr_block        = local.private_app_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index]

  tags = merge(local.common_tags, {
    Name                              = "${local.name_prefix}-private-app-${var.azs[count.index]}"
    Tier                              = "private-app"
    "kubernetes.io/role/internal-elb" = "1"
  })
}

resource "aws_subnet" "private_data" {
  count = length(var.azs)

  vpc_id            = aws_vpc.this.id
  cidr_block        = local.private_data_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index]

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-private-data-${var.azs[count.index]}"
    Tier = "private-data"
  })
}

resource "aws_subnet" "private_tgw" {
  count = length(var.azs)

  vpc_id            = aws_vpc.this.id
  cidr_block        = local.private_tgw_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index]

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-private-tgw-${var.azs[count.index]}"
    Tier = "private-tgw"
  })
}

# ----------------------------------------------------------------------------
# NAT Gateway (AZ별 1개 / 비용 절감 모드 1개)
# ----------------------------------------------------------------------------

locals {
  nat_count = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : length(var.azs)) : 0
}

resource "aws_eip" "nat" {
  count = local.nat_count

  domain = "vpc"

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-eip-nat-${count.index}"
  })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_nat_gateway" "this" {
  count = local.nat_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-natgw-${var.azs[count.index]}"
  })

  depends_on = [aws_internet_gateway.this]
}

# ----------------------------------------------------------------------------
# 라우팅 테이블
# ----------------------------------------------------------------------------

# Public — IGW 로 0.0.0.0/0
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-rt-public"
  })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count = length(var.azs)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Private-App — NAT 로 0.0.0.0/0 (single 모드면 모두 같은 NAT, 아니면 AZ별)
resource "aws_route_table" "private_app" {
  count = length(var.azs)

  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-rt-private-app-${var.azs[count.index]}"
  })
}

resource "aws_route" "private_app_nat" {
  count = var.enable_nat_gateway ? length(var.azs) : 0

  route_table_id         = aws_route_table.private_app[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this[var.single_nat_gateway ? 0 : count.index].id
}

resource "aws_route_table_association" "private_app" {
  count = length(var.azs)

  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private_app[count.index].id
}

# Private-Data — 인터넷 라우트 없음 (격리)
resource "aws_route_table" "private_data" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-rt-private-data"
  })
}

resource "aws_route_table_association" "private_data" {
  count = length(var.azs)

  subnet_id      = aws_subnet.private_data[count.index].id
  route_table_id = aws_route_table.private_data.id
}

# Private-TGW — 향후 TGW attach 시 라우트 추가
resource "aws_route_table" "private_tgw" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-rt-private-tgw"
  })
}

resource "aws_route_table_association" "private_tgw" {
  count = length(var.azs)

  subnet_id      = aws_subnet.private_tgw[count.index].id
  route_table_id = aws_route_table.private_tgw.id
}
