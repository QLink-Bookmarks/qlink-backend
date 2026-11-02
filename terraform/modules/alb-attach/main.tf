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

  condition {
    http_header {
      http_header_name = var.requester_header_name
      values           = ["*"]
    }
  }

  tags = {
    Name = var.listener_rule_tag_name
  }
}

resource "aws_lb_listener_rule" "qlink_preflight" {
  listener_arn = var.https_listener_arn
  priority     = var.listener_rule_priority + 1

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.qlink_tg.arn
  }

  condition {
    host_header {
      values = [var.host_header]
    }
  }

  condition {
    http_request_method {
      values = ["OPTIONS"]
    }
  }

  tags = {
    Name = "${var.listener_rule_tag_name}-preflight"
  }
}

resource "aws_lb_listener_rule" "qlink_docs" {
  listener_arn = var.https_listener_arn
  priority     = var.listener_rule_priority + 2

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.qlink_tg.arn
  }

  condition {
    host_header {
      values = [var.host_header]
    }
  }

  condition {
    path_pattern {
      values = var.docs_path_patterns
    }
  }

  tags = {
    Name = "${var.listener_rule_tag_name}-docs"
  }
}

resource "aws_lb_listener_rule" "qlink_block" {
  listener_arn = var.https_listener_arn
  priority     = var.listener_rule_priority + 3

  action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not Found"
      status_code  = "404"
    }
  }

  condition {
    host_header {
      values = [var.host_header]
    }
  }

  tags = {
    Name = "${var.listener_rule_tag_name}-block"
  }
}
