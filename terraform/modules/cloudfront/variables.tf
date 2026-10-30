variable "domain_name" {
  description = "Custom domain served by CloudFront (e.g. images.qlinkapps.com)"
  type        = string
}

variable "hosted_zone_id" {
  description = "Route53 hosted zone id for DNS validation + alias records"
  type        = string
}

variable "price_class" {
  type    = string
  default = "PriceClass_200"
}

variable "bucket_id" {
  type = string
}

variable "bucket_arn" {
  type = string
}

variable "bucket_regional_domain_name" {
  type = string
}

variable "additional_s3_origins" {
  description = "Additional private S3 origins routed by URL path"
  type = map(object({
    path_pattern                = string
    bucket_id                   = string
    bucket_arn                  = string
    bucket_regional_domain_name = string
  }))
  default = {}
}
