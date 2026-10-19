# Inputs from network, data and edge, through their SSM contracts (*/ssm.tf). All plain
# String/StringList, so insecure_value is safe, except origin_verify, which stays sensitive.
locals {
  ssm = "/devcool/${var.environment}"

  ssm_inputs = toset([
    "network/vpc_id",
    "network/private_subnet_ids",
    "network/public_subnet_ids",
    "network/sg_alb_id",
    "network/sg_api_id",
    "data/db_endpoint",
    "data/db_port",
    "data/db_name",
    "data/db_master_secret_arn",
    "data/media_bucket_name",
    "data/media_bucket_arn",
    "data/jwt_access_secret_arn",
    "data/jwt_refresh_secret_arn",
    "data/log_group_api",
    "edge/zone_id",
    "edge/cert_arn",
    "edge/domain_name",
  ])

  input = { for k, p in data.aws_ssm_parameter.input : k => p.insecure_value }
}

data "aws_ssm_parameter" "input" {
  for_each = local.ssm_inputs
  name     = "${local.ssm}/${each.key}"
}

data "aws_ssm_parameter" "origin_verify" {
  name = "${local.ssm}/edge/origin_verify"
}

data "aws_caller_identity" "current" {}

data "aws_ecr_repository" "app" {
  name = "devcool" # bootstrap/ecr.tf
}
