output "vpc_id" {
  description = "VPC id."
  value       = module.vpc.vpc_id
}

output "private_subnet_ids" {
  description = "Private subnets for ECS tasks, Aurora and endpoint ENIs."
  value       = module.vpc.private_subnets
}

output "public_subnet_ids" {
  description = "Public subnets for the ALB and NAT."
  value       = module.vpc.public_subnets
}

output "security_group_ids" {
  description = "SG chain: alb → api → aurora, plus the endpoints SG."
  value = {
    alb    = aws_security_group.alb.id
    api    = aws_security_group.api.id
    aurora = aws_security_group.aurora.id
    vpce   = aws_security_group.vpce.id
  }
}

output "ssm_prefix" {
  description = "SSM path consumer stacks read from."
  value       = local.ssm_prefix
}
