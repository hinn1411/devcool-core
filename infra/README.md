# infra/ — operating notes

Layout, commands and conventions: [`CLAUDE.md`](CLAUDE.md). Design: [phase 2](../docs/plans/phases/phase-2-infra-cicd-walking-skeleton.md), ADR-0003, ADR-0013.

## Apply order
bootstrap (by hand) → network → data → edge → app. Hibernate and resume: P2-T09 (`env-power.yml`).

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
docker build --platform linux/amd64 -t "$REPO:$SHA" . && docker push "$REPO:$SHA"

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

# 3. Apply at the default min (1): autoscaling starts the first api task
terraform -chdir=infra/stacks/app apply -var-file=../../envs/dev.tfvars -var image_tag="$SHA"
aws elbv2 describe-target-health --target-group-arn "$(terraform -chdir=infra/stacks/app output -raw api_target_group_arn)" \
  --query 'TargetHealthDescriptions[].TargetHealth.State'                        # ["healthy"]
```

Checks:
```bash
curl -s https://dev-cool.click/actuator/health                                   # {"status":"UP"} through CloudFront
wscat -c wss://dev-cool.click/ws                                                 # reaches the app, rejected (4xx) until P4 auth
curl -s -o /dev/null -w '%{http_code}\n' https://origin.dev-cool.click/actuator/health   # 403: no X-Origin-Verify
terraform -chdir=infra/stacks/app plan -var-file=../../envs/dev.tfvars -var image_tag="$SHA"   # No changes
```

If a task stops before it's healthy, check `aws ecs describe-tasks … --query 'tasks[0].stoppedReason'` and the `/ecs/devcool-dev/api` log group:
- `ResourceInitializationError … secrets`: an empty JWT secret (see `data` above) or the execution role's secret ARNs.
- `CannotPullContainerError`: the NAT or the interface endpoints are off (`network` toggles).
- `Read-only file system`: a path outside `/tmp` is being written. Add it to the module's `scratch_paths` and as a `VOLUME` in the `Dockerfile`.
- Connection timeouts at start: Aurora resuming. The pgjdbc timeouts in `SPRING_DATASOURCE_URL` allow 60 s.

Carry-overs: `env-power` sleep must set the autoscaling min to 0, not only the desired count (registering the target moves the count into [min, max]); resume and `infra-apply` must pass the running `image_tag`, read from the family's last ACTIVE revision (`aws ecs describe-task-definition --task-definition devcool-dev-api`), since `skip_destroy` keeps revisions through hibernate (P2-T08, P2-T09).
