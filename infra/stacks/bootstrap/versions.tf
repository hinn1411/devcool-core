terraform {
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Local state for now: this stack creates the state bucket, so it can't start in it.
  # P2-T01 adds the bucket, then moves this state there with `terraform init -migrate-state`.
}
