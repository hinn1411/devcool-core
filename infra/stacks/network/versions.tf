terraform {
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.28" # terraform-aws-modules/vpc 6.x needs >= 6.28
    }
  }
}
