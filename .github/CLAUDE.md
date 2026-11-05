# .github/ — CI/CD

- `ci.yml` is a router: `changes` (dorny/paths-filter) picks the areas, and each area job runs `.github/actions/ci-<area>/`. The job holds `runs-on`, `permissions`, `env`, secrets and checkout; the action holds the steps. Every step blocks.
- `ci-gate` is the only required check. Add every new router job to its `needs`.
- actionlint doesn't shellcheck `run:` blocks in `action.yml`, so keep those to one-liners and put longer shell in `.github/scripts/`.
- No lint baselines. An accepted finding gets an inline `# shellcheck disable=SCxxxx` or `#trivy:ignore:<id>` with a reason.
