# 0003 — Terraform with layered stacks

- **Status:** Accepted; environments, hibernate scope (Valkey toggle in `data`, `edge` kept) and apply order (`edge` before `app`) amended by [0013](0013-dev-cost-envelope-under-20-usd.md)
- **Date:** 2026-09-29
- **Phase:** P2

## Context
There is no IaC. The target has ~40 AWS resources across networking, data, compute and edge. "Scale to zero" also means being able to destroy expensive always-on parts (NAT, ALB) while keeping data.

## Decision drivers
- Reproducible environments (dev, prod).
- Reviewable changes (`plan` in PRs).
- The ability to destroy cost-heavy layers without touching data.
- Interview value.

## Options considered

### A — Terraform
- Pros: the most requested IaC skill. Declarative plan/apply review. A huge module registry (`terraform-aws-modules`). Cloud-agnostic knowledge.
- Cons: you manage state (S3 backend). HCL has limited abstraction.

### B — AWS CDK (Java or TypeScript)
- Pros: real programming language, high-level constructs, generated IAM.
- Cons: synthesizes CloudFormation (slow, opaque drift handling). AWS-only.

### C — Pulumi
- Pros: real languages, multi-cloud.
- Cons: smaller community; the managed state service is another account.

### Layout sub-options
- **One root module:** simple, but every apply touches everything and you can't destroy NAT/ALB alone.
- **Layered stacks** with separate state, reading each other's outputs via `terraform_remote_state` or SSM parameters: small blast radius, independent lifecycles. **Chosen.**
- **Terragrunt:** DRY across many envs. Overkill for two.

## Decision
**Terraform, layered stacks**, one state file per stack per env:

```
infra/
  modules/                 # own reusable modules (ecs-service, sqs-with-dlq, …)
  stacks/
    bootstrap/   # state bucket, GitHub OIDC provider + roles, ECR     (apply once, by hand)
    network/     # VPC, subnets, NAT, VPC endpoints, security groups
    data/        # Aurora, Valkey, S3 media, SNS/SQS, secrets          (never destroyed)
    app/         # ECS cluster, services, task defs, ALB, autoscaling
    edge/        # CloudFront, S3 SPA bucket, Route 53, ACM, WAF
  envs/
    dev.tfvars
    prod.tfvars
```

- Backend: S3 with native state locking (`use_lockfile = true`), so no DynamoDB table is needed.
- Community modules (`terraform-aws-modules/vpc`, `…/ecs`, `…/rds-aurora`) for undifferentiated parts; own modules where the design matters (the ECS service with its sidecar, a queue with its DLQ).

## Consequences
- Stack order matters: bootstrap → network → data → app → edge. The deploy workflow encodes it. *Amended by [0013](0013-dev-cost-envelope-under-20-usd.md): bootstrap → network → data → edge → app, since `app` reads `edge`'s zone and cert from SSM.*
- "Hibernate" = `destroy` edge + app (+ NAT in network); `data` survives. *Amended by [0013](0013-dev-cost-envelope-under-20-usd.md): only `app` is destroyed; `edge` is kept, and `network` and `data` turn their toggles off.*
- Task definition image tags are owned by the deploy pipeline, not Terraform (`lifecycle { ignore_changes = [task_definition] }` on the service), to avoid a tug of war.

## Revisit when
- More than two environments or accounts: consider Terragrunt or Terraform Stacks.
