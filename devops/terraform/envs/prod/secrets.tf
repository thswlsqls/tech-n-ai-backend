# Secrets Manager 시크릿 스텁
# - 매트릭스 §4 정의: 5종 (aurora-credentials 는 Aurora Managed Master User Password 가 자동 생성)
# - 본 stub 은 secret 자체만 생성하고, 값(secret_version)은 별도 워크플로(보안팀)에서 입력.
# - lifecycle.ignore_changes 로 Terraform 이 재실행해도 값을 덮어쓰지 않음.

# ----------------------------------------------------------------------------
# JWT 서명 키 — HMAC SHA-512, 듀얼 키 회전 (active/next, 180일)
# 매트릭스 §4 갱신 — Spring 가 active 와 next 둘 다 검증, 회전 시 active 만 next 로 승격
# ----------------------------------------------------------------------------

resource "aws_secretsmanager_secret" "jwt_signing_key" {
  name        = "${var.project}/${var.environment}/jwt-signing-key"
  description = "API Auth JWT HMAC SHA-512 듀얼 키 (active/next). 180일 무중단 회전."
  kms_key_id  = aws_kms_key.auth.arn

  tags = local.common_tags
}

# 초기 placeholder 값 — 실 운영 시 별도 보안 워크플로로 갱신 후 lifecycle 무시
resource "aws_secretsmanager_secret_version" "jwt_signing_key_initial" {
  secret_id = aws_secretsmanager_secret.jwt_signing_key.id

  secret_string = jsonencode({
    active_kid = "v1-PLACEHOLDER"
    active_key = "REPLACE_WITH_BASE64_ENCODED_512BIT_RANDOM"
    next_kid   = "v2-PLACEHOLDER"
    next_key   = "REPLACE_WITH_BASE64_ENCODED_512BIT_RANDOM"
    rotated_at = "2026-01-01T00:00:00Z"
  })

  lifecycle {
    ignore_changes = [secret_string] # 실 값은 별도 입력 후 Terraform 무관여
  }
}

# ----------------------------------------------------------------------------
# OpenAI API 키 — 분기 수동 회전
# ----------------------------------------------------------------------------

resource "aws_secretsmanager_secret" "openai_api_key" {
  name        = "${var.project}/${var.environment}/openai-api-key"
  description = "OpenAI API 키. 분기 수동 회전."
  kms_key_id  = aws_kms_key.ai.arn

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "openai_api_key_initial" {
  secret_id     = aws_secretsmanager_secret.openai_api_key.id
  secret_string = "sk-PLACEHOLDER-REPLACE-WITH-REAL-KEY"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

# ----------------------------------------------------------------------------
# MongoDB Atlas connection URI — X.509 인증 권장
# ----------------------------------------------------------------------------

resource "aws_secretsmanager_secret" "mongodb_uri" {
  name        = "${var.project}/${var.environment}/mongodb-uri"
  description = "MongoDB Atlas connection URI (X.509 인증)."
  kms_key_id  = aws_kms_key.data.arn

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "mongodb_uri_initial" {
  secret_id     = aws_secretsmanager_secret.mongodb_uri.id
  secret_string = "mongodb+srv://PLACEHOLDER:PLACEHOLDER@cluster.mongodb.net/techai?retryWrites=true&w=majority"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

# ----------------------------------------------------------------------------
# ElastiCache AUTH 토큰 — module 이 random_password 로 생성한 값 저장
# 90일 수동 회전 (Lifecycle 정책 별도 워크플로)
# ----------------------------------------------------------------------------

resource "aws_secretsmanager_secret" "elasticache_auth_token" {
  count = var.enable_elasticache ? 1 : 0

  name        = "${var.project}/${var.environment}/elasticache-auth-token"
  description = "Valkey AUTH 토큰. ElastiCache 모듈이 random_password 로 생성."
  kms_key_id  = aws_kms_key.data.arn

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "elasticache_auth_token_initial" {
  count = var.enable_elasticache ? 1 : 0

  secret_id     = aws_secretsmanager_secret.elasticache_auth_token[0].id
  secret_string = module.cache[0].auth_token

  lifecycle {
    ignore_changes = [secret_string] # 토큰 회전 시 별도 워크플로 처리
  }
}
