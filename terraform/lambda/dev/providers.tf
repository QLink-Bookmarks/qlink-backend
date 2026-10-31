terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.19.0"
    }
  }

  required_version = ">= 1.14"

  backend "s3" {
    bucket       = "qlink-tfstate"
    key          = "lambda/dev/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
  }
}

provider "aws" {
  region = "ap-northeast-2"
}
