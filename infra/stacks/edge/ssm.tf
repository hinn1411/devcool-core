# Cross-stack values for app (P2-T04) and frontend-deploy (P5-T14). Plain String, never
# SecureString: gha-plan can't decrypt (bootstrap review #4).
locals {
  ssm_prefix = "/devcool/${var.environment}/edge"

  ssm_params = {
    zone_id         = aws_route53_zone.this.zone_id
    cert_arn        = aws_acm_certificate_validation.this.certificate_arn
    domain_name     = var.domain_name
    distribution_id = aws_cloudfront_distribution.this.id
    spa_bucket_name = aws_s3_bucket.spa.bucket
  }
}

resource "aws_ssm_parameter" "edge" {
  for_each = local.ssm_params

  name  = "${local.ssm_prefix}/${each.key}"
  type  = "String"
  value = each.value
}

# String like the rest: defence in depth only, and it's in edge's state either way.
resource "aws_ssm_parameter" "origin_verify" {
  name  = "${local.ssm_prefix}/origin_verify"
  type  = "String"
  value = random_password.origin_verify.result
}
