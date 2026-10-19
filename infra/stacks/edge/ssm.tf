# Cross-stack values: app (P2-T04) reads zone_id, cert_arn and domain_name for the ALB
# listener and the origin.<domain> alias; frontend-deploy (P5-T14) reads the bucket and the
# distribution. Plain String, never SecureString: gha-plan can't decrypt (bootstrap review #4).
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
