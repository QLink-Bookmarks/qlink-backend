variable "shared_vpc_id" {
  description = "VPC that hosts the ECS instances and the shared ALB"
  type        = string
}

variable "db_vpc_id" {
  description = "VPC that hosts the RDS instance, reached over peering"
  type        = string
}

variable "shared_alb_security_group_id" {
  type = string
}

variable "peering_connection_id" {
  description = "Peering connection the cross-VPC rules depend on"
  type        = string
}

variable "app_sg_name" {
  type = string
}

variable "app_sg_description" {
  type = string
}

variable "rds_sg_name" {
  type = string
}

variable "rds_sg_description" {
  type = string
}
