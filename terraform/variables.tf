variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name used for tagging and naming."
  type        = string
  default     = "dev"
}

variable "instance_type" {
  description = "EC2 instance type. The Compose stack builds four Java images, so 8GB+ RAM is recommended."
  type        = string
  default     = "t3.large"
}

variable "repo_url" {
  description = "Git URL MeshPay is cloned from on the VM (https or ssh)."
  type        = string
}

variable "repo_ref" {
  description = "Git ref (branch/tag/SHA) to check out."
  type        = string
  default     = "main"
}

variable "ssh_public_key" {
  description = "SSH public key material used to create a key pair for VM access. Leave empty to use an existing key via key_name."
  type        = string
  default     = ""
}

variable "key_name" {
  description = "Name of an existing EC2 key pair to use when ssh_public_key is empty."
  type        = string
  default     = ""
}

variable "allowed_ssh_cidrs" {
  description = "CIDR blocks allowed to SSH into the VM."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "allowed_app_cidrs" {
  description = "CIDR blocks allowed to reach the dashboard and API gateway ports."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "extra_env" {
  description = "Additional KEY=value lines appended to the VM's .env file (e.g. MESHPAY_PACKET_* keys, JWT settings)."
  type        = map(string)
  default     = {}
}
