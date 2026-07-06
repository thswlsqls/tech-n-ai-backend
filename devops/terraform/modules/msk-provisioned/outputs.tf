output "cluster_arn" {
  description = "MSK 클러스터 ARN."
  value       = aws_msk_cluster.this.arn
}

output "cluster_name" {
  description = "클러스터 이름."
  value       = aws_msk_cluster.this.cluster_name
}

output "bootstrap_brokers_sasl_iam" {
  description = "IAM SASL bootstrap brokers (port 9098)."
  value       = aws_msk_cluster.this.bootstrap_brokers_sasl_iam
}

output "bootstrap_brokers_tls" {
  description = "TLS bootstrap brokers (port 9094)."
  value       = aws_msk_cluster.this.bootstrap_brokers_tls
}

output "kafka_version" {
  description = "현재 Kafka 버전."
  value       = aws_msk_cluster.this.kafka_version
}

output "current_version" {
  description = "MSK 클러스터 버전 식별자 (configuration 갱신용)."
  value       = aws_msk_cluster.this.current_version
}

output "configuration_arn" {
  description = "Configuration ARN."
  value       = aws_msk_configuration.this.arn
}

output "log_group_name" {
  description = "브로커 로그 CloudWatch Log Group 이름."
  value       = aws_cloudwatch_log_group.broker.name
}

output "security_group_id" {
  description = "MSK SG ID."
  value       = aws_security_group.this.id
}

output "open_monitoring_prometheus_jmx_endpoint_list" {
  description = "Prometheus JMX 스크레이프 엔드포인트 (브로커호스트:11001 리스트)."
  value = try(
    [for b in split(",", aws_msk_cluster.this.bootstrap_brokers_sasl_iam) : "${split(":", b)[0]}:11001"],
    null,
  )
}
