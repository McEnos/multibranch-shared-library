// In your shared library vars/dynamicJobs.groovy

def call(String submodule = null) {
    if (submodule) {
        buildSubmodule(submodule)
    } else {
        echo "No submodule specified. Skipping submodule build."
    }
}

def buildSubmodule(submodule) {
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
    }
}
