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
