output "public_ip" {
  description = "Public IP of the MeshPay VM."
  value       = aws_instance.meshpay.public_ip
}

output "ssh_command" {
  description = "SSH into the VM to manage the stack."
  value       = "ssh -i <your-key.pem> ubuntu@${aws_instance.meshpay.public_ip}"
}

output "dashboard_url" {
  description = "MeshPay dashboard."
  value       = "http://${aws_instance.meshpay.public_ip}:3001"
}

output "gateway_url" {
  description = "API gateway base URL."
  value       = "http://${aws_instance.meshpay.public_ip}:8080"
}
