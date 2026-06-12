resource "aws_vpc" "qlink_vpc" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = var.vpc_name
  }
}

resource "aws_internet_gateway" "qlink_igw" {
  vpc_id = aws_vpc.qlink_vpc.id

  tags = {
    Name = var.igw_name
  }
}

resource "aws_subnet" "qlink_public_a" {
  vpc_id                  = aws_vpc.qlink_vpc.id
  cidr_block              = var.public_subnet_a_cidr
  availability_zone       = var.az_a
  map_public_ip_on_launch = false

  tags = {
    Name = var.public_subnet_a_name
  }
}

resource "aws_subnet" "qlink_public_c" {
  vpc_id                  = aws_vpc.qlink_vpc.id
  cidr_block              = var.public_subnet_c_cidr
  availability_zone       = var.az_c
  map_public_ip_on_launch = false

  tags = {
    Name = var.public_subnet_c_name
  }
}

resource "aws_subnet" "qlink_private_a" {
  vpc_id            = aws_vpc.qlink_vpc.id
  cidr_block        = var.private_subnet_a_cidr
  availability_zone = var.az_a

  tags = {
    Name = var.private_subnet_a_name
  }
}

resource "aws_subnet" "qlink_private_c" {
  vpc_id            = aws_vpc.qlink_vpc.id
  cidr_block        = var.private_subnet_c_cidr
  availability_zone = var.az_c

  tags = {
    Name = var.private_subnet_c_name
  }
}

resource "aws_route_table" "qlink_public_rt" {
  vpc_id = aws_vpc.qlink_vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.qlink_igw.id
  }

  tags = {
    Name = var.public_route_table_name
  }
}

resource "aws_route_table" "qlink_private_rt_a" {
  vpc_id = aws_vpc.qlink_vpc.id

  tags = {
    Name = var.private_route_table_name
  }
}

resource "aws_route_table_association" "public_a" {
  subnet_id      = aws_subnet.qlink_public_a.id
  route_table_id = aws_route_table.qlink_public_rt.id
}

resource "aws_route_table_association" "public_c" {
  subnet_id      = aws_subnet.qlink_public_c.id
  route_table_id = aws_route_table.qlink_public_rt.id
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = aws_subnet.qlink_private_a.id
  route_table_id = aws_route_table.qlink_private_rt_a.id
}

resource "aws_route_table_association" "private_c" {
  subnet_id      = aws_subnet.qlink_private_c.id
  route_table_id = aws_route_table.qlink_private_rt_a.id
}

resource "aws_network_acl" "qlink_public" {
  vpc_id = aws_vpc.qlink_vpc.id

  tags = {
    Name = "${var.vpc_name}-public-nacl"
  }
}

resource "aws_network_acl" "qlink_private" {
  vpc_id = aws_vpc.qlink_vpc.id

  tags = {
    Name = "${var.vpc_name}-private-nacl"
  }
}

resource "aws_network_acl_association" "public_a" {
  subnet_id      = aws_subnet.qlink_public_a.id
  network_acl_id = aws_network_acl.qlink_public.id
}

resource "aws_network_acl_association" "public_c" {
  subnet_id      = aws_subnet.qlink_public_c.id
  network_acl_id = aws_network_acl.qlink_public.id
}

resource "aws_network_acl_association" "private_a" {
  subnet_id      = aws_subnet.qlink_private_a.id
  network_acl_id = aws_network_acl.qlink_private.id
}

resource "aws_network_acl_association" "private_c" {
  subnet_id      = aws_subnet.qlink_private_c.id
  network_acl_id = aws_network_acl.qlink_private.id
}

resource "aws_network_acl_rule" "public_inbound_ssh" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 100
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = "0.0.0.0/0"
  from_port      = 22
  to_port        = 22
}

resource "aws_network_acl_rule" "public_inbound_http" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 110
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = "0.0.0.0/0"
  from_port      = 80
  to_port        = 80
}

resource "aws_network_acl_rule" "public_inbound_https" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 120
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = "0.0.0.0/0"
  from_port      = 443
  to_port        = 443
}

resource "aws_network_acl_rule" "public_inbound_public_ephemeral_a" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 130
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = var.public_subnet_a_cidr
  from_port      = 1024
  to_port        = 65535
}

resource "aws_network_acl_rule" "public_inbound_public_ephemeral_c" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 140
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = var.public_subnet_c_cidr
  from_port      = 1024
  to_port        = 65535
}

resource "aws_network_acl_rule" "public_inbound_internet_ephemeral" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 150
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = "0.0.0.0/0"
  from_port      = 1024
  to_port        = 65535
}

resource "aws_network_acl_rule" "public_inbound_postgres" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 160
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  # This keeps the requested rule, but public PostgreSQL exposure should be removed in production.
  cidr_block = "0.0.0.0/0"
  from_port  = 5432
  to_port    = 5432
}

resource "aws_network_acl_rule" "public_inbound_deny_all" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 900
  egress         = false
  protocol       = "-1"
  rule_action    = "deny"
  cidr_block     = "0.0.0.0/0"
}

resource "aws_network_acl_rule" "public_outbound_http" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 100
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = "0.0.0.0/0"
  from_port      = 80
  to_port        = 80
}

resource "aws_network_acl_rule" "public_outbound_https" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 110
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = "0.0.0.0/0"
  from_port      = 443
  to_port        = 443
}

resource "aws_network_acl_rule" "public_outbound_public_ephemeral_a" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 120
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = var.public_subnet_a_cidr
  from_port      = 1024
  to_port        = 65535
}

resource "aws_network_acl_rule" "public_outbound_public_ephemeral_c" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 130
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = var.public_subnet_c_cidr
  from_port      = 1024
  to_port        = 65535
}

resource "aws_network_acl_rule" "public_outbound_internet_ephemeral" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 140
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = "0.0.0.0/0"
  from_port      = 1024
  to_port        = 65535
}

resource "aws_network_acl_rule" "public_outbound_postgres" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 150
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = "0.0.0.0/0"
  from_port      = 5432
  to_port        = 5432
}

resource "aws_network_acl_rule" "public_outbound_deny_all" {
  network_acl_id = aws_network_acl.qlink_public.id
  rule_number    = 900
  egress         = true
  protocol       = "-1"
  rule_action    = "deny"
  cidr_block     = "0.0.0.0/0"
}

resource "aws_network_acl_rule" "private_inbound_postgres_a" {
  network_acl_id = aws_network_acl.qlink_private.id
  rule_number    = 100
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = var.public_subnet_a_cidr
  from_port      = 5432
  to_port        = 5432
}

resource "aws_network_acl_rule" "private_inbound_postgres_c" {
  network_acl_id = aws_network_acl.qlink_private.id
  rule_number    = 110
  egress         = false
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = var.public_subnet_c_cidr
  from_port      = 5432
  to_port        = 5432
}

resource "aws_network_acl_rule" "private_inbound_deny_all" {
  network_acl_id = aws_network_acl.qlink_private.id
  rule_number    = 900
  egress         = false
  protocol       = "-1"
  rule_action    = "deny"
  cidr_block     = "0.0.0.0/0"
}

resource "aws_network_acl_rule" "private_outbound_ephemeral_a" {
  network_acl_id = aws_network_acl.qlink_private.id
  rule_number    = 100
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = var.public_subnet_a_cidr
  from_port      = 1024
  to_port        = 65535
}

resource "aws_network_acl_rule" "private_outbound_ephemeral_c" {
  network_acl_id = aws_network_acl.qlink_private.id
  rule_number    = 110
  egress         = true
  protocol       = "tcp"
  rule_action    = "allow"
  cidr_block     = var.public_subnet_c_cidr
  from_port      = 1024
  to_port        = 65535
}

resource "aws_network_acl_rule" "private_outbound_deny_all" {
  network_acl_id = aws_network_acl.qlink_private.id
  rule_number    = 900
  egress         = true
  protocol       = "-1"
  rule_action    = "deny"
  cidr_block     = "0.0.0.0/0"
}

resource "aws_vpc_endpoint" "s3_gateway" {
  vpc_id            = aws_vpc.qlink_vpc.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.qlink_private_rt_a.id]

  tags = {
    Name = var.s3_endpoint_name
  }
}
