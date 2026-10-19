output "cluster_name" {
  description = "ECS cluster, for run-task and describe-services."
  value       = aws_ecs_cluster.this.name
}

output "api_service_name" {
  description = "api ECS service."
  value       = module.api.service_name
}

output "api_task_family" {
  description = "Task-definition family; the migrate task is a run-task of it with a profile override."
  value       = module.api.task_definition_family
}

output "api_target_group_arn" {
  description = "For `aws elbv2 describe-target-health`."
  value       = aws_lb_target_group.api.arn
}

output "origin_url" {
  description = "The ALB behind its fixed alias; it returns 403 without CloudFront's header."
  value       = "https://${aws_route53_record.origin.name}"
}

output "ssm_prefix" {
  description = "SSM path consumer workflows read from."
  value       = local.ssm_prefix
}
