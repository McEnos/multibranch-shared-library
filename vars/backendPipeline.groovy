def getCiPipeline() {
    return {
        node {
            def workspaceDir
            stage('Checkout Code') {
                checkout scm
            }

            stage('Building Docker Image') {
                workspaceDir = sh(script: 'ls -d */|head -n 1', returnStdout: true).trim()
                dir("${env.WORKSPACE}/${workspaceDir}") {
                    sh "echo compiling and  building latest docker image"
                    sh "chmod +x mvnw"
                    sh "./mvnw compile jib:build -DsendCredentialsOverHttp=true"
                }
            }
        }

    }
}
