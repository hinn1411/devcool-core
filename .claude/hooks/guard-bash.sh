#!/usr/bin/env bash
# PreToolUse (Bash): block commands that are destructive or bypass review, even when they are
# hidden inside a compound command. Permission rules cover the simple forms; this is the backstop.
# To run one of these on purpose, run it yourself with the `!` prefix.
set -uo pipefail

cmd=$(jq -r '.tool_input.command // empty')
[[ -n "$cmd" ]] || exit 0

block() {
  echo "Blocked by .claude/hooks/guard-bash.sh: $1" >&2
  echo "If this is intended, ask the user to run it themselves with the ! prefix." >&2
  exit 2
}

# Terraform: no destroy and no unreviewed apply from the agent.
if grep -Eq '(^|[;&|[:space:]])terraform[[:space:]]+(-chdir=[^[:space:]]+[[:space:]]+)?destroy' <<<"$cmd"; then
  block "terraform destroy"
fi
if grep -Eq 'terraform[[:space:]].*apply.*-auto-approve' <<<"$cmd"; then
  block "terraform apply -auto-approve (apply must be reviewed)"
fi

# Git: no force pushes, and no direct pushes to the default branch.
if grep -Eq 'git[[:space:]]+push.*(--force|--force-with-lease|[[:space:]]-f([[:space:]]|$))' <<<"$cmd"; then
  block "force push"
fi
if grep -Eq 'git[[:space:]]+push([[:space:]]+[^[:space:]]+)?[[:space:]]+(\+?master|\+?main|HEAD:master|HEAD:main)([[:space:]]|$)' <<<"$cmd"; then
  block "direct push to master/main (open a PR)"
fi

# AWS: no destructive calls from the agent.
if grep -Eq 'aws[[:space:]]+[a-z0-9-]+[[:space:]]+(delete-|terminate-|remove-|deregister-|rm([[:space:]]|$)|rb([[:space:]]|$))' <<<"$cmd"; then
  block "destructive aws command"
fi

# Secrets: don't print local env files.
if grep -Eq '(^|[;&|[:space:]])(cat|less|more|head|tail|source|bat)[[:space:]]+([^;&|]*[[:space:]])?([^[:space:];&|]*/)?(local\.env|\.env)([[:space:];&|]|$)' <<<"$cmd"; then
  block "reading local secrets (local.env / .env)"
fi

exit 0
