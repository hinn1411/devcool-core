# 09 — CI/CD with GitHub Actions, and supply-chain checks

> **Used in DevCool:** [Phase 1](../plans/phases/phase-1-foundation-hardening.md) (P1-T14: ITs in CI, blocking static analysis) · [Phase 2](../plans/phases/phase-2-infra-cicd-walking-skeleton.md) (P2-T01 OIDC roles, P2-T08 deploy/plan/apply, P2-T09 env-power, P2-T10 infra checks) · P5-T02, T13–T14 (API client check, E2E, frontend deploy) · P0-T13 (Claude review action) · [ADR-0004](../plans/architecture/adr/0004-monorepo.md) (path filters)
> **Links checked:** 2026-09-29

## Concepts to own

- **OIDC federation instead of access keys.** GitHub issues a short-lived OIDC token per job; AWS STS exchanges it for a role session. The role's trust policy pins the repo, branch or environment (`sub` claim). No long-lived AWS keys in secrets.
- **Two roles.** A read-only `gha-plan` role for PR plans and a `gha-deploy` role only assumable from `main`/a protected environment.
- **Environments and approvals.** GitHub environments gate deploys with required reviewers and scope secrets and OIDC `sub` claims.
- **Path filters in a monorepo.** Run Maven only when backend files change, npm only for `frontend/`, Terraform checks only for `infra/`.
- **Concurrency groups.** Prevent two deploys (or two `apply`s) to the same environment from overlapping.
- **Build once, deploy the same artifact.** Image tagged with the git SHA, pushed to ECR, the task definition rendered with that tag, then the ECS service updated. The DB migrate task runs before the service update.
- **Caching.** Maven repository, npm, Testcontainers images; cache keys based on lockfiles.
- **Quality gates.** Formatting (Spotless / google-java-format), static analysis (Checkstyle, PMD, SpotBugs), coverage (JaCoCo), unit tests (Surefire) and ITs (Failsafe).
- **Supply chain.** Dependency updates (Dependabot), image and IaC scanning (Trivy), ECR scan on push, pin third-party actions to a SHA.

## Read first

1. [Configuring OpenID Connect in Amazon Web Services](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws) — *GitHub docs* · The trust policy and `sub` claim conditions for P2-T01.
2. [aws-actions/configure-aws-credentials](https://github.com/aws-actions/configure-aws-credentials) — *README* · `role-to-assume`, `id-token: write` permission, session duration.
3. [Workflow syntax for GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax) — *GitHub docs* · `on.paths`, `permissions`, `concurrency`, `environment`, job outputs.
4. [Security hardening for GitHub Actions](https://docs.github.com/en/actions/reference/security/secure-use) — *GitHub docs* · Least-privilege `GITHUB_TOKEN`, pinning actions, untrusted input in `run:` steps.
5. [dorny/paths-filter](https://github.com/dorny/paths-filter) — *README* · Job-level path filtering for the monorepo CI.

## Reference

### GitHub Actions

- [Managing environments for deployment](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments) — *GitHub docs* · Required reviewers, branch rules, environment secrets.
- [Control the concurrency of workflows and jobs](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency) — *GitHub docs* · `concurrency.group` and `cancel-in-progress`.
- [Dependency caching](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching) — *GitHub docs* · Cache keys and restore keys; `actions/setup-java` has a built-in Maven cache.
- [actions/setup-java](https://github.com/actions/setup-java) — *README* · JDK 21 + Maven cache in one step.

### AWS deploy actions

- [aws-actions/amazon-ecr-login](https://github.com/aws-actions/amazon-ecr-login) — *README* · Docker login to ECR from a workflow.
- [aws-actions/amazon-ecs-render-task-definition](https://github.com/aws-actions/amazon-ecs-render-task-definition) — *README* · Swap the image tag into a task definition JSON.
- [aws-actions/amazon-ecs-deploy-task-definition](https://github.com/aws-actions/amazon-ecs-deploy-task-definition) — *README* · Register and deploy, optionally wait for service stability.
- [Create an OpenID Connect identity provider in IAM](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_create_oidc.html) — *AWS docs* · The AWS side of the OIDC setup (done in the `bootstrap` stack).

### Java build quality

- [Spotless Maven plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven) — *README* · `spotless:check` / `spotless:apply` with google-java-format.
- [google-java-format](https://github.com/google/google-java-format) — *README* · The formatter the hooks run.
- [Checkstyle](https://checkstyle.org/), [PMD](https://pmd.github.io/), [SpotBugs](https://spotbugs.github.io/) — *official sites* · The three static analysers in the `static-analysis` profile; read how to suppress a finding explicitly (P1-T14).
- [JaCoCo documentation](https://www.jacoco.org/jacoco/trunk/doc/) — *official docs* · Coverage counters and the Maven plugin goals.
- [Maven Failsafe plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/) — *official docs* · Why ITs run in `integration-test`/`verify`, separately from Surefire unit tests.

### Supply chain

- [Dependabot version updates](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependabot-version-updates) — *GitHub docs* · Version and security updates; grouping updates to reduce PR noise.
- [aquasecurity/trivy-action](https://github.com/aquasecurity/trivy-action) — *README* · Image and config scans in CI with SARIF upload.
- [SLSA](https://slsa.dev/) — *framework* · Vocabulary for build provenance and supply-chain levels, useful in interviews.

### AI review in CI

- [anthropics/claude-code-action](https://github.com/anthropics/claude-code-action) — *README* · The action behind `.github/workflows/claude.yml` (P0-T13). See also [16 — Claude Code](16-claude-code.md).

## Self-check

- What stops a workflow in a fork, or on a feature branch, from assuming `gha-deploy`?
- Why is `permissions: id-token: write` needed, and why set `permissions` at job level?
- In what order must the deploy workflow run the migrate task and the service update, and why?
- A frontend-only PR: which jobs should run, and which must still run (hint: the API client drift check)?
- Why pin third-party actions to a commit SHA rather than a tag?
- Where does the image tag live: in Terraform or in the pipeline? What prevents the tug of war?
