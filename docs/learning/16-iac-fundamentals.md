# 16 — Infrastructure as Code: Fundamentals

The theme: **what changes when infrastructure becomes a reviewed, versioned program instead of a sequence of console clicks?** This chapter doesn't depend on any tool. Chapter [17](17-terraform-in-devcool.md) applies it to Terraform and the P2 stacks.

It is written for a backend engineer. Most IaC ideas are ideas you already use in a different form, so each section starts from the backend idea you know.

---

## 0. The ideas you already know

| You already know… | The IaC version |
|---|---|
| Flyway: the schema is a versioned log in git, applied by a tool, never edited by hand in prod ([11](11-flyway-and-schema-management.md)) | The *whole environment* (network, DB, compute, DNS) is versioned in git and applied by a tool |
| `ddl-auto=update`: Hibernate compares entities to the DB and works out the diff | `plan`: the tool compares code to reality and works out the diff |
| Pull request + CI before merge | Plan output in the PR, apply on merge |
| A JPA entity is a *description*; the row is the real thing | A `resource` block is a description; the AWS object is the real thing |
| Hibernate's persistence context remembers which rows it loaded | IaC **state** remembers which real objects belong to which description |
| A class with a constructor and private fields | A **module** with input variables and outputs |

If one sentence carries the chapter, it's this one: **IaC treats infrastructure the way Flyway treats the schema.** It lives in git, it's reviewed, it's applied by a tool, and hand edits in prod count as bugs.

---

## 1. Why IaC at all

Before P2, `README.md` describes a manually managed ECS service. That works until one of these happens:

| Problem with click-ops | What it costs you |
|---|---|
| **Nobody knows the real config.** The ALB idle timeout is "whatever someone set in 2025". | WS connections drop at 60 s and nobody can tell why (see [14 §4](14-health-checks-and-graceful-shutdown.md)) |
| **No second environment.** Building `prod` means repeating 200 clicks from memory. | `dev` and `prod` differ in ways you only find in an incident |
| **No review.** A security group opened to `0.0.0.0/0` is one click and leaves no diff. | A security hole with no author and no history |
| **No undo.** You can't `git revert` a console session. | Recovery means remembering what it looked like before |
| **No teardown.** Deleting a whole environment by hand misses things, and the forgotten NAT gateway keeps billing. | P2's "hibernate" mode is impossible to do safely by hand |

IaC fixes all five for the same reason: **the code becomes the source of truth, and reality is made to match it.**

---

## 2. Declarative vs imperative

**Imperative** means you write the *steps*:

```bash
aws ec2 create-security-group --group-name api ...
aws ec2 authorize-security-group-ingress --group-id sg-123 --port 8080 ...
```

Run it twice and the second run fails ("already exists"), or creates a duplicate. The script only knows how to get from *empty* to *done*. It doesn't know how to get from *half done* or *slightly different* to *done*.

**Declarative** means you write the *end state*:

```hcl
resource "aws_security_group" "api" {
  name = "api"
  # ingress 8080 from the ALB SG
}
```

The tool reads the end state, looks at reality, and works out the steps itself: create, update in place, replace, or nothing.

| | Imperative (bash + CLI, SDK scripts) | Declarative (Terraform, CloudFormation) |
|---|---|---|
| You describe | How | What |
| Second run | Errors or duplicates | No-op |
| Handles partial failure | Only if you code it | Yes: the next run continues from wherever reality is |
| Shows the change before making it | No | Yes (`plan`) |
| Good for | One-off operational actions | Things that should *exist* |

The backend version of this: `INSERT` is imperative, `INSERT ... ON CONFLICT DO UPDATE` is declarative. An upsert says "this row should look like this", and running it twice is safe.

**Where DevCool uses each.** Terraform (declarative) owns things that exist: VPC, Aurora, ALB. GitHub Actions steps (imperative) do *actions*: run the migrate task once, push an image, invalidate a cache, set the desired count to 0. Mixing them up is a common mistake. Chapter 17 §6.4 shows what goes wrong when Terraform tries to own an action-like value such as the image tag.

---

## 3. Desired state, convergence and idempotency

Every declarative tool runs the same loop:

```
   desired state (code)  ──┐
                           ├──► diff ──► plan ──► apply ──► reality
   actual state (cloud)  ──┘                                   │
            ▲                                                  │
            └──────────────────── refresh ─────────────────────┘
```

- **Convergence:** each run moves reality closer to the code. If an apply fails halfway, rerunning it finishes the job. You don't have to clean up first.
- **Idempotency:** once reality matches the code, running again changes nothing. "Plan shows no changes" is the healthy steady state, and P2's test plan checks exactly that: *"`terraform plan` shows no changes after apply."*

An idempotent apply is what makes the tool safe to run in a pipeline on every merge. If running it twice could break something, you couldn't automate it.

**Not everything converges.** Some changes can't happen in place, and the cloud API forces a *replace* (destroy, then create). Renaming an Aurora cluster identifier or changing a VPC CIDR is a replace. For a stateless thing that's fine. For a database it means data loss. The core reading skill in IaC is to scan a plan for `must be replaced` on anything that holds state (chapter 17 §6.3).

---

## 4. Drift

**Drift** is when reality no longer matches the code, because something other than the tool changed it:

| Source of drift | Example in DevCool | Is it OK? |
|---|---|---|
| A human in the console | Someone raises the ALB idle timeout to 300 s during an incident | No. Either put it in the code or revert it |
| Another automated owner | Autoscaling sets the ECS desired count to 4. `env-power` sets it to 0 | Yes, **if the code says it doesn't own that field** (`ignore_changes`) |
| The pipeline, by design | `deploy.yml` registers a new task definition with image `:<sha>` | Yes, the same way |
| AWS itself | AWS adds a default tag, or normalizes a JSON policy | Usually a provider quirk, and harmless |

The rule: **every field has exactly one owner.** If two systems both think they own a value, they keep overwriting each other. Every apply "fixes" what the other one did, and you get a tug of war. Drift detection (`plan` with no code change) is how you find a field with two owners.

---

## 5. Mutable vs immutable infrastructure

- **Mutable:** you change servers in place (SSH in, `apt upgrade`, edit a config). Over time each server becomes a unique "snowflake".
- **Immutable:** you never change a running unit. You build a new one and replace the old one.

DevCool is immutable at the compute layer:

- The Docker image is built once per commit and tagged `:<sha>`. Nothing is ever patched in place.
- A deploy starts *new* ECS tasks from a new task definition revision and drains the old ones (rolling, min 100% / max 200%).
- Rollback means pointing back to the previous revision, which still exists, unchanged.

State can't be immutable. Aurora and S3 are the long-lived, mutable core, and you protect them differently: no destroy, backups, migrations as an append-only log. This split between disposable compute and protected state is the reason the stacks are layered the way they are (chapter 17 §4).

---

## 6. Blast radius

**Blast radius** is how much can break from one change, or from one mistake.

With one big IaC project, every apply *could* touch everything. A typo in a CloudFront setting is planned in the same run as the database. A bad state file loses track of all 40 resources at once.

The fix is to split by **lifecycle and risk**, not by service:

| Changes… | Examples | Risk |
|---|---|---|
| Almost never | State bucket, OIDC, ECR | Hard to rebuild by hand |
| Rarely | VPC, subnets | Everything sits on it |
| Rarely, and holds data | Aurora, S3 media, secrets | Data loss |
| Often | ECS service, ALB rules | Recoverable outage |
| Sometimes | CloudFront, DNS | Global outage, slow to propagate |

Each row becomes its own unit with its own state. That's ADR-0003's "layered stacks". It also gives you the ability to **destroy the expensive, stateless layers and keep the data layer**, which is exactly what P2's `hibernate` needs.

It's the same reasoning as the hexagonal layers ([03](03-hexagonal-architecture.md)): things that change for different reasons should live apart, and dependencies should point toward the stable core.

---

## 7. Environments as code

The goal is for `dev` and `prod` to be **the same code with different inputs**:

```
same stacks ──► dev.tfvars   (min 0 ACU, 1 task, NAT toggle off at night)
            └─► prod.tfvars  (min 0.5 ACU, 2+ tasks, NAT always on)
```

What may differ is sizes, counts, names and feature toggles. What must not differ is *structure*, such as "prod has a WAF rule that dev doesn't have". Structural differences are where "works in dev" stops meaning anything.

Each environment also gets **its own state**, so a dev apply can never touch prod resources, even by mistake. DevCool keys state as `devcool/<env>/<stack>.tfstate`.

---

## 8. GitOps: the change workflow

IaC only gives you review and history if **every** change goes through git. The workflow DevCool uses (P2-T08):

```
branch ──► PR touching infra/** ──► infra-plan.yml: plan per changed stack, posted as a PR comment
                                      │
                               human review of the PLAN (not just the code)
                                      │
           merge to master ──► infra-apply.yml: apply changed stacks in order
                                      │
                               prod: GitHub environment with required reviewers
```

Two points that are easy to miss:

1. **Review the plan, not just the diff.** A one-line code change can plan as "destroy and recreate the database". The code diff looks harmless, and only the plan shows the real effect.
2. **The pipeline gets credentials. Humans mostly don't.** CI assumes a role through OIDC (short-lived tokens, nothing stored in GitHub secrets). The plan role is read-only, and only the apply role, on `master`, can change things.

---

## 9. Principles, in one list

1. **The code is the source of truth.** If it isn't in git, it doesn't exist (or it's drift to remove).
2. **Declare the end state.** Use imperative scripts only for one-off actions.
3. **Every field has one owner:** IaC, the pipeline, autoscaling, or a human. Never two.
4. **Idempotent and convergent.** You can run it again, at any time, safely.
5. **Small blast radius.** Split by lifecycle and risk. Protect state.
6. **Immutable compute, protected data.**
7. **Same code, different inputs** for each environment.
8. **Plan → review → apply**, through CI, with least-privilege short-lived credentials.
9. **No secrets in code**, and as few as possible in state (chapter 17 §6.5).
10. **Pin versions** (tool, providers, modules), for the same reason you pin Maven dependencies.

---

## Where else this applies

The general idea: **describe what should be true, let a reconciler make it true, and keep one owner per fact.**

- **Flyway** ([11](11-flyway-and-schema-management.md)) is IaC for the schema, with one difference: it's an *append-only log of steps* (imperative migrations), not a desired-state diff. That's why you never edit an applied migration, but you *do* edit a Terraform resource in place.
- **Kubernetes** controllers are the same reconcile loop running continuously instead of once per apply.
- **ECS itself** reconciles. You declare "desired count 2", and the scheduler keeps starting tasks until 2 are healthy. The circuit breaker stops it when that can never converge.
- **Spring config** follows the same pattern: `application-<profile>.properties` is "same code, different inputs" for each environment.
- **The one-owner rule** shows up in the code too. The JPA `updateLoginTime` merge bug in [01](01-jpa-and-transactions.md) is two writers claiming the same column.

## Check yourself

1. Why is a bash script of `aws` CLI calls not "IaC" in the sense of this chapter, even though it lives in git?
2. Autoscaling raised the task count to 4. The next `terraform apply` sets it back to 2. What principle is broken, and what's the fix?
3. Give one DevCool resource where `must be replaced` in a plan is harmless, and one where it's an incident.
4. Why does splitting state by stack make `hibernate` possible?
