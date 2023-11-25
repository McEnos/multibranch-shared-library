def getCiPipeline() {
    return {
        node {
            def workspaceDir
            stage('Checkout Code') {
                checkout scm
            }

            stage('Unit Test') {
                workspaceDir = sh(script: 'ls -d */|head -n 1', returnStdout: true).trim()
                dir("${env.WORKSPACE}/${workspaceDir}") {
                    sh "echo running uni tests in ${workspaceDir}"
                }
            }

            stage('Quality Gate') {
                workspaceDir = sh(script: 'ls -d */|head -n 1', returnStdout: true).trim()
                dir("${env.WORKSPACE}/${workspaceDir}") {
                    sh "echo checking  quality in ${workspaceDir}"

                }
            }

            stage('Set Image Version') {
                workspaceDir = sh(script: 'ls -d */|head -n 1', returnStdout: true).trim()
                dir("${env.WORKSPACE}/${workspaceDir}") {
                    sh "echo setting image version"
                }
            }

            stage('Set Image Name') {
                workspaceDir = sh(script: 'ls -d */|head -n 1', returnStdout: true).trim()
                dir("${env.WORKSPACE}/${workspaceDir}") {
                    sh "echo setting  docker image name in ${workspaceDir}"
                }
            }

            stage('Build Docker Image') {
                workspaceDir = sh(script: 'ls -d */|head -n 1', returnStdout: true).trim()
                dir("${env.WORKSPACE}/${workspaceDir}") {
                    sh "echo Building docker image in ${workspaceDir}"
                }
            }



        }

    }
}
