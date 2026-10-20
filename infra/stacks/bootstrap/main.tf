# Shared by state.tf, oidc.tf, boundary.tf and budget.tf.
data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
}
