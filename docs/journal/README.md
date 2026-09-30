# Delivery journal

A working log of how DevCool is built: what was predicted, what happened, what broke, and what was learned. It's the evidence behind the interview stories. The rules and templates are in the [delivery playbook](../plans/delivery-playbook.md#9-journal-templates).

## Layout

```
docs/journal/
  briefs/      one file per framed task (written by /frame-task), e.g. P3-T03.md: why, value, concepts, agreed requirements
  weeks/       one file per ISO week, e.g. 2026-W40.md: task entries, parking lot, Friday review
  incidents/   one file per game day, blind drill or real bug, e.g. 2026-10-09-seq-race.md
  stories/     STAR stories distilled from the above, e.g. seq-race-and-idempotent-send.md
```

## Rules

- Write the task entry the same day. Numbers and timings come from what you ran, never from memory.
- Say whether something happened in a game day, a load test or a real bug.
- Note what Claude got wrong and what caught it. That log is interview material too.
- No secrets, tokens or personal data in any entry.

## Index

| Week | Phase(s) | Highlights |
|---|---|---|
| 2026-W40 | P0, P1 | — |
