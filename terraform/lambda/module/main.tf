locals {
  function_name         = "alink-link-preview-${var.environment}"
  content_function_name = "alink-link-content-${var.environment}"
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

  reserved_concurrent_executions = 30

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

resource "aws_cloudwatch_log_group" "content" {
  name              = "/aws/lambda/${local.content_function_name}"
  retention_in_days = 30
}

# Same package as the preview function, second entry point. Timeout chain: the handler's fetch deadline (20s) <
# this timeout (30s) < the API server's invoke timeout (45s).
resource "aws_lambda_function" "content" {
  function_name    = local.content_function_name
  description      = "Link content package from Git ${var.git_sha}"
  filename         = var.package_path
  source_code_hash = filebase64sha256(var.package_path)
  role             = var.content_execution_role_arn
  runtime          = "nodejs24.x"
  architectures    = ["x86_64"]
  handler          = "content.handler"
  memory_size      = 1024
  timeout          = 30
  publish          = true

  reserved_concurrent_executions = var.content_reserved_concurrency

  tags = {
    GitCommit = var.git_sha
  }

  environment {
    variables = {
      APP_ENV = var.environment
    }
  }

  depends_on = [aws_cloudwatch_log_group.content]
}

resource "aws_lambda_alias" "content_live" {
  name             = "live"
  function_name    = aws_lambda_function.content.function_name
  function_version = aws_lambda_function.content.version
}
