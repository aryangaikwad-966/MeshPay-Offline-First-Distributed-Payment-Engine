output "public_ip" {
  description = "Public IP of the SyncPay VM."
  value       = aws_instance.syncpay.public_ip
}

output "ssh_command" {
  description = "SSH into the VM to manage the stack."
  value       = "ssh -i <your-key.pem> ubuntu@${aws_instance.syncpay.public_ip}"
}

output "dashboard_url" {
  description = "SyncPay dashboard."
  value       = "http://${aws_instance.syncpay.public_ip}:3001"
}

output "gateway_url" {
  description = "API gateway base URL."
  value       = "http://${aws_instance.syncpay.public_ip}:8080"
}
