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
  #   bucket         = "syncpay-terraform-state"
  #   key            = "syncpay/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "syncpay-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "syncpay"
      ManagedBy   = "terraform"
      Environment = var.environment
    }
  }
}
