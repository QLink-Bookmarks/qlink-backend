variable "alb_name" {
  type = string
}

variable "alb_tag_name" {
  type = string
}

variable "target_group_name" {
  type = string
}

variable "target_group_tag_name" {
  type = string
}

variable "listener_tag_name" {
  type = string
}

variable "https_listener_tag_name" {
  type = string
}

variable "acm_certificate_arn" {
  type = string
}

variable "https_listener_ssl_policy" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "metrics_path_block_enabled" {
  description = "Block the Micrometer scrape endpoint at the ALB; Prometheus scrapes instances directly"
  type        = bool
  default     = true
}

variable "metrics_path" {
  type    = string
  default = "/metrics-micrometer"
}

variable "metrics_path_block_priority" {
  type    = number
  default = 1
}

variable "metrics_path_block_tag_name" {
  type    = string
  default = "block-metrics-path"
}

variable "host_header" {
  description = "Host that routes to this target group"
  type        = string
}

variable "listener_rule_priority" {
  description = "Priority of the requester rule; the preflight and docs rules take the next two"
  type        = number
}

variable "listener_rule_tag_name" {
  type = string
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
