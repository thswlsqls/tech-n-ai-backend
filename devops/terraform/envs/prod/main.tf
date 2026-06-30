# 환경 조립 계층 (dev/beta/prod 공통 — 차이는 tfvars 로만 둔다)
# 본 파일은 모듈 호출만 하고 자원을 직접 선언하지 않는다 (09 §2.2 레이어 규칙).

# ----------------------------------------------------------------------------
# 1. KMS 키 (어플리케이션 데이터·로그·S3 용)
#    매트릭스 §1 — `{env}-data`, `{env}-logs`, `{env}-s3-app`
#    세션 2a 에서는 dev 비용 최소화를 위해 우선 2개만 (data, logs/s3 통합).
#    세션 2b 진행 시 추가 분리.
# ----------------------------------------------------------------------------

resource "aws_kms_key" "data" {
  description             = "techai dev — Aurora·MongoDB·ElastiCache·MSK 데이터 암호화"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_alias" "data" {
  name          = "alias/${var.project}/${var.environment}-data"
  target_key_id = aws_kms_key.data.key_id
}

resource "aws_kms_key" "s3_app" {
  description             = "techai dev — 어플리케이션 S3 버킷 암호화"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_alias" "s3_app" {
  name          = "alias/${var.project}/${var.environment}-s3-app"
  target_key_id = aws_kms_key.s3_app.key_id
}

# ----------------------------------------------------------------------------
# 2. 네트워크
#    dev 비용 최소화: single_nat_gateway=true, VPC Endpoint 활성 (NAT 부하 감소)
# ----------------------------------------------------------------------------

module "network" {
  source = "../../modules/network"

  project     = var.project
  environment = var.environment
  cidr_block  = var.vpc_cidr
  azs         = var.azs

  enable_nat_gateway   = true
  single_nat_gateway   = var.single_nat_gateway   # dev/beta=true(비용), prod=false(AZ 격리)
  enable_vpc_endpoints = var.enable_vpc_endpoints # 시드 단계 false 권장 (NAT 폴백)
  enable_flow_logs     = true
}

# ----------------------------------------------------------------------------
# 3. 어플리케이션 S3 버킷 (샘플)
#    실제 버킷 정의는 후속 세션에서 chatbot uploads 등으로 확장
# ----------------------------------------------------------------------------

module "uploads_bucket" {
  source = "../../modules/s3-bucket"

  bucket_name = "${var.project}-${var.environment}-app-uploads"
  kms_key_arn = aws_kms_key.s3_app.arn

  versioning_enabled  = true
  block_public_access = true

  lifecycle_rules = [
    {
      id                                         = "expire-old-versions"
      enabled                                    = true
      noncurrent_version_expiration_days         = 30
      noncurrent_version_transition_glacier_days = 7
    }
  ]
}

# ----------------------------------------------------------------------------
# 4. ECS Task Execution Role (단일 공유)
#    이미지 pull, 로그 stream, secrets 주입을 담당
# ----------------------------------------------------------------------------

module "task_execution_role" {
  source = "../../modules/iam-role-workload"

  project            = var.project
  environment        = var.environment
  workload_name      = "task-execution"
  role_name_override = "${var.project}-${var.environment}-task-execution"
  trust_service      = "ecs-tasks.amazonaws.com"

  managed_policy_arns = [
    "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy",
  ]

  inline_policies = {
    secrets-and-ssm-read = data.aws_iam_policy_document.execution_extras.json
  }
}

data "aws_caller_identity" "current" {}

# ----------------------------------------------------------------------------
# 5. 데이터 계층 — Aurora MySQL (Serverless v2)
#    워크로드 SG 가 아직 없으므로 (세션 2c 도착 전) allowed_security_group_ids 는 빈 리스트.
#    세션 2c 에서 ecs-service 모듈 도입 후 SG ID 들을 주입한다.
# ----------------------------------------------------------------------------

module "aurora" {
  count  = var.enable_aurora ? 1 : 0
  source = "../../modules/aurora-mysql"

  project     = var.project
  environment = var.environment

  vpc_id          = module.network.vpc_id
  data_subnet_ids = module.network.data_subnet_ids
  kms_key_arn     = aws_kms_key.data.arn

  engine_mode               = var.aurora_engine_mode
  serverlessv2_min_capacity = var.aurora_min_acu
  serverlessv2_max_capacity = var.aurora_max_acu
  instance_count            = var.aurora_instance_count
  instance_class            = var.aurora_instance_class

  db_name = var.aurora_db_name

  allowed_security_group_ids = [] # 워크로드 SG 인바운드는 services.tf 의 별도 rule 로 추가

  backup_retention_period      = var.aurora_backup_retention_period
  deletion_protection          = var.aurora_deletion_protection
  skip_final_snapshot          = var.aurora_skip_final_snapshot
  storage_type                 = var.aurora_storage_type
  performance_insights_enabled = var.aurora_performance_insights_enabled
}

# ----------------------------------------------------------------------------
# 6. 데이터 계층 — ElastiCache Valkey (단일 노드, 비용 최소)
# ----------------------------------------------------------------------------

module "cache" {
  count  = var.enable_elasticache ? 1 : 0
  source = "../../modules/elasticache-valkey"

  project     = var.project
  environment = var.environment

  vpc_id          = module.network.vpc_id
  data_subnet_ids = module.network.data_subnet_ids
  kms_key_arn     = aws_kms_key.data.arn

  node_type                = var.cache_node_type
  replicas_per_node_group  = var.cache_replicas_per_node_group
  multi_az_enabled         = var.cache_multi_az_enabled
  snapshot_retention_limit = var.cache_snapshot_retention_limit

  auth_mode = "auth_token"

  allowed_security_group_ids = [] # services.tf 별도 rule
}

# ----------------------------------------------------------------------------
# 7. 메시징 — MSK Serverless (기본 비활성, 사용 시점에 enable_msk=true)
# ----------------------------------------------------------------------------

module "msk" {
  count  = var.enable_msk && !var.use_msk_provisioned ? 1 : 0
  source = "../../modules/msk-serverless"

  project     = var.project
  environment = var.environment

  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids

  allowed_security_group_ids = [] # services.tf 별도 rule
}

module "msk_provisioned" {
  count  = var.enable_msk && var.use_msk_provisioned ? 1 : 0
  source = "../../modules/msk-provisioned"

  project     = var.project
  environment = var.environment

  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.data_subnet_ids # MSK Provisioned 는 Private-Data 권장
  kms_key_arn        = aws_kms_key.data.arn

  kafka_version        = var.msk_kafka_version
  broker_count         = var.msk_broker_count
  broker_instance_type = var.msk_broker_instance_type

  allowed_security_group_ids = [] # services.tf 별도 rule
}

# ----------------------------------------------------------------------------
# Execution Role 추가 인라인 — Secrets Manager / SSM 의 워크로드 시크릿 read
# ----------------------------------------------------------------------------

data "aws_iam_policy_document" "execution_extras" {
  statement {
    sid = "ReadSecrets"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${var.project}/${var.environment}/*",
    ]
  }
  statement {
    sid = "ReadSsmParameters"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = [
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/${var.environment}/*",
    ]
  }
  statement {
    sid = "DecryptSecrets"
    actions = [
      "kms:Decrypt",
    ]
    resources = [aws_kms_key.data.arn, aws_kms_key.s3_app.arn]
  }
}
