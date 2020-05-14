#!groovy

//loading https://code.sbb.ch/projects/KD_ESTA/repos/pipeline-helper
@Library('pipeline-helper') _

pipeline {
    agent { label 'java' }
    tools {
        maven 'Apache Maven 3.6'
        jdk 'OpenJDK 11 64-Bit'
    }
    stages {
        stage('When on develop, Deploy Snapshot and analyze for sonar') {
            when {
                branch 'develop'
            }
            steps {
                withSonarQubeEnv('Sonar SBB CFF FFS AG') {
                    sh 'mvn -B clean deploy'
                }
            }
        }
        stage('Unit Tests') {
            steps {
                sh 'mvn -B clean compile test'
                junit '**/target/surefire-reports/*.xml'
            }
        }
        stage('When on master, Release: Adapt poms, tag, deploy and push.') {
            when {
                branch 'master'
            }
            steps {
                bin_releaseMvn(targetRepo: "simba.mvn")
            }
        }
    }
}