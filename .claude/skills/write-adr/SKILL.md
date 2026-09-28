---
name: write-adr
description: Write a new Architecture Decision Record in docs/plans/architecture/adr using the project template, auto-numbered, with real options and trade-offs.
argument-hint: <decision title>
disable-model-invocation: true
---

# New ADR: $ARGUMENTS

Existing ADRs:
!`ls docs/plans/architecture/adr`

1. Number = the highest existing `NNNN` + 1, zero-padded to 4 digits. File: `docs/plans/architecture/adr/NNNN-<kebab-title>.md`.
2. Use the template in `docs/plans/architecture/adr/README.md`. Status: `Proposed` unless the user says it's decided. Date: today.
3. **Context:** cite the actual code (`path:line`) and docs that create the need. No generic text.
4. **Options:** at least 3 real options, including "do nothing / keep current" when relevant. Each needs concrete pros and cons for *this* system (scale, ops burden, scale-to-zero, interview value). Add a comparison table when there are more than 3 criteria.
5. **Consequences:** what becomes easier and what becomes harder, and what tasks this creates (suggest phase task ids).
6. **Revisit when:** an observable trigger (a metric, a feature, a scale number), not "if needed".
7. Add a row to the index table in `adr/README.md`. If this supersedes an ADR, update that ADR's status line to `Superseded by NNNN`.
8. Check any library or cloud-service claims against current docs (context7 or the AWS Knowledge MCP) before stating them.
