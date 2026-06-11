output "alb_dns_name" {
  description = "DNS name of the load balancer"
  value       = module.alb.alb_dns_name
}

output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = module.rds.endpoint
}

output "rds_db_name" {
  description = "RDS database name"
  value       = module.rds.db_name
}

output "rds_username" {
  description = "RDS master username"
  value       = module.rds.username
}

output "rds_jdbc_url" {
  description = "JDBC URL for manually syncing secrets/application-dev.yaml"
  value       = local.rds_jdbc_url
}

output "vpc_id" {
  description = "ID of the VPC"
  value       = module.network.vpc_id
}

output "ecr_repository_url" {
  description = "ECR repository URL"
  value       = module.ecr.repository_url
}

output "target_group_arn" {
  description = "Target group ARN"
  value       = module.alb.target_group_arn
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = module.ecs.service_name
}

output "images_bucket_name" {
  description = "S3 bucket name for uploaded images"
  value       = module.s3.bucket_name
}

output "capacity_provider_name" {
  description = "ECS capacity provider name"
  value       = module.ecs.capacity_provider_name
}
