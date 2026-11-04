// Loaded from controller-owned JCasC, never from the PR's Jenkinsfile.
pipeline {
    agent any
    options {
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
        timeout(time: 25, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    parameters {
        string(name: 'PR_NUMBER', defaultValue: '', description: 'Team pull request number targeting main')
    }
    environment {
        JWT_SECRET = 'jenkins-pr-fixture-signing-key-at-least-32-bytes'
        CLOUDINARY_CLOUD_NAME = 'ci-fixture'
        CLOUDINARY_API_KEY = 'ci-fixture'
        CLOUDINARY_API_SECRET = 'ci-fixture'
        PUPPETEER_SKIP_DOWNLOAD = 'true'
    }
    stages {
        stage('Validate PR') {
            steps {
                deleteDir()
                sh 'node /casc/validate-pr.mjs'
                script {
                    env.PR_HEAD_SHA = readFile('pr-head.sha').trim()
                    currentBuild.displayName = "#${env.BUILD_NUMBER} PR ${params.PR_NUMBER}"
                    currentBuild.description = "PR #${params.PR_NUMBER}, head ${env.PR_HEAD_SHA}"
                }
            }
        }
        stage('Checkout PR with main') {
            steps {
                script {
                    checkout([$class: 'GitSCM',
                        branches: [[name: "refs/remotes/origin/pr-${params.PR_NUMBER}"]],
                        userRemoteConfigs: [[url: 'https://github.com/AyyoubMh48/buy-02.git',
                            refspec: "+refs/pull/${params.PR_NUMBER}/head:refs/remotes/origin/pr-${params.PR_NUMBER} +refs/heads/main:refs/remotes/origin/main"]],
                        extensions: [[$class: 'CloneOption', honorRefspec: true, noTags: true]]])
                }
                sh '''
                    set -eu
                    test "$(git rev-parse HEAD)" = "$PR_HEAD_SHA"
                    git -c user.name='Jenkins CI' -c user.email='ci@localhost' merge --no-edit origin/main
                    git rev-parse HEAD > validated-merge.sha
                    docker version
                '''
            }
        }
        stage('Backend build and tests') {
            steps {
                sh '''
                    set -eu
                    for service in product-service user-service media-service order-service gateway discovery; do
                        (cd "$service" && bash ./mvnw -B clean verify)
                        test -s "$service/target/site/jacoco/jacoco.xml"
                    done
                '''
            }
        }
        stage('Frontend build and tests') {
            steps {
                dir('frontend') {
                    sh 'npm ci --no-audit --no-fund'
                    sh 'CI=true npm run test:coverage'
                    sh 'npm run build'
                }
            }
        }
    }
    post {
        always {
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'validated-merge.sha, **/target/site/jacoco/**, frontend/coverage/frontend/**', allowEmptyArchive: true
        }
    }
}
