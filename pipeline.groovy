pipeline {
        agent any

  environment {
    SONARQUBE_URL = 'http://172.17.0.1:9000'
    SONARQUBE_AUTH_TOKEN = credentials('sonarqube-auth-token')
    SONARQUBE_PROJECT_KEY = 'Roche'
    SNYK_TOKEN = credentials('snyk-token')
    DIR = "$WORKSPACE"
    COSIGN_PASSWORD = credentials('cosign-password')

  }

  stages {
      
 stage('Clone') {
      steps {
        // Get code from a GitHub repository
        git url: 'https://github.com/rajatvarade-globant/my-java-project.git', branch: 'master'
      }

    }

 stage('Trufflehog Scan') {
  steps {
    sh 'docker run --rm -v "$PWD:/app" trufflesecurity/trufflehog:latest git https://github.com/rajatvarade-globant/my-java-project.git --json --max_depth 1 > trufflehog_report.json'
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
                sh 'snyk test --file=package.json > snyk_vulnerabilities_report.txt'
                sh 'snyk monitor'
             }
             
             post {
  always {
    echo 'Performing post-build actions...'
    archiveArtifacts artifacts: 'snyk_vulnerabilities_report.txt', onlyIfSuccessful: false
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
                sh 'checkov -d . --framework dockerfile > dockerfile_vulnerabilities_report.txt || true'
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
        withSonarQubeEnv('SonarQube') { // replace with your SonarQube server name in Jenkins

        //   sh('mvn sonar:sonar -Dsonar.projectKey=$SONARQUBE_PROJECT_KEY -Dsonar.host.url=$SONARQUBE_URL -Dsonar.token=$SONARQUBE_AUTH_TOKEN -Dsonar.language=js')
        sh('/usr/bin/sonar-scanner -Dsonar.projectKey=$SONARQUBE_PROJECT_KEY -Dsonar.host.url=$SONARQUBE_URL -Dsonar.token=$SONARQUBE_AUTH_TOKEN -Dsonar.language=js -Dsonar.projectVersion=1.0 -Dsonar.sources=src/helloworld.js,Dockerfile -Dsonar.coverage.exclusions=node_modules/*,coverage/* -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info -Dsonar.issuesreport.html.enable=true' )
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
        sh "docker build -f Dockerfile -t rajatvarade/test ."
      }
    }
    stage('Scanning Image') {
      steps {
        sysdigImageScan engineCredentialsId: 'sysdig-secure-api-credentials', imageName: "rajatvarade/test"
      }
    }
    stage('Push') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'docker', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
          sh "docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD}"
          sh "docker push rajatvarade/test:latest"
        }
      }
    }
    
   stage('Sign Docker image') {
            steps {
                sh "echo 'y' | cosign sign --key /var/jenkins_home/cosign.key rajatvarade/test:latest"
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
          kubectl apply -f kube-bench.yaml
          kubectl wait --for=condition=complete --timeout=600s job/kube-bench
          kubectl logs job/kube-bench > kube_bench_report.txt
          kubectl delete job/kube-bench
        '''
    //     script {
    //   def report = readFile 'kube_bench_report.txt'
    //   if (report.contains('FAIL')) {
    //     currentBuild.result = 'FAILURE'
    //     error "Kube Bench detected potential security issues"
    //   } else {
    //     sh 'echo "Kube Bench not detected potential security issues" > kube_bench_report.txt'
    //   }
    // }
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
                    helm upgrade -i my-app ./my-app
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
                sh '/usr/local/bin/zap/zap.sh -quickurl http://172.17.0.3:30000 -cmd  -quickprogress -quickout ${WORKSPACE}/report.html'
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