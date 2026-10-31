variable "lambda_role_arn" {
  type = string
  validation {
    condition     = can(regex("^arn:aws:iam::[0-9]{12}:role/.+$", var.lambda_role_arn))
    error_message = "lambda_role_arn must be an IAM role ARN."
  }
}

variable "lambda_git_sha" {
  type    = string
  default = "local"
}
