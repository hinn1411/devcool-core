# Here, not in app: app is destroyed nightly (ADR-0013) and its logs would go with it.
# AWS-managed encryption: a CMK adds $1/month.
#trivy:ignore:AWS-0017
resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name}/api"
  retention_in_days = 14
}
