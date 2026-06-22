# 표준 S3 버킷 모듈
# - SSE-KMS, Versioning, BPA, Lifecycle, Object Lock, HTTPS 강제 정책 모두 포함
# - 09 §5.7 spec 구현

locals {
  common_tags = merge(
    {
      ManagedBy = "Terraform"
      Module    = "s3-bucket"
    },
    var.tags,
  )
}

resource "aws_s3_bucket" "this" {
  bucket              = var.bucket_name
  object_lock_enabled = var.object_lock_enabled
  force_destroy       = var.force_destroy

  tags = local.common_tags
}

# Versioning
resource "aws_s3_bucket_versioning" "this" {
  bucket = aws_s3_bucket.this.id

  versioning_configuration {
    status = var.versioning_enabled ? "Enabled" : "Suspended"
  }
}

# SSE-KMS
resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = var.kms_key_arn
    }
    bucket_key_enabled = true # 비용 절감 — KMS request 90%↓
  }
}

# Block Public Access
resource "aws_s3_bucket_public_access_block" "this" {
  count = var.block_public_access ? 1 : 0

  bucket                  = aws_s3_bucket.this.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Object Lock 설정 (object_lock_enabled=true 인 경우만)
resource "aws_s3_bucket_object_lock_configuration" "this" {
  count = var.object_lock_enabled ? 1 : 0

  bucket = aws_s3_bucket.this.id

  rule {
    default_retention {
      mode = var.object_lock_mode
      days = var.object_lock_days
    }
  }
}

# Lifecycle
resource "aws_s3_bucket_lifecycle_configuration" "this" {
  count = length(var.lifecycle_rules) > 0 ? 1 : 0

  bucket = aws_s3_bucket.this.id

  dynamic "rule" {
    for_each = var.lifecycle_rules
    content {
      id     = rule.value.id
      status = rule.value.enabled ? "Enabled" : "Disabled"

      filter {
        prefix = rule.value.prefix
      }

      abort_incomplete_multipart_upload {
        days_after_initiation = rule.value.abort_incomplete_multipart_upload_days
      }

      dynamic "transition" {
        for_each = rule.value.transition_to_standard_ia_days != null ? [rule.value.transition_to_standard_ia_days] : []
        content {
          days          = transition.value
          storage_class = "STANDARD_IA"
        }
      }

      dynamic "transition" {
        for_each = rule.value.transition_to_intelligent_tiering_days != null ? [rule.value.transition_to_intelligent_tiering_days] : []
        content {
          days          = transition.value
          storage_class = "INTELLIGENT_TIERING"
        }
      }

      dynamic "transition" {
        for_each = rule.value.transition_to_glacier_days != null ? [rule.value.transition_to_glacier_days] : []
        content {
          days          = transition.value
          storage_class = "GLACIER"
        }
      }

      dynamic "transition" {
        for_each = rule.value.transition_to_deep_archive_days != null ? [rule.value.transition_to_deep_archive_days] : []
        content {
          days          = transition.value
          storage_class = "DEEP_ARCHIVE"
        }
      }

      dynamic "expiration" {
        for_each = rule.value.expiration_days != null ? [rule.value.expiration_days] : []
        content {
          days = expiration.value
        }
      }

      dynamic "noncurrent_version_expiration" {
        for_each = rule.value.noncurrent_version_expiration_days != null ? [rule.value.noncurrent_version_expiration_days] : []
        content {
          noncurrent_days = noncurrent_version_expiration.value
        }
      }

      dynamic "noncurrent_version_transition" {
        for_each = rule.value.noncurrent_version_transition_glacier_days != null ? [rule.value.noncurrent_version_transition_glacier_days] : []
        content {
          noncurrent_days = noncurrent_version_transition.value
          storage_class   = "GLACIER"
        }
      }
    }
  }
}
