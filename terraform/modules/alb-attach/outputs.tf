output "target_group_arn" {
  value = aws_lb_target_group.qlink_tg.arn
}

output "listener_rule_arn" {
  value = aws_lb_listener_rule.qlink_host.arn
}
