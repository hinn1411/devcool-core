locals {
  # Fargate in a private subnet with no NAT needs exactly these to start a task:
  # ECR (api + dkr) to pull, Logs for awslogs, Secrets Manager for injected secrets.
  # Image layers come from S3, through the gateway endpoint below.
  interface_endpoints = var.enable_interface_endpoints ? toset(["ecr.api", "ecr.dkr", "logs", "secretsmanager"]) : toset([])
}

module "vpc_endpoints" {
  source  = "terraform-aws-modules/vpc/aws//modules/vpc-endpoints"
  version = "6.7.3"

  vpc_id                = module.vpc.vpc_id
  create_security_group = false
  security_group_ids    = [aws_security_group.vpce.id]

  endpoints = merge(
    {
      # Gateway endpoints are free, so this one is always on.
      s3 = {
        service         = "s3"
        service_type    = "Gateway"
        route_table_ids = module.vpc.private_route_table_ids
        tags            = { Name = "${local.name}-s3" }
      }
    },
    {
      for svc in local.interface_endpoints : replace(svc, ".", "_") => {
        service             = svc
        private_dns_enabled = true
        subnet_ids          = module.vpc.private_subnets
        tags                = { Name = "${local.name}-${svc}" }
      }
    }
  )
}
