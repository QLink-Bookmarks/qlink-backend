locals {
  function_name = "alink-link-preview-${var.environment}"
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${local.function_name}"
  retention_in_days = 30
}

resource "aws_lambda_function" "preview" {
  function_name    = local.function_name
  description      = "Link preview package from Git ${var.git_sha}"
  filename         = var.package_path
  source_code_hash = filebase64sha256(var.package_path)
  role             = var.execution_role_arn
  runtime          = "nodejs24.x"
  architectures    = ["x86_64"]
  handler          = "index.handler"
  memory_size      = 512
  timeout          = 20
  publish          = true

  reserved_concurrent_executions = 10

  tags = {
    GitCommit = var.git_sha
  }

  environment {
    variables = {
      APP_ENV        = var.environment
      PREVIEW_BUCKET = var.bucket_name
    }
  }

  depends_on = [aws_cloudwatch_log_group.lambda]
}

resource "aws_lambda_alias" "live" {
  name             = "live"
  function_name    = aws_lambda_function.preview.function_name
  function_version = aws_lambda_function.preview.version
}
