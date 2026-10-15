# The chain is ALB → api :8080 → Aurora :5432. Every rule names a source/target SG, not a CIDR,
# so only the ALB reaches the app and only the app reaches the DB. Terraform removes the default
# allow-all egress, so an SG with no egress rule below has none.

data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "ALB: HTTPS from CloudFront only (ADR-0011 single origin)"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_security_group" "api" {
  name        = "${local.name}-api"
  description = "api ECS tasks"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_security_group" "aurora" {
  name        = "${local.name}-aurora"
  description = "Aurora PostgreSQL: api tasks only"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_security_group" "vpce" {
  name        = "${local.name}-vpce"
  description = "Interface VPC endpoints: HTTPS from api tasks"
  vpc_id      = module.vpc.vpc_id
}

# The prefix list has ~55 entries and each counts against the 60-rules-per-SG quota.
resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTPS from CloudFront origin-facing IPs"
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "alb_to_api" {
  security_group_id            = aws_security_group.alb.id
  description                  = "Forward to api tasks"
  referenced_security_group_id = aws_security_group.api.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_vpc_security_group_ingress_rule" "api_from_alb" {
  security_group_id            = aws_security_group.api.id
  description                  = "HTTP and WebSocket from the ALB"
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_vpc_security_group_egress_rule" "api_to_aurora" {
  security_group_id            = aws_security_group.api.id
  description                  = "PostgreSQL to Aurora"
  referenced_security_group_id = aws_security_group.aurora.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

# AWS APIs (ECR, Logs, Secrets Manager, S3) resolve to endpoint ENIs or public IPs via the NAT,
# and S3 gateway traffic targets the S3 prefix list, so no narrower destination covers them all.
#trivy:ignore:AWS-0104 HTTPS-only egress from private subnets, through the NAT or endpoints
resource "aws_vpc_security_group_egress_rule" "api_https_out" {
  security_group_id = aws_security_group.api.id
  description       = "HTTPS to AWS APIs via endpoints or NAT"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "aurora_from_api" {
  security_group_id            = aws_security_group.aurora.id
  description                  = "PostgreSQL from api tasks"
  referenced_security_group_id = aws_security_group.api.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_vpc_security_group_ingress_rule" "vpce_from_api" {
  security_group_id            = aws_security_group.vpce.id
  description                  = "HTTPS from api tasks"
  referenced_security_group_id = aws_security_group.api.id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}
