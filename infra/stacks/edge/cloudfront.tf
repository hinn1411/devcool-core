# Single origin (ADR-0011): the SPA from S3 by default; /api/*, /ws and the deploy smoke
# test go to the ALB at origin.<domain>. app (P2-T04) owns that alias, so the ALB can be
# recreated on every resume while this distribution never changes. Until app exists, or
# while it's hibernated, the API paths return CloudFront's 502 and the SPA still loads (ADR-0013).

locals {
  spa_origin_id = "spa-s3"
  api_origin_id = "api-alb"
  api_methods   = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
}

# Shared with app's ALB listener rule via SSM; stable, since edge survives hibernate.
resource "random_password" "origin_verify" {
  length  = 40
  special = false # sent as a header value; alphanumerics need no escaping
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

# Every viewer header, cookie and query string except Host: the ALB cert is for
# origin.<domain>, and CloudFront sends the origin's name as Host instead.
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

resource "aws_cloudfront_origin_access_control" "spa" {
  name                              = "devcool-${var.environment}-spa"
  description                       = "CloudFront → SPA bucket (SigV4)."
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# No WAF: a web ACL bills $5/month plus per rule and request, and nothing billed that way goes
# in edge without revisiting ADR-0013 (WAF is optional in P9).
# No access logs: dev only, and ALB/app logs cover the API paths (logging is P7).
#trivy:ignore:AWS-0011
#trivy:ignore:AWS-0010
resource "aws_cloudfront_distribution" "this" {
  enabled             = true
  comment             = "devcool ${var.environment}: SPA + API single origin"
  aliases             = [var.domain_name]
  default_root_object = "index.html"
  http_version        = "http2and3"
  is_ipv6_enabled     = true
  price_class         = "PriceClass_100" # North America + Europe edges only; the cheapest class

  origin {
    origin_id                = local.spa_origin_id
    domain_name              = aws_s3_bucket.spa.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.spa.id
  }

  origin {
    origin_id   = local.api_origin_id
    domain_name = "origin.${var.domain_name}"

    # The ALB forwards only requests carrying this (the SG admits any distribution).
    custom_header {
      name  = "X-Origin-Verify"
      value = random_password.origin_verify.result
    }

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      origin_read_timeout    = var.origin_read_timeout
    }
  }

  # No custom error responses: they apply to every behaviour, so an SPA 404 → index.html
  # rule would also turn /api/* JSON errors into HTML. SPA routing is a CloudFront Function
  # on this behaviour (P5).
  default_cache_behavior {
    target_origin_id       = local.spa_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
    compress               = true
  }

  # Exact path, not /actuator/*: only the health summary is public; the ALB checks readiness directly.
  ordered_cache_behavior {
    path_pattern             = "/actuator/health"
    target_origin_id         = local.api_origin_id
    viewer_protocol_policy   = "https-only"
    allowed_methods          = ["GET", "HEAD"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  # https-only rather than redirect-to-https: a 301 on a POST makes clients retry it as a GET.
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = local.api_origin_id
    viewer_protocol_policy   = "https-only"
    allowed_methods          = local.api_methods
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  # The WS upgrade is a GET; the origin request policy forwards Upgrade and ?ticket= (ADR-0011).
  ordered_cache_behavior {
    path_pattern             = "/ws"
    target_origin_id         = local.api_origin_id
    viewer_protocol_policy   = "https-only"
    allowed_methods          = ["GET", "HEAD"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.this.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}
