output "vpc_id" {
  value = aws_vpc.qlink_vpc.id
}

output "public_subnet_a_id" {
  value = aws_subnet.qlink_public_a.id
}

output "public_subnet_c_id" {
  value = aws_subnet.qlink_public_c.id
}

output "private_subnet_a_id" {
  value = aws_subnet.qlink_private_a.id
}

output "private_subnet_c_id" {
  value = aws_subnet.qlink_private_c.id
}
