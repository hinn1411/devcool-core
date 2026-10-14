# GitHub Actions → AWS without stored keys: each job gets a signed OIDC token, and STS
# swaps it for short-lived role credentials if the token's claims match the trust policy.
locals {
  github_oidc_host = "token.actions.githubusercontent.com"
  tfstate_lockfile = "${aws_s3_bucket.tfstate.arn}/*.tflock"
}

# No thumbprint_list: for GitHub, IAM validates the certificate against its own trusted CAs.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://${local.github_oidc_host}"
  client_id_list = ["sts.amazonaws.com"]
}

# ---------------------------------------------------------------------------
# gha-plan: `terraform plan` on pull requests. Read-only, except the state lock.
# The gha-* roles are deliberately not named devcool-*, so gha-deploy's IAM rights
# (below) can't touch them.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "gha_plan_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Any PR in this repo. Fork PRs never get an id-token, so they can't reach this.
    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:sub"
      values   = ["repo:${var.github_repo}:pull_request"]
    }
  }
}

resource "aws_iam_role" "gha_plan" {
  name                 = "gha-plan"
  description          = "GitHub Actions: terraform plan on PRs (read-only)."
  assume_role_policy   = data.aws_iam_policy_document.gha_plan_trust.json
  max_session_duration = 3600
}

# Plan refreshes every resource type the stacks manage, so a curated list would break
# with each new stack. ReadOnlyAccess can read state (and the secrets in it); the trust
# policy above is what keeps that to this repo's PRs.
resource "aws_iam_role_policy_attachment" "gha_plan_readonly" {
  role       = aws_iam_role.gha_plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

# use_lockfile writes <key>.tflock next to the state, even for a plan.
data "aws_iam_policy_document" "gha_plan_state_lock" {
  statement {
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = [local.tfstate_lockfile]
  }
}

resource "aws_iam_role_policy" "gha_plan_state_lock" {
  name   = "tfstate-lock"
  role   = aws_iam_role.gha_plan.id
  policy = data.aws_iam_policy_document.gha_plan_state_lock.json
}

# ---------------------------------------------------------------------------
# gha-deploy: infra-apply, deploy and env-power, only from jobs in the deploy environment.
# Not `ref:refs/heads/master`: every master-context job gets that subject (issue_comment,
# schedule, workflow_dispatch…), including claude.yml's @claude job. A job only gets
# `environment:<name>` by declaring it, and GitHub limits that environment to master.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "gha_deploy_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:sub"
      values   = ["repo:${var.github_repo}:environment:${var.deploy_environment}"]
    }
  }
}

resource "aws_iam_role" "gha_deploy" {
  name                 = "gha-deploy"
  description          = "GitHub Actions: terraform apply and ECS deploys (environment ${var.deploy_environment})."
  assume_role_policy   = data.aws_iam_policy_document.gha_deploy_trust.json
  max_session_duration = 3600
}

# PowerUserAccess covers every service the stacks use; its only IAM rights are the
# service-linked-role ones ECS, ELB and RDS need (brief decision, 2026-10-13). It's a documented exception to "no * resources": the apply touches
# ~40 resource types across five stacks.
resource "aws_iam_role_policy_attachment" "gha_deploy_poweruser" {
  role       = aws_iam_role.gha_deploy.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

# The IAM part: only roles and policies the stacks own (named devcool-*).
# Accepted risk: it could create an over-privileged devcool-* role; a permission
# boundary is the later hardening.
data "aws_iam_policy_document" "gha_deploy_iam" {
  statement {
    sid = "ManageDevcoolRolesAndPolicies"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:UpdateRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:GetRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:CreatePolicy",
      "iam:DeletePolicy",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListPolicyVersions",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:TagPolicy",
      "iam:UntagPolicy",
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/devcool-*",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/devcool-*",
    ]
  }

  # ECS needs the task and execution roles passed to it; nothing else may receive them.
  statement {
    sid       = "PassDevcoolRolesToEcsTasks"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/devcool-*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "gha_deploy_iam" {
  name   = "devcool-iam"
  role   = aws_iam_role.gha_deploy.id
  policy = data.aws_iam_policy_document.gha_deploy_iam.json
}
