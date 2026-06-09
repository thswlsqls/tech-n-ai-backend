# Terraform state 전용 KMS CMK
# - state 버킷 SSE-KMS, DynamoDB Lock 테이블 SSE-KMS 모두에 사용
# - 매트릭스 §1 의 `tfstate` 키 — 환경 무관 단일 공유 키 (bootstrap 에서만 정의)

resource "aws_kms_key" "tfstate" {
  description             = "Terraform state encryption (state S3 + DynamoDB Lock)"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  policy = data.aws_iam_policy_document.tfstate_kms.json
}

resource "aws_kms_alias" "tfstate" {
  name          = "alias/${var.project}/tfstate"
  target_key_id = aws_kms_key.tfstate.key_id
}

data "aws_caller_identity" "current" {}

# KMS 키 정책: 루트 계정 풀 권한 + Terraform Role 들이 키 사용 가능
data "aws_iam_policy_document" "tfstate_kms" {
  statement {
    sid    = "EnableRootPermissions"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${local.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  # Terraform Role 들에게 키 사용 허용
  # - 부트스트랩 첫 실행 시점에는 Role 이 아직 없으므로, 키 정책에 Role 들을 직접 넣지 않고
  #   Role 생성 후 키 grant 또는 정책 수정으로 처리할 수 있다.
  # - 본 모듈은 Role 을 같은 plan 에서 함께 만들기 때문에 Role ARN 을 정책에 포함한다.
  statement {
    sid    = "AllowTerraformRoles"
    effect = "Allow"
    principals {
      type = "AWS"
      identifiers = concat(
        [
          aws_iam_role.gha_terraform_readonly.arn,
        ],
        [for r in aws_iam_role.gha_terraform_apply : r.arn],
      )
    }
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:GenerateDataKey*",
      "kms:DescribeKey",
    ]
    resources = ["*"]
  }
}
