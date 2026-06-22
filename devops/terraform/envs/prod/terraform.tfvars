project     = "techai"
environment = "prod"
region      = "ap-northeast-2"
vpc_cidr    = "10.30.0.0/16"

# 네트워크 — AZ별 NAT (격리)
single_nat_gateway   = false
enable_vpc_endpoints = true

# Aurora Provisioned — Multi-AZ + I/O-Optimized
aurora_engine_mode                  = "provisioned"
aurora_instance_count               = 3 # Writer 1 + Reader 2 (3 AZ 분산, 04 §1.3 prod 권장)
aurora_instance_class               = "db.r7g.large"
aurora_storage_type                 = "aurora-iopt1"
aurora_backup_retention_period      = 30
aurora_deletion_protection          = true
aurora_skip_final_snapshot          = false
aurora_performance_insights_enabled = true

# ElastiCache — Multi-AZ
cache_node_type                = "cache.t4g.small"
cache_replicas_per_node_group  = 1
cache_multi_az_enabled         = true
cache_snapshot_retention_limit = 7

# MSK Provisioned (D-8: Kafka 3.9.x KRaft)
enable_aurora            = true
enable_elasticache       = true
enable_msk               = true
use_msk_provisioned      = true
msk_kafka_version        = "3.9.x.kraft"
msk_broker_count         = 3
msk_broker_instance_type = "kafka.m7g.large"

# ECS — 2~3 task baseline, 6 max
ecs_desired_count         = 2
ecs_autoscaling_min_count = 2
ecs_autoscaling_max_count = 6
