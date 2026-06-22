# Aurora 클러스터 파라미터 그룹
# - 04 §1.2 의 권장 파라미터를 적용
# - DB-level 파라미터 그룹은 (특별한 요구 없으면) 기본값 사용

resource "aws_rds_cluster_parameter_group" "this" {
  name_prefix = "${local.cluster_name}-cluster-"
  family      = "aurora-mysql8.0"
  description = "Cluster parameter group for ${local.cluster_name}"

  # 슬로우 쿼리 로깅
  parameter {
    name  = "slow_query_log"
    value = "1"
  }

  parameter {
    name  = "long_query_time"
    value = "1" # 1초 초과 쿼리 기록
  }

  parameter {
    name  = "log_output"
    value = "FILE"
  }

  # 일반 로그는 파일로 (CloudWatch Logs export 됨)
  parameter {
    name  = "general_log"
    value = "0" # 운영 시 비활성. 디버그 시에만 1.
  }

  # SQL 모드 — 엄격 모드
  parameter {
    name  = "sql_mode"
    value = "STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO"
  }

  # 시간대 UTC 고정
  parameter {
    name  = "time_zone"
    value = "UTC"
  }

  # 캐릭터셋 utf8mb4
  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_0900_ai_ci"
  }

  # 트랜잭션 격리 수준 — REPEATABLE-READ (MySQL 기본)
  parameter {
    name  = "transaction_isolation"
    value = "REPEATABLE-READ"
  }

  tags = local.common_tags

  lifecycle {
    create_before_destroy = true
  }
}
