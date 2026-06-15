terraform {
  required_version = "~> 1.15.0"

  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 5.100"
      configuration_aliases = [aws.us_east_1]
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = local.common_tags
  }
}

# us-east-1 — CloudFront ACM/WAF 용
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = local.common_tags
  }
}

# 향후 Tokyo DR 활성화 시 — D-5: 현재는 설계만 유지, 미구축
# provider "aws" {
#   alias  = "tokyo"
#   region = "ap-northeast-1"
#   default_tags { tags = local.common_tags }
# }

locals {
  common_tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "Terraform"
    CostCenter  = "tech-n-ai-platform"
    Compliance  = "production"
  }
}
