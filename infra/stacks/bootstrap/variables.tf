# Inputs for the bootstrap stack. Only budget_alert_emails has no default (set it in budget.local.tfvars, which is gitignored).
variable "region" {
  description = "AWS region for the stack."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment tag. Bootstrap is account-wide, so it is shared by every env."
  type        = string
  default     = "shared"
}

variable "monthly_budget_usd" {
  description = "Monthly AWS cost envelope in USD (playbook §6)."
  type        = number
  default     = 20
}

variable "budget_alert_emails" {
  description = "Recipients of budget alerts. Set them in an untracked *.local.tfvars or TF_VAR_budget_alert_emails, never in git."
  type        = set(string)

  validation {
    condition = length(var.budget_alert_emails) > 0 && alltrue([
      for e in var.budget_alert_emails : can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", e))
    ])
    error_message = "budget_alert_emails must hold at least one email address."
  }
}

variable "github_repo" {
  description = "GitHub repo (owner/name) whose Actions workflows may assume the gha-* roles."
  type        = string
  default     = "hinn1411/devcool-core"
}

variable "deploy_environment" {
  description = "GitHub environment whose jobs may assume gha-deploy. Its deployment-branch rule (master only) lives in GitHub, not here."
  type        = string
  default     = "aws-deploy"
}
