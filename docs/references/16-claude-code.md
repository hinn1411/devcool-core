# 16 — Claude Code setup

> **Used in DevCool:** [Phase 0](../plans/phases/phase-0-claude-code-setup.md) (P0-T01–T13) · [Claude Code guide](../plans/claude-code/claude-code-guide.md) (concepts, daily workflow, interview Q&A) · [ADR-0004](../plans/architecture/adr/0004-monorepo.md) (per-directory `CLAUDE.md`) · P2-T11, P5-T15, P7-T10 (plugins)
> **In the repo:** [CLAUDE.md](../../CLAUDE.md) · [.claude/settings.json](../../.claude/settings.json) · `.claude/rules/`, `.claude/hooks/`, `.claude/skills/`, `.claude/agents/` · [.mcp.json](../../.mcp.json)
> **Links checked:** 2026-09-29

## Concepts to own

- **Memory: `CLAUDE.md` and rules.** Root `CLAUDE.md` loads every session (keep it short); nested `CLAUDE.md` files load when Claude works in that directory; `.claude/rules/*.md` with `paths:` load only for matching files.
- **Settings scopes and precedence.** Managed > command line > local project > shared project > user. Permissions are `allow` / `ask` / `deny` rules; `deny` wins.
- **Hooks are guarantees.** Shell commands the harness runs on events (`PreToolUse`, `PostToolUse`, `Stop`, `SessionStart`). Use them for what must *always* happen (format, block edits to merged migrations, block dangerous commands), not for advice.
- **Skills are procedures.** A `SKILL.md` loaded on demand (or by `/name`), able to inject live context. *In DevCool:* `implement-task`, `new-use-case`, `verify`, `flyway-migration`, `ws-protocol`.
- **Subagents isolate context.** Their own context window and tool set; only the conclusion comes back. *In DevCool:* `hexagonal-reviewer`, `security-reviewer`, `test-writer`, `aws-architect`.
- **MCP connects tools.** context7 for current library docs, AWS Knowledge MCP for AWS docs; later Terraform and Grafana servers via plugins.
- **Plugins bundle all of the above** for installation from a marketplace.
- **CI integration.** The GitHub Action runs Claude on PRs with the repo's rules as the review brief.
- **Context is the budget.** Everything always-loaded costs tokens every turn; push detail into rules, skills and subagents.

## Read first

1. [Extend Claude Code](https://code.claude.com/docs/en/features-overview) — *official docs* · When to use `CLAUDE.md`, skills, subagents, hooks, MCP and plugins. The decision table behind [claude-code-guide §2](../plans/claude-code/claude-code-guide.md#2-which-mechanism-for-which-need).
2. [How Claude remembers your project](https://code.claude.com/docs/en/memory) — *official docs* · `CLAUDE.md` locations, imports, path-scoped rules, auto memory.
3. [Automate actions with hooks](https://code.claude.com/docs/en/hooks-guide) — *official docs* · Worked examples; then the [hooks reference](https://code.claude.com/docs/en/hooks) for event payloads and exit codes.
4. [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices) — *official docs* · Workflow habits: explore → plan → implement → verify, context management.
5. [Set up Claude Code in a monorepo or large codebase](https://code.claude.com/docs/en/large-codebases) — *official docs* · Nested `CLAUDE.md` and per-package skills, i.e. ADR-0004's setup.

## Reference

### Configuration

- [Explore the .claude directory](https://code.claude.com/docs/en/claude-directory) — *official docs* · What lives where, project vs home.
- [Settings files and precedence](https://code.claude.com/docs/en/settings) — *official docs* · Scopes and how values merge.
- [All settings](https://code.claude.com/docs/en/settings-reference) — *official docs* · Every `settings.json` key.
- [Configure permissions](https://code.claude.com/docs/en/permissions) — *official docs* · Rule syntax for `allow`/`ask`/`deny`, tool-specific patterns.
- [Permission modes](https://code.claude.com/docs/en/permission-modes) — *official docs* · What each mode (default, accept edits, plan, auto, …) lets Claude do without asking.
- [Debug your configuration](https://code.claude.com/docs/en/debug-your-config) — *official docs* · `/context`, `/hooks`, `/mcp` to see what actually loaded (P0-T11 checks).
- [Claude Code settings JSON schema](https://json.schemastore.org/claude-code-settings.json) — *SchemaStore* · The `$schema` referenced by `.claude/settings.json`.

### Skills, subagents, MCP, plugins

- [Extend Claude with skills](https://code.claude.com/docs/en/skills) — *official docs* · Frontmatter, arguments, dynamic context injection.
- [Create custom subagents](https://code.claude.com/docs/en/sub-agents) — *official docs* · Agent files, tool restrictions, model choice.
- [Run parallel sessions with worktrees](https://code.claude.com/docs/en/worktrees) — *official docs* · Isolating parallel work.
- [Connect Claude Code to tools via MCP](https://code.claude.com/docs/en/mcp) — *official docs* · Scopes, `.mcp.json`, auth.
- [Plugins overview](https://code.claude.com/docs/en/plugins/overview) — *official docs* · What a plugin can contain and how to install one.
- [Model Context Protocol](https://modelcontextprotocol.io/) — *specification site* · The protocol itself.
- [Context7](https://github.com/upstash/context7) — *README* · The docs MCP server used before adding or upgrading dependencies.
- [AWS Knowledge MCP Server](https://awslabs.github.io/mcp/servers/aws-knowledge-mcp-server) — *AWS Labs* · The server configured in `.mcp.json`.

### CI and review

- [Claude Code GitHub Actions](https://code.claude.com/docs/en/github-actions) — *official docs* · Setup and prompt configuration for `.github/workflows/claude.yml` (P0-T13).
- [Code Review](https://code.claude.com/docs/en/code-review) — *official docs* · Automated multi-agent PR review.
- [Catch security issues as Claude writes code](https://code.claude.com/docs/en/security-guidance) — *official docs* · The `security-guidance` plugin used in P9-T09.

### Background reading

- [Effective context engineering for AI agents](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents) — *Anthropic Engineering* · Why always-loaded context should be small; the reasoning behind rules and skills.
- [Equipping agents for the real world with Agent Skills](https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills) — *Anthropic Engineering* · Progressive disclosure: how skills load.
- [Glossary](https://code.claude.com/docs/en/glossary) — *official docs* · Precise meanings of the terms used in interviews.

## Self-check

These overlap with [claude-code-guide §11](../plans/claude-code/claude-code-guide.md#11-interview-qa):

- Which DevCool conventions are in root `CLAUDE.md`, which in rules, and why the split?
- Why is "never edit a merged migration" a hook and not a line in `CLAUDE.md`?
- When do you reach for a subagent instead of a skill?
- What does `deny` in project settings guarantee that an instruction in `CLAUDE.md` does not?
- What costs context every turn, and what costs nothing until used?
- How would you verify that the `flyway.md` rule actually loads when editing a migration?
