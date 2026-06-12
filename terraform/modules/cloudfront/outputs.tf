output "distribution_domain_name" {
  description = "CloudFront-assigned domain (e.g. dxxxx.cloudfront.net)"
  value       = aws_cloudfront_distribution.cdn.domain_name
}

output "distribution_hosted_zone_id" {
  description = "Fixed CloudFront hosted zone id for Route53 alias records"
  value       = aws_cloudfront_distribution.cdn.hosted_zone_id
}

output "distribution_arn" {
  value = aws_cloudfront_distribution.cdn.arn
}

output "public_base_url" {
  description = "Base URL the app returns for uploaded images"
  value       = "https://${var.domain_name}"
}
