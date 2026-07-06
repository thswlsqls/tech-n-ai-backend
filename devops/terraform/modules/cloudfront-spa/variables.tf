variable "project" {
  description = "프로젝트 식별자."
  type        = string
}

variable "environment" {
  description = "dev / beta / prod"
  type        = string
}

variable "distribution_name" {
  description = "Distribution 식별 이름."
  type        = string
}

variable "domain_aliases" {
  description = "CloudFront 도메인 alias (예: [`www.tech-n-ai.com`]). 도메인 미연결 시 빈 리스트 + acm_certificate_arn=null."
  type        = list(string)
  default     = []
}

variable "acm_certificate_arn" {
  description = "us-east-1 발급 ACM 인증서 ARN. domain_aliases 비어 있으면 null."
  type        = string
  default     = null
}

variable "origin_type" {
  description = "Origin 종류 — `amplify` (Amplify Hosting domain) 또는 `s3` (S3 + OAC)."
  type        = string

  validation {
    condition     = contains(["amplify", "s3"], var.origin_type)
    error_message = "origin_type 은 amplify 또는 s3."
  }
}

variable "amplify_origin_domain" {
  description = "Amplify branch domain (예: `main.dxyz.amplifyapp.com`). origin_type=amplify 시 필수."
  type        = string
  default     = null
}

variable "s3_bucket_regional_domain" {
  description = "S3 버킷 regional domain. origin_type=s3 시 필수."
  type        = string
  default     = null
}

variable "s3_bucket_arn" {
  description = "S3 버킷 ARN. origin_type=s3 시 필수 (OAC 정책 부여용)."
  type        = string
  default     = null
}

variable "waf_web_acl_arn" {
  description = "us-east-1 WAF Web ACL ARN (CLOUDFRONT scope). null 이면 미부착."
  type        = string
  default     = null
}

variable "spa_404_to_index" {
  description = "SPA 라우팅: 404 응답을 /index.html 로 변환."
  type        = bool
  default     = true
}

variable "tags" {
  description = "추가 태그."
  type        = map(string)
  default     = {}
}
