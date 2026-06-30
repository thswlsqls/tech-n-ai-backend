# 알람 통지 SNS Topic
# 구독자는 tfvars 의 alert_emails 로 주입한다. 비우면 구독을 만들지 않아
# 기존 동작(구독자를 IaC 밖에서 수동 추가)을 그대로 유지한다.

resource "aws_sns_topic" "alerts" {
  name              = "${var.project}-${var.environment}-alerts"
  kms_master_key_id = aws_kms_key.logs.id

  tags = local.common_tags
}

resource "aws_sns_topic_subscription" "alerts_email" {
  for_each = toset(var.alert_emails)

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = each.value
}
