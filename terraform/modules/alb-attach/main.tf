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

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb_listener_rule" "qlink_host" {
  listener_arn = var.https_listener_arn
  priority     = var.listener_rule_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.qlink_tg.arn
  }

  condition {
    host_header {
      values = [var.host_header]
    }
  }

  tags = {
    Name = var.listener_rule_tag_name
  }
}
