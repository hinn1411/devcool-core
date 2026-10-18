# Aurora Serverless v2, PostgreSQL 16 (ADR-0009). Raw resources rather than
# terraform-aws-modules/rds-aurora: `prevent_destroy` must be a literal in the resource
# block, and a module can't expose it (ADR-0013: `data` is applied every night).
locals {
  name = "devcool-${var.environment}"
}

resource "aws_db_subnet_group" "aurora" {
  name        = "${local.name}-aurora"
  description = "Private subnets for Aurora (network stack)."
  # insecure_value: these are plain String/StringList params, so no need to mark them sensitive.
  subnet_ids = split(",", data.aws_ssm_parameter.private_subnet_ids.insecure_value)
}

# AWS-managed aws/rds key, not a CMK: a CMK adds $1/month (ADR-0013).
#trivy:ignore:AWS-0079
resource "aws_rds_cluster" "aurora" {
  cluster_identifier = "${local.name}-aurora"
  engine             = "aurora-postgresql"
  engine_mode        = "provisioned" # Serverless v2 is "provisioned" + db.serverless instances
  engine_version     = var.aurora_engine_version
  database_name      = "devcool"
  master_username    = "devcool"

  # RDS generates the password and keeps it in Secrets Manager; state holds only the ARN.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.aurora.name
  vpc_security_group_ids = [data.aws_ssm_parameter.sg_aurora_id.insecure_value]

  storage_encrypted = true

  # 1 day costs nothing extra (backup storage up to the cluster size is free).
  backup_retention_period = 1
  copy_tags_to_snapshot   = true
  apply_immediately       = true # dev: don't wait for the maintenance window

  # dev: pause to 0 ACU after 10 idle minutes, max 4 (ADR-0009).
  serverlessv2_scaling_configuration {
    min_capacity             = 0
    max_capacity             = 4
    seconds_until_auto_pause = 600
  }

  # Belt and braces (brief Decisions: Aurora guard). prevent_destroy fails any Terraform plan
  # that deletes the cluster; deletion_protection makes AWS reject DeleteDBCluster from
  # anywhere (console, CLI, or Terraform after this block is removed); the final snapshot is
  # the way back. Static name: timestamp() would diff on every plan.
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-aurora-final"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_rds_cluster_instance" "writer" {
  identifier         = "${local.name}-aurora-writer"
  cluster_identifier = aws_rds_cluster.aurora.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.aurora.engine
  engine_version     = aws_rds_cluster.aurora.engine_version

  publicly_accessible = false
  apply_immediately   = true # else changes wait for the window and show as a diff

  # Minor upgrades are a deliberate bump of var.aurora_engine_version. With auto-upgrade on,
  # AWS moves the version under Terraform and the next plan tries to downgrade.
  auto_minor_version_upgrade = false

  # Enhanced monitoring off: it passes a role to monitoring.rds.amazonaws.com, which
  # gha-deploy may not do (bootstrap review #3b), and costs extra in dev.
  monitoring_interval          = 0
  performance_insights_enabled = false
}

# Tasks read the secret only at start (P2-T05), so a rotation while they run breaks new
# connections until the next deploy. Nightly hibernate restarts them anyway; 365 days keeps
# rotation out of the way in dev. Managed rotation: no Lambda.
resource "aws_secretsmanager_secret_rotation" "aurora_master" {
  secret_id          = aws_rds_cluster.aurora.master_user_secret[0].secret_arn
  rotate_immediately = false

  rotation_rules {
    automatically_after_days = 365
  }
}
