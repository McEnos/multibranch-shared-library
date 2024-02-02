def getCiPipeline() {
    return {
        node {
            def workspaceDir
            def imageName
            env.JAVA_HOME = tool name: 'java21', type: 'jdk'
            stage('Checkout Code') {
                checkout scm
            }
            stage('Building Docker Image') {
                workspaceDir = sh(script: 'ls -d */|head -n 1', returnStdout: true).trim()
                dir("${env.WORKSPACE}/${workspaceDir}") {
                    withCredentials([usernamePassword(credentialsId: 'registry-login', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        sh "echo \$PASSWORD | docker login -u \$USERNAME --password-stdin localhost:8050"
                        sh "chmod +x mvnw"
                        sh "./mvnw jib:build"
                    }
                }
            }
            stage("Delete image after generation"){
                sh "echo deleting ${imageName} image"
                sh "docker rmi ${imageName}"
            }


            stage('Quality Gate') {
                workspaceDir = sh(script: 'ls -d */|head -n 1', returnStdout: true).trim()
                dir("${env.WORKSPACE}/${workspaceDir}") {
                    sh "echo checking  quality in ${workspaceDir}"
                }
            }
        }
        post {
            always {
                script {
                    sh "docker logout"
                }
            }
        }

    }
}
