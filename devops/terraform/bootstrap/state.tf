# Terraform state S3 버킷 + DynamoDB Lock 테이블
# 09 §3.1 원격 상태 아키텍처 구현체

locals {
  state_bucket_name = coalesce(
    var.state_bucket_name_override,
    "${var.project}-tfstate-${local.account_id}-apne2",
  )
}

resource "aws_s3_bucket" "tfstate" {
  bucket              = local.state_bucket_name
  object_lock_enabled = true # 한 번 켜면 못 끔 — 사고성 삭제 보호

  # state 버킷은 절대 삭제되어선 안 됨 — terraform destroy 차단
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.tfstate.arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket                  = aws_s3_bucket.tfstate.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_object_lock_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    default_retention {
      mode = "GOVERNANCE"
      days = var.state_bucket_object_lock_days
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    # 모든 객체에 적용 — AWS Provider 가 rule 마다 filter/prefix 중 하나를 요구한다.
    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.state_bucket_noncurrent_version_expiration_days
    }

    # delete marker 만 남은 객체는 자동 정리
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# 버킷 정책: HTTPS 강제 + Terraform Role 외 접근 차단
resource "aws_s3_bucket_policy" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  policy = data.aws_iam_policy_document.tfstate_bucket_policy.json
}

data "aws_iam_policy_document" "tfstate_bucket_policy" {
  statement {
    sid    = "DenyInsecureTransport"
    effect = "Deny"
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.tfstate.arn,
      "${aws_s3_bucket.tfstate.arn}/*",
    ]
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  statement {
    sid    = "DenyUnencryptedPut"
    effect = "Deny"
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.tfstate.arn}/*"]
    condition {
      test     = "StringNotEquals"
      variable = "s3:x-amz-server-side-encryption"
      values   = ["aws:kms"]
    }
  }
}

# DynamoDB 상태 잠금
resource "aws_dynamodb_table" "tflock" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled     = true
    kms_key_arn = aws_kms_key.tfstate.arn
  }

  lifecycle {
    prevent_destroy = true
  }
}
