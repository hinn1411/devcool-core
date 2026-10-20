# Partial config: bucket/region/lock come from envs/<env>.backend.hcl, the key from the CLI
# (devcool/<env>/app.tfstate). See infra/CLAUDE.md.
terraform {
  backend "s3" {}
}
