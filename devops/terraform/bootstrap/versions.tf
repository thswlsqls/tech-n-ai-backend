# 부트스트랩은 순환 의존 회피를 위해 local state로 시작.
# 적용 후 같은 state를 본 모듈이 만든 S3로 마이그레이션한다 (README 절차 참조).
terraform {
  required_version = "~> 1.15.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.100"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project    = var.project
      ManagedBy  = "Terraform"
      Module     = "bootstrap"
      CostCenter = var.cost_center
    }
  }
}
