pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Terraform Security Scan') {
      steps {
        sh 'trivy config terraform/'
      }
    }

    stage('Terraform Plan') {
      steps {
        sh 'terraform -chdir=terraform init'
        sh 'terraform -chdir=terraform plan'
      }
    }
  }
}
