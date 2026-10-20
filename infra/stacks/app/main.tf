# Stateless: destroyed on hibernate, re-created on resume (ADR-0013).
locals {
  name   = "devcool-${var.environment}"
  domain = local.input["edge/domain_name"]
}

# Container Insights off: billed per metric; P7 uses the OTel sidecar.
#trivy:ignore:AWS-0034
resource "aws_ecs_cluster" "this" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}
