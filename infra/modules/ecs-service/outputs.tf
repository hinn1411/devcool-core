output "service_name" {
  description = "ECS service name (deploy.yml, env-power)."
  value       = aws_ecs_service.this.name
}

output "task_definition_family" {
  description = "Task-definition family the pipeline registers new revisions in."
  value       = aws_ecs_task_definition.this.family
}

output "container_name" {
  description = "App container name, for the pipeline's image render and run-task overrides."
  value       = var.container_name
}
