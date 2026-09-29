# 08 — Terraform and infrastructure as code

> **Used in DevCool:** [ADR-0003](../plans/architecture/adr/0003-terraform-layered-stacks.md) (layered stacks, S3 backend) · [Phase 2](../plans/phases/phase-2-infra-cicd-walking-skeleton.md) (P2-T01–T10, T13) · P6-T03 (SNS/SQS module) · P7-T07, T09 (Grafana integration, alerts as code) · P8-T07 (Bedrock IAM, endpoint)
> **Also see:** [infra/CLAUDE.md](../../infra/CLAUDE.md)
> **Links checked:** 2026-09-29

## Concepts to own

- **State is the source of truth for Terraform.** It maps resources in code to real IDs. Losing or corrupting it is the worst IaC failure; it lives in a versioned, encrypted S3 bucket.
- **State locking.** Two concurrent applies corrupt state. The S3 backend can now lock with a lock file in the bucket (`use_lockfile = true`), so no DynamoDB table is needed.
- **Layered stacks.** Separate root modules (and state files) per lifecycle: bootstrap → network → data → app → edge. Small blast radius, and you can destroy `app`/`edge` to save money while `data` survives.
- **Cross-stack wiring.** A later stack reads an earlier one's outputs via `terraform_remote_state` (or SSM parameters, which decouple stacks further).
- **Modules.** Community modules (`terraform-aws-modules/*`) for undifferentiated parts; your own modules where the design matters (`ecs-service` with its sidecar, `sqs-with-dlq`).
- **`lifecycle` meta-arguments.** `ignore_changes = [task_definition]` lets the deploy pipeline own image tags without Terraform reverting them; `prevent_destroy` guards data.
- **Environments via tfvars.** One set of stacks, `dev.tfvars` / `prod.tfvars`, one state per stack per env.
- **Plan as a review artifact.** `terraform plan` output in the PR, `apply` only from the main branch with approval.
- **Static checks.** `fmt`, `validate`, `tflint` (provider-aware lint), `trivy config` / Checkov (security misconfigurations).

## Read first

1. [Backend type: s3](https://developer.hashicorp.com/terraform/language/backend/s3) — *official docs* · `use_lockfile`, encryption, and the IAM permissions the state bucket needs.
2. [State](https://developer.hashicorp.com/terraform/language/state) — *official docs* · Purpose of state, remote state, sensitive data in state.
3. [The `lifecycle` meta-argument](https://developer.hashicorp.com/terraform/language/meta-arguments/lifecycle) — *official docs* · `ignore_changes`, `prevent_destroy`, `create_before_destroy`.
4. [Module composition](https://developer.hashicorp.com/terraform/language/modules/develop/composition) — *official docs* · Dependency inversion between modules; how to keep modules small and composable.
5. [How to manage Terraform state](https://www.gruntwork.io/blog/how-to-manage-terraform-state) — *Yevgeniy Brikman, Gruntwork* · Why and how to isolate state per environment and per component; the reasoning behind layered stacks.

## Reference

### Language and state

- [The `terraform_remote_state` data source](https://developer.hashicorp.com/terraform/language/state/remote-state-data) — *official docs* · Reading another stack's outputs, and the recommendation to prefer published values when possible.
- [Modules overview](https://developer.hashicorp.com/terraform/language/modules) — *official docs* · Root vs child modules, sources, versions.
- [Style guide](https://developer.hashicorp.com/terraform/language/style) — *official docs* · File layout, naming, variables and outputs conventions.

### AWS provider and community modules

- [AWS provider documentation](https://registry.terraform.io/providers/hashicorp/aws/latest/docs) — *Terraform Registry* · Resource reference; start from `aws_ecs_service`, `aws_cloudfront_distribution`, `aws_rds_cluster`.
- [terraform-aws-modules/vpc](https://github.com/terraform-aws-modules/terraform-aws-vpc) — *GitHub* · VPC, subnets, NAT options (single vs per-AZ), endpoints.
- [terraform-aws-modules/ecs](https://github.com/terraform-aws-modules/terraform-aws-ecs) — *GitHub* · Cluster and service submodules; read the service module to see what your own `ecs-service` module needs.
- [terraform-aws-modules/rds-aurora](https://github.com/terraform-aws-modules/terraform-aws-rds-aurora) — *GitHub* · Aurora Serverless v2 examples and the managed master password option.
- [Terraform AWS Provider best practices](https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/introduction.html) — *AWS Prescriptive Guidance* · Security, backend, code structure and CI/CD recommendations from AWS.

### Linting and security scanning

- [TFLint](https://github.com/terraform-linters/tflint) and the [AWS ruleset](https://github.com/terraform-linters/tflint-ruleset-aws) — *GitHub* · Invalid instance types, deprecated arguments, provider-specific mistakes.
- [Trivy — misconfiguration scanning](https://trivy.dev/docs/latest/scanner/misconfiguration/) — *official docs* · `trivy config infra/` in CI (P2-T10).
- [Checkov](https://www.checkov.io/) — *official docs* · Policy-as-code checks; an alternative or complement to Trivy.

### When the layout grows

- [Terragrunt](https://terragrunt.com/) — *official docs* · DRY configuration across many environments/accounts (ADR-0003's "revisit when").
- [Terraform Stacks](https://developer.hashicorp.com/terraform/language/stacks) — *official docs* · HashiCorp's native answer to multi-component, multi-environment deployments.

### Books

- *Terraform: Up & Running*, 3rd ed. (Yevgeniy Brikman) — ch. 3 (state), ch. 4 (modules), ch. 5 (loops/conditionals), ch. 9–10 (testing, team workflow).
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- What happens if two people run `apply` on the same stack at once without locking? How does `use_lockfile` prevent it?
- Why is `data` a separate stack that is never destroyed? What would one root module make impossible?
- The deploy pipeline registers a new task definition revision. Why would the next `terraform apply` try to revert it, and how does `ignore_changes` stop that?
- `terraform_remote_state` vs SSM parameters for cross-stack outputs: what coupling does each create?
- Which secrets can end up in Terraform state, and what does that mean for the state bucket's access policy?
- What does TFLint catch that `terraform validate` doesn't?
