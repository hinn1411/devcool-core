variable "region" {
  description = "AWS region for the stack."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name, used in resource names and SSM paths."
  type        = string
}

# No default: the pipeline owns the tag (ADR-0003); pass the running one (-var image_tag=…).
variable "image_tag" {
  description = "ECR tag (git SHA) for the first task definition."
  type        = string
}

# Defaults here, not in the shared tfvars: the other stacks would warn on an unknown var.
variable "api_min_count" {
  description = "api tasks at minimum (ADR-0013 prices 1). 0 only for the very first apply, before the migrate task has run."
  type        = number
  default     = 1
}

variable "api_max_count" {
  description = "api tasks at most, under CPU target tracking."
  type        = number
  default     = 2
}
