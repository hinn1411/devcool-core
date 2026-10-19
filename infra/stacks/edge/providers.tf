# us-east-1 already, so the CloudFront cert needs no provider alias (infra/CLAUDE.md).
provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "devcool"
      Environment = var.environment
      Stack       = "edge"
      ManagedBy   = "terraform"
    }
  }
}
