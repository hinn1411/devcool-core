output "budget_name" {
  description = "Name for `aws budgets describe-notifications-for-budget --budget-name`."
  value       = aws_budgets_budget.monthly.name
}
