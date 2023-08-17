pipeline {
    agent any

    stages {
        stage('Build') {
            agent {
                docker { 
                    image 'maven:3-openjdk-8'
                    reuseNode true
                }
            }
            steps {
                sh "mvn clean package"
            }

            post {
                success {
                    junit '**/target/surefire-reports/TEST-*.xml'
                    archiveArtifacts 'target/*.jar'
                }
            }
        }
    }
}
