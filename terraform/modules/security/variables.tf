variable "vpc_id" {
  type = string
}

variable "alb_sg_name" {
  type = string
}

variable "alb_sg_description" {
  type = string
}

variable "app_sg_name" {
  type = string
}

variable "app_sg_description" {
  type = string
}

variable "rds_app_sg_name" {
  type = string
}

variable "rds_app_sg_description" {
  type = string
}

variable "rds_legacy_sg_name" {
  type = string
}

variable "rds_legacy_sg_description" {
  type = string
}

variable "rds_public_sg_name" {
  type = string
}

variable "rds_public_sg_description" {
  type = string
}

variable "rds_public_ingress_cidrs" {
  type = list(string)
}
