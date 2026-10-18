output "db_endpoint" {
  description = "Aurora writer endpoint."
  value       = aws_rds_cluster.aurora.endpoint
}

output "db_master_secret_arn" {
  description = "RDS-managed master secret (keys: username, password)."
  value       = aws_rds_cluster.aurora.master_user_secret[0].secret_arn
}

output "jwt_secret_arns" {
  description = "JWT secrets to fill out of band (infra/README.md)."
  value       = { for k, s in aws_secretsmanager_secret.jwt : k => s.arn }
}

output "media_bucket_name" {
  description = "Media bucket name."
  value       = aws_s3_bucket.media.bucket
}

output "ssm_prefix" {
  description = "SSM path consumer stacks read from."
  value       = local.ssm_prefix
}
