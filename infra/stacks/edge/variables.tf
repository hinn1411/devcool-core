variable "region" {
  description = "AWS region for the stack. CloudFront only accepts ACM certs from us-east-1."
  type        = string
  default     = "us-east-1"

  validation {
    condition     = var.region == "us-east-1"
    error_message = "The CloudFront cert must be in us-east-1; another region needs a provider alias for acm.tf."
  }
}

variable "environment" {
  description = "Environment name, used in resource names and SSM paths."
  type        = string
}

# The defaults live here, not in the shared envs/<env>.tfvars: every stack reads that file,
# and the others would warn about an undeclared variable.
variable "domain_name" {
  description = "Apex domain served by CloudFront. The API origin is origin.<domain_name> (ADR-0013)."
  type        = string
  default     = "dev-cool.click"
}

variable "hosted_zone_id" {
  description = "The domain's existing public zone (created at registration, NS already delegated). Imported, never created."
  type        = string
  default     = "Z08037022ZW7LDOE7CIOE"
}

variable "origin_read_timeout" {
  description = "Seconds CloudFront waits for the ALB (AWS default 30, max 120 without a quota increase). An Aurora resume takes >= 30 s (P2-T04 carry-over)."
  type        = number
  default     = 60
}
