variable "vpc_id" {
  description = "Existing VPC to attach subnets to"
  type        = string
}

variable "internet_gateway_id" {
  type = string
}

variable "az_a" {
  type = string
}

variable "az_c" {
  type = string
}

variable "public_subnet_a_cidr" {
  type = string
}

variable "public_subnet_c_cidr" {
  type = string
}

variable "public_subnet_a_name" {
  type = string
}

variable "public_subnet_c_name" {
  type = string
}

variable "public_route_table_name" {
  type = string
}
