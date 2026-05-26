output "alb_security_group_id" {
  value = aws_security_group.qlink_alb_group.id
}

output "app_security_group_id" {
  value = aws_security_group.qlink_app_group.id
}

output "rds_app_security_group_id" {
  value = aws_security_group.qlink_rds_app_group.id
}

output "rds_public_security_group_id" {
  value = aws_security_group.qlink_rds_public_group.id
}
