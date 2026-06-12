terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
      # Default provider serves Route53 + the S3 bucket policy (regional);
      # aws.us_east_1 is required because CloudFront ACM certificates must live in us-east-1.
      configuration_aliases = [aws, aws.us_east_1]
    }
  }
}
