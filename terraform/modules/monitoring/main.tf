data "aws_ami" "al2023_arm64" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-6.1-arm64"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

resource "aws_security_group" "monitoring" {
  name        = var.security_group_name
  description = var.security_group_description
  vpc_id      = var.vpc_id

  tags = {
    Name = var.security_group_name
  }
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_from_alb" {
  security_group_id            = aws_security_group.monitoring.id
  referenced_security_group_id = var.alb_security_group_id
  from_port                    = var.grafana_port
  to_port                      = var.grafana_port
  ip_protocol                  = "tcp"
  description                  = "From ALB to Grafana"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_monitoring" {
  security_group_id            = var.alb_security_group_id
  referenced_security_group_id = aws_security_group.monitoring.id
  from_port                    = var.grafana_port
  to_port                      = var.grafana_port
  ip_protocol                  = "tcp"
  description                  = "From ALB to Grafana"
}

resource "aws_vpc_security_group_egress_rule" "monitoring_http_out" {
  security_group_id = aws_security_group.monitoring.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
  description       = "From Monitoring to Internet"
}

resource "aws_vpc_security_group_egress_rule" "monitoring_https_out" {
  security_group_id = aws_security_group.monitoring.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "From Monitoring to Internet"
}

resource "aws_vpc_security_group_egress_rule" "monitoring_to_app_metrics" {
  security_group_id            = aws_security_group.monitoring.id
  referenced_security_group_id = var.app_security_group_id
  from_port                    = var.app_metrics_port
  to_port                      = var.app_metrics_port
  ip_protocol                  = "tcp"
  description                  = "From Monitoring to App metrics"
}

resource "aws_vpc_security_group_egress_rule" "monitoring_to_node_exporter" {
  security_group_id            = aws_security_group.monitoring.id
  referenced_security_group_id = var.app_security_group_id
  from_port                    = var.node_exporter_port
  to_port                      = var.node_exporter_port
  ip_protocol                  = "tcp"
  description                  = "From Monitoring to node_exporter"
}

resource "aws_vpc_security_group_ingress_rule" "app_metrics_from_monitoring" {
  security_group_id            = var.app_security_group_id
  referenced_security_group_id = aws_security_group.monitoring.id
  from_port                    = var.app_metrics_port
  to_port                      = var.app_metrics_port
  ip_protocol                  = "tcp"
  description                  = "From Monitoring to App metrics"
}

resource "aws_vpc_security_group_ingress_rule" "app_node_exporter_from_monitoring" {
  security_group_id            = var.app_security_group_id
  referenced_security_group_id = aws_security_group.monitoring.id
  from_port                    = var.node_exporter_port
  to_port                      = var.node_exporter_port
  ip_protocol                  = "tcp"
  description                  = "From Monitoring to node_exporter"
}

resource "aws_ssm_parameter" "grafana_admin_password" {
  name  = var.grafana_admin_password_parameter_name
  type  = "SecureString"
  value = var.grafana_admin_password
}

resource "aws_iam_role" "monitoring" {
  name = var.instance_role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "monitoring_ssm" {
  role       = aws_iam_role.monitoring.name
  policy_arn = var.ssm_managed_policy_arn
}

resource "aws_iam_role_policy" "monitoring_discovery" {
  name = "${var.instance_role_name}-discovery"
  role = aws_iam_role.monitoring.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ec2:DescribeInstances"]
        Resource = ["*"]
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter"]
        Resource = [aws_ssm_parameter.grafana_admin_password.arn]
      }
    ]
  })
}

resource "aws_iam_instance_profile" "monitoring" {
  name = var.instance_profile_name
  role = aws_iam_role.monitoring.name
}

data "aws_subnet" "monitoring" {
  id = var.subnet_id
}

resource "aws_ebs_volume" "monitoring_data" {
  availability_zone = data.aws_subnet.monitoring.availability_zone
  size              = var.data_volume_size
  type              = "gp3"
  encrypted         = true

  tags = {
    Name = var.data_volume_tag_name
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "monitoring_data" {
  device_name = var.data_volume_device_name
  volume_id   = aws_ebs_volume.monitoring_data.id
  instance_id = aws_instance.monitoring.id

  stop_instance_before_detaching = true
}

locals {
  prometheus_config = templatefile("${path.module}/templates/prometheus.yml.tftpl", {
    aws_region         = var.aws_region
    vpc_id             = var.vpc_id
    scrape_interval    = var.prometheus_scrape_interval
    app_metrics_port   = var.app_metrics_port
    app_metrics_path   = var.app_metrics_path
    node_exporter_port = var.node_exporter_port
    scrape_targets     = var.scrape_targets
    scrape_ecs_node    = var.scrape_ecs_node_exporter
  })

  compose_config = templatefile("${path.module}/templates/docker-compose.yml.tftpl", {
    data_mount_path           = var.data_mount_path
    prometheus_image          = var.prometheus_image
    grafana_image             = var.grafana_image
    node_exporter_image       = var.node_exporter_image
    grafana_port              = var.grafana_port
    prometheus_retention      = var.prometheus_retention
    prometheus_memory_limit   = var.prometheus_memory_limit
    grafana_memory_limit      = var.grafana_memory_limit
    prometheus_max_samples    = var.prometheus_max_samples
    prometheus_retention_size = var.prometheus_retention_size
    prometheus_mem_limit      = var.prometheus_mem_limit
    grafana_mem_limit         = var.grafana_mem_limit
    node_exporter_mem_limit   = var.node_exporter_mem_limit
  })

  datasource_config = templatefile("${path.module}/templates/datasource.yml.tftpl", {})

  dashboard_provider_config = templatefile("${path.module}/templates/dashboard-provider.yml.tftpl", {})

  dashboard_service_config = templatefile("${path.module}/templates/dashboard-service.json.tftpl", {})

  user_data = templatefile("${path.module}/templates/user_data.sh.tftpl", {
    aws_region                 = var.aws_region
    grafana_domain             = var.grafana_domain
    grafana_password_parameter = aws_ssm_parameter.grafana_admin_password.name
    docker_compose_version     = var.docker_compose_version
    swap_size_mb               = var.swap_size_mb
    data_device_name           = var.data_volume_device_name
    data_mount_path            = var.data_mount_path
    prometheus_config          = local.prometheus_config
    compose_config             = local.compose_config
    datasource_config          = local.datasource_config
    dashboard_provider_config  = local.dashboard_provider_config
    dashboard_service_config   = local.dashboard_service_config
  })
}

resource "aws_instance" "monitoring" {
  ami                         = data.aws_ami.al2023_arm64.id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [aws_security_group.monitoring.id]
  iam_instance_profile        = aws_iam_instance_profile.monitoring.name
  associate_public_ip_address = true

  user_data_base64            = base64gzip(local.user_data)
  user_data_replace_on_change = true

  root_block_device {
    volume_size           = var.root_volume_size
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = {
    Name = var.instance_tag_name
  }

  depends_on = [aws_ebs_volume.monitoring_data]
}

resource "aws_lb_target_group" "grafana" {
  name        = var.target_group_name
  port        = var.grafana_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "instance"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    path                = "/api/health"
    matcher             = "200"
    port                = "traffic-port"
    protocol            = "HTTP"
  }

  tags = {
    Name = var.target_group_tag_name
  }
}

resource "aws_lb_target_group_attachment" "grafana" {
  target_group_arn = aws_lb_target_group.grafana.arn
  target_id        = aws_instance.monitoring.id
  port             = var.grafana_port
}

resource "aws_lb_listener_rule" "grafana" {
  listener_arn = var.https_listener_arn
  priority     = var.listener_rule_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.grafana.arn
  }

  condition {
    host_header {
      values = [var.grafana_domain]
    }
  }

  tags = {
    Name = var.listener_rule_tag_name
  }
}
