output "public_subnet_a_id" {
  value = aws_subnet.public_a.id
}

output "public_subnet_ids" {
  value = [aws_subnet.public_a.id]
}

output "public_route_table_id" {
  value = aws_route_table.public.id
}
