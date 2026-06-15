terraform {
  required_version = "~> 1.15.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.100"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = local.common_tags
  }
}

# 도쿄 DR 리전은 설계만 유지 (D-5). 실제 자원 미생성.
# 향후 활성화 시 alias 추가:
# provider "aws" {
#   alias  = "tokyo"
#   region = "ap-northeast-1"
# }

locals {
  common_tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "Terraform"
    CostCenter  = "tech-n-ai-platform"
    Owner       = "platform-team"
  }
}
