variable "region" {
  description = "AWS region for the stack."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name, used in resource names and SSM paths."
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR. Subnets are /24s carved from it (public 0-1, private 10-11)."
  type        = string
  default     = "10.0.0.0/16"
}

variable "enable_nat" {
  description = "Single NAT gateway for private-subnet egress. Off only on hibernate, when no tasks run (ADR-0013)."
  type        = bool
  default     = true
}

variable "enable_interface_endpoints" {
  description = "ECR api/dkr, Logs and Secrets Manager interface endpoints in both AZs. Billed hourly per AZ, so off on hibernate (ADR-0013)."
  type        = bool
  default     = true
}
