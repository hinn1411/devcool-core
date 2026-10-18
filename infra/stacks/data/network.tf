# Inputs from the network stack, through the SSM contract in network/ssm.tf.
locals {
  network_ssm_prefix = "/devcool/${var.environment}/network"
}

data "aws_ssm_parameter" "private_subnet_ids" {
  name = "${local.network_ssm_prefix}/private_subnet_ids"
}

data "aws_ssm_parameter" "sg_aurora_id" {
  name = "${local.network_ssm_prefix}/sg_aurora_id"
}
