# 매트릭스 §1 의 추가 KMS 키 — auth, ai, logs
# data, s3-app 은 main.tf 에서 정의

resource "aws_kms_key" "auth" {
  description             = "techai ${var.environment} — api-auth JWT 서명 + RDS IAM envelope"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_alias" "auth" {
  name          = "alias/${var.project}/${var.environment}-auth"
  target_key_id = aws_kms_key.auth.key_id
}

resource "aws_kms_key" "ai" {
  description             = "techai ${var.environment} — OpenAI/Cohere 키 암호화 (Bedrock 권한은 코드 도입 시 별도 ADR — D-12)"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_alias" "ai" {
  name          = "alias/${var.project}/${var.environment}-ai"
  target_key_id = aws_kms_key.ai.key_id
}

resource "aws_kms_key" "logs" {
  description             = "techai ${var.environment} — CloudWatch Logs / Athena 암호화"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  # CloudWatch Logs 가 키 사용 가능
  policy = data.aws_iam_policy_document.logs_kms.json
}

resource "aws_kms_alias" "logs" {
  name          = "alias/${var.project}/${var.environment}-logs"
  target_key_id = aws_kms_key.logs.key_id
}

data "aws_iam_policy_document" "logs_kms" {
  statement {
    sid    = "EnableRoot"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  statement {
    sid    = "AllowCloudWatchLogs"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["logs.${var.region}.amazonaws.com"]
    }
    actions = [
      "kms:Encrypt*",
      "kms:Decrypt*",
      "kms:ReEncrypt*",
      "kms:GenerateDataKey*",
      "kms:Describe*",
    ]
    resources = ["*"]
    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:*"]
    }
  }

  # CloudWatch 알람이 암호화된 alerts SNS 토픽에 통지를 발행하려면
  # cloudwatch.amazonaws.com 이 이 키로 데이터 키를 만들고 복호화할 수 있어야 한다.
  statement {
    sid    = "AllowCloudWatchAlarmsToPublishToSns"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }
    actions = [
      "kms:Decrypt",
      "kms:GenerateDataKey*",
    ]
    resources = ["*"]
  }
}
