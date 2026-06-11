variable "bucket_name" {
  type = string
}

variable "bucket_tag_name" {
  type = string
}

variable "cors_allowed_origins" {
  type    = list(string)
  default = ["*"]
}
