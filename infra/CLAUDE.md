# infra/ — Terraform (AWS)

This is built in P2 (`docs/plans/phases/phase-2-infra-cicd-walking-skeleton.md`). The layout and reasoning are in ADR-0003.

## Layout
```
infra/
  modules/          # own modules (ecs-service, sqs-with-dlq, …)
  stacks/
    bootstrap/      # state bucket, GitHub OIDC + roles, ECR — applied once by hand
    network/        # VPC, subnets, NAT (toggle), endpoints, SGs
    data/           # Aurora PG + pgvector, Valkey, S3 media, SNS/SQS, secrets — never destroyed
    app/            # ECS cluster/services, ALB, autoscaling, IAM task roles
    edge/           # CloudFront, S3 SPA bucket, ACM, Route 53
  envs/{dev,prod}.tfvars
  observability/    # collector config, Grafana dashboards/alerts as code (P7)
```
Apply order: bootstrap → network → data → app → edge.

## Commands
```bash
terraform -chdir=infra/stacks/<stack> init -backend-config=../../envs/<env>.backend.hcl
terraform -chdir=infra/stacks/<stack> plan -var-file=../../envs/<env>.tfvars
terraform fmt -recursive infra
tflint --chdir infra
```
Claude may run `init`, `fmt`, `validate` and `plan`. `apply` always needs the user's approval. `destroy` is blocked; the user runs it (or the `env-power` workflow does).

## Conventions
- Region `ap-southeast-1` (ACM certs for CloudFront in `us-east-1` via a provider alias).
- Tag every resource: `Project=devcool`, `Environment=<env>`, `Stack=<stack>`, `ManagedBy=terraform`.
- Prefer `terraform-aws-modules/*` for undifferentiated resources. Pin module and provider versions.
- **IAM:** specific actions on specific ARNs. No `*` resources except where AWS requires it (and say why in a comment).
- No secrets in `.tfvars` or code. Use Secrets Manager / RDS-managed secrets and reference them by ARN.
- The ECS service ignores `task_definition` changes; the deploy pipeline owns the image tag.
- Cross-stack values go through SSM parameters written by the producer stack.
- Keep scale-to-zero working: anything always-on and billed hourly must be in `app`, `edge` or a toggle in `network`, never in `data`, unless it holds state.
- Use the `aws-architect` agent to review plans; check provider arguments with the Terraform MCP/AWS Knowledge MCP rather than from memory.
