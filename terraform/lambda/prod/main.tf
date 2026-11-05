module "link_preview_lambda" {
  source                       = "../module"
  environment                  = "prod"
  bucket_name                  = "alink-previews-prod"
  execution_role_arn           = "arn:aws:iam::650177546654:role/alinkPreviewLambdaRole"
  content_execution_role_arn   = "arn:aws:iam::650177546654:role/alinkContentLambdaRole"
  content_reserved_concurrency = 10
  package_path                 = "${path.root}/../../../lambda/link-fetch/build/link-fetch.zip"
  git_sha                      = var.lambda_git_sha
}

output "alias_arn" {
  value = module.link_preview_lambda.alias_arn
}

output "content_alias_arn" {
  value = module.link_preview_lambda.content_alias_arn
}
