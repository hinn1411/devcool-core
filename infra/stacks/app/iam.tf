# Execution role: the ECS agent's image pull, logs and task-def secrets. Task role: the app's
# own AWS calls (S3 media). Both carry bootstrap's boundary, which gha-deploy requires.
locals {
  account_id   = data.aws_caller_identity.current.account_id
  boundary_arn = "arn:aws:iam::${local.account_id}:policy/workload-boundary" # bootstrap/boundary.tf

  # Hard-coded: a data source would need iam:GetPolicy on AWS-managed policies (review #3c).
  ecs_execution_policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"

  db_secret_arn = local.input["data/db_master_secret_arn"]
  task_secret_arns = [
    local.db_secret_arn,
    local.input["data/jwt_access_secret_arn"],
    local.input["data/jwt_refresh_secret_arn"],
  ]
}

# Only ECS tasks of this account (confused-deputy guard).
data "aws_iam_policy_document" "ecs_tasks_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }
}

resource "aws_iam_role" "api_execution" {
  name                 = "${local.name}-api-execution"
  path                 = "/"
  description          = "ECS agent for api tasks: image pull, logs, injected secrets."
  assume_role_policy   = data.aws_iam_policy_document.ecs_tasks_trust.json
  permissions_boundary = local.boundary_arn
}

resource "aws_iam_role_policy_attachment" "api_execution" {
  role       = aws_iam_role.api_execution.name
  policy_arn = local.ecs_execution_policy_arn
}

# The exact ARNs: the RDS-managed secret is named rds!cluster-…, outside devcool/<env>/*.
data "aws_iam_policy_document" "api_execution_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = local.task_secret_arns
  }
}

resource "aws_iam_role_policy" "api_execution_secrets" {
  name   = "secrets"
  role   = aws_iam_role.api_execution.id
  policy = data.aws_iam_policy_document.api_execution_secrets.json
}

resource "aws_iam_role" "api_task" {
  name                 = "${local.name}-api-task"
  path                 = "/"
  description          = "The api app's own AWS calls: S3 media."
  assume_role_policy   = data.aws_iam_policy_document.ecs_tasks_trust.json
  permissions_boundary = local.boundary_arn
}

# S3StorageAdapter: putObject on upload, presigned getObject on read.
data "aws_iam_policy_document" "api_task_media" {
  statement {
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${local.input["data/media_bucket_arn"]}/*"]
  }
}

resource "aws_iam_role_policy" "api_task_media" {
  name   = "media"
  role   = aws_iam_role.api_task.id
  policy = data.aws_iam_policy_document.api_task_media.json
}
