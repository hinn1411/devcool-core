#!/usr/bin/env bash
# SessionStart (startup|resume|compact): tell Claude where the roadmap stands.
# Plain stdout from a SessionStart hook is added to Claude's context. Keep it short.
set -uo pipefail

root="${CLAUDE_PROJECT_DIR:-.}"
readme="$root/docs/plans/README.md"
[[ -f "$readme" ]] || exit 0

branch=$(git -C "$root" branch --show-current 2>/dev/null || echo "?")
dirty=$(git -C "$root" status --porcelain 2>/dev/null | wc -l | tr -d ' ')

# Status table rows look like: | P3 | in-progress | [phase-3-...md](phases/phase-3-....md) |
mapfile -t active < <(grep -E '^\| P[0-9] \| in-progress \|' "$readme" | awk -F'|' '{gsub(/ /,"",$2); print $2}')
if [[ ${#active[@]} -eq 0 ]]; then
  next=$(grep -E '^\| P[0-9] \| todo \|' "$readme" | head -n1 | awk -F'|' '{gsub(/ /,"",$2); print $2}')
  active=("${next:-}")
fi

echo "DevCool roadmap context (from docs/plans/README.md):"
echo "- Branch: $branch ($dirty uncommitted file(s))"
for p in "${active[@]}"; do
  [[ -n "$p" ]] || continue
  n="${p#P}"
  f=$(ls "$root"/docs/plans/phases/phase-"$n"-*.md 2>/dev/null | head -n1)
  [[ -n "$f" ]] || continue
  open=$(grep -cE '^\s*- \[ \] \*\*'"$p"'-T' "$f")
  echo "- Current phase: $p (${f#"$root"/}), $open open task(s). Next up:"
  grep -E '^\s*- \[ \] \*\*'"$p"'-T' "$f" | head -n 3 | sed -E 's/^\s*- \[ \] /  - /' | cut -c1-160
done
echo "- Frame a task with /frame-task <id>, then build it with /implement-task <id>; ADRs in docs/plans/architecture/adr/."
exit 0
