output "url" {
  description = "Public URL of the SPA and API."
  value       = "https://${var.domain_name}"
}

output "distribution_domain_name" {
  description = "CloudFront's own *.cloudfront.net name (for debugging DNS)."
  value       = aws_cloudfront_distribution.this.domain_name
}

output "name_servers" {
  description = "Must match the registrar's NS for the domain; they never change (ADR-0013)."
  value       = aws_route53_zone.this.name_servers
}

output "spa_bucket_name" {
  description = "SPA bucket; upload a placeholder index.html until P5 (infra/README.md)."
  value       = aws_s3_bucket.spa.bucket
}

output "ssm_prefix" {
  description = "SSM path consumer stacks read from."
  value       = local.ssm_prefix
}
