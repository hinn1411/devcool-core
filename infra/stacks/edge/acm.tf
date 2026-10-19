# One cert for both names: CloudFront serves <domain>, and the ALB's HTTPS listener (P2-T04)
# serves origin.<domain> with the same ARN, read from SSM. ACM certs have no hourly charge.
resource "aws_acm_certificate" "this" {
  domain_name               = var.domain_name
  subject_alternative_names = ["origin.${var.domain_name}"]
  validation_method         = "DNS"

  lifecycle {
    # CloudFront switches to the new ARN in the same apply, but the ALB listener (app) only
    # does on app's next apply. Changing the SANs: apply edge, then app, then edge again
    # (the old cert can't be deleted while the listener still uses it).
    create_before_destroy = true
  }
}

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for o in aws_acm_certificate.this.domain_validation_options : o.domain_name => {
      name   = o.resource_record_name
      type   = o.resource_record_type
      record = o.resource_record_value
    }
  }

  zone_id         = aws_route53_zone.this.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 300
  allow_overwrite = true # ACM may reuse a CNAME from an earlier cert for the same name
}

# Blocks until ACM sees the records, so CloudFront is only created with an ISSUED cert.
resource "aws_acm_certificate_validation" "this" {
  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [for r in aws_route53_record.cert_validation : r.fqdn]
}
