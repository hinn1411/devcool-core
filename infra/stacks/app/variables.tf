variable "region" {
  description = "AWS region for the stack."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name, used in resource names and SSM paths."
  type        = string
}

# No default: the pipeline owns the tag after the first apply (ADR-0003), so every plan and
# apply passes the tag that is running (`-var image_tag=<git sha>`).
variable "image_tag" {
  description = "ECR tag (git SHA) for the first task definition."
  type        = string
}

# The defaults live here, not in the shared envs/<env>.tfvars: every stack reads that file,
# and the others would warn about an undeclared variable.
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
