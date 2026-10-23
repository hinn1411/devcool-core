# GitHub Actions → AWS without stored keys: each job gets a signed OIDC token, and STS
# swaps it for short-lived role credentials if the token's claims match the trust policy.
locals {
  github_oidc_host = "token.actions.githubusercontent.com"
  tfstate_lockfile = "${aws_s3_bucket.tfstate.arn}/*.tflock"

  # The repo's sub template includes job_workflow_ref (review #7; infra/README.md), so each
  # role trusts named workflow files only, not every job with the same event or environment.
  workflow_ref = "${var.github_repo}/.github/workflows"
}

# No thumbprint_list: for GitHub, IAM validates the certificate against its own trusted CAs.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://${local.github_oidc_host}"
  client_id_list = ["sts.amazonaws.com"]
}

# ---------------------------------------------------------------------------
# gha-plan: `terraform plan` on pull requests. Read-only, except the state lock.
# The gha-* roles aren't devcool-*, so gha-deploy can't touch them, directly or through a
# role it creates (boundary.tf).
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

    # infra-plan.yml on any PR in this repo, not claude.yml's PR jobs. Fork PRs never get an
    # id-token. The only wildcard is the PR number.
    condition {
      test     = "StringLike"
      variable = "${local.github_oidc_host}:sub"
      values   = ["repo:${var.github_repo}:pull_request:job_workflow_ref:${local.workflow_ref}/infra-plan.yml@refs/pull/*/merge"]
    }
  }
}

resource "aws_iam_role" "gha_plan" {
  name                 = "gha-plan"
  description          = "GitHub Actions: terraform plan on PRs (read-only)."
  assume_role_policy   = data.aws_iam_policy_document.gha_plan_trust.json
  max_session_duration = 7200 # review #8
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
# gha-deploy: deploy and infra-apply (env-power in P2-T09), only from jobs in the deploy environment.
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
      values = [for wf in var.deploy_workflows :
        "repo:${var.github_repo}:environment:${var.deploy_environment}:job_workflow_ref:${local.workflow_ref}/${wf}@refs/heads/master"
      ]
    }
  }
}

resource "aws_iam_role" "gha_deploy" {
  name                 = "gha-deploy"
  description          = "GitHub Actions: terraform apply and ECS deploys (environment ${var.deploy_environment})."
  assume_role_policy   = data.aws_iam_policy_document.gha_deploy_trust.json
  max_session_duration = 7200 # resume can outlast 1 h (review #8)
}

# PowerUserAccess covers every service the stacks use; its only IAM rights are the
# service-linked-role ones ECS, ELB and RDS need (brief decision, 2026-10-13). It's a documented exception to "no * resources": the apply touches
# ~40 resource types across five stacks.
resource "aws_iam_role_policy_attachment" "gha_deploy_poweruser" {
  role       = aws_iam_role.gha_deploy.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

# The IAM part: only devcool-* roles and policies, and only roles carrying the boundary;
# without it, gha-deploy could create a role, attach AdministratorAccess and assume it.
data "aws_iam_policy_document" "gha_deploy_iam" {
  statement {
    sid = "ReadAndTagDevcoolRoles"
    actions = [
      "iam:GetRole",
      "iam:DeleteRole",
      "iam:UpdateRole",
      "iam:UpdateRoleDescription",
      # Unconditioned: the role stays bounded, so a new trust policy shares it, not escalates.
      "iam:UpdateAssumeRolePolicy",
      "iam:TagRole",
      "iam:ListRoleTags",
      "iam:UntagRole",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:GetRolePolicy",
    ]
    resources = ["arn:aws:iam::${local.account_id}:role/devcool-*"]
  }

  # The boundary after the call (Create/PutBoundary) or the current one (policy edits).
  statement {
    sid = "ChangeBoundedDevcoolRoles"
    actions = [
      "iam:CreateRole",
      "iam:PutRolePermissionsBoundary",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
    ]
    resources = ["arn:aws:iam::${local.account_id}:role/devcool-*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.workload_boundary.arn]
    }
  }

  # Attach also passes DenyAttachingOtherPolicies: only devcool-* and the ECS execution policy.
  statement {
    sid       = "DenyAttachingOtherPolicies"
    effect    = "Deny"
    actions   = ["iam:AttachRolePolicy"]
    resources = ["arn:aws:iam::${local.account_id}:role/devcool-*"]

    condition {
      test     = "ArnNotLike"
      variable = "iam:PolicyARN"
      values = [
        "arn:aws:iam::${local.account_id}:policy/devcool-*",
        "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy",
      ]
    }
  }

  statement {
    sid       = "KeepTheBoundary"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary"]
    resources = ["arn:aws:iam::${local.account_id}:role/devcool-*"]
  }

  statement {
    sid = "ManageDevcoolPolicies"
    actions = [
      "iam:CreatePolicy",
      "iam:DeletePolicy",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListPolicyVersions",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:TagPolicy",
      "iam:UntagPolicy",
      "iam:ListPolicyTags",
    ]
    resources = ["arn:aws:iam::${local.account_id}:policy/devcool-*"]
  }

  # ECS needs the task and execution roles passed to it; nothing else may receive them.
  statement {
    sid       = "PassDevcoolRolesToEcsTasks"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${local.account_id}:role/devcool-*"]

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
