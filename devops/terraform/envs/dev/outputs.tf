output "vpc_id" {
  description = "dev VPC ID. 다른 stack 의 terraform_remote_state 가 참조."
  value       = module.network.vpc_id
}

output "vpc_cidr" {
  description = "dev VPC CIDR."
  value       = module.network.vpc_cidr
}

output "private_subnet_ids" {
  description = "Private-App 서브넷 ID 목록."
  value       = module.network.private_subnet_ids
}

output "data_subnet_ids" {
  description = "Private-Data 서브넷 ID 목록."
  value       = module.network.data_subnet_ids
}

output "public_subnet_ids" {
  description = "Public 서브넷 ID 목록 (ALB 위치)."
  value       = module.network.public_subnet_ids
}

output "vpce_security_group_id" {
  description = "VPCE 전용 SG ID."
  value       = module.network.vpce_security_group_id
}

output "kms_data_key_arn" {
  description = "데이터 계층 KMS 키 ARN."
  value       = aws_kms_key.data.arn
}

output "kms_s3_app_key_arn" {
  description = "S3 어플리케이션 KMS 키 ARN."
  value       = aws_kms_key.s3_app.arn
}

output "task_execution_role_arn" {
  description = "ECS Task Execution Role ARN. taskdef 의 executionRoleArn 에 사용."
  value       = module.task_execution_role.role_arn
}

output "uploads_bucket_arn" {
  description = "어플리케이션 업로드 버킷 ARN."
  value       = module.uploads_bucket.bucket_arn
}

# ----------------------------------------------------------------------------
# 데이터 계층 출력
# ----------------------------------------------------------------------------

output "aurora_writer_endpoint" {
  description = "Aurora Writer 엔드포인트. enable_aurora=false 면 null."
  value       = try(module.aurora[0].cluster_endpoint, null)
}

output "aurora_reader_endpoint" {
  description = "Aurora Reader 엔드포인트."
  value       = try(module.aurora[0].reader_endpoint, null)
}

output "aurora_cluster_resource_id" {
  description = "Aurora 클러스터 Resource ID. IAM DB 인증 정책에 사용."
  value       = try(module.aurora[0].cluster_resource_id, null)
}

output "aurora_master_user_secret_arn" {
  description = "Managed Master User Password 시크릿 ARN."
  value       = try(module.aurora[0].master_user_secret_arn, null)
  sensitive   = true
}

output "aurora_security_group_id" {
  description = "Aurora SG. 워크로드가 outbound 3306 으로 사용."
  value       = try(module.aurora[0].security_group_id, null)
}

output "cache_primary_endpoint" {
  description = "Valkey Primary 엔드포인트."
  value       = try(module.cache[0].primary_endpoint, null)
}

output "cache_security_group_id" {
  description = "Valkey SG."
  value       = try(module.cache[0].security_group_id, null)
}

output "msk_bootstrap_brokers" {
  description = "MSK IAM SASL bootstrap. Serverless 또는 Provisioned 중 활성된 것."
  value = try(
    module.msk[0].bootstrap_brokers_sasl_iam,
    module.msk_provisioned[0].bootstrap_brokers_sasl_iam,
    null,
  )
}

output "msk_mode" {
  description = "활성 MSK 모드 (serverless / provisioned / disabled)."
  value = (
    var.enable_msk == false ? "disabled" :
    var.use_msk_provisioned ? "provisioned" : "serverless"
  )
}

output "msk_security_group_id" {
  description = "MSK SG (Serverless 또는 Provisioned)."
  value       = try(module.msk[0].security_group_id, module.msk_provisioned[0].security_group_id, null)
}

# ----------------------------------------------------------------------------
# 워크로드 출력
# ----------------------------------------------------------------------------

output "ecs_cluster_arn" {
  description = "ECS Cluster ARN."
  value       = aws_ecs_cluster.main.arn
}

output "ecs_cluster_name" {
  description = "ECS Cluster 이름."
  value       = aws_ecs_cluster.main.name
}

output "alb_dns_name" {
  description = "ALB DNS name. 외부 진입점 (dev 는 HTTP)."
  value       = aws_lb.main.dns_name
}

output "alb_arn" {
  description = "ALB ARN."
  value       = aws_lb.main.arn
}

output "alb_security_group_id" {
  description = "ALB SG ID."
  value       = aws_security_group.alb.id
}

output "alb_listener_http_arn" {
  description = "HTTP Listener ARN."
  value       = aws_lb_listener.http.arn
}

output "service_security_group_ids" {
  description = "워크로드별 SG ID map."
  value = {
    api-gateway       = module.api_gateway.security_group_id
    api-auth          = module.api_auth.security_group_id
    api-emerging-tech = module.api_emerging_tech.security_group_id
    api-chatbot       = module.api_chatbot.security_group_id
    api-bookmark      = module.api_bookmark.security_group_id
    api-agent         = module.api_agent.security_group_id
  }
}

output "task_role_arns" {
  description = "워크로드별 Task Role ARN map."
  value = {
    api-gateway       = module.task_role_api_gateway.role_arn
    api-auth          = module.task_role_api_auth.role_arn
    api-emerging-tech = module.task_role_api_emerging_tech.role_arn
    api-chatbot       = module.task_role_api_chatbot.role_arn
    api-bookmark      = module.task_role_api_bookmark.role_arn
    api-agent         = module.task_role_api_agent.role_arn
  }
}

output "alerts_sns_topic_arn" {
  description = "알람 통지 SNS Topic ARN. 이메일·Slack 구독 추가 가능."
  value       = aws_sns_topic.alerts.arn
}

output "kms_key_arns" {
  description = "환경 KMS 키 ARN map (data, s3-app, auth, ai, logs)."
  value = {
    data   = aws_kms_key.data.arn
    s3-app = aws_kms_key.s3_app.arn
    auth   = aws_kms_key.auth.arn
    ai     = aws_kms_key.ai.arn
    logs   = aws_kms_key.logs.arn
  }
}
