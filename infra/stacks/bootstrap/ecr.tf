# One image for both ECS services (api, worker; ADR-0001), tagged with the git SHA by deploy.yml.
resource "aws_ecr_repository" "app" {
  name = "devcool"

  # A SHA tag must always mean the same image: "build once, deploy the same artifact".
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  # AES256, not a KMS key: same $20 envelope trade-off as the state bucket (P2-T01 brief).
  #trivy:ignore:AWS-0033
  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 20 images (enough to roll back; storage is billed)."
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}
