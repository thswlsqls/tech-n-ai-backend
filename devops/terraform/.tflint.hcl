# tflint 설정 — AWS 룰셋 활성화
# 공식: https://github.com/terraform-linters/tflint-ruleset-aws

config {
  call_module_type = "all"
  force            = false
}

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

plugin "aws" {
  enabled = true
  version = "0.47.0"
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}

# 잘못된 인스턴스/노드 타입 탐지 — 트리가 실제 쓰는 리소스 기준
# - ElastiCache 는 replication_group 을 쓰므로 cluster 룰이 아니라 replication_group 룰로 검사
# - Aurora(aws_rds_cluster_instance)·MSK(aws_msk_cluster) 는 tflint-ruleset-aws 에 전용 invalid_type 룰이 없어 미검사
rule "aws_instance_invalid_type" { enabled = true }
rule "aws_db_instance_invalid_type" { enabled = true }
rule "aws_elasticache_replication_group_invalid_type" { enabled = true }

# 빈 리소스 이름·태그 누락 검사
rule "terraform_naming_convention" {
  enabled = true
  format  = "snake_case"
}

rule "terraform_required_version" { enabled = true }
rule "terraform_required_providers" { enabled = true }
rule "terraform_unused_declarations" { enabled = true }
rule "terraform_documented_outputs" { enabled = true }
rule "terraform_documented_variables" { enabled = true }
