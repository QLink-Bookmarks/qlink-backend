output "endpoint" {
  value = aws_db_instance.qlink_rds_pg18_public.endpoint
}

output "address" {
  value = aws_db_instance.qlink_rds_pg18_public.address
}

output "port" {
  value = aws_db_instance.qlink_rds_pg18_public.port
}

output "db_name" {
  value = aws_db_instance.qlink_rds_pg18_public.db_name
}

output "username" {
  value = aws_db_instance.qlink_rds_pg18_public.username
}
