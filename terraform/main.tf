terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state is recommended for team use; local state is fine for a demo.
  # backend "s3" {
  #   bucket         = "meshpay-terraform-state"
  #   key            = "meshpay/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "meshpay-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "meshpay"
      ManagedBy   = "terraform"
      Environment = var.environment
    }
  }
}
