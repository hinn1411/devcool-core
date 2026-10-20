# For deploy.yml (P2-T08) and env-power (P2-T09). Plain String: gha-plan can't decrypt.
locals {
  ssm_prefix = "${local.ssm}/app"

  ssm_params = {
    cluster_name           = aws_ecs_cluster.this.name
    api_service_name       = module.api.service_name
    api_task_family        = module.api.task_definition_family
    api_container_name     = module.api.container_name
    api_subnet_ids         = local.input["network/private_subnet_ids"]
    api_security_group_ids = local.input["network/sg_api_id"]
  }
}

resource "aws_ssm_parameter" "app" {
  for_each = local.ssm_params

  name  = "${local.ssm_prefix}/${each.key}"
  type  = "String"
  value = each.value
}
