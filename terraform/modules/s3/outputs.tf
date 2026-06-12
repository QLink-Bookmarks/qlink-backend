output "bucket_name" {
  value = aws_s3_bucket.images.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.images.arn
}

output "bucket_regional_domain_name" {
  value = aws_s3_bucket.images.bucket_regional_domain_name
}
