variable "project" {
  description = "프로젝트 식별자."
  type        = string
}

variable "environment" {
  description = "dev / beta / prod"
  type        = string
}

variable "log_kms_key_arn" {
  description = "Log Group KMS CMK ARN (`{env}-logs`). null 이면 미암호화."
  type        = string
  default     = null
}

variable "log_groups" {
  description = "생성할 Log Group 목록. ECS 모듈이 자체 LogGroup 을 만들면 본 모듈은 빈 리스트로 호출."
  type = list(object({
    name           = string
    retention_days = optional(number, 30)
  }))
  default = []
}

variable "alarm_sns_topic_arn" {
  description = "알람 통지 SNS Topic ARN. null 이면 알람만 만들고 알림 미연결."
  type        = string
  default     = null
}

variable "service_alarms" {
  description = "서비스별 표준 알람 (CPU·메모리·실행중 태스크 수). cluster_name + service_name 매핑."
  type = list(object({
    cluster_name      = string
    service_name      = string
    cpu_threshold     = optional(number, 80)
    memory_threshold  = optional(number, 85)
    min_running_count = optional(number, 1)
  }))
  default = []
}

variable "dashboard_enabled" {
  description = "기본 대시보드 생성 여부."
  type        = bool
  default     = true
}

variable "tags" {
  description = "추가 태그."
  type        = map(string)
  default     = {}
}
