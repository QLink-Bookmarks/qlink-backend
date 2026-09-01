resource "aws_security_group" "app" {
  name        = var.app_sg_name
  description = var.app_sg_description
  vpc_id      = var.shared_vpc_id

  tags = {
    Name = var.app_sg_name
  }
}

resource "aws_security_group" "rds" {
  name        = var.rds_sg_name
  description = var.rds_sg_description
  vpc_id      = var.db_vpc_id

  tags = {
    Name = var.rds_sg_name
  }
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = var.shared_alb_security_group_id
  from_port                    = 0
  to_port                      = 65535
  ip_protocol                  = "tcp"
  description                  = "From shared ALB to ECS"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  security_group_id            = var.shared_alb_security_group_id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 0
  to_port                      = 65535
  ip_protocol                  = "tcp"
  description                  = "From shared ALB to ECS"
}

resource "aws_vpc_security_group_egress_rule" "app_http_out" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
  description       = "From ECS to Internet"
}

resource "aws_vpc_security_group_egress_rule" "app_https_out" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "From ECS to Internet"
}

resource "aws_vpc_security_group_egress_rule" "app_to_alb_response" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = var.shared_alb_security_group_id
  from_port                    = 1024
  to_port                      = 65535
  ip_protocol                  = "tcp"
  description                  = "From ECS to ALB response"
}

resource "aws_vpc_security_group_egress_rule" "app_to_rds" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.rds.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  description                  = "From ECS to RDS over VPC peering"

  depends_on = [var.peering_connection_id]
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_app" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  description                  = "From ECS to RDS over VPC peering"

  depends_on = [var.peering_connection_id]
}
