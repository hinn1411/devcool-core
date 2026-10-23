# infra/ — operating notes

Layout, commands and conventions: [`CLAUDE.md`](CLAUDE.md). Design: [phase 2](../docs/plans/phases/phase-2-infra-cicd-walking-skeleton.md), ADR-0003, ADR-0013.

## Apply order
bootstrap (by hand) → network → data → edge → app. Sleep, wake, hibernate and resume: [`env-power`](#env-power-sleep-wake-hibernate-resume-p2-t09).

## `data`: set the JWT secrets (once per environment)
Terraform creates `devcool/<env>/jwt-access` and `devcool/<env>/jwt-refresh` **without a value**, so the value never lands in state and `gha-plan` never needs `GetSecretValue` ([bootstrap review #4](../docs/improvements/2026-10-14-bootstrap-review-improvements.md#p2-t03--data-stack)). After the first `data` apply, set each one. The `$(…)` keeps the value out of your shell history; the app needs at least 32 characters.

```bash
for k in access refresh; do
  aws secretsmanager put-secret-value --secret-id "devcool/dev/jwt-$k" \
    --secret-string "$(openssl rand -base64 48)"
done
```

Check without reading the value: each secret shows an `AWSCURRENT` version.
```bash
for k in access refresh; do
  aws secretsmanager describe-secret --secret-id "devcool/dev/jwt-$k" --query VersionIdsToStages
done
```

Skipping this step makes every ECS task fail at start (`ResourceNotFoundException … AWSCURRENT`). If a secret is deleted and recreated, run the step again; a deleted name stays reserved for the recovery window (30 days by default).

The Aurora master password needs no step: RDS generates it in Secrets Manager (`/devcool/<env>/data/db_master_secret_arn`) and rotates it every 365 days.

## `edge`: first apply and checks

`edge` adopts the domain's existing Route 53 zone (`import` block in `route53.tf`), so its name servers and the SES records in it never change. The first apply waits for ACM DNS validation and a CloudFront deploy (5–15 min each). `edge` survives hibernate (ADR-0013); `env-power` never touches it.

Until P5 deploys the SPA, upload a placeholder by hand. It isn't in Terraform, because P5's `s3 sync --delete` owns the bucket's contents.
```bash
echo '<!doctype html><title>devcool</title><p>devcool dev' > /tmp/index.html
aws s3 cp /tmp/index.html "s3://$(terraform -chdir=infra/stacks/edge output -raw spa_bucket_name)/index.html"
```

Checks after apply. The API paths return 502 until `app` exists (P2-T04), and again while hibernated:
```bash
dig +short NS dev-cool.click                                   # same 4 awsdns servers as `output name_servers`
curl -sI https://dev-cool.click/ | head -1                      # 200 (placeholder, via CloudFront)
curl -sI "https://$(terraform -chdir=infra/stacks/edge output -raw spa_bucket_name).s3.amazonaws.com/index.html" | head -1  # 403 (OAC only)
curl -s -o /dev/null -w '%{http_code}\n' https://dev-cool.click/actuator/health   # 502 before app, 200 after
aws ssm get-parameters-by-path --path /devcool/dev/edge --query 'Parameters[].Name'
```

## `app`: first deploy by hand (P2-T04)

`app` is destroyed on every hibernate and re-created on resume (ADR-0013). It reads everything from SSM (`network`, `data`, `edge`), so apply those first. Every role it creates carries bootstrap's `workload-boundary`, so apply `bootstrap` before the first `app` apply.

The image tag has no default: the pipeline owns the task definition after the first apply (ADR-0003), so each `plan`/`apply` passes the running tag. The first apply runs at 0 tasks, because the schema doesn't exist until the migrate task has run.

```bash
# 0. Build and push (P2-T14's patched dependencies first). deploy.yml does this from P2-T08.
SHA=$(git rev-parse HEAD)
REPO=$(terraform -chdir=infra/stacks/bootstrap output -raw ecr_repository_url)
aws ecr get-login-password | docker login --username AWS --password-stdin "${REPO%%/*}"
# No attestations: with Docker Desktop's containerd store, the immutable tag can land on the
# provenance manifest instead of the image (the push then fails with a 400 on the index).
docker build --platform linux/amd64 --provenance=false --sbom=false -t "$REPO:$SHA" . && docker push "$REPO:$SHA"

# 1. Apply at 0 tasks
terraform -chdir=infra/stacks/app init -backend-config=../../envs/dev.backend.hcl -backend-config=key=devcool/dev/app.tfstate
terraform -chdir=infra/stacks/app apply -var-file=../../envs/dev.tfvars -var image_tag="$SHA" -var api_min_count=0

# 2. Migrate: the api task definition with the ecs,migrate profile; exit code 0 = migrated.
#    Run it once Aurora has been idle > 10 min, so it also proves the resume timeouts.
get() { aws ssm get-parameter --name "/devcool/dev/app/$1" --query Parameter.Value --output text; }
CLUSTER=$(get cluster_name)
TASK=$(aws ecs run-task --cluster "$CLUSTER" --task-definition "$(get api_task_family)" --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$(get api_subnet_ids)],securityGroups=[$(get api_security_group_ids)],assignPublicIp=DISABLED}" \
  --overrides '{"containerOverrides":[{"name":"api","environment":[{"name":"SPRING_PROFILES_ACTIVE","value":"ecs,migrate"}]}]}' \
  --query 'tasks[0].taskArn' --output text)
aws ecs wait tasks-stopped --cluster "$CLUSTER" --tasks "$TASK"
aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$TASK" --query 'tasks[0].[stoppedReason,containers[0].exitCode]'
# The Flyway lines from the task's log: expect "Successfully applied N migrations" (or "up to date").
aws logs get-log-events --log-group-name /ecs/devcool-dev/api --log-stream-name "api/api/${TASK##*/}" \
  --query 'events[].message' --output text | tr '\t' '\n' | grep -E 'Flyway|igrat|ERROR'

# 3. Apply at the default min (1): autoscaling starts the first api task
terraform -chdir=infra/stacks/app apply -var-file=../../envs/dev.tfvars -var image_tag="$SHA"
aws elbv2 describe-target-health --target-group-arn "$(terraform -chdir=infra/stacks/app output -raw api_target_group_arn)" \
  --query 'TargetHealthDescriptions[].TargetHealth.State'                        # ["healthy"]
```

Checks:
```bash
curl -s https://dev-cool.click/actuator/health                                   # {"status":"UP"} through CloudFront
wscat -c wss://dev-cool.click/ws                                                 # reaches the app, no 101: the interceptor rejects with an empty 200 until P4 auth
curl -s -m 8 -o /dev/null -w '%{http_code}\n' https://origin.dev-cool.click/actuator/health   # 000 (timeout): the SG admits CloudFront only; another distribution gets 403
terraform -chdir=infra/stacks/app plan -var-file=../../envs/dev.tfvars -var image_tag="$SHA"   # No changes
```

If a task stops before it's healthy, check `aws ecs describe-tasks … --query 'tasks[0].stoppedReason'` and the `/ecs/devcool-dev/api` log group:
- `ResourceInitializationError … secrets`: an empty JWT secret (see `data` above) or the execution role's secret ARNs.
- `CannotPullContainerError`: the NAT or the interface endpoints are off (`network` toggles).
- Push fails with `400 Bad Request` on the manifest, or the task can't find a linux/amd64 image: the tag points at a
  provenance attestation (check `aws ecr batch-get-image … --query 'images[0].imageManifest'`). Tags are immutable, so
  delete the image (`aws ecr batch-delete-image`) and rebuild with `--provenance=false --sbom=false`.
- `Read-only file system`: a path outside `/tmp` is being written. Add it to the module's `scratch_paths` and as a `VOLUME` in the `Dockerfile`.
- Connection timeouts at start (`SocketTimeoutException: Connect timed out`): Aurora resuming. The pgjdbc timeouts in
  `SPRING_DATASOURCE_URL` allow 60 s, but the first resume after a long pause can take longer (seen 2026-10-20: the migrate
  task failed cold, passed on a re-run). Re-run the task once `ServerlessDatabaseCapacity` is above 0.

## CI: deploy, plan, apply (P2-T08)

| Workflow | Runs on | awake | asleep (count 0) | hibernated |
|---|---|---|---|---|
| `deploy.yml` | backend merge | push → migrate → rollout → smoke | same, no smoke | push + register only; resume verifies |
| `infra-plan.yml` (in `ci.yml`) | PR touching infra | plan + comment per changed stack | `app` with min 0 | `network` with toggles off; `app` = full create |
| `infra-apply.yml` | infra merge | apply every stack (converges; P2-T09), then roll `api` onto the latest revision | `app` with min 0 | `network`/`data` with toggles off; `app` skipped |

State comes from `.github/scripts/describe-env.sh` (run it by hand to check). `app` always gets the running tag: the image of the family's last ACTIVE revision.

### One-time setup
1. Apply bootstrap and switch the repo's OIDC `sub` template **back to back**: the trusts now match `…:job_workflow_ref:<repo>/.github/workflows/<file>@<ref>` ([review #7](../docs/improvements/2026-10-14-bootstrap-review-improvements.md#7--low-optional--gha-plan-trust-also-matches-claude-auto-review)), so until both are done every assume fails.
   ```bash
   aws sts get-caller-identity --query Arn   # must equal state_admin_principal_arn (review #5), or you lose the state bucket policy
   terraform -chdir=infra/stacks/bootstrap apply -var-file=budget.local.tfvars
   gh api -X PUT repos/hinn1411/devcool-core/actions/oidc/customization/sub -F use_default=false \
     -f 'include_claim_keys[]=repo' -f 'include_claim_keys[]=context' -f 'include_claim_keys[]=job_workflow_ref'
   gh api repos/hinn1411/devcool-core/actions/oidc/customization/sub                # use_default: false
   terraform -chdir=infra/stacks/bootstrap plan -var-file=budget.local.tfvars       # No changes
   ```
   Then mention `@claude` on a PR once: `claude-code-action` also uses an OIDC token.
2. The bootstrap plan in CI needs the budget emails (never in git): `gh secret set BUDGET_ALERT_EMAILS --body '["you@example.com"]'`.
3. Master protection ([review #1](../docs/improvements/2026-10-14-bootstrap-review-improvements.md#1--high--master-is-unprotected-so-the-aws-deploy-gate-is-weak)) must stay on: the `master` ruleset is active with a PR required, `ci-gate` the only check, no bypass actors; `aws-deploy` has admin bypass off.
   ```bash
   gh api repos/hinn1411/devcool-core/rulesets --jq '.[]|{name,enforcement}'
   gh api repos/hinn1411/devcool-core/environments/aws-deploy --jq .can_admins_bypass   # false
   ```

### Gotchas
- `deploy`, `infra-apply` and `env-power` share the `aws-dev` concurrency group. It stops overlap, not loss: a newer pending run **cancels** an older pending one, from any of the three workflows (the nightly hibernate too).
  - A cancelled `infra-apply` loses nothing: every run applies every stack.
  - A cancelled `deploy` replaced by a newer deploy loses nothing either (the newer image contains it). Replaced by `infra-apply` or `env-power`, it's lost: re-run it, but **only if no newer deploy has succeeded**, or it rolls the service back to older code.
- A failed migration or rollout deregisters the new revision, so the last ACTIVE revision stays the running one.
- After a deploy, an `app` apply registers a revision with the same image and rolls the service onto it (a few minutes, no downtime). That's how Terraform changes to the task definition go live; the service itself ignores `task_definition`.
- A bootstrap change only plans in CI; `infra-apply` warns and skips it. A new workflow that needs `gha-deploy` goes in bootstrap's `deploy_workflows`.

## `env-power`: sleep, wake, hibernate, resume (P2-T09)

**Hibernated is the default state** (ADR-0013). Run a mode from Actions → `env-power` → Run workflow, or `gh workflow run env-power.yml -f mode=resume`. A schedule hibernates every night at 17:00 UTC, whatever the state.

| Mode | What it does | Takes | Health via CloudFront after |
|---|---|---|---|
| `sleep` | `app` with the autoscaling min at 0, then the desired count to 0; Aurora pauses on its own | ~2 min | 503 (no targets) |
| `wake` | `app` with the min back at 1 and the count raised to it; no migrate (a deploy while asleep already ran it) | ~5 min | 200 |
| `hibernate` | destroy `app` → `data` (guarded) → `network` with the NAT and interface endpoints off; `edge` untouched | ~10 min | 502 (no origin) |
| `resume` | `network` → `data` (guarded) → `app` at 0 tasks → migrate (1 retry) → `app` at min → roll out → smoke | ~20 min | 200 |

- `sleep`/`wake` fail on a hibernated env: run `resume`. `resume` works from any state, and an awake env keeps its tasks while it migrates.
- Every mode is safe to re-run; re-running `hibernate` finishes a half-done one (it always runs `destroy app`).
- **The `data` guard:** env-power applies `data` from a saved plan and fails, with nothing applied, if the plan changes any resource outside `DATA_ALLOWED_CHANGES` in `env-power.yml` (empty until P4-T05 adds Valkey). On a hibernate, the NAT and endpoints still go off before the run fails. A failure means an unapplied `data` change is pending: apply it through a PR (`infra-apply`), then re-run.
- `resume` also verifies what a merge registered while hibernated (`deploy.yml`): the schema is migrated before any task starts.

### Costs (us-east-1)
| State | Still billed | ≈ \$/h | ≈ \$/month if left |
|---|---|---|---|
| awake | 1 api task (0.5 vCPU/1 GB), Aurora 0.5 ACU, ALB, NAT, 4 endpoints × 2 AZ, 3 public IPv4 | 0.26 | 190 |
| asleep | ALB, NAT, endpoints, public IPv4 (Aurora auto-pauses, 0 tasks) | 0.16 | 115 |
| hibernated | Aurora storage, S3, ECR, secrets, Route 53 zone | — | 2.5 |
| awake, measured | fill in after the M1 window (Cost Explorer, tag `Project=devcool`, 24 h lag) | | |

Basis: NAT \$0.045/h, interface endpoint \$0.01/h per AZ, Aurora \$0.12/ACU-h (ADR-0013, checked 2026-10-15). ALB (\$0.0225/h + LCU), Fargate and public IPv4 (\$0.005/h each) are list prices. Sleep only saves the task and Aurora (~\$0.09/h), so use it for pauses inside a window and hibernate at the end. The budget alerts at \$10 and \$18 (`bootstrap/budget.tf`).

### One-time setup
Apply bootstrap by hand before the first run: it adds `env-power.yml` to `deploy_workflows` and raises both roles' sessions to 2 h (bootstrap review #8). Until then, the workflow's assume fails.
```bash
terraform -chdir=infra/stacks/bootstrap apply -var-file=budget.local.tfvars
```
