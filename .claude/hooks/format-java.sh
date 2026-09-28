#!/usr/bin/env bash
# PostToolUse (Edit|Write): format the edited Java file with Spotless (google-java-format).
# Only that file is formatted (-DspotlessFiles), so the hook stays fast. It never blocks:
# formatting is a convenience; `spotless:check` in CI is the gate.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // empty')
[[ "$file" == *.java && -f "$file" ]] || exit 0

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
[[ -x ./mvnw ]] || exit 0

# spotlessFiles is a comma-separated list of regexes matched against absolute paths.
regex=$(printf '%s' "$file" | sed 's/[][\.*^$()+?{}|]/\\&/g')

# Offline first (fast, no network); fall back to online if the plugin isn't cached yet.
if ! ./mvnw -q -o spotless:apply -DspotlessFiles="$regex" >/dev/null 2>&1; then
  if ! out=$(./mvnw -q spotless:apply -DspotlessFiles="$regex" 2>&1); then
    echo "format-java: spotless failed for $file (not blocking)" >&2
    printf '%s\n' "$out" | tail -n 15 >&2
  fi
fi
exit 0
