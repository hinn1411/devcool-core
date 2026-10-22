# Improvements — Bootstrap stack (P2-T01 review)

**Date:** 2026-10-14
**PRs:** #127 (bootstrap: state bucket, OIDC roles, ECR), #128 (removed the OIDC trust test)
**Source:** `aws-architect` review of `infra/stacks/bootstrap/` against live AWS and GitHub, after P2-T01 merged
**Brief:** [`docs/journal/briefs/P2-T01.md`](../journal/briefs/P2-T01.md)

Each item is fixed in the task that first needs it. The tasks are linked back from the [phase-2 file](../plans/phases/phase-2-infra-cicd-walking-skeleton.md).

> Bootstrap is applied **by hand** (ADR-0003), and `gha-deploy` can't change it. Even when an item is listed under a task for another stack, its bootstrap edit is a manual `plan` + `apply` in `infra/stacks/bootstrap`, done before that task's CI apply.

**Already fine (no action):**
- OIDC provider: `aud = sts.amazonaws.com`, no thumbprint.
- Both trusts use `StringEquals` on `aud` and `sub`, with no wildcards.
- State bucket: versioning, SSE-S3, 4 public-access blocks, `BucketOwnerEnforced`, TLS-only policy, `prevent_destroy`.
- ECR: IMMUTABLE tags, scan on push, AES256.
- `default_tags` applied; provider and Terraform versions pinned.

---

## P2-T03 — `data` stack

### #4 · Medium · `gha-plan` can't plan stacks that read secret values
**Resolved in P2-T03:** `data` creates the JWT secrets without a `secret_version`; the values are set out of band ([`infra/README.md`](../../infra/README.md)), and the SSM params are `String`.

**File:** `infra/stacks/bootstrap/oidc.tf:55` (`ReadOnlyAccess`)

`ReadOnlyAccess` grants Secrets Manager `Describe*`/`List*` only, and KMS `Get*`/`Describe*`/`List*`. It doesn't grant `secretsmanager:GetSecretValue`, and it doesn't grant `kms:Decrypt`.

**Failure:** P2-T03 manages the JWT secrets with `aws_secretsmanager_secret_version`. Its refresh calls `GetSecretValue`, so every `infra-plan` on a PR fails with AccessDenied. SSM `SecureString` parameters fail the same way.

**Fix (recommended):** keep secret *values* out of Terraform:
- Create `aws_secretsmanager_secret` only, and set the value out of band with `aws secretsmanager put-secret-value`, or use a write-only argument.
- Keep cross-stack SSM parameters as `String`.

**Alternative:** grant `gha-plan` `secretsmanager:GetSecretValue` on `arn:aws:secretsmanager:us-east-1:<acct>:secret:devcool/*`. This is consistent with the already accepted "PR jobs can read state", but it widens the plan role.

### #3b · Medium · PassRole only allows `ecs-tasks.amazonaws.com`
**Not needed in P2-T03:** enhanced monitoring is off, and nothing in `data` passes a role (Aurora uses its service-linked role). Still applies to any later feature that passes one.

**File:** `infra/stacks/bootstrap/oidc.tf:162`

**Failure:** if Aurora enhanced monitoring is turned on, the apply fails, because it passes a role to `monitoring.rds.amazonaws.com`. VPC flow logs (P2-T02, if added) need `vpc-flow-logs.amazonaws.com` in the same way.

**Fix:** add the service to the `iam:PassedToService` values in the same change that introduces the feature. Don't add it in advance.

---

## P2-T04 — `ecs-service` module + `app` stack

### #2 · High · `gha-deploy` can escalate to admin
**File:** `infra/stacks/bootstrap/oidc.tf:116-169`

The brief described the risk as "it could create an over-privileged `devcool-*` role". In fact the role can escalate *itself*:
- **Path A:** `iam:CreateRole devcool-x`, trusting `gha-deploy` or an external account → `AttachRolePolicy …/AdministratorAccess` → `sts:AssumeRole`. PowerUserAccess allows `sts`.
- **Path B:** pass that role to ECS (`PassRole`) → `ecs:RunTask` with any image.

Through that admin role, it can then edit the `gha-*` roles, change the OIDC provider, or plant a persistent cross-account role. The comment at `oidc.tf:16-17` ("gha-deploy can't touch gha-*") is therefore misleading: it only blocks the *direct* edit.

**Failure:** a compromised master job, or a prompt-injected agent with the role, becomes account admin.

**Fix:** a permission boundary owned by bootstrap (named outside `devcool-*`), required on every role `gha-deploy` creates, plus a cap on which policies it may attach:
```hcl
# on iam:CreateRole and iam:PutRolePermissionsBoundary
condition {
  test     = "StringEquals"
  variable = "iam:PermissionsBoundary"
  values   = [aws_iam_policy.workload_boundary.arn]
}
# on iam:AttachRolePolicy
condition {
  test     = "ArnLike"
  variable = "iam:PolicyARN"
  values = [
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/devcool-*",
    "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy",
  ]
}
```
- Deny `iam:DeleteRolePermissionsBoundary` as well.
- Set `permissions_boundary` on the roles in `app`.
- Rewrite the `oidc.tf:16-17` comment.
- Do this **before** P2-T04's first CI apply. That apply creates the first `devcool-*` roles.

### #3a · Medium · `iam:UpdateRoleDescription` is missing
**File:** `infra/stacks/bootstrap/oidc.tf:123-146`

**Failure:** the AWS provider calls `UpdateRoleDescription` whenever a role's `description` changes, so any description edit in `app` fails with AccessDenied.

**Fix:** add `iam:UpdateRoleDescription` to `ManageDevcoolRolesAndPolicies`.

### #3c · Low · No `GetPolicy` on AWS-managed policies
**File:** `infra/stacks/bootstrap/oidc.tf:145-148`

**Failure:** `data "aws_iam_policy"`, used to look up `AmazonECSTaskExecutionRolePolicy`, calls `iam:GetPolicy` and `iam:GetPolicyVersion` on `arn:aws:iam::aws:policy/*`. The current scope only covers `policy/devcool-*`.

**Fix:** add a read-only statement for those two actions on `arn:aws:iam::aws:policy/*`, or hard-code the managed policy ARN and skip the data source.

### #3d · Low · The `role/devcool-*` scope depends on naming
**File:** `infra/stacks/bootstrap/oidc.tf:149-150`

**Failure:** module defaults (for example `terraform-aws-modules/ecs`) generate role names that don't start with `devcool-`, or roles with a non-`/` path. Both fall outside the scope, so the apply fails.

**Fix:** in `ecs-service` and `app`, always set `name` (or `name_prefix`) to `devcool-…` and `path = "/"`.

### #10 · Low · Hygiene
- `data.aws_caller_identity.current` is declared in `budget.tf:1`, but `state.tf` and `oidc.tf` use it. Move it to a `main.tf` in the same change as #2.
- `filter {}` in the state lifecycle rule is stored live as `Prefix: ""`. Confirm the next bootstrap `plan` shows no changes (the reviewer's own plan was denied).

---

## P2-T08 — `deploy.yml`, `infra-plan.yml`, `infra-apply.yml`

### #1 · High · `master` is unprotected, so the `aws-deploy` gate is weak
**Resolved before P2-T08:** the ruleset is active (PR, `ci-gate` only, no bypass actors) and `aws-deploy` has admin bypass off; `infra-plan` is a router job in `ci-gate`'s `needs`.

**Where:** GitHub settings (not Terraform). Related: `oidc.tf:73-96`

What the reviewer found:
- `branches/master/protection` returns 404.
- The `master` ruleset has `enforcement: disabled`.
- `aws-deploy` has `can_admins_bypass: true`.

The environment's rule "master only" therefore means "anything that reaches master".

**Failure:** once `infra-apply.yml` exists, any direct push to master touching `infra/**` runs as `gha-deploy`. That includes a push from `claude.yml`'s `@claude` job, which has `contents: write` and can be prompt-injected through issue text.

**Fix (before merging the first `infra-apply.yml`):**
- Enable the `master` ruleset: require a PR, block force-push and deletion, and give no bypass to apps.
- Require exactly one status check: `ci-gate`. Don't require the per-area checks. When an area runs, its check is named `backend / Build, Test, Coverage, Sonar`; when its `if:` skips it, the check is named just `backend` (seen on PR #130). Either name, if required, would leave some PRs stuck on "Expected" forever. `ci-gate` always runs under one name and fails if any CI job failed or was cancelled.
- Make `infra-plan` a router job in `ci.yml` and add it to `ci-gate`'s `needs`. A separate workflow file can't be checked by the gate.
- Set admin bypass off on `aws-deploy`.

### #5 · Medium · `gha-deploy` can destroy state history
**Resolved in P2-T08:** `ProtectStateHistory` in `state.tf`, exempting `state_admin_principal_arn` (`user/admin`).

**File:** `infra/stacks/bootstrap/state.tf:66-91`

PowerUserAccess gives `gha-deploy` `s3:*` on the state bucket. It can delete object versions, suspend versioning, rewrite the lifecycle rule or remove the bucket policy.

**Failure:** a buggy `hibernate` script, or the escalation in #2, permanently loses every stack's state, old versions included.

**Fix:** a Deny in the bucket policy. A deny in a resource policy still holds if the role escalates.
```hcl
statement {
  sid    = "ProtectStateHistory"
  effect = "Deny"
  actions = ["s3:DeleteObjectVersion", "s3:PutBucketVersioning", "s3:PutLifecycleConfiguration",
             "s3:PutBucketPolicy", "s3:DeleteBucketPolicy", "s3:DeleteBucket"]
  resources = [aws_s3_bucket.tfstate.arn, "${aws_s3_bucket.tfstate.arn}/*"]
  principals { type = "*", identifiers = ["*"] }
  condition {
    test     = "ArnNotEquals"
    variable = "aws:PrincipalArn"
    values   = ["<your admin principal ARN>"]   # check with `aws sts get-caller-identity`
  }
}
```
**Careful:** with a wrong admin ARN, you lock yourself out of changing the bucket policy. Check the ARN first, and keep the root user as the break-glass.

### #6 · Low · `.tflock` delete markers pile up
**Resolved in P2-T08.**

**File:** `infra/stacks/bootstrap/state.tf:51-63`

The lock permissions themselves are correct: `PutObject`/`DeleteObject` on `*.tflock`, `GetObject` through `ReadOnlyAccess`.

**Failure:** every lock leaves a noncurrent version and a delete marker. The versions expire after 90 days; the markers stay forever. It's harmless, but the bucket fills with clutter once CI locks on every PR.

**Fix:** add `expiration { expired_object_delete_marker = true }` to the `expire-noncurrent-state` rule.

**Side note:** `gha-plan` can delete a running apply's lock (a break-lock). That's acceptable; just know it.

### #7 · Low (optional) · `gha-plan` trust also matches `claude-auto-review`
**Resolved in P2-T08:** sub template `repo,context,job_workflow_ref`; `gha-plan` trusts `infra-plan.yml`, `gha-deploy` the files in `deploy_workflows` ([`infra/README.md`](../../infra/README.md)).

**File:** `infra/stacks/bootstrap/oidc.tf:34-39`

`claude.yml`'s `claude-auto-review` runs on `pull_request` with `id-token: write`, so it can assume `gha-plan` and read state. Its tool allowlist limits this today.

**Fix (optional):** switch the repo's OIDC `sub` template (currently `use_default: true`) to include `job_workflow_ref`. Then pin `gha-plan` to `infra-plan.yml` and `gha-deploy` to `deploy.yml`/`infra-apply.yml`. Change both trusts in the same step as the template, or every assume breaks.

### #9 · Low · ECR "keep 20" counts attestation manifests
**Resolved in P2-T08:** both fixes (tagged-only rule, `provenance: false`).

**File:** `infra/stacks/bootstrap/ecr.tf:21-30`

With buildx's default provenance, one push stores an index plus 2 manifests, so "keep 20" means about 7 deploys of rollback. AWS won't expire referenced children, so nothing breaks; the number is just misleading.

**Fix:** count tagged images only (`tagStatus = "tagged"`, `tagPatternList = ["*"]`, `countNumber = 20`), or set `provenance: false` in `deploy.yml`'s build step.

---

## P2-T09 — `env-power.yml`

### #8 · Low · 1-hour sessions can expire mid-`resume`
**File:** `infra/stacks/bootstrap/oidc.tf:47`, `:105`

**Failure:** `resume` applies `network` (NAT), then `app`, then `edge`. A CloudFront create or disable-then-delete can take long enough that the chain outlives the credentials. That leaves a held lock and a half-applied stack.

**Fix:** set `max_session_duration = 7200` on both roles (bootstrap), and `role-duration-seconds: 7200` in `env-power.yml`.

---

## Later phases

- **P8-T07:** add `bedrock.amazonaws.com` to `gha-deploy`'s `iam:PassedToService` values if a role is passed to Bedrock (same pattern as #3b).
