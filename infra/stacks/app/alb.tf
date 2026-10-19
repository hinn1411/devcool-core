# CloudFront → ALB (origin.<domain>, HTTPS only) → api :8080. The SG admits only CloudFront's
# origin-facing prefix list (network/security_groups.tf), but that list is shared by every
# distribution, so the listener also requires the X-Origin-Verify header that only ours sends.

# Internet-facing by design: CloudFront has no private path to an ALB in this setup (ADR-0011).
#trivy:ignore:AWS-0053
resource "aws_lb" "api" {
  name               = "${local.name}-api"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [local.input["network/sg_alb_id"]]
  subnets            = split(",", local.input["network/public_subnet_ids"])

  # > 2× the 25 s client heartbeat, with margin (phase-2 design notes).
  idle_timeout               = 120
  drop_invalid_header_fields = true

  # Destroyed on every hibernate (ADR-0013).
  enable_deletion_protection = false
}

resource "aws_lb_target_group" "api" {
  name        = "${local.name}-api"
  vpc_id      = local.input["network/vpc_id"]
  target_type = "ip" # Fargate awsvpc tasks
  protocol    = "HTTP"
  port        = 8080

  # ECS deregisters the target, waits this long, and only then sends SIGTERM. In-flight HTTP
  # finishes; open sockets are cut when it ends, before the app can send a close frame.
  deregistration_delay = 30

  health_check {
    path                = "/actuator/health/readiness"
    interval            = 10
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200"
  }
}

# The edge cert covers <domain> and origin.<domain> (P2-T07).
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.api.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = local.input["edge/cert_arn"]

  # Anything that isn't our distribution.
  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Forbidden"
      status_code  = "403"
    }
  }
}

resource "aws_lb_listener_rule" "from_cloudfront" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 1

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }

  condition {
    http_header {
      http_header_name = "X-Origin-Verify"
      values           = [data.aws_ssm_parameter.origin_verify.value]
    }
  }
}

# CloudFront's api-alb origin. Fixed name, so edge never changes when the ALB is re-created
# (ADR-0013); IPv4 only, like the ALB.
resource "aws_route53_record" "origin" {
  zone_id = local.input["edge/zone_id"]
  name    = "origin.${local.domain}"
  type    = "A"

  alias {
    name                   = aws_lb.api.dns_name
    zone_id                = aws_lb.api.zone_id
    evaluate_target_health = false
  }
}
