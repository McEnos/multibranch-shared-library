// In your shared library vars/dynamicJobs.groovy

def call(String submodule = null) {
    if (submodule) {
        createJob(submodule)
    } else {
        setupMultiBranchPipeline()
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
    }
}

def setupMultiBranchPipeline() {
    properties([pipelineTriggers([
            [$class: 'GitHubPushTrigger'],
    ])])

    triggers {
        githubPush()
    }

    branches {
        branchFilter('master')
        branchFilter('*')
    }

    configure { project ->
        // Customize multi-branch pipeline configuration if needed
    }
}
