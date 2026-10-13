# 17 — Terraform in DevCool

The theme: **how Terraform turns `.tf` files into AWS resources, how P2's five stacks fit together, and what breaks in production.** Read [16 — IaC Fundamentals](16-iac-fundamentals.md) first. This chapter assumes desired state, drift, blast radius and the one-owner rule.

It is the background for P2 (`docs/plans/phases/phase-2-infra-cicd-walking-skeleton.md`), ADR-0002 (ECS Fargate), ADR-0003 (layered stacks), ADR-0009 (Aurora) and ADR-0011 (single CloudFront origin). `infra/` has no `.tf` files yet, so **every HCL snippet here is illustrative**: it shows the shape P2 should take, not code that exists.

Version-sensitive facts were checked against the Terraform docs (S3 backend, write-only arguments, `removed` blocks), the AWS provider docs (`aws_rds_cluster`, `aws_ecs_service`) and the AWS docs (Aurora Serverless v2 auto-pause, CloudFront origin request policies) on 2026-10-11.

---

# Part A — Terraform core

## 1. How Terraform runs

Terraform itself knows nothing about AWS. It's an engine that does three things: reads your config, builds a dependency graph, and calls **providers** (plugins) that know how to talk to an API.

```
 *.tf files ──► parse ──► dependency graph ──► diff against state + reality ──► plan
                                                                                 │
                    AWS provider plugin (CRUD calls to AWS APIs) ◄──── apply ◄───┘
                                     │
                                     ▼
                              state file updated
```

The commands, and their backend equivalents:

| Command | What it does | Backend analogy |
|---|---|---|
| `init` | Downloads providers and modules, connects to the state backend | `mvn dependency:resolve` |
| `fmt` / `validate` | Formats code; checks syntax and types without calling AWS | Spotless / `javac` |
| `plan` | Refreshes state from AWS, diffs it against the code, prints the actions | A Flyway dry run that shows the SQL |
| `apply` | Runs the plan (it asks first, unless given a saved plan file) | `flyway migrate` |
| `destroy` | Plans the removal of everything in this state | `DROP SCHEMA ... CASCADE` |

**The graph.** Terraform works out the order from references. If the ECS service mentions `aws_lb_target_group.api.arn`, the target group gets created first. Independent resources are created in parallel. You almost never write the order yourself. `depends_on` is for the rare hidden dependency that no reference expresses, for example "this IAM policy must exist before the task starts".

**Plan symbols.** `+` create, `~` update in place, `-` destroy, `-/+` **replace** (destroy then create), `+/-` replace with create-before-destroy. Learn to read `-/+`. On a stateful resource it means data loss.

---

## 2. The language: blocks you'll write

```hcl
terraform {                                  # tool and provider pins, backend
  required_version = ">= 1.11"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
  backend "s3" {}                            # filled in by -backend-config at init
}

provider "aws" {                             # how to talk to AWS
  region = var.region                        # us-east-1
  default_tags { tags = local.tags }         # Project/Environment/Stack/ManagedBy on everything
}

provider "aws" {                             # a second, aliased configuration
  alias  = "us_east_1"                       # CloudFront certs must live in us-east-1
  region = "us-east-1"
}

variable "env" { type = string }             # input, like a constructor parameter
locals { name = "devcool-${var.env}" }       # private computed value, like a final field

data "aws_ssm_parameter" "vpc_id" {          # READ something that exists already
  name = "/devcool/${var.env}/network/vpc_id"
}

resource "aws_lb" "api" {                    # MANAGE something: Terraform creates/updates/deletes it
  name    = "${local.name}-api"
  subnets = local.public_subnet_ids
  idle_timeout = 120
}

output "alb_dns_name" { value = aws_lb.api.dns_name }   # return value
```

| Block | Role | Java analogy |
|---|---|---|
| `resource` | A real object Terraform **owns**: create, update, delete | An entity that's persisted |
| `data` | A real object Terraform only **reads** | A read-only query |
| `variable` | An input to the stack or module | Constructor parameter |
| `locals` | A named expression inside the module | `private final` field |
| `output` | A value exposed to the caller | Return value / getter |
| `module` | Instantiate a reusable group of resources | `new EcsService(...)` |
| `provider` | Credentials and region for an API | A configured client bean |

**Resource vs data is the ownership line.** If two stacks both declare `resource "aws_s3_bucket" "media"`, they both own it, and the second one either fails ("already exists") or fights the first one. The consumer must use `data`.

### `count` and `for_each`

```hcl
resource "aws_nat_gateway" "this" {
  count = var.enable_nat ? 1 : 0             # a toggle: 0 or 1 copies
  # ...
}

resource "aws_vpc_endpoint" "interface" {
  for_each = toset(["ecr.api", "ecr.dkr", "logs", "secretsmanager"])
  service_name = "com.amazonaws.${var.region}.${each.key}"
  # ...
}
```

- Use `count` **only** for "0 or 1" toggles. For lists, `count` addresses items by index (`[0]`, `[1]`). Remove the first item and every later item shifts, so Terraform plans to destroy and recreate all of them.
- Use `for_each` for collections. Items are addressed by key (`["logs"]`), so removing one item touches only that one.

---

## 3. State, modules, lifecycle

### 3.1 State: Terraform's memory

State is a JSON file that maps each resource address to a real AWS ID and its last known attributes:

```
aws_lb.api  ──►  arn:aws:elasticloadbalancing:us-east-1:...:loadbalancer/app/devcool-dev-api/abc123
```

Without it, Terraform can't know that *this* block means *that* ALB. It would try to create a second one. Three properties matter:

1. **It is the source of ownership.** Something not in state is invisible to Terraform. Something in state that you delete from the code gets **destroyed** on the next apply.
2. **It contains secrets** in plain text: any attribute a provider returns, including a DB password set through `master_password`. Treat the state bucket like a production database: encrypted, versioned, public access blocked, few readers.
3. **Only one writer at a time.** Two concurrent applies would each write their own version and lose the other's changes.

**The DevCool backend** (ADR-0003):

```hcl
# envs/dev.backend.hcl  (passed with: terraform init -backend-config=../../envs/dev.backend.hcl)
bucket       = "devcool-tfstate-<account-id>"
key          = "devcool/dev/app.tfstate"      # one key per stack per env
region       = "us-east-1"
use_lockfile = true                           # S3 native locking: writes <key>.tflock next to the state
encrypt      = true
```

`use_lockfile = true` locks by creating a `.tflock` object with an S3 conditional write. DynamoDB-based locking still works but is **deprecated**, which is why DevCool doesn't create a DynamoDB table. The role running Terraform needs `s3:GetObject`/`PutObject` on the state key and `GetObject`/`PutObject`/`DeleteObject` on `<key>.tflock`. That includes the "read-only" plan role, because a plan takes the lock too.

**The chicken-and-egg problem.** The state bucket can't store the state of the stack that creates it. That's why `bootstrap` is applied once, by hand, with local state (or migrated into the bucket afterwards with `terraform init -migrate-state`).

### 3.2 Modules

A module is a directory of `.tf` files with variables in and outputs out. Every stack is itself a module (the *root* module).

```hcl
module "api" {
  source = "../../modules/ecs-service"        # own module: the design matters (sidecars, WS timings)
  name                 = "api"
  cpu                  = 512
  memory               = 1024
  health_check_path    = "/actuator/health/readiness"
  deregistration_delay = 30
  stop_timeout         = 45
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"   # community module: undifferentiated plumbing
  version = "~> 6.0"                          # pin it, the same as a Maven version
  # ...
}
```

DevCool's rule (`infra/CLAUDE.md`): use `terraform-aws-modules/*` for plumbing (VPC, Aurora). Write your own module where the design is the point: `ecs-service`, which must be sidecar-ready for the OTel collector in P7, and `sqs-with-dlq` in P6. Always pin `version`.

### 3.3 `lifecycle`: overriding the default reaction

```hcl
resource "aws_ecs_service" "this" {
  # ...
  lifecycle {
    ignore_changes = [task_definition, desired_count]   # owned by deploy.yml and autoscaling/env-power
  }
}

resource "aws_rds_cluster" "this" {
  # ...
  deletion_protection = true                  # AWS refuses the delete call
  lifecycle {
    prevent_destroy = true                    # Terraform refuses to plan a destroy
  }
}
```

| Setting | Effect | Use it for |
|---|---|---|
| `ignore_changes` | Terraform sets the field at create time, then never touches it again | Fields owned by another system (16 §4) |
| `prevent_destroy` | A plan that would destroy this resource fails | Aurora, the state bucket, the media bucket |
| `create_before_destroy` | On replace, create the new one first, then destroy the old one | Things referenced by others: ACM certs, launch configs, security groups |
| `replace_triggered_by` | Replace this resource when another one changes | Rare. Forcing a rebuild when a dependency is replaced |

**`prevent_destroy` has a hole.** It lives *inside* the resource block. If you delete the whole block, the protection is deleted with it, and the plan destroys the cluster. That's why Aurora also sets `deletion_protection = true` at the AWS level. Terraform can't bypass that without a separate apply that turns it off first.

### 3.4 Refactoring without destroying: `moved`, `import`, `removed`

Changing an address (renaming a resource, moving it into a module) looks to Terraform like "delete the old one, create a new one". Three blocks fix that, all of them reviewed in a plan:

```hcl
moved {                                       # rename or move into a module: the state follows the code
  from = aws_rds_cluster.main
  to   = module.aurora.aws_rds_cluster.this
}

import {                                      # adopt something created by hand (e.g. the existing media bucket)
  to = aws_s3_bucket.media
  id = "devcool-media-existing-name"
}

removed {                                     # stop managing it, but DON'T delete it (Terraform >= 1.7)
  from = aws_s3_bucket.legacy
  lifecycle { destroy = false }
}
```

These are config-driven versions of `terraform state mv`, `terraform import` and `terraform state rm`. Prefer the blocks: they show up in the PR and in the plan, while the CLI commands change state silently from a laptop.

### 3.5 Two lock files: don't confuse them

| File | What it locks | In git? |
|---|---|---|
| `.terraform.lock.hcl` | **Provider versions and checksums**, like a `package-lock.json` | **Yes**, commit it |
| `<key>.tflock` in S3 | **The state**, during one plan/apply | No, it's runtime-only |

---

# Part B — DevCool's design

## 4. The five stacks and how they connect

```
                          ┌──────────────────────────────────────────────┐
  apply ONCE, by hand     │ bootstrap   state bucket · GitHub OIDC ·     │
                          │             gha-plan / gha-deploy roles · ECR│
                          └──────────────────────┬───────────────────────┘
                                                 │ (state bucket, roles used by every other stack)
                          ┌──────────────────────▼───────────────────────┐
  rarely; NAT toggle      │ network     VPC (2 AZ) · subnets · NAT? ·    │
                          │             VPC endpoints · security groups  │
                          └──────────┬────────────────────────┬──────────┘
                     SSM: vpc_id, private_subnet_ids, sg ids  │
                          ┌──────────▼──────────┐             │
  NEVER destroyed         │ data   Aurora PG16  │             │
                          │        S3 media     │             │
                          │        secrets      │             │
                          └──────────┬──────────┘             │
                     SSM: db endpoint, secret ARNs, bucket    │
                          ┌──────────▼────────────────────────▼──────────┐
  often; destroyed on     │ app     ECS cluster · api service · ALB ·    │
  hibernate               │         listener · task/exec roles · logs    │
                          └──────────────────────┬───────────────────────┘
                     SSM: alb dns name           │
                          ┌──────────────────────▼───────────────────────┐
  destroyed on hibernate  │ edge    CloudFront · SPA bucket (OAC) ·      │
                          │         ACM (us-east-1) · Route 53           │
                          └──────────────────────────────────────────────┘
```

Apply order follows the arrows: **bootstrap → network → data → app → edge**. Destroy order is the reverse. `hibernate` destroys `edge`, then `app`, then re-applies `network` with `enable_nat=false`. `data` never moves.

### 4.1 How values cross stacks: SSM parameters

Each stack has its own state, so `app` can't write `aws_lb.api` and expect `edge` to see it. There are two ways to pass values across:

| | `terraform_remote_state` | SSM Parameter Store (**DevCool's choice**) |
|---|---|---|
| How | The consumer reads the producer's whole state file | The producer writes named parameters; the consumer reads them with `data "aws_ssm_parameter"` |
| Consumer needs | Read access to the producer's **state** (which may contain secrets) | Read access to a few parameters |
| Coupling | To the producer's output names *and* backend location | To a parameter name, which works like a published API |
| Other readers | Terraform only | Also scripts, workflows, the app itself |

```hcl
# network stack: producer
resource "aws_ssm_parameter" "private_subnets" {
  name  = "/devcool/${var.env}/network/private_subnet_ids"
  type  = "StringList"
  value = join(",", module.vpc.private_subnets)
}

# app stack: consumer
data "aws_ssm_parameter" "private_subnets" {
  name = "/devcool/${var.env}/network/private_subnet_ids"
}
locals { private_subnet_ids = split(",", data.aws_ssm_parameter.private_subnets.value) }
```

This is the hexagonal idea ([03](03-hexagonal-architecture.md)) applied to infrastructure. The parameter path is the **port**, and the consumer depends on the contract, not on the producer's internals. The cost is that the order is now implicit: if `network` hasn't been applied, `app`'s plan fails with "parameter not found". That's why the workflows encode the order.

### 4.2 Stack by stack: what and why

**bootstrap** (P2-T01)
- **State bucket:** versioned (so state can be recovered, §6.9), encrypted, public access blocked, `prevent_destroy`.
- **GitHub OIDC provider + two roles.** GitHub Actions gets a signed token. AWS checks its `sub` claim in the role's trust policy and returns temporary credentials. No access keys are stored in GitHub.
  ```hcl
  condition {                                 # gha-deploy: only master of this repo
    test     = "StringEquals"
    variable = "token.actions.githubusercontent.com:sub"
    values   = ["repo:hinn1411/devcool-core:ref:refs/heads/master"]
  }
  ```
  `gha-plan` trusts `pull_request` tokens and is read-only, except for the state lock (§3.1). `gha-deploy` trusts only `master`.
- **ECR:** scan on push, lifecycle policy that keeps the last 20 images. Rollback needs old images to still exist, so don't make this number tiny.

**network** (P2-T02)
- **2 AZs, public + private subnets.** The ALB lives in public subnets. ECS tasks and Aurora live in private subnets with no public IP.
- **NAT toggle:** `count = var.enable_nat ? 1 : 0`. Private tasks need *some* path to AWS APIs (ECR pulls, CloudWatch Logs, Secrets Manager).
- **VPC endpoints** give that path without NAT. An S3 **gateway** endpoint is free and carries ECR image layers. **Interface** endpoints (`ecr.api`, `ecr.dkr`, `logs`, `secretsmanager`) are billed per hour, per AZ.
- **Security groups as a chain:** `ALB SG → api SG :8080 → Aurora SG :5432`. Each rule references the source *security group*, not a CIDR, so only the ALB can reach the app and only the app can reach the DB.

> **Open question for P2-T02: endpoint cost vs NAT cost.** Four interface endpoints × 2 AZs is eight hourly-billed endpoint-AZs. At typical regional prices that is roughly the cost of a NAT gateway, or more. P2's `hibernate` turns off NAT but doesn't mention endpoints. `infra/CLAUDE.md` says "anything always-on and billed hourly must be in `app`, `edge` or a toggle in `network`", so the interface endpoints should sit behind a toggle too, or be justified by data-transfer savings. Check current `us-east-1` pricing when you write P2-T02 and record the decision in `infra/README.md` (P2-T09).

**data** (P2-T03)
- **Aurora Serverless v2, PG16.** Dev uses `min_capacity = 0`, which enables auto-pause. `seconds_until_auto_pause` can be 300–86,400 s (default 300). Setting min ≥ 0.5 disables pausing.
  ```hcl
  serverlessv2_scaling_configuration {
    min_capacity             = 0        # dev; prod would use >= 0.5 to avoid cold resumes
    max_capacity             = 4
    seconds_until_auto_pause = 300
  }
  manage_master_user_password = true    # RDS creates and rotates the secret; Terraform never sees the password
  ```
  **Pausing needs zero connections.** Hikari keeps a pool of idle connections open, so Aurora can't pause while any ECS task is running. That's why `sleep` sets the ECS desired count to 0: the tasks go away, then Aurora pauses on its own.
- **S3 media bucket:** it already exists (the app uses it today), so it's adopted with an `import` block, not recreated. The name comes in through a variable.
- **JWT secrets** live in Secrets Manager. The *container* for the secret is created in Terraform. The *value* is set outside state (§6.5).

**app** (P2-T04)
- **The `ecs-service` module:** task definition, service, autoscaling and log group (14-day retention), with room for sidecars.
- **The WS timing chain** (from P2's table, explained in [14](14-health-checks-and-graceful-shutdown.md)):

  | Setting | Value | Must be… |
  |---|---|---|
  | ALB `idle_timeout` | 120 s | > 2 × the 25 s heartbeat |
  | Target group health check | `/actuator/health/readiness`, 10 s | readiness, not a DB check |
  | `deregistration_delay` | 30 s | enough for the WS drain |
  | Container `stopTimeout` | 45 s | > Spring's `timeout-per-shutdown-phase` |
  | Deployment | min 100% / max 200%, circuit breaker + rollback | no capacity dip |

  These values live in three places (ALB, target group, task definition) but describe **one** behaviour. Keep them together in the module, with a comment explaining each one, so nobody changes one without the others.
- **Two IAM roles, often confused:**

  | Role | Used by | Needs |
  |---|---|---|
  | **Execution role** | The ECS *agent*, before your code starts | Pull from ECR, write logs, read the secrets injected as env vars |
  | **Task role** | *Your Spring app* at runtime | `s3:PutObject`/`GetObject` on the media bucket ARN, and nothing else |

- **The service doesn't own the image** (`ignore_changes = [task_definition]`, §6.4).
- **A regional ACM cert** for the ALB's HTTPS listener. The app is in `us-east-1`, the same region CloudFront requires, so one cert covering both hostnames can serve both; keep two if you want separate lifecycles.

**edge** (P2-T07)
- **One CloudFront distribution, three behaviours** (ADR-0011):

  | Path | Origin | Cache policy | Origin request policy |
  |---|---|---|---|
  | `/api/*` | ALB | `CachingDisabled` | `AllViewerExceptHostHeader` |
  | `/ws` | ALB | `CachingDisabled` | `AllViewerExceptHostHeader` |
  | default `/*` | SPA bucket via **OAC** | cached | — |

  There's no "enable WebSocket" switch in CloudFront. The upgrade works when the behaviour forwards the viewer's `Upgrade`, `Connection` and `Sec-WebSocket-*` headers and doesn't cache. "All viewer headers" does exactly that.
- **ACM in us-east-1** through the aliased provider. CloudFront only accepts certificates from that region, whatever region the rest of the stack uses:
  ```hcl
  resource "aws_acm_certificate" "cdn" {
    provider          = aws.us_east_1
    domain_name       = var.domain
    validation_method = "DNS"
    lifecycle { create_before_destroy = true }
  }
  ```
- **OAC (Origin Access Control):** the SPA bucket stays private. Its bucket policy allows only `cloudfront.amazonaws.com` with `AWS:SourceArn` set to *this* distribution.

---

## 5. Who owns what: Terraform vs the pipelines

This applies 16 §4's one-owner rule to every field that more than one system touches:

| Field | Owner | Why Terraform must ignore or not manage it |
|---|---|---|
| ECS task definition (image tag) | `deploy.yml` | Changes on every backend merge; Terraform would roll it back |
| ECS desired count | Autoscaling + `env-power.yml` | Autoscaling changes it; `sleep` sets it to 0 |
| Schema | Flyway, run as a one-off ECS task | The DB's contents aren't infrastructure |
| SPA files in the bucket | `frontend-deploy.yml` | Content, not infrastructure |
| Secret **values** | A human or a script, outside state | Must not land in state (§6.5) |
| Everything else in `infra/**` | Terraform via `infra-apply.yml` | — |

And which workflow touches which stack:

| Workflow | bootstrap | network | data | app | edge | Outside Terraform |
|---|---|---|---|---|---|---|
| `infra-plan.yml` (PR) | plan | plan | plan | plan | plan | — |
| `infra-apply.yml` (master) | — (by hand) | apply | apply | apply | apply | — |
| `deploy.yml` | — | — | — | — | — | ECR push, migrate task, new task def revision |
| `env-power.yml sleep/wake` | — | — | — | — | — | ECS desired count |
| `env-power.yml hibernate` | — | apply `enable_nat=false` | — | **destroy** | **destroy** | — |
| `env-power.yml resume` | — | apply | — | apply | apply | — |

---

# Part C — Production scenarios

Each scenario: **symptom → cause → fix → prevention.** They are the incidents P2's design is built to avoid, or to survive.

### 6.1 "Error acquiring the state lock"

- **Symptom:** every plan in CI fails with `Error acquiring the state lock`, showing a lock ID and who holds it.
- **Cause:** an earlier run was cancelled or its runner died mid-apply, and the `.tflock` object was never deleted.
- **Fix:** first make sure nothing is really running (check the Actions tab). Then run `terraform force-unlock <LOCK_ID>` once, from a trusted shell. Then run a `plan` to see whether the interrupted apply left anything half-done. Convergence (16 §3) means the next apply finishes it.
- **Prevention:** set a `concurrency:` group per stack per env in the workflows so two runs never compete. Never cancel an apply job halfway.

### 6.2 Someone fixed it in the console

- **Symptom:** a PR that changes only a tag plans `~ idle_timeout = 300 -> 120` on the ALB.
- **Cause:** during an incident someone raised the timeout in the console. The code still says 120, so the next apply "fixes" the hotfix away.
- **Fix:** decide which value is right. If it's 300, change the code to 300 (with the reason) and merge. If not, let the apply revert it.
- **Prevention:** a scheduled drift check (`plan -detailed-exitcode`; exit code 2 means changes) that opens an issue. The plan role is read-only, so a person in a hurry can't "fix" things through CI either.

### 6.3 A refactor that would destroy Aurora

- **Symptom:** a PR that "only moves Aurora into a module" plans `- aws_rds_cluster.main` and `+ module.aurora.aws_rds_cluster.this`.
- **Cause:** the address changed, so Terraform sees one resource deleted and another created. Applied, that's a new, empty database.
- **Fix:** add a `moved { from = ..., to = ... }` block. The plan then shows `has moved to` and no destroy.
- **Prevention:** three layers. `prevent_destroy` makes the plan fail. `deletion_protection = true` makes AWS refuse even if Terraform tries. `skip_final_snapshot = false` leaves a snapshot if both are bypassed. Reviewers search plan output for `must be replaced` and `destroy` in the `data` stack. Ideally CI fails any `data` plan that contains a delete.

### 6.4 The image tag tug of war

- **Symptom:** a harmless infra PR is merged, and production goes back to last week's image.
- **Cause:** Terraform's task definition says `image = "...:latest"` (or a hardcoded sha). `deploy.yml` had deployed `:<new-sha>`. Terraform saw the difference and "corrected" it.
- **Fix:** `lifecycle { ignore_changes = [task_definition] }` on the service. Terraform creates the first task definition. After that, `deploy.yml` owns the revisions: it renders a new one with `:<sha>` and deploys it.
- **The same trap with desired count:** `sleep` sets `desiredCount=0`. Without `desired_count` in `ignore_changes`, the next `infra-apply` wakes the environment up again (and autoscaling fights Terraform all day).
- **The catch to know:** if you change an env var in Terraform's task definition, Terraform registers a new revision, but the service ignores it. The change only goes live on the next `deploy.yml` run, and only if the deploy renders from the latest revision of the family rather than from a template file in the repo. Decide which in P2-T08, and write it down.

### 6.5 A secret ends up in state

- **Symptom:** a security review finds the Aurora master password and the JWT signing key in plain text in `devcool/dev/data.tfstate`.
- **Cause:** `master_password = var.db_password` and `aws_secretsmanager_secret_version { secret_string = var.jwt_key }`. Every argument and returned attribute is written to state.
- **Fix, by preference:**
  1. **Don't let Terraform see the value at all.** `manage_master_user_password = true` makes RDS generate the password and store it in Secrets Manager. The ECS task definition references the secret ARN, and the agent injects it at start.
  2. **Write-only arguments** (Terraform ≥ 1.11): `master_password_wo` with `master_password_wo_version`, or `secret_string_wo`, combined with an `ephemeral` random password. The value is sent to AWS and never written to the plan or state. Bump the `_version` to rotate.
  3. **Create only the secret container** in Terraform, and set the value once with the CLI.
- **Prevention:** no secrets in `.tfvars` (an `infra/CLAUDE.md` rule). Treat the state bucket as secret anyway, because some attributes always leak.

### 6.6 A provider upgrade breaks CI

- **Symptom:** a plan that worked yesterday fails in CI with `the cached package for registry.terraform.io/hashicorp/aws ... does not match any of the checksums recorded in the dependency lock file`. Or a minor provider bump suddenly plans to replace resources.
- **Cause 1:** `.terraform.lock.hcl` was generated on a Mac, so it only holds `darwin_arm64` hashes, and CI runs `linux_amd64`.
- **Cause 2:** the version constraint was loose (`>= 5.0`), so a new major version changed defaults.
- **Fix:** run `terraform providers lock -platform=linux_amd64 -platform=darwin_arm64` and commit the result. Pin with `~> 6.x`.
- **Prevention:** upgrade providers on purpose, in their own PR (`init -upgrade`), and read the plan for every stack. That's the same discipline as a Spring Boot upgrade.

### 6.7 `resume` fails halfway

- **Symptom:** `env-power resume` fails in `app` with `parameter /devcool/dev/network/... not found`, or ECS tasks can't pull images (`CannotPullContainerError ... i/o timeout`).
- **Cause:** the order was broken (`app` before `network`), or `network` came back without NAT and without the endpoints the tasks need to reach ECR.
- **Fix:** rerun in the right order: `network` → `app` → `edge`. It's convergent, so rerunning is safe.
- **Prevention:** the workflow encodes the order as dependent jobs (`needs:`), never as parallel ones. `resume` sets `enable_nat` back explicitly. The P2 test plan step "`sleep` → 503, `wake` → 200" is run regularly, not just once.

### 6.8 The migration fails mid-deploy

- **Symptom:** `deploy.yml` is green, but the new tasks crash with Hibernate `validate` errors (a missing column).
- **Cause:** the workflow ran the migrate task and waited with `aws ecs wait tasks-stopped`. **That waiter succeeds whenever the task stops, including when Flyway exits with code 1.** The deploy then went ahead without the schema change.
- **Fix:** after the wait, read `describe-tasks` → `containers[0].exitCode` and fail the job if it isn't `0`. The circuit breaker rolls the service back to the previous revision. Because migrations follow expand/contract ([11 §7d](11-flyway-and-schema-management.md)), the old version still runs on the current schema.
- **Prevention:** the migrate task is a separate, checked step *before* the service update (P2 design notes). The app runs with `spring.flyway.enabled=false` in `ecs`, so N tasks never race to migrate.

### 6.9 State is lost or corrupted

- **Symptom:** after a failed apply or a bad manual `state rm`, the plan wants to **create** resources that clearly exist.
- **Cause:** the state no longer records them. Terraform thinks they're gone.
- **Fix:** the bucket is versioned, so download the previous version of `devcool/<env>/<stack>.tfstate`, check it, and restore it. If only a few entries are missing, add `import` blocks for them instead.
- **Prevention:** versioning and `prevent_destroy` on the state bucket (P2-T01). Never edit state by hand. Use `moved`/`import`/`removed` blocks, which are reviewed in a plan.

### 6.10 WebSocket works locally, fails through CloudFront

- **Symptom:** `wscat -c wss://<cloudfront>/ws` gets `400`/`403`, or connects and drops after a minute, but it works against the ALB directly.
- **Causes, in the order to check them:**
  1. The `/ws` behaviour uses a policy that doesn't forward the upgrade headers, or caches. Use `AllViewerExceptHostHeader` + `CachingDisabled`.
  2. The `/ws?ticket=` query string (ADR-0011, P4) is dropped by a policy that strips query strings.
  3. **Host and TLS mismatch:** `AllViewerExceptHostHeader` makes CloudFront send `Host: <origin domain>`. If the origin is the raw `*.elb.amazonaws.com` name over HTTPS, there's no ACM cert for that name. Give the ALB its own name (e.g. `origin.<domain>`) with a regional cert, and use that as the origin domain.
  4. The ALB idle timeout is at the default 60 s and is shorter than the gap between messages. Keep 120 s and keep the 25 s heartbeat.
  5. Spring's `setAllowedOrigins` doesn't include the CloudFront domain (ADR-0011).
- **Prevention:** the P2 test plan's `wscat` check runs as part of the smoke test, not by hand once. Restrict the ALB security group to CloudFront's origin-facing managed prefix list, so the "works against the ALB directly" path doesn't exist in the first place.

### 6.11 The prod apply that couldn't assume its role

- **Symptom:** after adding a GitHub environment `prod` with required reviewers, `infra-apply` fails: `Not authorized to perform sts:AssumeRoleWithWebIdentity`.
- **Cause:** when a job uses `environment: prod`, the OIDC token's `sub` claim becomes `repo:hinn1411/devcool-core:environment:prod`, not `...:ref:refs/heads/master`. The trust policy no longer matches.
- **Fix:** trust the environment subject for the prod role (and keep it narrow: one repo, one environment).
- **Related:** the reviewer approves the apply job, but the apply re-plans at run time. If `master` moved in between, what's applied may differ from what was reviewed in the PR. For prod, save the plan (`plan -out=tfplan`), show it to the approver, and `apply tfplan`. A saved plan refuses to apply if the state changed in the meantime.

---

## Interview talking points

- **Why layered stacks?** Blast radius and lifecycle. You can destroy `app`/`edge` for cost and never touch `data`.
- **Why SSM over remote state?** Consumers depend on a named contract, not on read access to another stack's state, which may hold secrets.
- **Why doesn't Terraform own the image tag?** One owner per field. The deploy pipeline changes it every merge, so Terraform must ignore it or the two fight (§6.4).
- **How do you keep secrets out of state?** RDS-managed secrets, write-only arguments, and the value set outside Terraform (§6.5).
- **How do you make a destroy impossible?** `prevent_destroy` *and* AWS `deletion_protection`, because the first one disappears with the block (§3.3).
- **OIDC vs access keys:** short-lived, scoped by repo/branch/environment, nothing to rotate or leak (§4.2, §6.11).
- **What does "scale to zero" really cost?** Name what still bills: ALB, NAT, interface endpoints, Aurora storage. Explain why Aurora only pauses once the tasks are gone (§4.2).

## Where else this applies

- **The one-owner table (§5)** is the same exercise as deciding which service owns which table in a modular monolith ([15](15-architecture-styles-analogies.md)).
- **SSM parameters as ports (§4.1)** are hexagonal architecture ([03](03-hexagonal-architecture.md)) at the infrastructure level.
- **"The waiter succeeded but the task failed" (§6.8)** is the same class of bug as `return null` producing HTTP 200 (README issue #6): a success signal that doesn't check the result.
- **Expand/contract (§6.8)** is what lets an immutable rolling deploy coexist with one shared, mutable database.

## Check yourself

1. You rename `aws_lb.main` to `aws_lb.api`. What does the plan show without a `moved` block, and why is that worse for the ALB than it looks? (Hint: CloudFront's origin.)
2. Why does the read-only `gha-plan` role still need `s3:PutObject` on the state bucket?
3. `sleep` ran at 22:00. At 22:05 a docs-only change to `infra/README.md` merges. What happens to the environment if `desired_count` isn't in `ignore_changes`?
4. Aurora in dev never pauses, even at 3 a.m. List two causes.
5. Where would you put the `/ws` CloudFront behaviour's policies if you wanted `frontend-deploy.yml` to never be able to change them?
