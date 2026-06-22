variable "project" {
  description = "프로젝트 식별자."
  type        = string
  default     = "techai"
}

variable "environment" {
  description = "고정값 prod — envs/prod 전용."
  type        = string
  default     = "prod"

  validation {
    condition     = var.environment == "prod"
    error_message = "envs/prod 는 environment=prod 만 허용."
  }
}

variable "region" {
  description = "AWS 프라이머리 리전."
  type        = string
  default     = "ap-northeast-2"
}

variable "vpc_cidr" {
  description = "VPC CIDR (/16). 02 §1.1.1 표 — prod=10.30.0.0/16."
  type        = string
  default     = "10.30.0.0/16"
}

variable "azs" {
  description = "AZ 목록 — 서울 리전 3 AZ."
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2b", "ap-northeast-2c"]
}

# ----------------------------------------------------------------------------
# 데이터 계층 토글 (D-11) — 비용 최소 정책
# ----------------------------------------------------------------------------

variable "enable_aurora" {
  description = "Aurora 활성화 여부. 데이터 적재가 시작되면 true."
  type        = bool
  default     = true
}

variable "enable_elasticache" {
  description = "ElastiCache Valkey 활성화 여부."
  type        = bool
  default     = true
}

variable "enable_msk" {
  description = "MSK Serverless 활성화 여부. dev 는 사용 시점까지 false (D-11)."
  type        = bool
  default     = false
}

# ----------------------------------------------------------------------------
# Aurora 사이즈 (Serverless v2)
# ----------------------------------------------------------------------------

variable "aurora_min_acu" {
  description = "Aurora Serverless v2 최소 ACU."
  type        = number
  default     = 0.5
}

variable "aurora_max_acu" {
  description = "Aurora Serverless v2 최대 ACU. dev 는 2 로 제한."
  type        = number
  default     = 2.0
}

variable "aurora_db_name" {
  description = "초기 데이터베이스 이름."
  type        = string
  default     = "techai"
}

# ----------------------------------------------------------------------------
# ElastiCache 사이즈
# ----------------------------------------------------------------------------

variable "cache_node_type" {
  description = "Valkey 노드 타입. dev=micro, beta/prod=small 권장."
  type        = string
  default     = "cache.t4g.micro"
}

variable "cache_replicas_per_node_group" {
  description = "ElastiCache 복제본 수. dev=0(단일), beta/prod=1(Multi-AZ)."
  type        = number
  default     = 0
}

variable "cache_multi_az_enabled" {
  description = "ElastiCache Multi-AZ. replicas≥1 일 때만 의미."
  type        = bool
  default     = false
}

variable "cache_snapshot_retention_limit" {
  description = "ElastiCache 백업 보관 일수. dev=0, beta=3, prod=7 권장."
  type        = number
  default     = 0
}

# ----------------------------------------------------------------------------
# Aurora 환경별 사이즈 (Serverless v2 vs Provisioned 분기)
# ----------------------------------------------------------------------------

variable "aurora_engine_mode" {
  description = "Aurora 모드 — `serverlessv2` (dev/beta) 또는 `provisioned` (prod)."
  type        = string
  default     = "serverlessv2"
}

variable "aurora_instance_count" {
  description = "Provisioned 모드 인스턴스 수 (Writer 1 + Reader N)."
  type        = number
  default     = 2
}

variable "aurora_instance_class" {
  description = "Provisioned 모드 인스턴스 클래스."
  type        = string
  default     = "db.r7g.large"
}

variable "aurora_storage_type" {
  description = "Aurora 스토리지 — `aurora` 또는 `aurora-iopt1`(prod 권장)."
  type        = string
  default     = "aurora"
}

variable "aurora_backup_retention_period" {
  description = "Aurora 백업 보관 일수. dev=1, beta=7, prod=30."
  type        = number
  default     = 1
}

variable "aurora_deletion_protection" {
  description = "Aurora 삭제 보호. prod=true 강제."
  type        = bool
  default     = false
}

variable "aurora_skip_final_snapshot" {
  description = "destroy 시 최종 스냅샷 생성 여부. dev=true, prod=false 권장."
  type        = bool
  default     = true
}

variable "aurora_performance_insights_enabled" {
  description = "Performance Insights 활성화. prod=true."
  type        = bool
  default     = false
}

# ----------------------------------------------------------------------------
# 네트워크
# ----------------------------------------------------------------------------

variable "single_nat_gateway" {
  description = "단일 NAT — dev/beta=true(비용 절감), prod=false(AZ 격리)."
  type        = bool
  default     = true
}

variable "enable_vpc_endpoints" {
  description = "VPC Endpoint Interface 활성화. 시드 환경(트래픽 0)에서는 false 권장 — VPC Endpoint Interface 비용 $194/월 절감 (NAT 폴백)."
  type        = bool
  default     = true
}

# ----------------------------------------------------------------------------
# ALB / HTTPS
# ----------------------------------------------------------------------------

variable "alb_certificate_arn" {
  description = "ALB HTTPS 리스너용 ACM 인증서 ARN (리전 = ap-northeast-2). 값이 있으면 HTTPS(443) 리스너를 만들고 HTTP(80)는 443 으로 리다이렉트한다. 빈 문자열이면 HTTP(80) 단독 — dev/beta."
  type        = string
  default     = ""
}

variable "alb_ssl_policy" {
  description = "HTTPS 리스너 보안 정책. TLS 1.3 + 1.2 호환 정책이 기본. prod 는 AWS 권장 PQ-TLS 정책으로 올릴 수 있다."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

variable "alb_enable_deletion_protection" {
  description = "ALB 삭제 보호. prod=true 권장."
  type        = bool
  default     = false
}

# ----------------------------------------------------------------------------
# ECS 워크로드 사이즈
# ----------------------------------------------------------------------------

variable "ecs_desired_count" {
  description = "ECS Service 기본 태스크 수. dev=1, beta=1~2, prod=2~3."
  type        = number
  default     = 1
}

variable "ecs_autoscaling_min_count" {
  description = "ECS Auto Scaling 최소 태스크."
  type        = number
  default     = 1
}

variable "ecs_autoscaling_max_count" {
  description = "ECS Auto Scaling 최대 태스크."
  type        = number
  default     = 3
}

# ----------------------------------------------------------------------------
# MSK 모드 (prod 만 Provisioned)
# ----------------------------------------------------------------------------

variable "use_msk_provisioned" {
  description = "true 면 msk-provisioned 모듈 사용 (prod). false 면 msk-serverless (dev/beta)."
  type        = bool
  default     = false
}

variable "msk_kafka_version" {
  description = "MSK Provisioned 시 Kafka 버전 (D-8: 3.9.x KRaft)."
  type        = string
  default     = "3.9.x.kraft"
}

variable "msk_broker_count" {
  description = "MSK Provisioned 브로커 수."
  type        = number
  default     = 3
}

variable "msk_broker_instance_type" {
  description = "MSK Provisioned 브로커 인스턴스 타입."
  type        = string
  default     = "kafka.m7g.large"
}

# ----------------------------------------------------------------------------
# ecs-service Sidecar 옵션 (08 §2 ADOT/FireLens 통합)
# ----------------------------------------------------------------------------

variable "enable_otel_sidecar" {
  description = "ADOT Collector sidecar 자동 추가 (OTLP 4317/4318 → X-Ray + CloudWatch EMF)."
  type        = bool
  default     = false
}

variable "enable_firelens_sidecar" {
  description = "FireLens (Fluent Bit) sidecar 자동 추가 (메인 stdout → PII 마스킹 → CloudWatch Logs)."
  type        = bool
  default     = false
}

variable "otel_config_ssm_arn" {
  description = "ADOT Collector 설정(adot-collector.yaml) SSM Parameter ARN. null 이면 sidecar 가 자체 기본값 사용."
  type        = string
  default     = null
}

variable "firelens_config_s3_arn" {
  description = "FireLens config S3 객체 ARN. null 이면 firelens 기본값."
  type        = string
  default     = null
}

# ----------------------------------------------------------------------------
# Frontend (Amplify Hosting)
# ----------------------------------------------------------------------------

variable "enable_amplify" {
  description = "Amplify App 활성화. GitHub PAT secret 입력 후 true (시드 단계 false)."
  type        = bool
  default     = false
}

variable "frontend_repository_url" {
  description = "Frontend GitHub 리포지토리 URL."
  type        = string
  default     = "https://github.com/your-org/tech-n-ai-frontend"
}

variable "frontend_branch_name" {
  description = "Amplify 가 추적할 git 브랜치."
  type        = string
  default     = "develop"
}
