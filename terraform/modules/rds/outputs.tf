output "endpoint" {
  value = aws_db_instance.qlink_rds.endpoint
}

output "address" {
  value = aws_db_instance.qlink_rds.address
}

output "port" {
  value = aws_db_instance.qlink_rds.port
}

output "db_name" {
  value = aws_db_instance.qlink_rds.db_name
}

output "username" {
  value = aws_db_instance.qlink_rds.username
}
