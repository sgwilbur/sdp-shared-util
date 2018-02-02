import java.util.regex.Pattern
import java.util.regex.Matcher

def version_ts_separator = '_'
Pattern version_pattern = ~/^(\d\.\d\.\d)${version_ts_separator}(\d{8}-\d{6})$/
//version_pattern        = ~/^(\d\.\d\.\d)(${version_ts_separator})(\d{8})(.*)(\d{6})$/

def nexus_versions = ['1.0.0_20171127-234252', '1.0.0_20171128-001713', '1.0.0_20171128-002200', '1.0.0_20171128-002700', '1.0.0_20171128-003200' ]

println nexus_versions

// validate format of versions is what we expect
println "Testing ${nexus_versions[0]} match against the expected pattern  [${version_pattern}]"
Matcher m = version_pattern.matcher( nexus_versions[0] )
if ( ! m.matches() ){
  println "  Error does not match the expected pattern."
  return
}else{
  println "  Matched!"
  println "  all : ${m.group(0)}" // all
  println " V.M.R: ${m.group(1)}" // V.M.R
  println "  date: ${m.group(2)}" // date-time

}
