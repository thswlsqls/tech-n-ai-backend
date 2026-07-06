# ECS Service 6개 호출
# - 모든 서비스가 같은 ALB 뒤에 path-based routing (dev 단순화)
# - 컨테이너 이미지는 시드 placeholder. 실제 배포는 GitHub Actions 가 task-definition revision 생성.
# - secrets_arn_map 은 빈 map 으로 시작 — 실제 시크릿 생성은 별도 워크플로 (값 입력 회피)

# ----------------------------------------------------------------------------
# 공통 환경변수 (Actuator probe 활성화는 모든 백엔드 모듈 필수 — 매트릭스 §5)
# ----------------------------------------------------------------------------

locals {
  common_env = [
    { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
    { name = "MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", value = "true" },
    { name = "AWS_REGION", value = var.region },
  ]

  # 시드 이미지 — 실제 배포 시 GitHub Actions 가 digest 로 갱신
  ecr_registry = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  placeholder_image_for = { for m in [
    "api-gateway", "api-emerging-tech", "api-auth", "api-chatbot", "api-bookmark", "api-agent"
  ] : m => "${local.ecr_registry}/techai/${m}:initial" }
}

# ----------------------------------------------------------------------------
# api-gateway (8081) — path /* (다른 specific 매칭 후 fallback)
# ----------------------------------------------------------------------------

module "api_gateway" {
  source = "../../modules/ecs-service"

  project      = var.project
  environment  = var.environment
  service_name = "api-gateway"

  cluster_arn        = aws_ecs_cluster.main.arn
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids

  container_image = local.placeholder_image_for["api-gateway"]
  container_port  = 8081
  cpu             = 512
  memory          = 1024
  desired_count   = var.ecs_desired_count # dev — 비용 최소

  task_role_arn      = module.task_role_api_gateway.role_arn
  execution_role_arn = module.task_execution_role.role_arn
  log_kms_key_arn    = aws_kms_key.logs.arn

  # Sidecar 옵션 — 08 §2
  enable_otel_sidecar     = var.enable_otel_sidecar
  enable_firelens_sidecar = var.enable_firelens_sidecar
  otel_config_ssm_arn     = var.otel_config_ssm_arn
  firelens_config_s3_arn  = var.firelens_config_s3_arn

  alb_listener_arn       = local.app_listener_arn
  alb_security_group_id  = aws_security_group.alb.id
  listener_rule_priority = 1000 # 가장 낮은 우선순위 — fallback
  listener_path_patterns = ["/*"]

  environment_vars = local.common_env

  autoscaling_min_count = var.ecs_autoscaling_min_count
  autoscaling_max_count = var.ecs_autoscaling_max_count
}

# ----------------------------------------------------------------------------
# api-auth (8083) — /auth/*
# ----------------------------------------------------------------------------

module "api_auth" {
  source = "../../modules/ecs-service"

  project      = var.project
  environment  = var.environment
  service_name = "api-auth"

  cluster_arn        = aws_ecs_cluster.main.arn
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids

  container_image = local.placeholder_image_for["api-auth"]
  container_port  = 8083
  cpu             = 512
  memory          = 1024
  desired_count   = var.ecs_desired_count

  task_role_arn      = module.task_role_api_auth.role_arn
  execution_role_arn = module.task_execution_role.role_arn
  log_kms_key_arn    = aws_kms_key.logs.arn

  # Sidecar 옵션 — 08 §2
  enable_otel_sidecar     = var.enable_otel_sidecar
  enable_firelens_sidecar = var.enable_firelens_sidecar
  otel_config_ssm_arn     = var.otel_config_ssm_arn
  firelens_config_s3_arn  = var.firelens_config_s3_arn

  alb_listener_arn       = local.app_listener_arn
  alb_security_group_id  = aws_security_group.alb.id
  listener_rule_priority = 100
  listener_path_patterns = ["/auth/*"]

  environment_vars = local.common_env

  autoscaling_min_count = var.ecs_autoscaling_min_count
  autoscaling_max_count = var.ecs_autoscaling_max_count
}

# ----------------------------------------------------------------------------
# api-emerging-tech (8082) — /emerging-tech/*
# ----------------------------------------------------------------------------

module "api_emerging_tech" {
  source = "../../modules/ecs-service"

  project      = var.project
  environment  = var.environment
  service_name = "api-emerging-tech"

  cluster_arn        = aws_ecs_cluster.main.arn
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids

  container_image = local.placeholder_image_for["api-emerging-tech"]
  container_port  = 8082
  cpu             = 512
  memory          = 1024
  desired_count   = var.ecs_desired_count

  task_role_arn      = module.task_role_api_emerging_tech.role_arn
  execution_role_arn = module.task_execution_role.role_arn
  log_kms_key_arn    = aws_kms_key.logs.arn

  # Sidecar 옵션 — 08 §2
  enable_otel_sidecar     = var.enable_otel_sidecar
  enable_firelens_sidecar = var.enable_firelens_sidecar
  otel_config_ssm_arn     = var.otel_config_ssm_arn
  firelens_config_s3_arn  = var.firelens_config_s3_arn

  alb_listener_arn       = local.app_listener_arn
  alb_security_group_id  = aws_security_group.alb.id
  listener_rule_priority = 110
  listener_path_patterns = ["/emerging-tech/*"]

  environment_vars = local.common_env
}

# ----------------------------------------------------------------------------
# api-chatbot (8084) — /chatbot/*  (Bedrock 권한 미부여 — D-12)
# ----------------------------------------------------------------------------

module "api_chatbot" {
  source = "../../modules/ecs-service"

  project      = var.project
  environment  = var.environment
  service_name = "api-chatbot"

  cluster_arn        = aws_ecs_cluster.main.arn
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids

  container_image = local.placeholder_image_for["api-chatbot"]
  container_port  = 8084
  cpu             = 1024 # RAG 추론 — 더 큼
  memory          = 2048
  desired_count   = var.ecs_desired_count

  task_role_arn      = module.task_role_api_chatbot.role_arn
  execution_role_arn = module.task_execution_role.role_arn
  log_kms_key_arn    = aws_kms_key.logs.arn

  # Sidecar 옵션 — 08 §2
  enable_otel_sidecar     = var.enable_otel_sidecar
  enable_firelens_sidecar = var.enable_firelens_sidecar
  otel_config_ssm_arn     = var.otel_config_ssm_arn
  firelens_config_s3_arn  = var.firelens_config_s3_arn

  alb_listener_arn       = local.app_listener_arn
  alb_security_group_id  = aws_security_group.alb.id
  listener_rule_priority = 120
  listener_path_patterns = ["/chatbot/*"]

  environment_vars = local.common_env

  rollback_alarm_latency_p95_seconds = 5.0 # LLM 호출 — 다른 서비스보다 느림
}

# ----------------------------------------------------------------------------
# api-bookmark (8085) — /bookmark/*
# ----------------------------------------------------------------------------

module "api_bookmark" {
  source = "../../modules/ecs-service"

  project      = var.project
  environment  = var.environment
  service_name = "api-bookmark"

  cluster_arn        = aws_ecs_cluster.main.arn
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids

  container_image = local.placeholder_image_for["api-bookmark"]
  container_port  = 8085
  cpu             = 256
  memory          = 512
  desired_count   = var.ecs_desired_count

  task_role_arn      = module.task_role_api_bookmark.role_arn
  execution_role_arn = module.task_execution_role.role_arn
  log_kms_key_arn    = aws_kms_key.logs.arn

  # Sidecar 옵션 — 08 §2
  enable_otel_sidecar     = var.enable_otel_sidecar
  enable_firelens_sidecar = var.enable_firelens_sidecar
  otel_config_ssm_arn     = var.otel_config_ssm_arn
  firelens_config_s3_arn  = var.firelens_config_s3_arn

  alb_listener_arn       = local.app_listener_arn
  alb_security_group_id  = aws_security_group.alb.id
  listener_rule_priority = 130
  listener_path_patterns = ["/bookmark/*"]

  environment_vars = local.common_env
}

# ----------------------------------------------------------------------------
# api-agent (8086) — /agent/*
# ----------------------------------------------------------------------------

module "api_agent" {
  source = "../../modules/ecs-service"

  project      = var.project
  environment  = var.environment
  service_name = "api-agent"

  cluster_arn        = aws_ecs_cluster.main.arn
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids

  container_image = local.placeholder_image_for["api-agent"]
  container_port  = 8086
  cpu             = 512
  memory          = 1024
  desired_count   = var.ecs_desired_count

  task_role_arn      = module.task_role_api_agent.role_arn
  execution_role_arn = module.task_execution_role.role_arn
  log_kms_key_arn    = aws_kms_key.logs.arn

  # Sidecar 옵션 — 08 §2
  enable_otel_sidecar     = var.enable_otel_sidecar
  enable_firelens_sidecar = var.enable_firelens_sidecar
  otel_config_ssm_arn     = var.otel_config_ssm_arn
  firelens_config_s3_arn  = var.firelens_config_s3_arn

  alb_listener_arn       = local.app_listener_arn
  alb_security_group_id  = aws_security_group.alb.id
  listener_rule_priority = 140
  listener_path_patterns = ["/agent/*"]

  environment_vars = local.common_env
}

# ----------------------------------------------------------------------------
# 데이터 SG 인바운드 — 각 데이터 저장소를 실제로 쓰는 워크로드만 접근 허용
# 매트릭스 §3 의 sg-aurora·sg-elasticache·sg-msk 인바운드 규칙
# ----------------------------------------------------------------------------

locals {
  # Aurora 에 접근하는 워크로드만 (api-auth, api-bookmark, api-emerging-tech, api-agent)
  aurora_consumers = [
    module.api_auth.security_group_id,
    module.api_emerging_tech.security_group_id,
    module.api_bookmark.security_group_id,
    module.api_agent.security_group_id,
  ]

  # ElastiCache 에 접근하는 워크로드 (api-auth, api-chatbot, api-bookmark)
  cache_consumers = [
    module.api_auth.security_group_id,
    module.api_chatbot.security_group_id,
    module.api_bookmark.security_group_id,
  ]

  # MSK 에 접근하는 워크로드 (api-emerging-tech, api-bookmark, api-agent)
  msk_consumers = [
    module.api_emerging_tech.security_group_id,
    module.api_bookmark.security_group_id,
    module.api_agent.security_group_id,
  ]
}

# Aurora SG 인바운드 추가 (count=0 일 때 모듈이 없으므로 try)
resource "aws_security_group_rule" "aurora_from_workloads" {
  for_each = var.enable_aurora ? toset(local.aurora_consumers) : []

  type                     = "ingress"
  description              = "MySQL 3306 from workload"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  source_security_group_id = each.value
  security_group_id        = module.aurora[0].security_group_id
}

resource "aws_security_group_rule" "cache_from_workloads" {
  for_each = var.enable_elasticache ? toset(local.cache_consumers) : []

  type                     = "ingress"
  description              = "Valkey 6379 from workload"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = each.value
  security_group_id        = module.cache[0].security_group_id
}

locals {
  msk_security_group_id = try(
    module.msk[0].security_group_id,
    module.msk_provisioned[0].security_group_id,
    null,
  )
}

resource "aws_security_group_rule" "msk_from_workloads" {
  for_each = var.enable_msk && local.msk_security_group_id != null ? toset(local.msk_consumers) : []

  type                     = "ingress"
  description              = "MSK IAM SASL 9098 from workload"
  from_port                = 9098
  to_port                  = 9098
  protocol                 = "tcp"
  source_security_group_id = each.value
  security_group_id        = local.msk_security_group_id
}
