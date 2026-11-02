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
  description = "Priority of the requester rule; the preflight, docs and block rules take the next three"
  type        = number
}

variable "listener_rule_tag_name" {
  type = string
}

variable "host_header" {
  description = "Host that routes to this target group"
  type        = string
}

variable "requester_header_name" {
  description = "Header our clients and future API consumers must send to reach the app"
  type        = string
  default     = "Alink-Requester"
}

variable "docs_path_patterns" {
  description = "API documentation paths reachable without the requester header"
  type        = list(string)
  default     = ["/docs*", "/openapi*", "/redoc*"]
}
