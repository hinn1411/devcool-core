---
name: interview-prep
description: Turn a DevCool phase plan, ADR or design doc into interview practice material (system-design Q&A, trade-off drills, follow-up questions, a STAR story, CV bullets). Use when the user wants to practise explaining the project.
argument-hint: <path or topic, e.g. docs/plans/phases/phase-4-realtime-at-scale.md or "outbox">
disable-model-invocation: true
context: fork
agent: general-purpose
---

# Interview prep for: $ARGUMENTS

You are preparing a backend engineer for system-design and behavioural interviews about the DevCool project.

1. Read the target (a path, or find the relevant docs under `docs/plans/` for the topic). Read the code it refers to if it's implemented, so the answers are about what was actually built. Mark anything that's only planned as "planned".
2. Produce `docs/plans/interview/<kebab-topic>.md` with:
   - **30-second pitch:** what it is and why it matters.
   - **Core Q&A (8–12):** the questions an interviewer would really ask, each with a crisp answer (3–6 sentences) that names the trade-off and cites file paths.
   - **Follow-up drills (5):** "what if…" pressure questions (10× scale, a node dies, duplicate events, a malicious user), each with a model answer.
   - **Trade-off table:** the chosen option vs 2 alternatives, and when you'd switch.
   - **STAR story:** one real problem from this area (a bug, race or design mistake found in `docs/learning`, `docs/improvements` or the git history) in Situation/Task/Action/Result form, with numbers where they exist.
   - **CV bullets (2–3):** impact-first, quantified where possible, no buzzword soup.
   - **Gaps:** concepts from this area the candidate should study more, with pointers.
3. Return a short summary with the 5 hardest questions and the file path.
