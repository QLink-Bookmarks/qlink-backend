variable "https_listener_arn" {
  description = "HTTPS listener of the shared ALB"
  type        = string
}

variable "vpc_id" {
  type = string
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

variable "host_header" {
  description = "Host that routes to this target group"
  type        = string
}
