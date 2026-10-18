variable "region" {
  description = "AWS region for the stack."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name, used in resource names and SSM paths."
  type        = string
}

# The default lives here, not in the shared envs/<env>.tfvars: every stack reads that file,
# and the others would warn about an undeclared variable.
variable "media_bucket_name" {
  description = "Media bucket name. Matches the app's aws.s3.bucket default (application-ecs.properties)."
  type        = string
  default     = "devcool-media"
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL version. Auto-pause at 0 ACU needs >= 16.3 (ADR-0009)."
  type        = string
  default     = "16.15"
}
