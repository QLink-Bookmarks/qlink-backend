variable "aws_region" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "alb_security_group_id" {
  type = string
}

variable "app_security_group_id" {
  type = string
}

variable "https_listener_arn" {
  type = string
}

variable "security_group_name" {
  type = string
}

variable "security_group_description" {
  type = string
}

variable "instance_role_name" {
  type = string
}

variable "instance_profile_name" {
  type = string
}

variable "ssm_managed_policy_arn" {
  type    = string
  default = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

variable "instance_type" {
  type = string
}

variable "instance_tag_name" {
  type = string
}

variable "root_volume_size" {
  type = number
}

variable "target_group_name" {
  type = string
}

variable "target_group_tag_name" {
  type = string
}

variable "listener_rule_priority" {
  type = number
}

variable "listener_rule_tag_name" {
  type = string
}

variable "grafana_domain" {
  type = string
}

variable "grafana_port" {
  type    = number
  default = 3000
}

variable "grafana_admin_password" {
  type      = string
  sensitive = true
}

variable "grafana_admin_password_parameter_name" {
  type = string
}

variable "app_metrics_port" {
  type    = number
  default = 8080
}

variable "app_metrics_path" {
  type    = string
  default = "/metrics-micrometer"
}

variable "node_exporter_port" {
  type    = number
  default = 9100
}

variable "scrape_targets" {
  description = "Environment name to ECS instance Name tag; becomes the env label"
  type        = map(string)
}

variable "scrape_ecs_node_exporter" {
  description = "Enable once node_exporter is installed on the ECS instances"
  type        = bool
  default     = false
}

variable "prometheus_scrape_interval" {
  type    = string
  default = "60s"
}

variable "prometheus_retention" {
  type    = string
  default = "15d"
}

variable "prometheus_retention_size" {
  type    = string
  default = "8GB"
}

variable "prometheus_mem_limit" {
  type    = string
  default = "512m"
}

variable "grafana_mem_limit" {
  type    = string
  default = "256m"
}

variable "node_exporter_mem_limit" {
  type    = string
  default = "64m"
}

variable "prometheus_max_samples" {
  type    = number
  default = 1000000
}

variable "prometheus_memory_limit" {
  type    = string
  default = "400MiB"
}

variable "grafana_memory_limit" {
  type    = string
  default = "200MiB"
}

variable "prometheus_image" {
  type    = string
  default = "prom/prometheus:v3.1.0"
}

variable "grafana_image" {
  type    = string
  default = "grafana/grafana:11.4.0"
}

variable "node_exporter_image" {
  type    = string
  default = "prom/node-exporter:v1.8.2"
}

variable "docker_compose_version" {
  type    = string
  default = "v2.32.1"
}

variable "swap_size_mb" {
  type    = number
  default = 1024
}

variable "data_volume_size" {
  type = number
}

variable "data_volume_tag_name" {
  type = string
}

variable "data_volume_device_name" {
  type    = string
  default = "/dev/sdf"
}

variable "data_mount_path" {
  type    = string
  default = "/opt/monitoring/data"
}
