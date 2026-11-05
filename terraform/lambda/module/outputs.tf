output "alias_arn" { value = aws_lambda_alias.live.arn }
output "alias_name" { value = aws_lambda_alias.live.name }
output "content_alias_arn" { value = aws_lambda_alias.content_live.arn }
output "content_alias_name" { value = aws_lambda_alias.content_live.name }
