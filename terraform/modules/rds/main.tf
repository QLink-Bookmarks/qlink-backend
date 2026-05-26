resource "aws_db_subnet_group" "qlink_db_subnet_group" {
  name       = var.db_subnet_group_name
  subnet_ids = var.subnet_ids

  tags = {
    Name = var.db_subnet_group_tag_name
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "qlink_rds_pg18" {
  identifier     = var.identifier
  engine         = var.engine
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage  = var.allocated_storage
  storage_type       = var.storage_type
  iops               = var.iops
  storage_throughput = var.storage_throughput

  db_name  = var.db_name
  username = var.username
  password = var.db_password

  vpc_security_group_ids = var.rds_security_group_ids
  db_subnet_group_name   = aws_db_subnet_group.qlink_db_subnet_group.name
  copy_tags_to_snapshot  = true

  availability_zone   = var.availability_zone
  publicly_accessible = var.publicly_accessible
  multi_az            = var.multi_az

  enabled_cloudwatch_logs_exports = var.enabled_cloudwatch_logs_exports

  skip_final_snapshot = var.skip_final_snapshot

  tags = {
    Name = var.instance_tag_name
  }

  lifecycle {
    ignore_changes = [password]
  }
}
