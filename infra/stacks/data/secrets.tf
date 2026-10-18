# JWT signing keys (application.properties: security.jwt.*). Terraform owns the containers
# only. The values are set once out of band (infra/README.md), so they never reach state,
# and gha-plan's refresh never needs GetSecretValue (bootstrap review #4).
# Two secrets so access and refresh keys can be rotated or revoked independently.
resource "aws_secretsmanager_secret" "jwt" {
  for_each = toset(["access", "refresh"])

  name        = "devcool/${var.environment}/jwt-${each.key}"
  description = "JWT ${each.key}-token signing secret (min 32 chars). Value set out of band."
}
