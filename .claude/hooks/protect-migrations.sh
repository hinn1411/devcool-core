#!/usr/bin/env bash
# PreToolUse (Edit|Write): block changes to Flyway migrations that already exist on origin/master.
# A merged migration has already run somewhere; editing it breaks Flyway's checksum validation
# and silently diverges environments. The fix is always a new migration.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // empty')
[[ "$file" =~ /db/migration/.*V[0-9]+__[^/]*\.sql$ ]] || exit 0

root="${CLAUDE_PROJECT_DIR:-.}"
rel="${file#"$root"/}"

# Compare against the default branch as last fetched. If the ref is missing, fail open.
base_ref="${MIGRATION_BASE_REF:-origin/master}"
git -C "$root" rev-parse --verify --quiet "$base_ref" >/dev/null || exit 0

if git -C "$root" cat-file -e "$base_ref:$rel" 2>/dev/null; then
  cat >&2 <<EOF
Blocked: $rel is already merged on $base_ref. Merged migrations are immutable.
Create a new migration instead (use the flyway-migration skill to get the next version number).
EOF
  exit 2
fi
exit 0
