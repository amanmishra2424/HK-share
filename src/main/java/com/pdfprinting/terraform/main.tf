resource "aws_security_group" "insecure_sg" {
  name = "insecure-sg"

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]   

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
   cidr_blocks = ["10.0.0.0/16"]
  }
}
