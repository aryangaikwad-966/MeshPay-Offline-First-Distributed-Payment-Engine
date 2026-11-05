data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_security_group" "meshpay" {
  name        = "meshpay-${var.environment}"
  description = "MeshPay application ports"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.allowed_ssh_cidrs
  }

  # Dashboard (3001) and API Gateway (8080). Eureka (8761) stays internal.
  dynamic "ingress" {
    for_each = { for p in [3001, 8080] : p => p }
    content {
      description = "MeshPay app port ${ingress.key}"
      from_port   = ingress.key
      to_port     = ingress.key
      protocol    = "tcp"
      cidr_blocks = var.allowed_app_cidrs
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "meshpay-${var.environment}" }
}

resource "aws_key_pair" "meshpay" {
  count      = var.ssh_public_key != "" ? 1 : 0
  key_name   = "meshpay-${var.environment}"
  public_key = var.ssh_public_key
}

locals {
  key_name = var.ssh_public_key != "" ? aws_key_pair.meshpay[0].key_name : var.key_name

  extra_env_lines = join("\n", [for k, v in var.extra_env : "${k}=${v}"])

  user_data = templatefile("${path.module}/cloud-init.sh.tftpl", {
    repo_url       = var.repo_url
    repo_ref       = var.repo_ref
    extra_env_lines = local.extra_env_lines
  })
}

resource "aws_instance" "meshpay" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.meshpay.id]
  key_name               = local.key_name

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  user_data = local.user_data

  tags = { Name = "meshpay-${var.environment}" }
}
