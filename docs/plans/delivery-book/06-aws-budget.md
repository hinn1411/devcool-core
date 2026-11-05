# §6 AWS under $20/month

[← Playbook](../delivery-playbook.md)

The topology, the toggles and the cost basis are decided in [ADR-0013](../architecture/adr/0013-dev-cost-envelope-under-20-usd.md): one `dev` environment, hibernated by default, about $0.26/h awake and about $2.5/month hibernated. This page covers how you spend the awake hours.

## Awake windows

| Window | Week | Hours | What you do |
|---|---|---|---|
| M1 | 3 | ~4 | First deploy; sleep → wake → hibernate → resume; measure the real cost per hour |
| M2 | 6 | ~6 | Two api tasks; deploy during chat (drain); scale-out on connections |
| M3 | 8 | ~2 | Grafana Cloud dashboards with real ECS/ALB metrics |
| M4 | 11 | ~3 | Ask DevCool demo on Bedrock; one eval run |
| M5 | 12 | ~8 | Load tests and AWS chaos (P9) |
| Buffer | — | ~5 | Something always goes wrong |

## Guardrails

- AWS Budgets alerts at **$10** (warning) and **$18** (stop and hibernate).
- `env-power.yml` hibernates every night on a schedule.
- The `aws-architect` agent flags hourly-billed resources in every plan.
- Check Cost Explorer in each Friday review. If it shows more than $15 in a month, apply ADR-0013's revisit triggers.

## Bedrock

- Develop against Ollama (the `local` profile).
- On Bedrock, use Haiku for everything except the M4 demo.
- Run the eval suite locally with the fake/Ollama adapters. Use Bedrock only in the M4 window, with a token cap per run.
- Check Bedrock pricing for the enabled models before each window.
