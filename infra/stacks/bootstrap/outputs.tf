output "budget_name" {
  description = "Name for `aws budgets describe-notifications-for-budget --budget-name`."
  value       = aws_budgets_budget.monthly.name
}

output "tfstate_bucket" {
  description = "State bucket for every stack's S3 backend."
  value       = aws_s3_bucket.tfstate.bucket
}

output "gha_plan_role_arn" {
  description = "Set as the AWS_PLAN_ROLE_ARN repo variable (not a secret)."
  value       = aws_iam_role.gha_plan.arn
}

output "gha_deploy_role_arn" {
  description = "Set as the AWS_DEPLOY_ROLE_ARN repo variable (not a secret)."
  value       = aws_iam_role.gha_deploy.arn
}

output "ecr_repository_url" {
  description = "Push target for deploy.yml (`<url>:<git sha>`)."
  value       = aws_ecr_repository.app.repository_url
}
