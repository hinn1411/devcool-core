# The zone already exists and the registrar points at its name servers, so it's adopted,
# not created: a new zone gets new NS and the cert validation below would never resolve.
# It also holds hand-made SES records (DKIM, mail.<domain> MX/SPF); Terraform leaves
# records it doesn't declare alone. The import block is a no-op after the first apply.
import {
  to = aws_route53_zone.this
  id = var.hosted_zone_id
}

resource "aws_route53_zone" "this" {
  name    = var.domain_name
  comment = "HostedZone created by Route53 Registrar" # kept as imported, so plans don't drift

  lifecycle {
    prevent_destroy = true # NS and the registrar delegation must never change (ADR-0013)
  }
}

# Apex → CloudFront. origin.<domain> → ALB belongs to app (P2-T04), so it follows the ALB's lifecycle.
resource "aws_route53_record" "apex" {
  for_each = toset(["A", "AAAA"])

  zone_id = aws_route53_zone.this.zone_id
  name    = var.domain_name
  type    = each.key

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false # not supported for CloudFront targets
  }
}
