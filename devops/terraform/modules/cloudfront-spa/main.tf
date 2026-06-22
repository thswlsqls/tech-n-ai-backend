# CloudFront SPA 배포
# - Amplify origin: 그대로 forward (Amplify 가 캐싱·라우팅 함께 처리)
# - S3 origin: OAC + SSE-KMS

locals {
  name = "${var.project}-${var.environment}-${var.distribution_name}"

  common_tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "Terraform"
      Module      = "cloudfront-spa"
    },
    var.tags,
  )

  is_amplify = var.origin_type == "amplify"
  is_s3      = var.origin_type == "s3"

  # 커스텀 ACM 인증서가 연결됐는지 — 미연결 시 CloudFront 기본 인증서 사용
  has_custom_cert = var.acm_certificate_arn != null
}

# ----------------------------------------------------------------------------
# Origin Access Control (S3 origin 시)
# ----------------------------------------------------------------------------

resource "aws_cloudfront_origin_access_control" "this" {
  count = local.is_s3 ? 1 : 0

  name                              = "${local.name}-oac"
  description                       = "OAC for ${local.name}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ----------------------------------------------------------------------------
# 응답 헤더 정책 — 보안 헤더 강제
# ----------------------------------------------------------------------------

resource "aws_cloudfront_response_headers_policy" "security" {
  name = "${local.name}-security-headers"

  security_headers_config {
    content_security_policy {
      content_security_policy = "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https:;"
      override                = true
    }
    content_type_options { override = true }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      preload                    = true
      override                   = true
    }
    xss_protection {
      mode_block = true
      protection = true
      override   = true
    }
  }
}

# ----------------------------------------------------------------------------
# Distribution
# ----------------------------------------------------------------------------

resource "aws_cloudfront_distribution" "this" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = local.name
  price_class     = "PriceClass_200" # 북미·유럽·아시아·중동 (서울 포함)

  aliases = var.domain_aliases

  web_acl_id = var.waf_web_acl_arn

  # Amplify origin (Amplify 가 SPA 라우팅 자체 처리)
  dynamic "origin" {
    for_each = local.is_amplify ? [1] : []
    content {
      domain_name = var.amplify_origin_domain
      origin_id   = "amplify-origin"

      custom_origin_config {
        http_port              = 80
        https_port             = 443
        origin_protocol_policy = "https-only"
        origin_ssl_protocols   = ["TLSv1.2"]
      }
    }
  }

  # S3 origin (정적 호스팅 + OAC)
  dynamic "origin" {
    for_each = local.is_s3 ? [1] : []
    content {
      domain_name              = var.s3_bucket_regional_domain
      origin_id                = "s3-origin"
      origin_access_control_id = aws_cloudfront_origin_access_control.this[0].id
    }
  }

  default_cache_behavior {
    target_origin_id       = local.is_amplify ? "amplify-origin" : "s3-origin"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    cache_policy_id            = "658327ea-f89d-4fab-a63d-7e88639e58f6" # CachingOptimized (AWS Managed)
    origin_request_policy_id   = "59781a5b-3903-41f3-afcb-af62929ccde1" # CORS-CustomOrigin (Managed)
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
  }

  # SPA 라우팅 — 404/403 을 /index.html 로 (S3 origin 시 유효, Amplify 는 자체 처리)
  dynamic "custom_error_response" {
    for_each = local.is_s3 && var.spa_404_to_index ? [403, 404] : []
    content {
      error_code            = custom_error_response.value
      response_code         = 200
      response_page_path    = "/index.html"
      error_caching_min_ttl = 60
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn            = var.acm_certificate_arn
    cloudfront_default_certificate = !local.has_custom_cert
    minimum_protocol_version       = local.has_custom_cert ? "TLSv1.2_2021" : "TLSv1"
    ssl_support_method             = local.has_custom_cert ? "sni-only" : null
  }

  tags = local.common_tags
}

# ----------------------------------------------------------------------------
# S3 Origin 정책 (OAC 만 접근 허용)
# ----------------------------------------------------------------------------

resource "aws_s3_bucket_policy" "oac" {
  count = local.is_s3 ? 1 : 0

  # 본 모듈은 외부에서 만든 S3 버킷에 정책을 추가. bucket 식별자는 bucket_arn 으로부터 분해.
  bucket = element(split(":", var.s3_bucket_arn), 5)

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontOAC"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${var.s3_bucket_arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.this.arn
        }
      }
    }]
  })
}
