data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"] # skip Local/Wavelength zones
  }
}

locals {
  name = "devcool-${var.environment}"
  azs  = slice(data.aws_availability_zones.available.names, 0, 2)
}

# Public subnets: ALB and the NAT. Private subnets: ECS tasks, Aurora, endpoint ENIs.
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "6.7.3"

  name = local.name
  cidr = var.vpc_cidr
  azs  = local.azs

  public_subnets  = [for i in range(2) : cidrsubnet(var.vpc_cidr, 8, i)]
  private_subnets = [for i in range(2) : cidrsubnet(var.vpc_cidr, 8, i + 10)]

  # One NAT for both AZs: half the cost of one per AZ; an AZ outage only loses dev egress.
  enable_nat_gateway = var.enable_nat
  single_nat_gateway = true

  # Interface endpoints with private DNS need both.
  enable_dns_hostnames = true
  enable_dns_support   = true

  map_public_ip_on_launch = false

  # Adopt the default SG and leave it with no rules, so nothing can use it by accident.
  manage_default_security_group  = true
  default_security_group_ingress = []
  default_security_group_egress  = []
}
