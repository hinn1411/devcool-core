# Used by CI (ci.yml, infra job) and locally:
#   tflint --chdir infra --init
#   tflint --chdir infra --recursive --config "$PWD/infra/.tflint.hcl"
# --recursive resolves a relative --config per module dir, so pass an absolute path.

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

# Provider-aware rules (invalid instance types, deprecated arguments, …) that `terraform validate` can't see.
plugin "aws" {
  enabled = true
  version = "0.49.0"
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}
