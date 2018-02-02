#!/usr/bin/groovy
/* Simple script to create a list of versions available from a specific Nexus
 * repository.
 *
 * N.B
 *   See the swagger api for json format http://nexus-server/swagger-ui/
 *
 * Make SSL to self-signed certificates work
 * https://stackoverflow.com/questions/2793150/using-java-net-urlconnection-to-fire-and-handle-http-requests?rq=1
 * https://stackoverflow.com/questions/3242335/how-to-use-ssl-with-a-self-signed-certificate-in-groovy
 *
 * If running in sandbox, requires adding these to whitelist: Manage Jenkins > In Process Script Approvals:
 *
 method groovy.lang.Script println java.lang.Object
 method java.net.HttpURLConnection getResponseCode
 method java.net.URL openConnection
 method java.net.URLConnection getContent
 method java.net.URLConnection getInputStream
 method java.net.URLConnection setRequestProperty java.lang.String java.lang.String
 method java.text.DateFormat parse java.lang.String
 method java.util.Date getTime
 new java.sql.Timestamp long
 staticMethod javax.net.ssl.SSLContext getInstance java.lang.String

 */
import groovy.json.JsonSlurper
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.regex.Pattern
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

def call(String serverUrl, String nexusRepo, String nexusGroup, String nexusArtifactId, String versionPattern)
{
  // Helper code for trusting self-signed certs
  def nullTrustManager = [getAcceptedIssuers: { null }, checkClientTrusted: { chain, authType -> }, checkServerTrusted: { chain, authType -> }]
  def nullHostnameVerifier = [verify: { hostname, session -> true }]
  def sc = SSLContext.getInstance("SSL")
  sc.init(null, [nullTrustManager as X509TrustManager] as TrustManager[], null )
  HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
  HttpsURLConnection.setDefaultHostnameVerifier(nullHostnameVerifier as HostnameVerifier)

  def version_ts_separator = '_'
  def date_format_str = 'yyyyMMdd-HHmmss'

  // V.M.R<version_ts_separator><date_format_str>
  SimpleDateFormat sdf = new SimpleDateFormat( date_format_str )
  Pattern version_pattern = ~/^(\d\.\d\.\d)${version_ts_separator}(\d{8}-\d{6})$/
  Pattern filterPattern = ~/^${versionPattern}$/
  def nexus_versions = []

  println "Calling shared library getNexusVersions"
  println "      serverUrl : ${serverUrl}"
  println "      nexusRepo : ${nexusRepo}"
  println "     nexusGroup : ${nexusGroup}"
  println "nexusArtifactId : ${nexusArtifactId}"
  println " versionPattern : ${versionPattern} "
  println " Using date format: ${date_format_str}"
  println " Timestamp Pattern: ${version_pattern}"
  println " Filter pattern   : ${filterPattern}"

  def has_more_results = true
  def continuation = ''

  while( has_more_results) {
    has_more_results = false

    // N.B. Valid for Nexus3 ONLY
    def nexus_component_query_url = "${serverUrl}/service/siesta/rest/beta/search?${continuation}repositoryId=${nexusRepo}?q=${nexusGroup}?q=${nexusArtifactId}"
    def conn = new URL( nexus_component_query_url ).openConnection() as HttpURLConnection
    conn.setRequestProperty( 'User-Agent', 'groovy-2.4.12' )
    conn.setRequestProperty('Accept', 'application/json')

    // reading the response triggers the request
    if ( conn.responseCode != 200 ){
      println "ERROR: Connection to Nexus failed - ${conn.responseCode} : ${conn.inputStream.text} "
      return
    }

    def json = new JsonSlurper().parseText(conn.content.text)

    if( ! json.items ){
      println " >>> No results"
      break
    }

    // Need to explicitly cast from GString to String during collect so it shows in drop down properly, otherwise all options just shows as Object[...]
    // https://issues.jenkins-ci.org/browse/JENKINS-27916
    switch( json.items[0].format ) {
      case 'raw':
        // in a raw repo there is no version attribute but the name format is <group>/<artifactid>/<version>/<filename> so we pull it out of there
        nexus_versions.addAll( json.items.findAll({ it.name =~ '.zip$' && it.name =~ nexusArtifactId }).collect({ ( it.name.split('/')[2] ) as String }) );
        break;
      case 'maven':
        nexus_versions.addAll( json.items.findAll({ it.name =~ nexusArtifactId }).collect({ "${it.version}" as String }) );
        break;
      default:
        println "ERROR: unknown repo type: ${repo_type}"
        return []
    }

    // token is null on final page of results, otherwise set this so the next loop with get next page
    if( json.continuationToken ){
      continuation = "continuationToken=${json.continuationToken}&"
      has_more_results = true
    }
  } // end while

  println "Available Nexus versions: " + nexus_versions

  // After collecting all possible versions, we drop all non-matching versions.
  // TODO: Include this in the original collection steps above
  def filtered_versions = nexus_versions.findAll({ filterPattern.matcher( it ).matches() })
  println "Available after applying filter: " + filtered_versions

  // since this is a simple object it can be just sorted as a string, only caveat
  // is that we need to reverse the list afterwards as we cannot control sort order
  if ( filtered_versions ){
    def sorted_versions = filtered_versions.sort().reverse()
    println "Sorted: " + sorted_versions
    return sorted_versions
  }else{
    println "WARN: No versions being returned returned"
    return []
  }

}



// Simulate how this is called in shared library
// def call(String serverUrl, String nexusRepo, String nexusGroup, String nexusArtifactId, String versionPattern)
def serverUrl = 'http://localhost:8081'
def nexusRepo = 'eBiz-RC'
def nexusGroup = 'OnlineBind'
def nexusArtifactId = 'Qot.Njs.App.OLB'
def versionPattern = '1.0.0_20171214.*'
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
call( serverUrl, nexusRepo, nexusGroup, nexusArtifactId, versionPattern )
