# Destroyed on every hibernate and re-created on resume (ADR-0013): nothing here holds state.
# The log group is in data, the cert and zone in edge.
locals {
  name   = "devcool-${var.environment}"
  domain = local.input["edge/domain_name"]
}

# Container Insights off: it bills per metric, and P7 sends metrics through the OTel sidecar.
#trivy:ignore:AWS-0034
resource "aws_ecs_cluster" "this" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}
