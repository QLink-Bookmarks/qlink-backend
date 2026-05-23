output "cluster_name" {
  value = aws_ecs_cluster.qlink_ecs_cluster.name
}

output "service_name" {
  value = aws_ecs_service.qlink_ecs_service.name
}

output "capacity_provider_name" {
  value = aws_ecs_capacity_provider.qlink_capacity_provider.name
}
