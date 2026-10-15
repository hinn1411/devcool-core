# 0013 — Dev environment cost envelope under $20/month

- **Status:** Accepted
- **Date:** 2026-10-15
- **Phase:** P2

## Context
The AWS budget is **< $20/month** ([playbook §0](../../delivery-playbook.md#0-profile-and-goals)), with alerts at $10/$18 from P2-T13 (`infra/stacks/bootstrap/budget.tf`). [Playbook §6](../../delivery-playbook.md#6-aws-under-20month) prices the P2 plan as written at about $260/month if always on, and asks for this ADR before P2-T02. Three parts of the current plan don't fit:
- P2-T02 lists 4 interface endpoints but only the NAT has a toggle. Endpoints bill per hour per AZ even with no traffic, so they would bill while hibernated.
- [ADR-0003](0003-terraform-layered-stacks.md) and P4-T05 put Valkey in `data` ("never destroyed"), which bills ~$7.4/month while asleep. That breaks the rule in `infra/CLAUDE.md` ("anything always-on and billed hourly must be in `app`, `edge` or a toggle in `network`").
- ADR-0003 and P2-T08 plan two environments (`dev`, `prod`), which doubles every cost.

## Decision drivers
- Keep monthly spend under $20 with ~28 planned awake hours (playbook §6 windows).
- Hibernated is the default state; the hibernated baseline must stay near $2–3.
- Keep the production-shaped network (private tasks, PrivateLink) for the interview story.
- Hibernate and resume must be one workflow run each (P2-T09), not manual cleanup.

## Options considered
Awake = 1 api task, 0.5 ACU, us-east-1.

| | A. Plan as written | **B. Hibernate-first, production-shaped** | C. Cheapest | D. NAT only |
|---|---|---|---|---|
| Envs | dev + prod | dev only | dev only | dev only |
| Egress | NAT (toggle) + 4 endpoints × 2 AZ (always on) | NAT + 4 endpoints × 2 AZ, **both toggled** | Tasks in public subnets, public IP, no NAT/endpoints | NAT (toggle), no endpoints |
| Valkey (P4) | `data`, always on | destroyable, in `app` | destroyable | destroyable |
| Domain | Route 53 + ACM | Route 53 + ACM | CloudFront default | either |
| Awake $/h | ~$0.26 per env | ~$0.26 | ~$0.12 | ~$0.18 |
| Hibernated $/month | ~$66 (endpoints + Valkey) ×2 envs | ~$2.5 | ~$2 | ~$2.5 |
| Interview story | full, but busts the budget | full: private subnets, PrivateLink, SG chain | weaker: public task IPs, no egress control | private, but no PrivateLink |

- **A** fails the budget while asleep. Not viable.
- **C** saves ~$0.14/h but drops private subnets and egress control, which are the parts worth explaining in an interview.
- **D** is the cheapest private option. It was rejected so that the endpoint path (`CannotPullContainerError` without NAT, ECR layers via the S3 gateway) is built and measured, not only described.
- Not chosen in any option: **ARM/Graviton** (~20% of Fargate, about $0.005/h here; the image stays `linux/amd64`, P2-T06), and **fck-nat** (cheaper than NAT, but an instance to patch and operate).

## Decision
**B.** Only `dev` exists. `network` has two toggles, `enable_nat` and `enable_interface_endpoints`, both on while awake and both off on hibernate. Valkey lives in `app`, so hibernate destroys it. `edge` uses a Route 53 domain with an ACM cert.

Cost basis (us-east-1): NAT $0.045/h, interface endpoint $0.01/h per AZ + $0.01/GB, Aurora Serverless v2 $0.12/ACU-h (checked on the AWS pricing pages on 2026-10-15). ALB, Fargate and public IPv4 are list prices; refresh them with the measured cost after the M1 window. At ~$0.26/h awake (≈ $0.32/h at M2 with 2 api + 1 worker + Valkey) and ~$2.5/month hibernated, $18 buys ~48–60 awake hours, against ~28 planned.

## Consequences
- **P2-T02:** both toggles; turning one off must change only what it owns (plan diff). With tasks running, both are on.
- **P2-T07:** the Route 53 **hosted zone must not live in `edge`**, since hibernate destroys `edge` and a new zone gets new name servers, breaking the registrar delegation. Keep the zone (and optionally the cert) in a stack that survives; `edge` keeps the alias records. Register the domain in a low-usage month (a yearly charge lands on that month's budget).
- **P2-T08:** no `prod` apply and no `prod` GitHub environment; `infra/envs/` keeps the per-env layout so `prod` is only new tfvars + backend files later.
- **P2-T09:** hibernate = destroy `edge` + `app` (incl. Valkey), then `network` with both toggles off; resume reverses it. The nightly hibernate is mandatory, not optional.
- **P4-T05:** Valkey goes in `app`, not `data`; this amends ADR-0003's layout for Valkey and environments.
- Harder: no prod/dev separation story (say so in interviews), and every resume pays the endpoint and NAT hours from the start.

## Revisit when
- Cost Explorer shows > $15 in any month, or the measured awake cost after M1 is > $0.35/h: drop the interface endpoints first (option D).
- A second environment or real users appear: add `prod` and re-price.
