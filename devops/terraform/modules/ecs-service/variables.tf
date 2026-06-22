variable "project" {
  description = "프로젝트 식별자."
  type        = string
}

variable "environment" {
  description = "dev / beta / prod"
  type        = string
}

variable "service_name" {
  description = "서비스 이름. 모듈마다 고유 (api-auth, api-chatbot, ...)."
  type        = string
}

variable "cluster_arn" {
  description = "ECS Cluster ARN."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "private_subnet_ids" {
  description = "Task ENI 가 배치될 Private-App 서브넷 ID."
  type        = list(string)
}

variable "container_image" {
  description = "ECR 이미지 URI (digest 형태 권장: `<reg>/techai/api-auth@sha256:...`)."
  type        = string
}

variable "container_port" {
  description = "컨테이너 listen 포트 (예: api-auth=8083)."
  type        = number
}

variable "cpu" {
  description = "Fargate CPU (예: 256, 512, 1024). 모듈별 03 §2.5 시드값 따름."
  type        = number
  default     = 512
}

variable "memory" {
  description = "Fargate 메모리(MiB). cpu 와 호환되는 값."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "초기 태스크 수."
  type        = number
  default     = 2
}

variable "task_role_arn" {
  description = "ECS Task Role ARN (워크로드 권한)."
  type        = string
}

variable "execution_role_arn" {
  description = "ECS Task Execution Role ARN (ECR pull, 로그 stream, secrets fetch)."
  type        = string
}

variable "alb_listener_arn" {
  description = "외부에서 만든 ALB Listener ARN. 본 모듈은 listener_rule 만 추가."
  type        = string
}

variable "alb_security_group_id" {
  description = "ALB SG ID. 본 서비스 SG 의 인바운드 소스."
  type        = string
}

variable "listener_rule_priority" {
  description = "Listener Rule 우선순위. 서비스마다 고유."
  type        = number
}

variable "listener_path_patterns" {
  description = "Listener path 매칭 (예: [`/auth/*`])."
  type        = list(string)
  default     = []
}

variable "listener_host_headers" {
  description = "Listener host header 매칭. 미사용 시 빈 리스트."
  type        = list(string)
  default     = []
}

variable "health_check_path" {
  description = "Target Group 헬스체크 경로. 03 §2.7 — readiness probe 권장."
  type        = string
  default     = "/actuator/health/readiness"
}

variable "health_check_grace_period_seconds" {
  description = "ECS Service 헬스체크 grace period (초). Spring Boot 워밍업 고려."
  type        = number
  default     = 60
}

variable "deregistration_delay" {
  description = "Target Group deregistration delay (초). 짧을수록 배포 빠름."
  type        = number
  default     = 30
}

variable "log_group_name" {
  description = "CloudWatch Log Group 이름. null 이면 모듈이 자동 생성."
  type        = string
  default     = null
}

variable "log_retention_days" {
  description = "Log Group 보관 일수."
  type        = number
  default     = 30
}

variable "log_kms_key_arn" {
  description = "Log Group KMS CMK ARN (`{env}-logs`)."
  type        = string
  default     = null
}

variable "environment_vars" {
  description = "Task Definition `environment[]`. SPRING_PROFILES_ACTIVE 등."
  type = list(object({
    name  = string
    value = string
  }))
  default = []
}

variable "secrets_arn_map" {
  description = "Task Definition `secrets[]`. key=환경변수명, value=Secret ARN."
  type        = map(string)
  default     = {}
}

variable "autoscaling_min_count" {
  description = "오토스케일링 최소 태스크."
  type        = number
  default     = 2
}

variable "autoscaling_max_count" {
  description = "오토스케일링 최대 태스크."
  type        = number
  default     = 10
}

variable "autoscaling_cpu_target" {
  description = "CPU 사용률 목표 %."
  type        = number
  default     = 60
}

variable "autoscaling_memory_target" {
  description = "메모리 사용률 목표 %."
  type        = number
  default     = 70
}

variable "enable_blue_green" {
  description = "CodeDeploy Blue/Green 배포 활성화. true 면 deployment_controller=CODE_DEPLOY."
  type        = bool
  default     = true
}

variable "deployment_config_name" {
  description = "CodeDeploy deployment config (Canary/Linear/AllAtOnce)."
  type        = string
  default     = "CodeDeployDefault.ECSCanary10Percent5Minutes"
}

variable "rollback_alarm_5xx_threshold" {
  description = "ALB 5xx 비율 임계값(%). 초과 시 자동 롤백."
  type        = number
  default     = 1
}

variable "rollback_alarm_latency_p95_seconds" {
  description = "Target Response Time p95 임계값(초). 초과 시 자동 롤백."
  type        = number
  default     = 1.5
}

variable "tags" {
  description = "추가 태그."
  type        = map(string)
  default     = {}
}

# ----------------------------------------------------------------------------
# Sidecar — ADOT Collector (OTel) / FireLens (Fluent Bit)
# 08 §2 sidecar 통합 패턴
# ----------------------------------------------------------------------------

variable "enable_otel_sidecar" {
  description = "ADOT Collector sidecar 자동 추가. 메인 컨테이너에 OTLP 엔드포인트 환경변수 자동 주입."
  type        = bool
  default     = false
}

variable "otel_sidecar_image" {
  description = "ADOT Collector 이미지."
  type        = string
  default     = "public.ecr.aws/aws-observability/aws-otel-collector:latest"
}

variable "otel_config_ssm_arn" {
  description = "ADOT Collector 설정(adot-collector.yaml)의 SSM Parameter ARN. null 이면 sidecar 가 기본 설정으로 동작."
  type        = string
  default     = null
}

variable "enable_firelens_sidecar" {
  description = "FireLens (Fluent Bit) sidecar 자동 추가. 메인 컨테이너 logConfiguration 을 awsfirelens 로 전환."
  type        = bool
  default     = false
}

variable "firelens_image" {
  description = "FireLens (aws-for-fluent-bit) 이미지."
  type        = string
  default     = "public.ecr.aws/aws-observability/aws-for-fluent-bit:stable"
}

variable "firelens_config_s3_arn" {
  description = "FireLens config 파일이 저장된 S3 객체 ARN. null 이면 기본 firelensConfiguration 사용."
  type        = string
  default     = null
}
