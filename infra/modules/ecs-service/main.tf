# One ECS service on Fargate: task definition, service, CPU target tracking. Used for api now
# and the worker in P6; sidecars (OTel, P7) join the task through var.sidecars.
locals {
  scratch_volumes = var.readonly_root_filesystem ? {
    for p in var.scratch_paths : "scratch${replace(p, "/", "-")}" => p
  } : {}

  app_container = {
    name      = var.container_name
    image     = var.image
    essential = true

    portMappings = [{ containerPort = var.container_port, protocol = "tcp" }]

    # Sorted name/value lists: the order ECS stores them in, so plans don't drift.
    environment = [for k, v in var.environment : { name = k, value = v }]
    secrets     = [for k, v in var.secrets : { name = k, valueFrom = v }]

    readonlyRootFilesystem = var.readonly_root_filesystem
    mountPoints            = [for n, p in local.scratch_volumes : { sourceVolume = n, containerPath = p, readOnly = false }]

    stopTimeout = var.stop_timeout

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = var.log_group_name
        awslogs-region        = var.region
        awslogs-stream-prefix = var.container_name
      }
    }
  }
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  container_definitions = jsonencode(concat([local.app_container], var.sidecars))

  # Keep revisions on replace and on hibernate's destroy: the service may still run one, and
  # resume reads the last ACTIVE revision's image to know what to redeploy (P2-T09).
  skip_destroy = true

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64" # the image is linux/amd64 (P2-T06, ADR-0013)
  }

  dynamic "volume" {
    for_each = local.scratch_volumes
    content {
      name = volume.key
    }
  }
}

resource "aws_ecs_service" "this" {
  name             = var.name
  cluster          = var.cluster_arn
  task_definition  = aws_ecs_task_definition.this.arn
  desired_count    = var.min_count
  launch_type      = "FARGATE"
  platform_version = "LATEST"

  # Rolling, no capacity dip: new tasks pass health checks before old ones drain. The circuit
  # breaker rolls back a deploy whose tasks keep failing (ADR-0002).
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  health_check_grace_period_seconds = var.target_group_arn == null ? null : var.health_check_grace_period_seconds

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = var.target_group_arn == null ? [] : [var.target_group_arn]
    content {
      target_group_arn = load_balancer.value
      container_name   = var.container_name
      container_port   = var.container_port
    }
  }

  enable_ecs_managed_tags = true
  propagate_tags          = "SERVICE"

  # The deploy pipeline owns the task definition (ADR-0003); autoscaling and env-power own
  # the count.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }
}

# Registering the target moves the count into [min, max] right away, so env-power's sleep
# (P2-T09) must lower min_count to 0, not only the desired count.
resource "aws_appautoscaling_target" "this" {
  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${var.cluster_name}/${aws_ecs_service.this.name}"
  min_capacity       = var.min_count
  max_capacity       = var.max_count
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.this.service_namespace
  scalable_dimension = aws_appautoscaling_target.this.scalable_dimension
  resource_id        = aws_appautoscaling_target.this.resource_id

  target_tracking_scaling_policy_configuration {
    target_value = var.cpu_target

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
