# 0013 — Dev environment cost envelope under \$20/month

- **Status:** Accepted; amended 2026-10-16 (Valkey toggle in `data`, `edge` survives hibernate)
- **Date:** 2026-10-15
- **Phase:** P2

## Context
The AWS budget is **< \$20/month** ([playbook §0](../../delivery-playbook.md#0-profile-and-goals)), with alerts at \$10/\$18 from P2-T13 (`infra/stacks/bootstrap/budget.tf`). [Playbook §6](../../delivery-playbook.md#6-aws-under-20month) prices the P2 plan as written at about \$260/month if always on, and asks for this ADR before P2-T02. Three parts of the current plan don't fit:
- P2-T02 lists 4 interface endpoints but only the NAT has a toggle. Endpoints bill per hour per AZ even with no traffic, so they would bill while hibernated.
- [ADR-0003](0003-terraform-layered-stacks.md) and P4-T05 put Valkey in `data` ("never destroyed"), which bills ~\$7.4/month while asleep. That breaks the rule in `infra/CLAUDE.md` ("anything always-on and billed hourly must be in `app`, `edge` or a toggle in `network`").
- ADR-0003 and P2-T08 plan two environments (`dev`, `prod`), which doubles every cost.

## Decision drivers
- Keep monthly spend under \$20 with ~28 planned awake hours (playbook §6 windows).
- Hibernated is the default state; the hibernated baseline must stay near \$2–3.
- Keep the production-shaped network (private tasks, PrivateLink) for the interview story.
- Hibernate and resume must be one workflow run each (P2-T09), not manual cleanup.

## Options considered
Awake = 1 api task, 0.5 ACU, us-east-1.

| | A. Plan as written | **B. Hibernate-first, production-shaped** | C. Cheapest | D. NAT only |
|---|---|---|---|---|
| Envs | dev + prod | dev only | dev only | dev only |
| Egress | NAT (toggle) + 4 endpoints × 2 AZ (always on) | NAT + 4 endpoints × 2 AZ, **both toggled** | Tasks in public subnets, public IP, no NAT/endpoints | NAT (toggle), no endpoints |
| Valkey (P4) | `data`, always on | `data`, toggled (`enable_valkey`) | destroyable | destroyable |
| Domain | Route 53 + ACM | Route 53 + ACM | CloudFront default | either |
| Awake \$/h | ~\$0.26 per env | ~\$0.26 | ~\$0.12 | ~\$0.18 |
| Hibernated \$/month | ~\$66 (endpoints + Valkey) ×2 envs | ~\$2.5 | ~\$2 | ~\$2.5 |
| Interview story | full, but busts the budget | full: private subnets, PrivateLink, SG chain | weaker: public task IPs, no egress control | private, but no PrivateLink |

- **A** fails the budget while asleep. Not viable.
- **C** saves ~\$0.14/h but drops private subnets and egress control, which are the parts worth explaining in an interview.
- **D** is the cheapest private option. It was rejected so that the endpoint path (`CannotPullContainerError` without NAT, ECR layers via the S3 gateway) is built and measured, not only described.
- Not chosen in any option: **ARM/Graviton** (~20% of Fargate, about \$0.005/h here; the image stays `linux/amd64`, P2-T06), and **fck-nat** (cheaper than NAT, but an instance to patch and operate).

## Decision
**B.** Only `dev` exists. Three toggles, all on while awake and all off on hibernate: `enable_nat` and `enable_interface_endpoints` in `network`, and `enable_valkey` in `data`. Valkey stays in `data` with the other datastores; it holds only temporary data (WS tickets, pub/sub, presence, rate limits), so dropping it on hibernate loses nothing. `edge` uses a Route 53 domain with an ACM cert and **survives hibernate**: CloudFront, the SPA bucket, ACM and the hosted zone have no hourly charge (the zone is \$0.50/month). The hourly-billed ALB is in `app`.

Cost basis (us-east-1): NAT \$0.045/h, interface endpoint \$0.01/h per AZ + \$0.01/GB, Aurora Serverless v2 \$0.12/ACU-h (checked on the AWS pricing pages on 2026-10-15). ALB, Fargate and public IPv4 are list prices; refresh them with the measured cost after the M1 window. At ~\$0.26/h awake (≈ \$0.32/h at M2 with 2 api + 1 worker + Valkey) and ~\$2.5/month hibernated, \$18 buys ~48–60 awake hours, against ~28 planned.

## Consequences
- **Apply order** becomes bootstrap → network → data → edge → app: `app` reads the zone id and cert ARN from `edge` (SSM), and `edge` reads nothing from `app`.
- **P2-T02:** both toggles; turning one off must change only what it owns (plan diff). With tasks running, both are on.
- **P2-T03:** `prevent_destroy` on the Aurora cluster and the media bucket, since hibernate now applies `data`.
- **P2-T04:** the ALB's HTTPS listener uses the `edge` cert, and `app` creates a fixed `origin.<domain>` alias record pointing at the ALB. The ALB's DNS name changes every time `app` is recreated; the alias keeps CloudFront's origin stable.
- **P2-T07:** the hosted zone lives in `edge`; since `edge` survives hibernate, the name servers and the registrar delegation never change. One us-east-1 cert covers `<domain>` and `origin.<domain>` and serves both CloudFront and the ALB. The API origin is `origin.<domain>`, so while hibernated the SPA still loads and `/api/*` and `/ws` return 502 (optionally a custom error page says the env is asleep). Nothing billed hourly (e.g. WAF) goes in `edge` without revisiting this ADR. Register the domain in a low-usage month (a yearly charge lands on that month's budget).
- **P2-T08:** no `prod` apply and no `prod` GitHub environment; `infra/envs/` keeps the per-env layout so `prod` is only new tfvars + backend files later.
- **P2-T09:** hibernate = destroy `app`, apply `data` with `enable_valkey=false`, then `network` with both toggles off; resume reverses it, and `edge` is not touched. The `data` step applies a saved plan and fails unless the Valkey cache is the only change (`terraform show -json`), and `env-power`'s role needs write access on `data`. The nightly hibernate is mandatory, not optional.
- **P4-T05:** Valkey goes in `data` behind `enable_valkey`. This amends ADR-0003's environments, hibernate scope and apply order; its Valkey placement stands.
- Harder: no prod/dev separation story (say so in interviews); every resume pays the endpoint and NAT hours from the start; and an unattended nightly apply runs on the stack that holds Aurora, guarded by the plan check and `prevent_destroy`.

## Revisit when
- Cost Explorer shows > \$15 in any month, or the measured awake cost after M1 is > \$0.35/h: drop the interface endpoints first (option D).
- A second environment or real users appear: add `prod` and re-price.
