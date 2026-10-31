locals {
  function_name = "alink-link-preview-${var.environment}"
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${local.function_name}"
  retention_in_days = 30
}

resource "aws_iam_role" "lambda" {
  name = "${local.function_name}-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "lambda" {
  name = "${local.function_name}-policy"
  role = aws_iam_role.lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "WritePreviewObjects"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = ["${var.bucket_arn}/${var.environment}-preview-*", "${var.bucket_arn}/${var.environment}-favicon-*"]
      },
      {
        Sid      = "HeadMissingObjects"
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = [var.bucket_arn]
      },
      {
        Sid      = "WriteLambdaLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = ["${aws_cloudwatch_log_group.lambda.arn}:*"]
      }
    ]
  })
}

resource "aws_lambda_function" "preview" {
  function_name    = local.function_name
  description      = "Link preview package from Git ${var.git_sha}"
  filename         = var.package_path
  source_code_hash = filebase64sha256(var.package_path)
  role             = aws_iam_role.lambda.arn
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

  depends_on = [aws_iam_role_policy.lambda, aws_cloudwatch_log_group.lambda]
}

resource "aws_lambda_alias" "live" {
  name             = "live"
  function_name    = aws_lambda_function.preview.function_name
  function_version = aws_lambda_function.preview.version
}
