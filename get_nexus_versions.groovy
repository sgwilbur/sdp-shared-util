/* Simple script to create an active choice list
 *
 * N.B
 *
 *   See the swagger api for json format http://nexus-server/swagger-ui/
 *
 * https://support.cloudbees.com/hc/en-us/articles/217958928-How-to-populate-Choice-Parameter-with-artifact-information-using-Nexus-REST-API-
 * https://stackoverflow.com/questions/41549766/jenkins-active-choices-parameter-groovy-to-build-a-list-based-on-rest-respond
 *
 * Make SSL to self-signed certificates work
 * https://stackoverflow.com/questions/2793150/using-java-net-urlconnection-to-fire-and-handle-http-requests?rq=1
 * https://stackoverflow.com/questions/3242335/how-to-use-ssl-with-a-self-signed-certificate-in-groovy
 *
 * Requires adding these to whitelist: Manage Jenkins > In Process Script Approvals:
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

def nullTrustManager = [getAcceptedIssuers: { null }, checkClientTrusted: { chain, authType -> }, checkServerTrusted: { chain, authType -> }]
def nullHostnameVerifier = [verify: { hostname, session -> true }]
def sc = SSLContext.getInstance("SSL")
sc.init(null, [nullTrustManager as X509TrustManager] as TrustManager[], null )
HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
HttpsURLConnection.setDefaultHostnameVerifier(nullHostnameVerifier as HostnameVerifier)

def nexus_url
def nexus_repository
def nexus_artifact
def version_ts_separator
def date_format_str

// AAA testing
// version_ts_separator = '-'
// nexus_url = 'https://nexusrepo.ace.aaaclubnet.com'
// nexus_repository = NEXUS_REPOSITORY
// nexus_artifact = NEXUS_ARTIFACT
//
// if (NEXUS_REPOSITORY == '' || NEXUS_ARTIFACT == '' ) {
//   println "No Repository Selected"
//   return nexus_versions
// }

// DevOps in a Box testing
version_ts_separator = '_'
nexus_url = 'http://nexus.devopsinabox.perficientdevops.com:8081'
// nexus_repository = 'petsonline'
// nexus_artifact = 'jpetstore'
nexus_repository = 'RawRepo'
nexus_artifact = 'RawTesting'


date_format_str = 'yyyyMMdd-HHmmss'
// V.M.R<version_ts_separator><date_format_str>
SimpleDateFormat sdf = new SimpleDateFormat( date_format_str )
Pattern version_pattern = ~/^(\d\.\d\.\d)${version_ts_separator}(\d{8}-\d{6})$/

def nexus_versions = []



def has_more_results = true
def continuation = ''
def repo_type = 'maven'

while( has_more_results) {
  //println ">>> Starting a new while loop"
  has_more_results = false

  // Example for Nexus3
  def nexus_component_query_url = "${nexus_url}/service/siesta/rest/beta/components?${continuation}repositoryId=${nexus_repository}"
  def conn = new URL( nexus_component_query_url ).openConnection() as HttpURLConnection
  conn.setRequestProperty( 'User-Agent', 'groovy-2.4.12' )
  conn.setRequestProperty('Accept', 'application/json')

  //println "Calling ${nexus_component_query_url}"

  // reading the response triggers the request
  if ( conn.responseCode != 200 ){
    println " ${conn.responseCode} : ${conn.inputStream.text} "
    return
  }

  def json = new JsonSlurper().parseText(conn.content.text)

  if( ! json.items ){
    println " >>> No results"
    break
  }

  if ( repo_type == 'raw' || json.items[0].format == 'raw' ){
    repo_type = 'raw'
  }

  // println " >>> json.items: ${json.items}"

  // Need to explicitly cast from GString to String so it shows in drop down properly, otherwise all options just shows as Object[...]
  // https://issues.jenkins-ci.org/browse/JENKINS-27916
  if ( repo_type == 'raw' ){
  // TODO: Just make them look the same...
    nexus_versions.addAll( json.items.findAll({ it.name =~ '.zip$' && it.name =~ nexus_artifact }).collect({ ( it.name.split('/')[2] ) as String }) )
   } else { // assume maven
    nexus_versions.addAll( json.items.findAll({ it.name =~ nexus_artifact }).collect({ "${it.version}" as String }) )
   }

  // token is null on final page of results
  if( json.continuationToken ){
    //println "Found a continuation token: ${json.continuationToken}"
    continuation = "continuationToken=${json.continuationToken}&"
    has_more_results = true
  }
  //println "<<< Ending while loop"
} // end while

//println nexus_versions

// validate format of versions is what we expect
println "Testing if ${nexus_versions.first()} matches expected pattern [${version_pattern}]"
if ( ! version_pattern.matcher( nexus_versions.first() ).matches() )
{
  println "Error ${ nexus_versions.first() } does not match the expected pattern [${version_pattern}]"
  println "Returning unsorted list"
  return nexus_versions
}

// Custom sort, based on the timestamp in the version
def sorted_versions = nexus_versions.sort{ a,b ->
    //new Timestamp( sdf.parse( b.split( version_ts_separator,2)[1] ).getTime() ) <=> new Timestamp( sdf.parse( a.split(version_ts_separator,2)[1] ).getTime() )
    sdf.parse( b.split( version_ts_separator,2)[1] ) <=> sdf.parse( a.split(version_ts_separator,2)[1] )
  }
println "Sorted: " + sorted_versions

return sorted_versions
