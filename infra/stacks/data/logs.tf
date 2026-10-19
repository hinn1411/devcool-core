# The api log group lives here, not in app: app is destroyed every night (ADR-0013), and its
# logs would go with it. It's billed for storage only, so it fits the hibernated baseline.
# The worker's group joins it in P6.
# AWS-managed encryption, not a CMK: a CMK adds $1/month (ADR-0013).
#trivy:ignore:AWS-0017
resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name}/api"
  retention_in_days = 14
}
