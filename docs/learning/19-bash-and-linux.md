# 19 — Bash & Linux

The theme: **how do you read, and safely change, a Bash script?** Every example comes from the four P2-T08 scripts in `.github/scripts/`. The CI workflows run them, and so can you.

| Script | What it does |
|---|---|
| `changed-stacks.sh` | Maps the files a change touched to the Terraform stacks to plan, printed as a JSON array |
| `tf-stack.sh` | `terraform init` + `plan` (and optionally `apply`) for one stack |
| `env-state.sh` | Reports whether an environment is `awake`, `asleep` or `hibernated` |
| `ecs-roll-to-latest.sh` | Points an ECS service at the newest task definition and waits for the rollout |

The one sentence that carries the chapter: **a shell script is a list of programs to run, glued together by text, exit codes and three standard streams.** Bash does not have types, return values or exceptions the way Java does. It has strings and numbers from 0 to 255, and most surprises come from forgetting that.

---

## 1. Linux fundamentals

### 1.1 The first line and the `x` bit

```bash
#!/usr/bin/env bash        # line 1 of every script
```

`#!` (the *shebang*) tells the kernel which interpreter runs the file. `/usr/bin/env bash` means "the first `bash` on my `PATH`", not a fixed location. That matters on macOS, where `/bin/bash` is Bash 3.2 and has no `declare -A` (§2.6). Homebrew's Bash 5 is found on `PATH` instead.

`ls -l` shows `-rwxrwxr-x` for these files. Read it as three groups of `r`ead / `w`rite / e`x`ecute: **owner** `rwx`, **group** `rwx`, **others** `r-x` (octal `775`). Without the `x` bit, `./tf-stack.sh` fails with `Permission denied`, but `bash tf-stack.sh` still works because then `bash` is the program and the script is only its input. Git stores only that one bit (mode `100755` vs `100644`). After `chmod +x`, commit the file so CI gets the bit too.

### 1.2 Environment variables and `PATH`

Every process gets a copy of its parent's **environment**: `KEY=value` strings. Changing a variable in a child never changes it in the parent. The scripts read their inputs this way:

```bash
env="${ENVIRONMENT:-dev}"               # tf-stack.sh:10  — use $ENVIRONMENT, or "dev" if unset/empty
out="${GITHUB_OUTPUT:-/dev/null}"       # tf-stack.sh:12  — GitHub Actions sets this; by hand it isn't
```

`PATH` is just another variable: a `:`-separated list of directories searched for commands (`echo "$PATH"`, `command -v jq`). Running a script with one-off variables: `ENVIRONMENT=dev STATE=asleep .github/scripts/tf-stack.sh plan app`.

### 1.3 Three streams, redirection and pipes

Every process starts with **stdin (0)**, **stdout (1)** and **stderr (2)**. Rule of thumb: **stdout is data, stderr is messages for humans.**

| Syntax | Meaning | Real use |
|---|---|---|
| `cmd > f` | stdout to file (overwrite) | `tf-stack.sh:43` writes `plan.txt` |
| `cmd >> f` | stdout to file (append) | `tf-stack.sh:44` appends `changes=…` to `$GITHUB_OUTPUT` |
| `cmd >&2` | stdout to stderr | `changed-stacks.sh:40` warns on stderr so stdout stays valid JSON |
| `cmd 2>&1` | stderr to wherever stdout goes | `tf-stack.sh:36` so `tee` captures Terraform's errors too |
| `cmd 2>/dev/null` | throw stderr away | `env-state.sh:18` |
| `a \| b` | `a`'s stdout becomes `b`'s stdin | `tf-stack.sh:36-37` |
| `{ …; } >> f` | redirect a whole group once | `env-state.sh:39-46` |

`/dev/null` swallows everything you write to it; `/dev/stdout` is "my own stdout". That is why `env-state.sh:10` defaults `out` to `/dev/stdout` (show me the values when I run it by hand) while `tf-stack.sh:12` defaults to `/dev/null` (the value only matters to CI).

### 1.4 Exit codes

Every command ends with a number from 0 to 255: **0 means success, anything else means failure.** `$?` holds the last one. `if`, `&&` and `||` test exit codes, not true/false values. Some tools give extra meaning to non-zero codes. `terraform plan -detailed-exitcode` returns **0** for no changes, **1** for an error and **2** when there are changes, and `tf-stack.sh` depends on that (§2.2). An exit code above 128 means the process was killed by a signal: 128 + the signal number. 137 = 128 + 9 (`SIGKILL`, e.g. the OOM killer, see [18](18-docker-and-ecs.md) §1.1), and 130 = 128 + 2 (`SIGINT`, Ctrl-C).

---

## 2. Bash core

### 2.1 Strict mode: `set -euo pipefail`

Line 5 or 6 of every script. Without it, Bash just carries on after a failure.

| Flag | Effect | Why the scripts need it |
|---|---|---|
| `-e` | Exit as soon as a command fails | A failed `terraform init` must not be followed by `plan` |
| `-u` | Using an unset variable is an error | A missing argument fails loudly (`$2: unbound variable`) instead of acting as `""` |
| `-o pipefail` | A pipeline fails if *any* part fails | Without it, `terraform plan … \| tee` reports `tee`'s exit code, which is always 0 |

`-u` is why you see `${2:-all}`, `${STATE:-awake}` and `${hit[bootstrap]:-}`: `:-` supplies a default (even an empty one), so reading an optional value is not an error. `-e` does **not** fire for a failing command inside an `if` condition or on the left of `&&`/`||`. That is how `cmd 2>/dev/null || true` (`env-state.sh:18`) says "this may fail, and that's fine".

### 2.2 When an exit code is data: `set +e` and `PIPESTATUS`

```bash
set +e                                                        # tf-stack.sh:35-40
terraform -chdir="$dir" plan … -detailed-exitcode -out=tfplan "${args[@]}" 2>&1 \
  | tee "$dir/plan.log"
rc=${PIPESTATUS[0]}
set -e
[ "$rc" -eq 1 ] && exit 1
```

Exit code 2 means "changes found", not failure, but `-e` would end the script on it. So strict mode is switched off for one command. `PIPESTATUS` is an array of every exit code in the last pipeline. `[0]` is Terraform's, `[1]` would be `tee`'s. It must be read straight away, because the next command overwrites it.

### 2.3 Variables and quoting

`name=value` with **no spaces** around `=`. `$name` expands it. **Always quote expansions: `"$dir"`, not `$dir`.** Unquoted, the value is split on spaces and glob characters like `*` are expanded, so a path with a space turns into two arguments. Single quotes are literal (`'services[0].taskDefinition'` reaches `aws` exactly as typed), and double quotes still expand `$var` and `$(…)`.

`$(cmd)` captures a command's stdout as a string, with trailing newlines removed:

```bash
running=$(aws ecs describe-services … --output text)          # ecs-roll-to-latest.sh:13
echo "changes=$([ "$rc" -eq 2 ] && echo true || echo false)"   # tf-stack.sh:44
```

### 2.4 Cutting strings without `sed`: parameter expansion

| Form | Removes | Example → result |
|---|---|---|
| `${x#pat}` | shortest match of `pat` from the **start** | `${path#infra/stacks/}`: `infra/stacks/app/main.tf` → `app/main.tf` |
| `${x##pat}` | longest match from the start | `${image##*:}`: `…/devcool:abc123` → `abc123` (the tag) |
| `${x%pat}` | shortest match from the **end** | |
| `${x%%pat}` | longest match from the end | `${s%%/*}`: `app/main.tf` → `app` (the stack) |
| `${x:-def}` | — (default value) | `${ENVIRONMENT:-dev}` |

Mnemonic: on a US keyboard `#` is left of `$` (the start) and `%` is right of it (the end). Double the symbol to make the match greedy.

### 2.5 Tests and branching

`[ … ]` is a command (named `test`), so it needs spaces inside the brackets, and every variable in it must be quoted. `[[ … ]]` is Bash syntax and adds glob matching and regexes:

```bash
[[ "$image" == *:* && "$image" != "None" ]]        # env-state.sh:20  — glob: contains a colon
[[ "${2:-}" =~ ^0+$ ]]                             # changed-stacks.sh:11 — regex: all zeros
```

The all-zeros SHA is what GitHub sends as the "before" commit when a branch is first pushed, so there is nothing to diff against and every stack is planned. Compare strings with `=` / `!=` and numbers with `-eq -ne -lt -gt`. `-n "$x"` is "not empty", `-z` is "empty" and `-d path` is "is a directory" (`changed-stacks.sh:46`).

`case` matches one string against glob patterns. `|` separates alternatives and `;;` ends a branch. Joining two values with `:` gives a two-key switch:

```bash
case "$stack:${STATE:-awake}" in                                       # tf-stack.sh:24-28
  network:hibernated) args+=(-var enable_nat=false …) ;;
  app:asleep) args+=(-var api_min_count=0) ;;
esac
```

`{ …; }` groups commands in the current shell, so an `exit` inside it ends the script (`tf-stack.sh:31`). `( … )` would run them in a **subshell**, and `exit` there only ends the subshell.

### 2.6 Loops and arrays

```bash
order=(bootstrap network data edge app)    # changed-stacks.sh:8 — indexed array
for s in "${order[@]}"; do …; done         # "${arr[@]}" = each element as its own word
args+=(-var "image_tag=$IMAGE_TAG")        # tf-stack.sh:32 — append two elements
terraform … "${args[@]}"                   # tf-stack.sh:36 — pass them as separate args
declare -A hit=()                          # changed-stacks.sh:9 — associative (string keys)
hit[app]=1;  "${!app[@]}"  "${#stacks[@]}" # set a key; all keys; element count
for _ in $(seq 120); do …; sleep 15; done  # ecs-roll-to-latest.sh:25 — 120 × 15 s = 30 min
```

Build argument lists in **arrays, never in one string**. `"${args[@]}"` keeps `-var` and `image_tag=…` as two separate arguments, even when a value contains spaces. `declare -A hit` works as a set: the script marks a stack with `hit[$s]=1` and later checks `[ -n "${hit[$s]:-}" ]`.

---

## 3. Text tools

| Tool | Real use | What the flags do |
|---|---|---|
| `git diff --name-only A...B` | `changed-stacks.sh:36` | One changed path per line; `...` = changes on `B` since it branched from `A` |
| `grep -l pat files` | `changed-stacks.sh:27` | Print only the **names** of matching files. The `\"` in `"modules/$m\""` matches the closing quote, so `modules/app` can't match `modules/app-x` |
| `read -r a b` | `changed-stacks.sh:14`, `env-state.sh:24` | Read one line into variables; `-r` keeps `\` literal. `IFS=$'\t'` splits on tabs, for this one `read` only |
| `tee f` | `tf-stack.sh:37` | Copy stdin to a file **and** to stdout, so the log shows on screen and is also saved |
| `jq -cRn '[inputs]'` | `changed-stacks.sh:48` | `-R` reads raw text lines, `-n` + `inputs` collects all of them, `[…]` makes an array, `-c` prints it on one line: `["network","app"]` |
| `printf '%s\n' "${a[@]}"` | `changed-stacks.sh:48` | One element per line. Prefer `printf` to `echo` for data |
| `aws … --query … --output text` | all AWS calls | `--query` is a JMESPath filter. `text` gives a plain string, and `None` when nothing matched (`env-state.sh:20`, `ecs-roll-to-latest.sh:30`) |

**Reading command output line by line:** `while read …; done < <(cmd)`. The `<(cmd)` (process substitution) looks like a file containing `cmd`'s output. Why not `cmd | while read …`? In a pipeline, the loop runs in a **subshell**, so every `hit[...]=1` it sets would be thrown away when the loop ends.

---

## 4. One script end to end: `changed-stacks.sh`

1. **Lines 7–9:** `$1` is the mode. `order` fixes the apply order, and `hit` is the set of stacks touched.
2. **Line 11:** if there is no base SHA, or it is all zeros, every stack is "hit".
3. **Lines 14–36:** otherwise, for each changed path (`< <(git diff …)`), a `case` maps it to stacks. `infra/stacks/X/…` gives X. `infra/modules/M/…` gives every stack whose `.tf` references module M. `infra/envs/` or the pipeline's own files give every stack.
4. **Lines 39–42:** `apply` never touches `bootstrap` (ADR-0003). It warns on **stderr** and removes the key (`unset 'hit[bootstrap]'`, quoted so the shell doesn't treat `[…]` as a glob).
5. **Lines 44–48:** walk `order` (not `hit`, whose key order is random), keep stacks that were hit **and** still exist as directories, then print JSON on **stdout**. The workflow reads that output.

Try it without AWS: `.github/scripts/changed-stacks.sh plan all` → `["bootstrap","network","data","edge","app"]`, and `.github/scripts/changed-stacks.sh apply origin/master HEAD` for this branch. That second command compares commits, so uncommitted edits don't count, and it prints `[]` until you commit.

---

## 5. Traps (and how to catch them)

- **`cond && cmd` as the last line.** If `cond` is false, the script's exit code is 1 and CI goes red. Use `if …; then …; fi`, or end the script with `exit 0`.
- **Unquoted variables.** `rm -rf $dir/*` with `dir` empty becomes `rm -rf /*`. Quote everything, and use `set -u`.
- **`local x=$(cmd)` / `export x=$(cmd)` hides `cmd`'s failure.** `-e` sees the exit code of `local`, which is 0. Declare the variable first, then assign it on its own line.
- **`cmd | while read`** loses every variable set in the loop (§3).
- **macOS Bash 3.2** has no `declare -A`, `${x,,}` or `mapfile`. Run `bash --version` before blaming the script.
- **Debugging:** run `bash -n script.sh` to check syntax only, `bash -x script.sh` (or add `set -x` in the script) to print each command before it runs, and `shellcheck script.sh` to find most of the above automatically.

---

**Next (planned): Scripts in GitHub Actions.** How `deploy.yml`, `infra-plan.yml` and `infra-apply.yml` call these scripts: `$GITHUB_OUTPUT` and step outputs, `env:`, `::error::`/`::warning::` annotations, matrix jobs fed by `changed-stacks.sh`'s JSON, and OIDC AWS credentials.
