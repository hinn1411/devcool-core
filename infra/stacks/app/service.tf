module "api" {
  source = "../../modules/ecs-service"

  name           = "${local.name}-api"
  region         = var.region
  cluster_arn    = aws_ecs_cluster.this.arn
  cluster_name   = aws_ecs_cluster.this.name
  container_name = "api"
  image          = "${data.aws_ecr_repository.app.repository_url}:${var.image_tag}"

  # 0.5 vCPU / 1 GB to start; tune with the P9 load test.
  cpu    = 512
  memory = 1024

  environment = {
    SPRING_PROFILES_ACTIVE = "ecs"
    # pgjdbc's own limits (connect 10 s, SSL reply 5 s) fail long before Hikari's 60 s while
    # Aurora resumes from 0 ACU (>= 30 s after a long pause). connectTimeout is in seconds,
    # sslResponseTimeout in ms.
    SPRING_DATASOURCE_URL = "jdbc:postgresql://${local.input["data/db_endpoint"]}:${local.input["data/db_port"]}/${local.input["data/db_name"]}?sslmode=require&connectTimeout=60&sslResponseTimeout=60000"
    AWS_S3_BUCKET         = local.input["data/media_bucket_name"]
    # Host is origin.<domain> behind CloudFront, so same-origin checks can't work; P4-T06
    # reads this allowlist (until then WebSocketConfig allows "*").
    WS_ALLOWED_ORIGINS = "https://${local.domain}"
  }

  # Resolved by the execution role at task start (P2-T05: application-ecs.properties).
  secrets = {
    SPRING_DATASOURCE_USERNAME = "${local.db_secret_arn}:username::"
    SPRING_DATASOURCE_PASSWORD = "${local.db_secret_arn}:password::"
    JWT_ACCESS_SECRET          = local.input["data/jwt_access_secret_arn"]
    JWT_REFRESH_SECRET         = local.input["data/jwt_refresh_secret_arn"]
  }

  log_group_name     = local.input["data/log_group_api"]
  execution_role_arn = aws_iam_role.api_execution.arn
  task_role_arn      = aws_iam_role.api_task.arn

  subnet_ids         = split(",", local.input["network/private_subnet_ids"])
  security_group_ids = [local.input["network/sg_api_id"]]
  target_group_arn   = aws_lb_target_group.api.arn

  min_count = var.api_min_count
  max_count = var.api_max_count

  # The listener and its target group must exist before the service registers targets.
  depends_on = [aws_lb_listener_rule.from_cloudfront]
}
