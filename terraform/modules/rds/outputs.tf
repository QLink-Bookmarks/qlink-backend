output "endpoint" {
  value = aws_db_instance.qlink_rds.endpoint
}

output "db_name" {
  value = aws_db_instance.qlink_rds.db_name
}

output "username" {
  value = aws_db_instance.qlink_rds.username
}
