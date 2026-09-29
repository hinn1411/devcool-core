#!/usr/bin/env bash
# PostToolUse (Edit|Write): format frontend files with the project's Prettier and Terraform
# files with `terraform fmt`. Each formatter runs only if it is installed. Never blocks.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // empty')
[[ -n "$file" && -f "$file" ]] || exit 0
root="${CLAUDE_PROJECT_DIR:-.}"

case "$file" in
  "$root"/frontend/*.ts|"$root"/frontend/*.tsx|"$root"/frontend/*.js|"$root"/frontend/*.jsx|\
  "$root"/frontend/*.css|"$root"/frontend/*.json|"$root"/frontend/*.md|"$root"/frontend/*.html)
    prettier="$root/frontend/node_modules/.bin/prettier"
    [[ -x "$prettier" ]] && "$prettier" --write --log-level warn "$file" >/dev/null 2>&1
    ;;
  *.tf|*.tfvars)
    command -v terraform >/dev/null 2>&1 && terraform fmt "$file" >/dev/null 2>&1
    ;;
esac
exit 0
