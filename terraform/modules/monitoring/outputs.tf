output "instance_id" {
  value = aws_instance.monitoring.id
}

output "private_ip" {
  value = aws_instance.monitoring.private_ip
}

output "security_group_id" {
  value = aws_security_group.monitoring.id
}

output "target_group_arn" {
  value = aws_lb_target_group.grafana.arn
}
