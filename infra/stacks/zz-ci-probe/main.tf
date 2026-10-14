# THROWAWAY: P2-T10 negative test for the CI infra job. Reverted before merge; never applied.
# Each block breaks one check: fmt (misaligned `=`), tflint (t5.micro), Trivy (public bucket).
terraform {
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}

# fmt: the `=` below are deliberately not aligned.
resource "aws_instance" "probe" {
  ami = "ami-0123456789abcdef0"
  instance_type    = "t5.micro" # tflint: aws_instance_invalid_type (no such family)
}

# Trivy: public ACL + public-access block switched off → HIGH findings (AWS-0086…0094).
resource "aws_s3_bucket" "probe" {
  bucket = "devcool-ci-probe-public"
}

resource "aws_s3_bucket_public_access_block" "probe" {
  bucket                  = aws_s3_bucket.probe.id
  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_acl" "probe" {
  bucket = aws_s3_bucket.probe.id
  acl    = "public-read"
}
