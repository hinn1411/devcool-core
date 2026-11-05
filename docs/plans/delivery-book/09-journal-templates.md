# §9 Journal templates

[← Playbook](../delivery-playbook.md)

Layout in [`docs/journal/`](../../journal/README.md):

```
docs/journal/
  weeks/2026-W40.md        one file per ISO week: task entries, parking lot, weekly review
  incidents/2026-10-09-seq-race.md
  stories/seq-race-and-idempotent-send.md
```

## Task entry (in the week file)

```markdown
### P3-T03 — seq assignment + idempotent send · mode A · est 5h · actual _h

**Prediction (Frame):** invariants, design sketch, tests I expect to write.
**Plan diff:** where Claude's plan differed from mine, and who was right.
**What I built:** 2–3 lines, by layer.
**Evidence:** tests (red → green), break-it result, numbers.
**Claude got wrong / how I caught it:**
**Merge gate:** what · why (ADR/trade-off) · how it fails and how I'd know.
**Explain-back misses:** self-check questions I couldn't answer → Friday list.
**Story candidate?** yes/no, and the one-line hook.
**CI reds:** _ · **Reviewer findings:** _ · **Correction rounds:** _
```

## Daily reflection (5 minutes)

1. What did I build today, in one sentence a non-engineer would understand?
2. What do I know now that I didn't know this morning?
3. What did Claude get wrong, and what caught it: me, a test, a hook or a reviewer?
4. If this ran in production tonight, what would break first, and would I notice?
5. Is there a story here? Write the hook now.

## Incident report

```markdown
# <date> — <title> (game day | blind drill | real bug)

**Hypothesis / expectation:**
**Steady state:** metric and normal value.
**Timeline:** injected hh:mm · detected hh:mm (how) · mitigated hh:mm · resolved hh:mm
**TTD:** _ min · **TTR:** _ min
**Impact:** what users would have seen, and for how long.
**Root cause:**
**What worked / what didn't:** alerts, runbooks, dashboards.
**Follow-ups:** tasks, tests, runbook fixes, with ids.
```

## STAR story

```markdown
# <title>

**Situation (2 lines):**
**Task (1 line):** what you had to achieve, and the constraint.
**Action (4–6 lines):** what *you* did; the option you rejected and why.
**Result (2 lines):** numbers from your own runs.
**Follow-up questions to expect:** 3, with one-line answers.
**Sources:** journal entries, PRs, incident reports.
```

## Decision note (below ADR level)

```markdown
**Decision:** · **Options:** · **Chosen because:** · **Revisit when:**
```

If a decision changes an ADR or crosses modules, run `/write-adr` instead.

## Weekly review (Friday, in the week file)

```markdown
## Review W<nn>
- Planned vs done: _ / _ tasks · average overrun: _× · cuts made: _
- Game day: scenario, TTD, TTR, top follow-up
- Claude: what it was great at / what it got wrong (patterns)
- Learning: top 3 things I can now explain; top 3 gaps → next week's reading
- Interview: questions missed in the drill; story drafted
- AWS: hours awake, cost this month so far
- Next week: Must tasks, the one Could I'd cut first
```
