# Phase 2 — Infrastructure and CI/CD walking skeleton

**Weeks:** 2–3 · **Depends on:** P1 (Flyway, actuator) · **ADRs:** 0002, 0003, 0009, 0011

## Goal
Deploy the **current** app to AWS through a pipeline, with the final network shape:
- CloudFront → ALB → ECS (2 tasks) → Aurora.
- Everything in Terraform.
- Deployed by GitHub Actions via OIDC.
- Able to sleep and wake cheaply.

Later phases only add services (Valkey, SNS/SQS, Bedrock) into a working skeleton.

## Why it matters (interview angle)
Deploying early surfaces the hard problems (health checks, migrations, secrets, WS through CloudFront/ALB) while the app is still simple. "Walking skeleton" is a phrase interviewers recognize.

## Prerequisites
- An AWS account with a budget alarm.
- A domain in Route 53 (or use the CloudFront default domain at first).
- `terraform` ≥ 1.11, `tflint` and the AWS CLI installed.
- Bedrock model access requested in the target region (it takes time; start now for P8).

## Scope
- **In:**
  - Terraform stacks `bootstrap`, `network`, `data` (Aurora, S3 media, secrets), `app` (ECS, ALB), `edge` (CloudFront, SPA bucket, ACM, DNS).
  - Deploy and infra workflows.
  - Sleep/wake/hibernate.
- **Out:**
  - Valkey (added in P4).
  - SNS/SQS and the `worker` service (P6).
  - OTel sidecar (P7).
  - WAF (optional, P9).

## Design notes

### Terraform layout (ADR-0003)
```
infra/
  modules/
    ecs-service/       # task def + service + autoscaling + log group; sidecar-ready
    sqs-with-dlq/      # (used in P6)
  stacks/
    bootstrap/         # tf state bucket, GitHub OIDC provider, gha-plan + gha-deploy roles, ECR repo
    network/           # vpc (2 AZ), public/private subnets, NAT, endpoints (S3 gw; ECR, logs, secrets if)
    data/              # aurora-serverless-v2 (pg16), s3 media, secrets (jwt keys)
    app/               # ecs cluster, api service, ALB + target group + listener, SGs, IAM task roles
    edge/              # cloudfront (S3 SPA + ALB origins), s3 spa bucket (OAC), acm (us-east-1), route53
  envs/
    dev.tfvars
    prod.tfvars
```

- **State:** one S3 key per stack per env (`devcool/<env>/<stack>.tfstate`), `use_lockfile = true`. Every stack sets `required_version = ">= 1.11"` (S3 native locking is GA from 1.11).
- **Cross-stack values:** `terraform_remote_state` or SSM parameters written by producer stacks. SSM is preferred, since it decouples consumers from state access.
- The **image tag** is set by the deploy workflow, not Terraform. The service ignores `task_definition` changes.

### ECS and ALB details that matter for WebSocket
| Setting | Value | Why |
|---|---|---|
| ALB idle timeout | 120 s | > 2× the 25 s client heartbeat, with margin |
| Target group health check | `/actuator/health/readiness`, 10 s interval | Readiness flips during graceful shutdown |
| Deregistration delay | 30 s | Time for the WS drain |
| ECS `stopTimeout` | 45 s | Longer than Spring's shutdown phase |
| Deployment | rolling, min 100% / max 200%, circuit breaker + rollback | No capacity dip; auto-rollback on failed health |
| Task size | api: 0.5 vCPU / 1 GB to start | Tune with P9 load test |
| Autoscaling | target tracking CPU 60% (P4 adds the connections metric) | |

### Database migrations in the pipeline
Run Flyway as a **one-off ECS task** (same image, `SPRING_PROFILES_ACTIVE=ecs,migrate`, `spring.main.web-application-type=none`) **before** updating the service. The app itself starts with `spring.flyway.enabled=false` in `ecs`, so N tasks never race on migrations and a failed migration stops the deploy before any task changes. Migrations follow expand/contract, so the old version keeps working during a rolling deploy.

### Scale to zero
| Mode | What | Idle cost left |
|---|---|---|
| `sleep` (workflow_dispatch) | ECS desired count 0; Aurora auto-pauses on its own (min 0 ACU) | ALB, NAT, CloudFront (≈ ALB + NAT hourly) |
| `wake` | desired count back to N | — |
| `hibernate` | `terraform destroy` on `edge` and `app`, and the NAT (`network` with `enable_nat=false`) | Aurora storage, S3, ECR, secrets |
| `resume` | apply `network` → `app` → `edge` | — |

### GitHub Actions
| Workflow | Trigger | Steps |
|---|---|---|
| `ci.yml` (existing, extended) | push / PR | path filters → backend (spotless, static analysis, unit + IT), frontend (P5), infra (fmt, validate, tflint, Trivy config) |
| `infra-plan.yml` | PR touching `infra/**` | OIDC (`gha-plan` role, read-only) → `terraform plan` per changed stack → comment on PR |
| `infra-apply.yml` | push to `master` touching `infra/**` | OIDC (`gha-deploy`) → apply changed stacks in order; `prod` uses a GitHub environment with required reviewers |
| `deploy.yml` | push to `master` touching backend | build → Trivy image scan (fail on HIGH/CRITICAL with fix) → push `:<sha>` to ECR → run the migrate task and wait → render the task def with the new image → `amazon-ecs-deploy-task-definition` (wait for stability) → smoke test `GET /actuator/health` through CloudFront |
| `frontend-deploy.yml` | push to `master` touching `frontend/**` (P5) | build → `aws s3 sync --delete` → CloudFront invalidation `/index.html` |
| `env-power.yml` | workflow_dispatch (`sleep`/`wake`/`hibernate`/`resume`) + optional nightly schedule | as the table above |

## Tasks
- [ ] **P2-T01** `infra/stacks/bootstrap`: state bucket (versioned, encrypted, public access blocked), GitHub OIDC provider, `gha-plan` (read-only) and `gha-deploy` roles scoped to this repo/branch, ECR repo with scan on push and a lifecycle policy (keep the last 20)
- [ ] **P2-T02** `infra/stacks/network`: VPC across 2 AZs, NAT (toggle), S3 gateway endpoint, interface endpoints (ECR api/dkr, logs, secretsmanager), security groups (ALB → api:8080; api → Aurora:5432)
- [ ] **P2-T03** `infra/stacks/data`: Aurora Serverless v2 PG16 (dev min 0 ACU), RDS-managed master secret, S3 media bucket (existing name via var), JWT secrets in Secrets Manager
- [ ] **P2-T04** `infra/modules/ecs-service` + `infra/stacks/app`:
  - Cluster, `api` service, ALB, HTTPS listener.
  - Task role (S3 media, secrets) and execution role.
  - CloudWatch log group with 14-day retention.
- [ ] **P2-T05** `application-ecs.properties`: datasource from injected secrets, Flyway off; add `application-migrate.properties`
- [ ] **P2-T06** `Dockerfile`:
  - Non-root user, `JAVA_TOOL_OPTIONS` with `-XX:MaxRAMPercentage=75`.
  - Layered jar for caching.
  - `HEALTHCHECK` not needed (the ALB does it).
- [ ] **P2-T07** `infra/stacks/edge`: CloudFront with the SPA bucket (OAC) + ALB origin, behaviours `/api/*` and `/ws` (caching disabled, all viewer headers/cookies/query), ACM cert in us-east-1, Route 53 alias
- [ ] **P2-T08** `deploy.yml` with the migrate task and ECS deploy; `infra-plan.yml`, `infra-apply.yml`
- [ ] **P2-T09** `env-power.yml` (sleep/wake/hibernate/resume). Document the costs in `infra/README.md`
- [ ] **P2-T10** CI infra job: `terraform fmt -check`, `validate`, `tflint`, `trivy config infra/`
- [ ] **P2-T11** Enable the `terraform` plugin (HashiCorp MCP) in `.claude/settings.json`; delegate plan reviews to the `aws-architect` agent
- [ ] **P2-T12** Update `README.md` "Deployment" (replace the manual steps) and fill in `docker/ecs/README.md`
- [ ] **P2-T13** Budget alarm (AWS Budgets, email) at the monthly limit you set

## Files touched
`infra/**`, `.github/workflows/*.yml`, `Dockerfile`, `src/main/resources/application-ecs.properties`, `application-migrate.properties`, `README.md`

## Test plan
- `terraform plan` shows no changes after apply (no drift).
- The deploy pipeline goes green on a no-op commit. A broken health check triggers circuit-breaker rollback (test once on purpose).
- `wscat -c wss://<cloudfront>/ws` reaches the app (it's rejected without auth until P4 — which proves routing works).
- `sleep` → the site returns 503. `wake` → it returns 200 within a few minutes.

## Definition of Done
Milestone **M1**: a merge to `master` deploys to dev automatically. Infra changes go plan → review → apply. No AWS keys are stored in GitHub.

## Interview talking points
- OIDC federation vs access keys in CI secrets.
- Why migrations run as a separate step, and what expand/contract means for rolling deploys.
- Why Terraform doesn't own the image tag.
- ALB idle timeout vs WS heartbeat; deregistration delay vs graceful shutdown.
- How the environment scales to near zero, and what still costs money (ALB, NAT).

## Risks
- CloudFront + WebSocket misconfiguration (missing `Upgrade` forwarding via the origin request policy). Test early with `wscat`.
- Aurora resume latency makes the first request after sleep time out. Raise the Hikari connection timeout in dev.
- NAT gateway cost. The `hibernate` mode, or fck-nat as a documented alternative.
