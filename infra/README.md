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
