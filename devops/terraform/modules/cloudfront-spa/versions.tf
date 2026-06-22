terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 5.100"
      configuration_aliases = [aws.us_east_1] # ACM·WAF CloudFront scope 는 us-east-1
    }
  }
}
