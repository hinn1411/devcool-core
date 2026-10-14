# This stack's own state, in the bucket it created (state.tf). It started on local state
# and was moved here with `terraform init -migrate-state` (the chicken-and-egg step).
# Other stacks use the same bucket with key devcool/<env>/<stack>.tfstate.
terraform {
  backend "s3" {
    bucket       = "devcool-tfstate-725069913301"
    key          = "devcool/shared/bootstrap.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
