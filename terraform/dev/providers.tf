terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.92"
    }
  }

  required_version = ">= 1.14"

  backend "s3" {
    bucket       = "qlink-tfstate"
    key          = "dev/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region
}

# CloudFront ACM certificates must be requested in us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}
