data "aws_caller_identity" "current" {}

module "link_preview_lambda" {
  source             = "../module"
  environment        = "dev"
  bucket_name        = "alink-previews-dev"
  execution_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/alinkPreviewLambda-dev"
  package_path       = "${path.root}/../../../lambda/link-preview/build/link-preview.zip"
  git_sha            = var.lambda_git_sha
}

output "alias_arn" {
  value = module.link_preview_lambda.alias_arn
}
