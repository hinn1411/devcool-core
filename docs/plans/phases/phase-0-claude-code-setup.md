# Phase 0 — Claude Code setup

**Weeks:** 1 (days 1–2) · **Depends on:** — · **ADRs:** 0004

## Goal
Configure Claude Code so that everyday work on this repo is faster and safer:
- Conventions load only where they apply.
- Repetitive procedures are one command.
- Formatting and dangerous-command checks are automatic.
- Reviews run in isolated subagents.

The concepts and interview material are in [`../claude-code/claude-code-guide.md`](../claude-code/claude-code-guide.md).

## Why it matters (interview angle)
"How do you use AI tools in your workflow?" is now a standard question. A concrete, reasoned setup is a much stronger answer than "I use it for autocomplete". For example: *"hooks for what must always happen, skills for procedures, subagents for isolated review, path-scoped rules to keep context small"*.

## Scope
- **In:**
  - Project-level config committed to the repo: `.claude/settings.json`, hooks, rules, skills, agents and `.mcp.json`.
  - Per-directory `CLAUDE.md` files and `.gitignore` entries.
  - Local tooling the config expects.
- **Out:**
  - Organisation-managed settings.
  - Personal config beyond recommendations.

## Design notes
| Need | Mechanism | Why this one |
|---|---|---|
| Always-true project facts (commands, layout) | Root `CLAUDE.md` (short) | Always loaded; costs context every turn, so keep it lean |
| Conventions for one area (hexagonal, Flyway, WS protocol, GenAI safety) | `.claude/rules/*.md` with `paths:` | Load only when Claude touches matching files |
| Frontend/infra conventions | `frontend/CLAUDE.md`, `infra/CLAUDE.md` | Load on demand when working in that directory |
| Multi-step procedures (implement a task, add a use case, verify) | Skills | Loaded when invoked; can inject live context with `` !`cmd` `` |
| Independent review/research | Subagents | Own context window; only the conclusion returns |
| Things that must *always* happen (format, block edits to merged migrations) | Hooks | Deterministic; not dependent on the model remembering |
| External knowledge (AWS docs, Terraform registry, browser) | MCP servers and plugins | Tools, not text; deferred until needed |

## Tasks
- [x] **P0-T01** Create `docs/plans/` (this roadmap, architecture, ADRs, phases)
- [x] **P0-T02** Slim the root `CLAUDE.md`: fix the stale `local` profile row, add the monorepo map and plan workflow, move the long request-flow example to `docs/architecture-request-flow.md`
- [x] **P0-T03** Add `.claude/rules/`: `hexagonal.md`, `testing.md`, `flyway.md`, `websocket-protocol.md`, `genai-safety.md`
- [x] **P0-T04** Add `.claude/settings.json`: permissions (allow/ask/deny), hooks, `enabledPlugins`, worktree settings
- [x] **P0-T05** Add hook scripts in `.claude/hooks/`: `format-java.sh`, `format-other.sh`, `protect-migrations.sh`, `guard-bash.sh`, `session-context.sh`, `compile-on-stop.sh`
- [x] **P0-T06** Add skills: `implement-task`, `new-use-case`, `flyway-migration`, `verify`, `write-adr`, `interview-prep`, `ws-protocol`
- [x] **P0-T07** Add subagents: `hexagonal-reviewer`, `security-reviewer`, `test-writer`, `aws-architect`
- [x] **P0-T08** Add `.mcp.json` (AWS Knowledge MCP); `frontend/CLAUDE.md`, `infra/CLAUDE.md`; `.gitignore` entries
- [x] **P0-T09** Write `claude-code/claude-code-guide.md`
- [x] **P0-T10** Install local binaries used by the config:
  - `jdtls` (the Java LSP for the `jdtls-lsp` plugin)
  - `npm i -g typescript-language-server typescript@6` (for `typescript-lsp`; TypeScript 7 has no `tsserver`, which the language server needs)
  - `terraform` ≥ 1.11 (S3 native locking, ADR-0003, is GA from 1.11), `tflint`
  - `uv` (optional, for AWS MCP servers run via `uvx`)
- [ ] **P0-T11** First session checks:
  - `/plugin` installs the enabled plugins; approve the project `.mcp.json` server.
  - `/hooks`, `/agents`, `/mcp` and `/context` list what you expect.
  - `/doctor` is clean.
- [ ] **P0-T12** Personal settings clean-up (optional):
  - `~/.claude/settings.json` has an `autoMode.environment` describing *tylink*. Move it to that repo's `.claude/settings.local.json`, or add a DevCool description here.
  - Pick a status line (`/statusline`).
  - Try the `learning-output-style` plugin for study sessions.
- [ ] **P0-T13** Extend `.github/workflows/claude.yml`: have the auto-review prompt reference `CLAUDE.md` PR guidelines and `.claude/rules/*`, with path-specific focus (hexagonal for `src/`, security for `infra/` and auth code)

## Files touched
`CLAUDE.md`, `.claude/**`, `.mcp.json`, `frontend/CLAUDE.md`, `infra/CLAUDE.md`, `.gitignore`, `docs/plans/**`, `docs/architecture-request-flow.md`

## Test plan
- Pipe sample hook input into each hook script and check its exit code and output (see the Verification section in the guide).
- Start `claude` and check that `/hooks`, `/agents`, `/mcp` and the `/` skill list include everything.
- Open a file under `src/main/resources/db/migration/` and confirm with `/context` that the `flyway` rule loaded, and that it did not load while you were only in `frontend/`.

## Definition of Done
- A new session knows the current phase (SessionStart hook output).
- Editing a Java file leaves it Spotless-formatted.
- Editing a merged migration is blocked.
- `/implement-task P1-T01` produces a plan that references the right files.

## Interview talking points
- The context window is the scarce resource. Everything in this setup is about loading the right instructions at the right time.
- Hooks turn "please always…" into guarantees. The migration guard is a real example of protecting production.
- Subagents trade latency for a clean main context: exploration and review return conclusions, not file dumps.

## Risks
- **Over-configuring:** a CLAUDE.md or rule nobody reads costs tokens every turn. Revisit after each phase.
- **Hooks that are slow:** a format hook that runs full Maven on every edit would hurt. The hook formats one file only.
