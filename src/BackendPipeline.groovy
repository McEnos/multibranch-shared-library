def getCiPipeline(){
    pipeline{
        agent any
        stages{
             stage('Setup') {
                steps {
                    script {
                        echo "Running setups...."
                    }
                }
                stage("Unit test"){
                    steps{
                       script {
                        echo "Running Unit tests..."
                    } 
                    }
                }
        }
    }
}