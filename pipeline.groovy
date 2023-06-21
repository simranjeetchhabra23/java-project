pipeline {
        agent any

  environment {
    SONARQUBE_URL = 'http://18.219.128.164:9000'
    SONARQUBE_AUTH_TOKEN = credentials('sonar-project-token')
    SONARQUBE_PROJECT_KEY = 'nodejs'
    SNYK_TOKEN = credentials('snyk-token')
    CONFIG = credentials('miniconfig')
    DIR = "$WORKSPACE"
    COSIGN_PASSWORD = credentials('cosign-password')

  }

  stages {
      
 stage('Clone') {
      steps {
        // Get code from a GitHub repository
        git url: 'https://github.com/simranjeetchhabra23/java-project.git', branch: 'main'
      }

    }

 stage('Trufflehog Scan') {
  steps {
    sh 'docker run --rm -v "$PWD:/app" trufflesecurity/trufflehog:latest git https://github.com/simranjeetchhabra23/java-project.git --json --max_depth 1 > trufflehog_report.json'
    script {
      def report = readFile 'trufflehog_report.json'
      if (report.contains('DetectorType')) {
          sh 'echo report '
        currentBuild.result = 'FAILURE'
        error "Trufflehog detected potential security issues"
      } else {
        sh 'echo "Trufflehog not detected potential security issues" > trufflehog_report.json'
      }
    }
  }
  
post {
  always {
    echo 'Performing post-build actions...'
    archiveArtifacts artifacts: 'trufflehog_report.json', onlyIfSuccessful: false
  }
}

}

 stage('snyk scan dependencies for vulnerabilities '){
        steps { 
                sh 'npm install'
                sh 'snyk auth $SNYK_TOKEN'
                sh 'snyk test --print-deps --file=package.json > snyk_vulnerabilities_report.html'
                sh 'snyk monitor'
             }
             
             post {
  always {
    echo 'Performing post-build actions...'
    archiveArtifacts artifacts: 'snyk_vulnerabilities_report.html', onlyIfSuccessful: false
  }
}
        
    }
 stage('Test') {
      steps {
        sh 'npm test -- helloWorld.test.js'
      }

    }
    stage('Dockerfile scanning'){
        steps { 
                sh '/usr/local/bin/checkov -d . --framework dockerfile > dockerfile_vulnerabilities_report.txt || true'
             }
             
             post {
  always {
    echo 'Performing post-build actions...'
    sh 'cat dockerfile_vulnerabilities_report.txt'
    archiveArtifacts artifacts: 'dockerfile_vulnerabilities_report.txt', onlyIfSuccessful: false
  }
}
        
    }
    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv('sonarqube') { // replace with your SonarQube server name in Jenkins

        //   sh('mvn sonar:sonar -Dsonar.projectKey=$SONARQUBE_PROJECT_KEY -Dsonar.host.url=$SONARQUBE_URL -Dsonar.token=$SONARQUBE_AUTH_TOKEN -Dsonar.language=js')
        sh('sonar-scanner -Dsonar.projectKey=$SONARQUBE_PROJECT_KEY -Dsonar.host.url=$SONARQUBE_URL -Dsonar.token=$SONARQUBE_AUTH_TOKEN -Dsonar.language=js -Dsonar.projectVersion=1.0 -Dsonar.sources=src/helloworld.js,Dockerfile -Dsonar.coverage.exclusions=node_modules/*,coverage/* -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info -Dsonar.issuesreport.html.enable=true' )
        }
      }
    }
    
     stage('Wait for quality gate') {
  steps {
    timeout(time: 1, unit: 'HOURS') {
      script {
        def qg = waitForQualityGate()
        if (qg.status != 'OK') {
          error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
      }
    }
  }
}

    stage('Build Image') {
      steps {
        sh "docker build -f Dockerfile -t simranjeetchhabra/springboot-image:2.0.0 ."
      }
    }
    stage('Scanning Image') {
      steps {
        sysdigImageScan engineCredentialsId: 'sysdig-token', imageName: "simranjeetchhabra/springboot-image:2.0.0", bailOnFail: false
       } 
    }
    stage('Push') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'docker', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
          sh "docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}"
          sh "docker push simranjeetchhabra/springboot-image:2.0.0"
        }
      }
    }
    
   stage('Sign Docker image') {
            steps {
                sh "echo 'y' | cosign sign --key /var/lib/jenkins/cosign.key simranjeetchhabra/springboot-image:2.0.0"
            }
        }
        
        
    stage('helm template') {
  steps {
    sh 'helm template my-app/ --namespace default > manifests.yaml'
  }
  
}
    stage('Evaluate Kubernetes manifests') {
      steps {
        sh 'kube-score score manifests.yaml > kube-score_report.txt || true'
      }
      post {
  always {
    echo 'Performing post-build actions...'
    sh 'cat kube-score_report.txt'
    archiveArtifacts artifacts: 'kube-score_report.txt', onlyIfSuccessful: false
  }
}
    }
    stage('Run Kube-bench') {
      steps {
        sh '''
          wget https://raw.githubusercontent.com/aquasecurity/kube-bench/master/job.yaml -O kube-bench.yaml
          kubectl apply --kubeconfig=$CONFIG -f kube-bench.yaml
          kubectl wait --kubeconfig=$CONFIG --for=condition=complete --timeout=600s job/kube-bench
          kubectl logs --kubeconfig=$CONFIG job/kube-bench > kube_bench_report.txt
          kubectl delete --kubeconfig=$CONFIG job/kube-bench
          '''
          
      }
      post {
  always {
    echo 'Performing post-build actions...'
    archiveArtifacts artifacts: 'kube_bench_report.txt', onlyIfSuccessful: false
  }
}
    }
    
    stage('Deploy') {
            steps {
                sh '''
                    helm upgrade --kubeconfig=$CONFIG -i my-app ./my-app
                '''
            }
        }
    
    stage('Somke') {
      steps {
        sh 'npm test -- smoke.test.js'
      }

    }
    stage('Zap') {
            steps {
                sh '/usr/local/bin/zap/zap.sh -quickurl http://192.168.49.2:30000 -cmd  -quickprogress -quickout ${WORKSPACE}/report.html -port 8090'
            }
            
              post {
  always {
    echo 'Performing post-build actions...'
    archiveArtifacts artifacts: 'report.html', onlyIfSuccessful: false
  }
}
        }
   


  }
  post { 
        always { 
            cleanWs()
        }
    }
}
