data "aws_caller_identity" "current" {}

module "link_preview_lambda" {
  source             = "../module"
  environment        = "prod"
  bucket_name        = "alink-previews-prod"
  execution_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/alinkPreviewLambda-prod"
  package_path       = "${path.root}/../../../lambda/link-preview/build/link-preview.zip"
  git_sha            = var.lambda_git_sha
}

output "alias_arn" {
  value = module.link_preview_lambda.alias_arn
}
