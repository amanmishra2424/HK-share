resource "aws_security_group" "insecure_sg" {
  name        = "insecure-sg"
  description = "Insecure security group for DevSecOps demo"

  ingress {
    description = "Allow SSH from anywhere (INSECURE)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"] # ❌ INTENTIONAL VULNERABILITY
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
