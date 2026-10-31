module "link_preview_lambda" {
  source             = "../module"
  environment        = "dev"
  bucket_name        = "alink-previews-dev"
  execution_role_arn = var.lambda_role_arn
  package_path       = "${path.root}/../../../lambda/link-preview/build/link-preview.zip"
  git_sha            = var.lambda_git_sha
}

output "alias_arn" {
  value = module.link_preview_lambda.alias_arn
}
