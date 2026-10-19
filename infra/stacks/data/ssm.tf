# Cross-stack values for app (P2-T04). Plain String, never SecureString: gha-plan can't
# decrypt, so plans of consumer stacks would fail (bootstrap review #4). ARNs, not values.
locals {
  ssm_prefix = "/devcool/${var.environment}/data"

  ssm_params = {
    db_endpoint            = aws_rds_cluster.aurora.endpoint
    db_port                = tostring(aws_rds_cluster.aurora.port)
    db_name                = aws_rds_cluster.aurora.database_name
    db_master_secret_arn   = aws_rds_cluster.aurora.master_user_secret[0].secret_arn
    media_bucket_name      = aws_s3_bucket.media.bucket
    media_bucket_arn       = aws_s3_bucket.media.arn
    jwt_access_secret_arn  = aws_secretsmanager_secret.jwt["access"].arn
    jwt_refresh_secret_arn = aws_secretsmanager_secret.jwt["refresh"].arn
    log_group_api          = aws_cloudwatch_log_group.api.name
  }
}

resource "aws_ssm_parameter" "data" {
  for_each = local.ssm_params

  name  = "${local.ssm_prefix}/${each.key}"
  type  = "String"
  value = each.value
}
