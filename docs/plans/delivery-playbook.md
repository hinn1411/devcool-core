# DevCool — Delivery playbook

How to run the 12-week roadmap as a solo, full-time developer: ship fast with Claude Code, and come out knowing what you built, with production-like experience and answers you can give in an interview.

What to build is in the [roadmap](README.md) and [phases](phases/); why it's designed that way is in [architecture](architecture/) and the [ADRs](architecture/adr/). This playbook covers **how to run each day, week and task**.

## Contents

| § | File | Answers |
|---|---|---|
| 0 | [Goals](delivery-book/00-goals.md) | Who this is for, the four goals, and how you'll know you hit them |
| 1 | [Operating rules](#1-operating-rules) (below) | The six rules everything else follows |
| 2 | [Ownership](delivery-book/02-ownership.md) | Who writes what: modes A/B/C per area |
| 3 | [Task loop](delivery-book/03-task-loop.md) | Frame → Red → Green → Review → Refactor → Close, the merge gate, prompts |
| 4 | [Pace and scope](delivery-book/04-pace-and-scope.md) | Daily rhythm, stop rules, Must/Should/Could, milestones, 12 weeks at a glance |
| 5 | [Production practice](delivery-book/05-production-practice.md) | Game days, blind drills, synthetic users |
| 6 | [AWS budget](delivery-book/06-aws-budget.md) | Awake windows, guardrails, Bedrock rules under $20/month |
| 7 | [Claude Code](delivery-book/07-claude-code.md) | Quality gates per mode, anti-patterns, what to measure |
| 8 | [Interview](delivery-book/08-interview.md) | Tracks, STAR candidates, weekly drill, question bank, honesty rules |
| 9 | [Journal templates](delivery-book/09-journal-templates.md) | Task entry, reflection, incident, STAR, decision note, weekly review |

## 1. Operating rules

1. **One hands-on task, at most one delegated task.** One mode A/B task, plus at most one mode C task in a Claude worktree. Nothing else is in progress.
2. **Every task ends with evidence:** a test that failed before and passes now, a number, or a journal note.
3. **Never merge what you can't explain.** The [merge gate](delivery-book/03-task-loop.md#merge-gate) applies to Claude's code and to yours.
4. **Timebox and cut.** Past 1.5× the estimate, split the task; past 2×, cut it ([§4](delivery-book/04-pace-and-scope.md)).
5. **Local-first; AWS in windows.** Develop and drill locally; wake AWS only for planned windows ([§6](delivery-book/06-aws-budget.md)).
6. **Capture stories the day they happen,** with numbers, in the journal.
