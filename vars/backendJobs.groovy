def call() {
    // Change the directory to backend-services
    dir('backend-services') {
        // Get a list of submodules
        def submodules = sh(script: 'find . -name "Jenkinsfile" -exec dirname {} \\; | xargs -n1 basename', returnStdout: true).trim().split('\n')
        // Dynamically create Jenkins jobs for each submodule
        for (submodule in submodules) {
            createJob(submodule)
        }
    }
}
def createJob(submodule) {
    pipeline {
        agent any
        triggers {
            githubPush()
        }
        stages {
            stage('Build') {
                steps {
                    echo "Building ${submodule}"
                    // Trigger the submodule Jenkinsfile or build process
                    // Example: build job: "${submodule}/Jenkinsfile" or mvn -f ${submodule} clean install
                }
            }
            // Add more stages or jobs for each submodule as needed
        }
         stage('Test') {
                steps {
                    echo "Testing $projectName"
                }
            }
            stage('Deploy') {
                steps {
                    echo "Deploying $projectName"
                }
            }
    }
}