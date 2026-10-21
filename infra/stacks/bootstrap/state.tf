# Remote state for every stack (`devcool/<env>/<stack>.tfstate`), this one included.
# State can hold secrets in plain text, so treat this bucket like a database.
resource "aws_s3_bucket" "tfstate" {
  bucket = "devcool-tfstate-${data.aws_caller_identity.current.account_id}"

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

# SSE-S3, not a KMS key: a CMK adds $1/month plus request costs (playbook §6).
# Accepted in docs/journal/briefs/P2-T01.md (Decisions: state encryption).
#trivy:ignore:AWS-0132
resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# Old state versions are the recovery path for a corrupted state; 90 days is plenty.
# Every lock leaves a delete marker; drop it once its versions have expired (review #6).
resource "aws_s3_bucket_lifecycle_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    id     = "expire-noncurrent-state"
    status = "Enabled"
    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    expiration {
      expired_object_delete_marker = true
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

data "aws_iam_policy_document" "tfstate_tls_only" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.tfstate.arn,
      "${aws_s3_bucket.tfstate.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

# A resource-policy deny holds even if a role escalates (review #5): only the admin may erase
# history or change these settings. Check the ARN first (`aws sts get-caller-identity`); a
# wrong one locks everyone but the root user out of this policy.
data "aws_iam_policy_document" "tfstate_protect_history" {
  statement {
    sid    = "ProtectStateHistory"
    effect = "Deny"
    actions = [
      "s3:DeleteObjectVersion",
      "s3:PutBucketVersioning",
      "s3:PutLifecycleConfiguration",
      "s3:PutBucketPolicy",
      "s3:DeleteBucketPolicy",
      "s3:DeleteBucket",
    ]
    resources = [
      aws_s3_bucket.tfstate.arn,
      "${aws_s3_bucket.tfstate.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "ArnNotEquals"
      variable = "aws:PrincipalArn"
      values   = [var.state_admin_principal_arn]
    }
  }
}

data "aws_iam_policy_document" "tfstate" {
  source_policy_documents = [
    data.aws_iam_policy_document.tfstate_tls_only.json,
    data.aws_iam_policy_document.tfstate_protect_history.json,
  ]
}

resource "aws_s3_bucket_policy" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  policy = data.aws_iam_policy_document.tfstate.json

  # The policy and the public access block both modify bucket settings; serialize them.
  depends_on = [aws_s3_bucket_public_access_block.tfstate]
}
