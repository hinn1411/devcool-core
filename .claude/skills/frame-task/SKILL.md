---
name: frame-task
description: Frame a roadmap task before implementing it — why it exists, the value it adds to the roadmap goal, its core concepts with With/Without examples, and agreed requirements (predict first, then compare). Run before /implement-task.
argument-hint: "[task-id, e.g. P3-T03 — empty = next unticked task]"
arguments: [task]
disable-model-invocation: true
---

# Frame task $task

You are a senior engineer helping a teammate understand a task *before* anyone writes code. The output is an agreed brief, not an implementation. Don't write code, create branches or tick the task.

The brief's structure is in [brief-template.md](brief-template.md). Read it before step 3.

## 0. Locate
1. If the task id above is empty, read the status table in `docs/plans/README.md`, open the `in-progress` phase file, pick its first unticked task, and say which one you picked. Use that id wherever this skill says `$task`.
2. Find the line `**$task**` in `docs/plans/phases/phase-*.md`. If it doesn't exist, stop and say so.
3. If `docs/journal/briefs/$task.md` already exists, show its `Status` and date and ask whether to refresh it or keep it. If you're keeping it, summarize it and go to step 5's hand-off.

## 1. Gather (quietly)
Read these without pasting them into the chat:
- The task line and its phase file: **Goal**, **Why it matters**, **Scope**, **Design notes**, **Test plan**, **Definition of Done**, **Risks**.
- Every ADR and architecture section the phase links for this task (`docs/plans/architecture/`).
- The matching reference file in `docs/references/` (use the index in its README; phases are listed per file). Take its **Concepts to own** and **Self-check**.
- From `docs/plans/delivery-playbook.md`: the task's ownership mode (§2, A/B/C) and its Must/Should/Could tier (§4).
- Dependencies:
  - Earlier unticked tasks in the same phase that this one needs.
  - Later tasks, phases or milestones (M1–M5 in `docs/plans/README.md`) that rely on this one. `grep -rn "$task" docs/plans` helps.
- The code it touches today. Find the concrete problem (`path:line`) that the task fixes or extends. For a broad search, use an Explore subagent so the file dumps stay out of this context. Also check `docs/learning/` and `docs/improvements/lessons.md` for related audit findings.

## 2. Predict: the user's turn
Show:
- **The task in plain words:** 2–3 sentences on *what* it delivers. Don't say *why* yet.
- **Your prediction, please:** ask the user to answer briefly, bullets are fine:
  - (a) Why do we need this?
  - (b) What must be true when it's done (acceptance criteria or invariants)?
  - (c) Which tests would prove it?
  - (d) An estimate in hours.

Tell them partial answers are fine, and that `skip` goes straight to the brief. Then **end your turn and wait**.

## 3. Compare and explain
Present the brief itself, following [brief-template.md](brief-template.md). Don't write a longer version in the chat. Rules:
- **Short:** the whole brief must be **40 lines or fewer**. One line per bullet, no prose paragraphs. State each fact once and link to its source (ADR, phase file, `path:line`) instead of copying it. Leave out template lines that don't apply.
- **Why** and **Value** must be concrete for DevCool: cite files, ADRs, milestones and downstream task ids. No generic text like "improves maintainability".
- **Concepts** (2–4, one line each): a *Without* and a *With* for the same concrete DevCool scenario, e.g. "user retries a send on a flaky network". Show what goes wrong, then what the concept changes. Add a code or SQL snippet only if it's 3 lines or fewer.
- **Prediction gaps:** list only where the user's answers differ from the brief, one line each, tagged:
  - **knowledge gap**: the user missed it. Point to what to read (a reference file section or ADR).
  - **plan issue**: the user's point is better than the plan. Update the brief and say so.
  - **open**: it needs a decision (step 4).

  If they skipped, leave this section out.

## 4. Clear the requirements
- Resolve anything the ADRs, design docs or current code already answer, and cite the source. Don't ask about it.
- If the task as written conflicts with an ADR or the current code, **stop**. Explain the conflict and suggest `/write-adr` or a phase-file fix instead of picking a side.
- Ask the remaining real decisions with AskUserQuestion: at most 4, the recommended option first with the reason. Examples: an edge-case behaviour, an error code, a limit value, what's in or out of scope.
- If there are no open decisions, say so and move on.

## 5. Save and hand off
1. Write `docs/journal/briefs/$task.md` from the template, with the decisions applied, status `agreed` and today's date. Create the folder if it's missing.
2. In the chat, print a 5-line summary:
   - **Why**
   - **Value**
   - **Done when**: the top 2–3 acceptance criteria
   - **Mode / tier**
   - **Estimate**
3. Print the next step:
   - The output style for the mode: A → `/output-style` Learning, B → Explanatory, C → default.
   - Then `/implement-task $task`, which reads this brief.
