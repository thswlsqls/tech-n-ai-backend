output "service_name" {
  description = "ECS Service 이름."
  value       = aws_ecs_service.this.name
}

output "service_arn" {
  description = "ECS Service ARN."
  value       = aws_ecs_service.this.id
}

output "task_definition_arn" {
  description = "최초 Task Definition ARN. 이후 revision 은 CI 가 등록하고 update-service 로 배포."
  value       = aws_ecs_task_definition.this.arn
}

output "blue_target_group_arn" {
  description = "Blue Target Group ARN."
  value       = aws_lb_target_group.blue.arn
}

output "green_target_group_arn" {
  description = "Green Target Group ARN."
  value       = aws_lb_target_group.green.arn
}

output "security_group_id" {
  description = "워크로드 SG ID. 다른 워크로드 SG 의 인바운드 소스로 사용."
  value       = aws_security_group.this.id
}

output "log_group_name" {
  description = "CloudWatch Log Group 이름."
  value       = local.log_group_name
}

output "listener_rule_arn" {
  description = "ALB Listener Rule ARN."
  value       = aws_lb_listener_rule.this.arn
}

output "alarm_5xx_arn" {
  description = "5xx 비율 알람 ARN."
  value       = aws_cloudwatch_metric_alarm.alb_5xx_rate.arn
}

output "alarm_latency_p95_arn" {
  description = "p95 latency 알람 ARN."
  value       = aws_cloudwatch_metric_alarm.target_response_time.arn
}
