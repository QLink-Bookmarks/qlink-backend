locals {
  rds_jdbc_url = "jdbc:postgresql://${module.rds.address}:${module.rds.port}/${var.rds_db_name}?currentSchema=qlink_local"
}

module "network" {
  source = "../modules/network"

  aws_region = var.aws_region

  vpc_cidr = var.vpc_cidr
  az_a     = var.az_a
  az_c     = var.az_c

  public_subnet_a_cidr  = var.public_subnet_a_cidr
  public_subnet_c_cidr  = var.public_subnet_c_cidr
  private_subnet_a_cidr = var.private_subnet_a_cidr
  private_subnet_c_cidr = var.private_subnet_c_cidr

  vpc_name                 = var.vpc_name
  igw_name                 = var.igw_name
  public_subnet_a_name     = var.public_subnet_a_name
  public_subnet_c_name     = var.public_subnet_c_name
  private_subnet_a_name    = var.private_subnet_a_name
  private_subnet_c_name    = var.private_subnet_c_name
  public_route_table_name  = var.public_route_table_name
  private_route_table_name = var.private_route_table_name
  s3_endpoint_name         = var.s3_endpoint_name
}

module "security" {
  source = "../modules/security"

  vpc_id = module.network.vpc_id

  alb_sg_name               = var.alb_sg_name
  alb_sg_description        = var.alb_sg_description
  app_sg_name               = var.app_sg_name
  app_sg_description        = var.app_sg_description
  rds_app_sg_name           = var.rds_app_sg_name
  rds_app_sg_description    = var.rds_app_sg_description
  rds_legacy_sg_name        = var.rds_legacy_sg_name
  rds_legacy_sg_description = var.rds_legacy_sg_description
  rds_public_sg_name        = var.rds_public_sg_name
  rds_public_sg_description = var.rds_public_sg_description
  rds_public_ingress_cidrs  = var.rds_public_ingress_cidrs
}

module "s3" {
  source = "../modules/s3"

  bucket_name     = var.aws_s3_bucket_name
  bucket_tag_name = var.aws_s3_bucket_name
}

module "cloudfront" {
  source = "../modules/cloudfront"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  domain_name                 = var.images_domain
  hosted_zone_id              = var.route53_hosted_zone_id
  price_class                 = "PriceClass_200"
  bucket_id                   = module.s3.bucket_name
  bucket_arn                  = module.s3.bucket_arn
  bucket_regional_domain_name = module.s3.bucket_regional_domain_name
}

module "ecr" {
  source = "../modules/ecr"

  repository_name      = var.ecr_repository_name
  repository_tag_name  = var.ecr_repository_tag_name
  image_tag_mutability = var.ecr_image_tag_mutability
  image_scan_on_push   = var.ecr_image_scan_on_push
  max_image_count      = var.ecr_max_image_count
}

module "alb" {
  source = "../modules/alb"

  alb_name                  = var.alb_name
  alb_tag_name              = var.alb_tag_name
  target_group_name         = var.target_group_name
  target_group_tag_name     = var.target_group_tag_name
  listener_tag_name         = var.listener_tag_name
  https_listener_tag_name   = var.https_listener_tag_name
  acm_certificate_arn       = var.acm_certificate_arn
  https_listener_ssl_policy = var.https_listener_ssl_policy
  vpc_id                    = module.network.vpc_id
  security_group_id         = module.security.alb_security_group_id
  public_subnet_ids = [
    module.network.public_subnet_a_id,
    module.network.public_subnet_c_id
  ]
}

module "route53_dev" {
  source = "../modules/route53-env"

  hosted_zone_id = var.route53_hosted_zone_id
  alias_a_records = {
    dev_api = {
      name                   = "dev.api.qlinkapps.com"
      dns_name               = "dualstack.${module.alb.alb_dns_name}"
      hosted_zone_id         = module.alb.alb_zone_id
      evaluate_target_health = true
    }
    images = {
      name                   = var.images_domain
      dns_name               = module.cloudfront.distribution_domain_name
      hosted_zone_id         = module.cloudfront.distribution_hosted_zone_id
      evaluate_target_health = false
    }
  }
}

module "ecs" {
  source = "../modules/ecs"

  key_pair_name         = var.ecs_key_pair_name
  app_security_group_id = module.security.app_security_group_id
  asg_subnet_ids        = [module.network.public_subnet_a_id]

  ecs_instance_role_name       = var.ecs_instance_role_name
  ecs_instance_role_policy_arn = var.ecs_instance_role_policy_arn
  ecs_instance_profile_name    = var.ecs_instance_profile_name

  ecs_cluster_name     = var.ecs_cluster_name
  ecs_cluster_tag_name = var.ecs_cluster_tag_name

  launch_template_name          = var.launch_template_name
  launch_template_image_id      = var.launch_template_image_id
  launch_template_instance_type = var.launch_template_instance_type
  ecs_instance_tag_name         = var.ecs_instance_tag_name

  asg_name             = var.asg_name
  asg_min_size         = var.asg_min_size
  asg_max_size         = var.asg_max_size
  asg_desired_capacity = var.asg_desired_capacity

  capacity_provider_name        = var.capacity_provider_name
  additional_capacity_providers = var.additional_capacity_providers

  task_execution_role_name       = var.task_execution_role_name
  task_execution_role_policy_arn = var.task_execution_role_policy_arn
  task_role_name                 = var.ecs_task_role_name
  s3_bucket_arn                  = module.s3.bucket_arn
  s3_access_enabled              = true

  log_group_name              = var.ecs_log_group_name
  log_group_retention_in_days = var.ecs_log_group_retention_in_days

  task_family         = var.ecs_task_family
  task_cpu            = var.ecs_task_cpu
  task_memory         = var.ecs_task_memory
  task_container_name = var.ecs_task_container_name
  task_container_port = var.ecs_task_container_port
  task_port_name      = var.ecs_task_port_name
  task_app_protocol   = var.ecs_task_app_protocol
  task_image          = "${module.ecr.repository_url}:${var.ecs_image_tag}"
  task_environment = {
    DB_JDBC_URL              = local.rds_jdbc_url
    DB_USERNAME              = var.rds_username
    DB_PASSWORD              = var.db_password
    DB_DRIVER_CLASS_NAME     = "org.postgresql.Driver"
    FCM_SERVICE_ACCOUNT_JSON = var.fcm_service_account_json
    EXPO_ACCESS_TOKEN        = var.expo_access_token
    AWS_S3_REGION            = var.aws_region
    AWS_S3_BUCKET            = module.s3.bucket_name
    AWS_S3_ENDPOINT          = ""
    AWS_S3_FORCE_PATH_STYLE  = "false"
    AWS_S3_PUBLIC_BASE_URL   = module.cloudfront.public_base_url
    # No AWS_S3_ACCESS_KEY_ID / SECRET: the app uses the ECS task role via the
    # default credential chain (see module.ecs task_role + s3_bucket_arn policy).
  }
  task_healthcheck_command = var.ecs_task_healthcheck_command
  task_definition_tag_name = var.ecs_task_definition_tag_name

  ecs_service_name          = var.ecs_service_name
  ecs_service_desired_count = var.ecs_service_desired_count
  ecs_service_tag_name      = var.ecs_service_tag_name
  target_group_arn          = module.alb.target_group_arn

  depends_on = [module.alb]
}

module "rds" {
  source = "../modules/rds"

  db_password = var.db_password

  rds_security_group_ids = [
    module.security.rds_app_security_group_id,
    module.security.rds_public_security_group_id
  ]
  subnet_ids = [
    module.network.public_subnet_a_id,
    module.network.public_subnet_c_id
  ]

  db_subnet_group_name     = var.db_subnet_group_name
  db_subnet_group_tag_name = var.db_subnet_group_tag_name

  identifier     = var.rds_identifier
  engine         = var.rds_engine
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class

  allocated_storage  = var.rds_allocated_storage
  storage_type       = var.rds_storage_type
  iops               = var.rds_iops
  storage_throughput = var.rds_storage_throughput

  db_name  = var.rds_db_name
  username = var.rds_username

  availability_zone   = var.rds_availability_zone
  publicly_accessible = var.rds_publicly_accessible
  multi_az            = var.rds_multi_az

  enabled_cloudwatch_logs_exports = var.rds_enabled_cloudwatch_logs_exports

  skip_final_snapshot = var.rds_skip_final_snapshot
  instance_tag_name   = var.rds_instance_tag_name
}
