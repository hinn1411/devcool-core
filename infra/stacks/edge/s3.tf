data "aws_caller_identity" "current" {}

# SPA build output (P5-T14 syncs it with --delete). Private; only this distribution reads it, via OAC.
# Terraform owns the bucket, not its objects: a Terraform-managed index.html would drift after a sync.
# Versioning off: every deploy is a full sync from git, so git is the history.
resource "aws_s3_bucket" "spa" {
  bucket = "devcool-${var.environment}-spa-${data.aws_caller_identity.current.account_id}"
}

# SSE-S3, not a KMS key: a CMK adds $1/month plus request costs (ADR-0013), and OAC
# would need a key policy for CloudFront.
#trivy:ignore:AWS-0132
resource "aws_s3_bucket_server_side_encryption_configuration" "spa" {
  bucket = aws_s3_bucket.spa.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "spa" {
  bucket = aws_s3_bucket.spa.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "spa" {
  bucket = aws_s3_bucket.spa.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

data "aws_iam_policy_document" "spa" {
  # OAC: the CloudFront service principal, limited to this distribution. No s3:ListBucket,
  # so a missing key returns 403, not 404 (and doesn't reveal which keys exist).
  statement {
    sid       = "AllowCloudFrontRead"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.spa.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.this.arn]
    }
  }

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.spa.arn,
      "${aws_s3_bucket.spa.arn}/*",
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

resource "aws_s3_bucket_policy" "spa" {
  bucket = aws_s3_bucket.spa.id
  policy = data.aws_iam_policy_document.spa.json

  # The policy and the public access block both modify bucket settings; serialize them.
  depends_on = [aws_s3_bucket_public_access_block.spa]
}
