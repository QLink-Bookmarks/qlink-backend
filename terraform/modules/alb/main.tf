resource "aws_lb" "qlink_alb" {
  name               = var.alb_name
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.security_group_id]
  subnets            = var.public_subnet_ids

  tags = {
    Name = var.alb_tag_name
  }
}

resource "aws_lb_target_group" "qlink_tg" {
  name        = var.target_group_name
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "instance"

  health_check {
    enabled             = true
    healthy_threshold   = 5
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 30
    path                = "/health"
    matcher             = "200"
    port                = "traffic-port"
    protocol            = "HTTP"
  }

  tags = {
    Name = var.target_group_tag_name
  }
}

resource "aws_lb_listener" "qlink_alb_listener" {
  load_balancer_arn = aws_lb.qlink_alb.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }

  tags = {
    Name = var.listener_tag_name
  }
}

resource "aws_lb_listener" "qlink_alb_https_listener" {
  load_balancer_arn = aws_lb.qlink_alb.arn
  port              = "443"
  protocol          = "HTTPS"
  certificate_arn   = var.acm_certificate_arn
  ssl_policy        = var.https_listener_ssl_policy

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.qlink_tg.arn
  }

  tags = {
    Name = var.https_listener_tag_name
  }
}

resource "aws_lb_listener_rule" "qlink_block_metrics" {
  count = var.metrics_path_block_enabled ? 1 : 0

  listener_arn = aws_lb_listener.qlink_alb_https_listener.arn
  priority     = var.metrics_path_block_priority

  action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not Found"
      status_code  = "404"
    }
  }

  condition {
    path_pattern {
      values = [var.metrics_path]
    }
  }

  tags = {
    Name = var.metrics_path_block_tag_name
  }
}
