data "aws_caller_identity" "current" {}

# Account-wide cost guardrail (P2-T13). It only alerts: AWS Budgets data lags by hours,
# and stopping spend is the env-power workflow's job (P2-T09).
resource "aws_budgets_budget" "monthly" {
  name         = "Devcool budget" # imported from the hand-made week-1 budget
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # The console's default view, kept explicit so imports and plans don't drift.
  billing_view_arn = "arn:aws:billing::${data.aws_caller_identity.current.account_id}:billingview/primary"

  # Track usage, not the invoice: promo credits would otherwise keep the cost at $0.
  metrics = ["UnblendedCost"]
  filter_expression {
    not {
      dimensions {
        key    = "RECORD_TYPE"
        values = ["Credit", "Refund"]
      }
    }
  }

  # Warning: half the envelope is gone.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 10
    threshold_type             = "ABSOLUTE_VALUE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.budget_alert_emails
  }

  # Stop: hibernate now and keep it hibernated until next month.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 18
    threshold_type             = "ABSOLUTE_VALUE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.budget_alert_emails
  }

  # Early warning: fires before the lagging actual alerts once the trend overshoots.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = var.budget_alert_emails
  }
}
