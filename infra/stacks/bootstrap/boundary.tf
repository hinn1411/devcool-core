# Permission boundary for every devcool-* role (bootstrap review #2). gha-deploy may only
# create or change a devcool-* role that carries it, so a role it creates can never hold more
# than this, whatever policies are attached. It's named outside devcool-*, so gha-deploy's
# policy/devcool-* rights can't edit it. Later phases widen it here, by a hand apply:
# SQS/SNS (P6), Bedrock (P8), CloudWatch metrics (P4).
data "aws_iam_policy_document" "workload_boundary" {
  # ECR's auth token has no resource-level permissions; the pulls below are scoped to the repo.
  #trivy:ignore:AWS-0057
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
    ]
    resources = [aws_ecr_repository.app.arn]
  }

  statement {
    sid     = "EcsLogs"
    actions = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = [
      "arn:aws:logs:${var.region}:${local.account_id}:log-group:/ecs/devcool-*",
      "arn:aws:logs:${var.region}:${local.account_id}:log-group:/ecs/devcool-*:log-stream:*",
    ]
  }

  # The stacks' own secrets, plus the RDS-managed master secret, whose name RDS picks.
  statement {
    sid     = "ReadSecrets"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:${var.region}:${local.account_id}:secret:devcool/*",
      "arn:aws:secretsmanager:${var.region}:${local.account_id}:secret:rds!cluster-*",
    ]
  }

  # Media objects only: devcool-* alone would also match the state bucket.
  statement {
    sid       = "MediaObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:aws:s3:::devcool-media*/*"]
  }
}

resource "aws_iam_policy" "workload_boundary" {
  name        = "workload-boundary"
  description = "Permission boundary required on every devcool-* role (bootstrap review #2)."
  policy      = data.aws_iam_policy_document.workload_boundary.json
}
