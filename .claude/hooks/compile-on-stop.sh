#!/usr/bin/env bash
# Stop: if Java sources changed since the last successful compile, compile before Claude
# finishes, so a turn never ends with code that doesn't build. Blocks at most twice per prompt
# (loop guard); after that it lets the stop through and just reports.
set -uo pipefail

input=$(cat)
root="${CLAUDE_PROJECT_DIR:-.}"
cd "$root" || exit 0
[[ -x ./mvnw ]] || exit 0

session=$(jq -r '.session_id // "s"' <<<"$input")
prompt=$(jq -r '.prompt_id // "p"' <<<"$input")
state_dir="${TMPDIR:-/tmp}/devcool-claude-hooks"
mkdir -p "$state_dir"

# Fingerprint of uncommitted Java changes (tracked diffs + untracked files).
fp=$( { git diff HEAD -- '*.java' 2>/dev/null
        git ls-files --others --exclude-standard -- '*.java' 2>/dev/null | xargs -r sha1sum 2>/dev/null
      } | sha1sum | cut -d' ' -f1)
empty_fp=$(printf '' | sha1sum | cut -d' ' -f1)
[[ "$fp" == "$empty_fp" ]] && exit 0

fp_file="$state_dir/$session.fp"
[[ -f "$fp_file" && "$(cat "$fp_file")" == "$fp" ]] && exit 0

out=$(./mvnw -q -o compile test-compile 2>&1)
status=$?
# Offline mode fails when a plugin/dependency isn't cached yet: retry online once.
if (( status != 0 )) && grep -qiE 'offline|could not resolve|cannot access' <<<"$out"; then
  out=$(./mvnw -q compile test-compile 2>&1)
  status=$?
fi
if (( status == 0 )); then
  echo "$fp" > "$fp_file"
  exit 0
fi

count_file="$state_dir/$session.$prompt.blocks"
count=$(cat "$count_file" 2>/dev/null || echo 0)
errors=$(grep -E '^\[ERROR\].*\.java' <<<"$out" | head -n 20)
if (( count >= 2 )); then
  echo "compile-on-stop: build still failing after 2 attempts; not blocking again." >&2
  exit 0
fi
echo $((count + 1)) > "$count_file"
{
  echo "The Java build fails. Fix these compile errors before finishing:"
  echo "${errors:-$(tail -n 20 <<<"$out")}"
} >&2
exit 2
