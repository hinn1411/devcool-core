# Cross-stack values for data (P2-T03) and app (P2-T04). Plain String/StringList, never
# SecureString: gha-plan can't decrypt, so plans of consumer stacks would fail (bootstrap review #4).
locals {
  ssm_prefix = "/devcool/${var.environment}/network"

  ssm_params = {
    vpc_id             = { type = "String", value = module.vpc.vpc_id }
    private_subnet_ids = { type = "StringList", value = join(",", module.vpc.private_subnets) }
    public_subnet_ids  = { type = "StringList", value = join(",", module.vpc.public_subnets) }
    sg_alb_id          = { type = "String", value = aws_security_group.alb.id }
    sg_api_id          = { type = "String", value = aws_security_group.api.id }
    sg_aurora_id       = { type = "String", value = aws_security_group.aurora.id }
  }
}

resource "aws_ssm_parameter" "network" {
  for_each = local.ssm_params

  name  = "${local.ssm_prefix}/${each.key}"
  type  = each.value.type
  value = each.value.value
}
