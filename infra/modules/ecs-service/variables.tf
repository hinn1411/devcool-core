variable "name" {
  description = "Service and task-definition family name, e.g. devcool-dev-api."
  type        = string
}

variable "region" {
  description = "Region of the log group (awslogs driver)."
  type        = string
}

variable "cluster_arn" {
  description = "ECS cluster the service runs in."
  type        = string
}

variable "cluster_name" {
  description = "The same cluster's name, for the autoscaling resource id."
  type        = string
}

variable "container_name" {
  description = "Name of the app container; the ALB and the deploy pipeline refer to it."
  type        = string
}

variable "image" {
  description = "Image for the first task definition only. The deploy pipeline owns it after that (ADR-0003)."
  type        = string
}

variable "cpu" {
  description = "Task CPU units (512 = 0.5 vCPU)."
  type        = number
}

variable "memory" {
  description = "Task memory in MiB. The JVM heap is 75 % of it (Dockerfile JAVA_TOOL_OPTIONS)."
  type        = number
}

variable "container_port" {
  description = "Port the app listens on."
  type        = number
  default     = 8080
}

variable "environment" {
  description = "Plain environment variables. Never secrets: they show in the task definition."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Env var name → Secrets Manager valueFrom (an ARN, or ARN:json-key::). Resolved by the execution role at start."
  type        = map(string)
  default     = {}
}

variable "log_group_name" {
  description = "Existing CloudWatch log group. It lives in data, so logs survive hibernate (ADR-0013)."
  type        = string
}

variable "execution_role_arn" {
  description = "Role the ECS agent uses: image pull, logs, injected secrets."
  type        = string
}

variable "task_role_arn" {
  description = "Role the app's own AWS calls run as."
  type        = string
}

variable "subnet_ids" {
  description = "Private subnets for the tasks; they get no public IP."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups for the task ENIs."
  type        = list(string)
}

variable "target_group_arn" {
  description = "ALB target group, or null for a service without a load balancer (the P6 worker)."
  type        = string
  default     = null
}

variable "health_check_grace_period_seconds" {
  description = "Seconds ECS ignores failing ALB checks after a task starts: Spring start plus an Aurora resume (>= 30 s)."
  type        = number
  default     = 120
}

variable "stop_timeout" {
  description = "Seconds from SIGTERM to SIGKILL. Longer than Spring's 30 s shutdown phase (phase-2 design notes)."
  type        = number
  default     = 45
}

variable "min_count" {
  description = "Autoscaling minimum, and the desired count when the service is created. 0 for the very first apply, before the migrate task has run."
  type        = number
}

variable "max_count" {
  description = "Autoscaling maximum."
  type        = number
}

variable "cpu_target" {
  description = "Target average CPU % for target tracking."
  type        = number
  default     = 60
}

variable "readonly_root_filesystem" {
  description = "Read-only root; scratch_paths get writable ephemeral mounts. Each path needs a VOLUME line in the Dockerfile, or it mounts root-owned 0755."
  type        = bool
  default     = true
}

variable "scratch_paths" {
  description = "Writable paths under a read-only root (Tomcat and the JVM write to /tmp)."
  type        = list(string)
  default     = ["/tmp"]
}

variable "sidecars" {
  description = "Extra container definitions (the OTel collector, P7), in ECS JSON shape."
  type        = list(any)
  default     = []
}
