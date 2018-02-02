/*
 Requires:
  - build-timestamp
  - pipeline-utility-steps
  - nexus-artifact-uploader

*/
pipeline
{
  agent any
  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '7', numToKeepStr: '10'))
    //timestamps()
  }

  environment
  {
    // global server level
    NEXUS_PROTO = "http"
    NEXUS_HOST = "nexus.devopsinabox.perficientdevops.com"
    NEXUS_PORT = "8081"

    // job specific
    NEXUS_CREDSID = 'nexus-admin'
    NEXUS_REPOSITORY = 'RawRepo'
    NEXUS_GROUP = 'Test'

  }

  stages
  {

    stage( "Setup Environment Variables from workspace metadata" ) {
      steps{
        script {
          version='1.0.0'
          APP_ID = 'RawTesting'

          // expecting timestamp to be in yyyyMMdd-HHmmss format
          VERSION = "${version}_${BUILD_TIMESTAMP}"
          VERSION_TAG="${VERSION}"
          ARTIFACT_FILENAME="${APP_ID}-${version}.zip"
          // modify build name to match
          currentBuild.displayName = "${VERSION_TAG}"
        }
        sh "echo \"version: ${VERSION}\""
        sh "echo \"version_tag: ${VERSION_TAG}\""
      }
    }

    stage('Create a file to upload') {
      steps
      {
        sh 'touch test'
        sh "zip ${ARTIFACT_FILENAME} test"
      }
    } // end Build

    // Publish version to Nexus
    stage('Publish to Nexus') {
      steps
      {
        nexusArtifactUploader artifacts:
          [[artifactId: APP_ID, classifier: '', file: "${ARTIFACT_FILENAME}", type: 'zip']],
          credentialsId: NEXUS_CREDSID,
          groupId: NEXUS_GROUP,
          nexusUrl: "$NEXUS_HOST:$NEXUS_PORT",
          nexusVersion: 'nexus3',
          protocol: NEXUS_PROTO,
          repository: NEXUS_REPOSITORY,
          version: VERSION
      }
    }




  } //end stages

} // end pipeline
