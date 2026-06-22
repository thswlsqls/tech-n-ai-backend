variable "bucket_name" {
  description = "S3 버킷 이름. 전역 유일."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "S3 버킷 이름 규칙 위반."
  }
}

variable "kms_key_arn" {
  description = "SSE-KMS 용 KMS CMK ARN. 매트릭스 §1 의 키 중 적절한 것 선택."
  type        = string
}

variable "versioning_enabled" {
  description = "객체 버전 관리 활성화. 기본 true."
  type        = bool
  default     = true
}

variable "object_lock_enabled" {
  description = "Object Lock 활성화 (감사 로그·raw 데이터에 권장). 한 번 켜면 끌 수 없음."
  type        = bool
  default     = false
}

variable "object_lock_mode" {
  description = "Object Lock 보호 모드 — GOVERNANCE 또는 COMPLIANCE."
  type        = string
  default     = "GOVERNANCE"

  validation {
    condition     = contains(["GOVERNANCE", "COMPLIANCE"], var.object_lock_mode)
    error_message = "object_lock_mode 는 GOVERNANCE 또는 COMPLIANCE."
  }
}

variable "object_lock_days" {
  description = "Object Lock 기본 보관 일수."
  type        = number
  default     = 30
}

variable "lifecycle_rules" {
  description = "라이프사이클 규칙 목록. 미지정 시 빈 리스트."
  type = list(object({
    id                                         = string
    enabled                                    = bool
    prefix                                     = optional(string, "")
    abort_incomplete_multipart_upload_days     = optional(number, 7)
    transition_to_standard_ia_days             = optional(number)
    transition_to_intelligent_tiering_days     = optional(number)
    transition_to_glacier_days                 = optional(number)
    transition_to_deep_archive_days            = optional(number)
    expiration_days                            = optional(number)
    noncurrent_version_expiration_days         = optional(number)
    noncurrent_version_transition_glacier_days = optional(number)
  }))
  default = []
}

variable "block_public_access" {
  description = "Block Public Access 4종 강제 (block_public_acls/policy, ignore_public_acls, restrict_public_buckets)."
  type        = bool
  default     = true
}

variable "bucket_policy_json" {
  description = "추가 버킷 정책 JSON (HTTPS 강제·SSE 강제는 본 모듈이 자동 추가). null 이면 기본 정책만."
  type        = string
  default     = null
}

variable "force_destroy" {
  description = "destroy 시 비어있지 않아도 삭제 허용. prod=false 권장."
  type        = bool
  default     = false
}

variable "tags" {
  description = "추가 태그."
  type        = map(string)
  default     = {}
}
