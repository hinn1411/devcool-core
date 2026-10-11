provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "devcool"
      Environment = var.environment
      Stack       = "bootstrap"
      ManagedBy   = "terraform"
    }
  }
}
