aws_region = "ap-northeast-2"

# prod shares dev's image bucket + CloudFront (images.archivelink.app)
aws_s3_bucket_name = "qlink-images-dev"
images_domain      = "images.archivelink.app"

vpc_cidr = "172.30.0.0/16"
az_a     = "ap-northeast-2a"
az_c     = "ap-northeast-2c"

public_subnet_a_cidr  = "172.30.0.0/20"
public_subnet_c_cidr  = "172.30.32.0/20"
private_subnet_a_cidr = "172.30.128.0/20"
private_subnet_c_cidr = "172.30.144.0/20"

vpc_name                 = "alink-prod"
igw_name                 = "alink-prod-igw"
public_subnet_a_name     = "alink-prod-public-a"
public_subnet_c_name     = "alink-prod-public-c"
private_subnet_a_name    = "alink-prod-private-a"
private_subnet_c_name    = "alink-prod-private-c"
public_route_table_name  = "alink-prod-public"
private_route_table_name = "alink-prod-private-rtb-a"
s3_endpoint_name         = "alink-prod-s3-endpoint"

alb_sg_name               = "alinkAlbGroupProd"
alb_sg_description        = "ALB Security Group (prod)"
app_sg_name               = "alinkAppGroupProd"
app_sg_description        = "ECS Security Group (prod)"
rds_app_sg_name           = "alinkRdsAppGroupProd"
rds_app_sg_description    = "RDS App Security Group (prod)"
rds_legacy_sg_name        = "alinkRdsGroupProd"
rds_legacy_sg_description = "RDS Security Group (prod)"
rds_public_sg_name        = "alinkRdsPublicGroupProd"
rds_public_sg_description = "RDS Public Security Group (prod, unused)"
rds_public_ingress_cidrs  = []

alb_name                  = "alink-alb-prod"
alb_tag_name              = "alink-alb-prod"
target_group_name         = "alink-tg-prod"
target_group_tag_name     = "alink-tg-prod"
listener_tag_name         = "alink-alb-listener-prod"
https_listener_tag_name   = "alink-alb-https-listener-prod"
acm_certificate_arn       = "arn:aws:acm:ap-northeast-2:650177546654:certificate/5cada5c0-3c8e-4e01-8796-36014f087cde"
https_listener_ssl_policy = "ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09"
route53_hosted_zone_id    = "Z08989212MZQ9OYDBC0JK"

ecr_image_tag_mutability = "IMMUTABLE"
ecr_image_scan_on_push   = false
ecr_max_image_count      = 20

ecs_key_pair_name               = "qlink-dev-ssh"
ecs_instance_role_name          = "alinkEcsInstanceRoleProd"
ecs_instance_role_policy_arn    = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
ecs_instance_profile_name       = "alinkEcsInstanceRoleProd"
ecs_cluster_name                = "alink-ecs-cluster-prod"
ecs_cluster_tag_name            = "alink-ecs-cluster-prod"
launch_template_name            = "alink-prod-ecs-lt"
launch_template_image_id        = "ami-08767fd97a1f677c7"
launch_template_instance_type   = "t4g.small"
ecs_instance_tag_name           = "ECS Instance - alink-ecs-cluster-prod"
asg_name                        = "alink-prod-ecs-asg"
asg_min_size                    = 1
asg_max_size                    = 2
asg_desired_capacity            = 1
capacity_provider_name          = "alink-ecs-cp-prod"
additional_capacity_providers   = ["FARGATE", "FARGATE_SPOT"]
task_execution_role_name        = "alinkEcsTaskExecutionRoleProd"
task_execution_role_policy_arn  = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
ecs_task_role_name              = "alinkEcsTaskRoleProd"
ecs_log_group_name              = "/ecs/alink-ecs-task-prod"
ecs_log_group_retention_in_days = 14
ecs_task_family                 = "alink-ecs-task-prod"
ecs_task_cpu                    = "1024"
ecs_task_memory                 = "1536"
ecs_task_container_name         = "alink-app-prod"
ecs_task_container_port         = 8080
ecs_task_port_name              = "ktor-webapp-port"
ecs_task_app_protocol           = "http"
ecs_task_healthcheck_command    = "wget -qO- http://localhost:8080/health >/dev/null || exit 1"
ecs_task_definition_tag_name    = "alink-ecs-task-prod"
ecs_service_name                = "alink-ecs-service-prod"
ecs_service_desired_count       = 1
ecs_service_tag_name            = "alink-ecs-service-prod"
ssm_parameter_prefix            = "/alink/prod/"

db_subnet_group_name                = "alink-prod-db-subnet-group"
db_subnet_group_tag_name            = "alink prod DB subnet group"
rds_identifier                      = "alink-rds-prod"
rds_engine                          = "postgres"
rds_engine_version                  = "18"
rds_instance_class                  = "db.t4g.micro"
rds_allocated_storage               = 20
rds_storage_type                    = "gp3"
rds_iops                            = null
rds_storage_throughput              = null
rds_availability_zone               = "ap-northeast-2a"
rds_publicly_accessible             = false
rds_multi_az                        = false
rds_enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
rds_skip_final_snapshot             = false
rds_instance_tag_name               = "alink-rds-prod"

grafana_domain                        = "grafana.archivelink.app"
grafana_admin_password_parameter_name = "/alink/prod/grafana/admin-password"

monitoring_sg_name                  = "alink-monitoring-sg-prod"
monitoring_sg_description           = "Monitoring instance security group"
monitoring_instance_role_name       = "alink-monitoring-role-prod"
monitoring_instance_profile_name    = "alink-monitoring-profile-prod"
monitoring_instance_type            = "t4g.micro"
monitoring_instance_tag_name        = "alink-monitoring-prod"
monitoring_root_volume_size         = 10
monitoring_data_volume_size         = 20
monitoring_data_volume_tag_name     = "alink-monitoring-data-prod"
monitoring_target_group_name        = "alink-grafana-tg-prod"
monitoring_target_group_tag_name    = "alink-grafana-tg-prod"
monitoring_listener_rule_priority   = 100
monitoring_listener_rule_tag_name   = "grafana-host-rule"
monitoring_scrape_ecs_node_exporter = false
