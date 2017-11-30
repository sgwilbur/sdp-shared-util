#!/usr/bin/env python
## REST API is still beta
## See /swagger-ui/ on your server

## Reference:
## * http://www.sonatype.org/nexus/2015/07/13/sonatype-nexus-delete-artifacts-based-on-a-selection/
## * https://support.sonatype.com/hc/en-us/articles/213465308-Can-I-delete-releases-from-Nexus-after-they-have-been-published-
## * https://parkerwy.wordpress.com/2011/07/10/curl-safely-delete-artifacts-from-nexus/

import requests
import json
import sys
import re
import logging
import datetime

### globals vars

nexus_base_url = 'http://nexus.devopsinabox.perficientdevops.com:8081'
nexus_user= 'admin'
nexus_password='D3v0ps!'

repo = 'petsonline'
group = 'com.perfient'
artifact = 'jpetstore'

timestamp_format = '%Y%m%d-%H%M%S'

### Configure Logging
LOG_LEVEL=logging.INFO
#LOG_LEVEL=logging.DEBUG

logging.basicConfig(
    level=LOG_LEVEL,
#    filename='cleanup_nexus_versions.log',
    format='%(asctime)s %(levelname)s:%(message)s',
    datefmt= timestamp_format)
###

'''
 Helper to wrap the common GET command
'''
def get_json( url ):
  logging.debug( "Trying to call: %s" % (url) )
  r = requests.get( url, auth=(nexus_user, nexus_password))
  if r.status_code != 200:
      logging.error( "%s: %s" % (r.status_code, r.text) )
  return r.json()

'''
Wrapper around the DELETE automatically
'''
def delete_component( comp_id, dry_run=False ):
  logging.debug( 'delete_component - comp_id: %s, dry_run: %s' % (comp_id, dry_run) )

  nexus_uri = 'service/siesta/rest/beta/components/%s' % ( comp_id)
  nexus_url = '%s/%s' % (nexus_base_url,nexus_uri )
  logging.info( "DELETE: %s" % (nexus_url) )
  if not dry_run:
    r = requests.delete( nexus_url, auth=(nexus_user, nexus_password) )
    # expect a HTTP 204: No Content response from Nexus
    if r.status_code != 204:
      logging.error( "%s: %s Reponse Object: %s" % (r.status_code, r.text, str(r)) )
      sys.exit(1)
  else:
    logging.info(' Skipping actual call to DELETE as dry_run is set to True.')
  return


'''
Get the first page or a specific page of component results
'''
def get_components( repositoryId, continuationId='' ):
  logging.debug( 'get_components - repositoryId: %s, continuationId: %s' % (repositoryId, continuationId) )

  continuation = ''
  if continuationId:
    continuation = 'continuationToken=%s&' % (continuationId )

  nexus_uri = 'service/siesta/rest/beta/components?%srepositoryId=%s' % ( continuation, repositoryId )
  nexus_url = '%s/%s' % (nexus_base_url,nexus_uri )
  return get_json( nexus_url )

'''
Get a generator that will automatically page through all results, optional argument
of the component name to do exact match against to return only one versions for one
artifact.
'''
def get_components_generator( repositoryId, component_name=None ):
  logging.debug( 'get_components_generator - repositoryId: %s, component_name: %s' % (repositoryId, component_name) )

  components = get_components( repositoryId=repositoryId )
  has_more_results = True

  while has_more_results:

    for item in components['items']:
      # Value returned from next() command
      if component_name is None or component_name == item['name']:
        yield item

    if components['continuationToken'] is None:
      has_more_results = False
    else:
      logging.debug( "Found continuation token: %s " % (components['continuationToken']) )
      components = get_components( repositoryId=repositoryId, continuationId=components['continuationToken'] )

'''
Loop over all components and checking version against the given regex
'''
def delete_components_matching_pattern( repositoryId, version_match_pattern, component_name=None, dry_run=False ):
  logging.debug( 'delete_components_matching_pattern - respositoryId: %s, version_match_pattern: [%s], component_name: %s, dry_run: %s' % (repositoryId, version_match_pattern, component_name, dry_run ) )

  for item in get_components_generator( repositoryId=repositoryId, component_name=component_name ):
    ## Super-hack when the format is raw the version is jammed in the name and group
    if item['format'] == 'raw':
      full_version = item['name'].split('/')[2]
    else:
      full_version = item['version']

    if re.match( version_match_pattern, full_version ):
      logging.info( "Found a version we want to delete: %s : %s : %s : %s" % (item['id'], item['name'], item['version'], item['group'] ) )
      delete_component( item['id'], dry_run=dry_run )
    else:
      logging.debug( "Not matched [%s] skipping" % (full_version) )

'''
Delete components from repository that are older than the given timestamp
'''
def delete_components_by_age( repositoryId, max_age_datetime, component_name=None, dry_run=False):
  logging.debug( 'delete_components_by_age - respositoryId: %s, max_age_datetime: %s, component_name: %s, dry_run: %s' % (repositoryId, max_age_datetime, component_name, dry_run ) )

  for item in get_components_generator( repositoryId=repositoryId, component_name=component_name ):
    ## Super-hack when the format is raw the version is jammed in the name and group
    if item['format'] == 'raw':
      full_version = item['name'].split('/')[2]
    else:
      full_version = item['version']

    logging.debug( 'full_version: %s' % (full_version) )
    # Assert version is like: 1.0.0_20171101-121512
    if not re.match( r'^\d|\.\d+\.\d+_\d{6}-\d{6}}$', full_version ):
      log.error( 'Version does not match expected format.')
      sys.exit(1)

    version_ts = full_version.split('_')[1]
    logging.debug( 'version_ts: %s' % (version_ts) )

    version_datetime = datetime.datetime.strptime( version_ts, timestamp_format )
    logging.debug( 'version_datetime: %s' % (version_datetime) )
    logging.debug( 'max_age_datetime: %s' % (max_age_datetime) )

    if ( version_datetime < max_age_datetime ):
      logging.info( ">>> Found a version to delete - %s" % (full_version) )
      delete_component( item['id'], dry_run=dry_run)

def delete_components_by_age_days( repositoryId, max_age_days, component_name=None, dry_run=False):
  logging.debug( 'delete_components_by_age_days - respositoryId: %s, max_age_days: %s, component_name: %s, dry_run: %s' % (repositoryId, max_age_days, component_name, dry_run ) )

  max_age_datetime = datetime.datetime.now() - datetime.timedelta( days=max_age_days)
  delete_components_by_age( repositoryId, max_age_datetime,component_name=component_name, dry_run=dry_run )


###

## Simple call to get all components in a repo for inspection
#components = get_components( repo )
# #print components
#

## Check with a get components generator
# for item in get_components_generator( repo, component_name='jpetstore' ):
#   print item


## Delete the components with a simple version
#delete_components_matching_pattern( repo, r'_\d+$', component_name='jpetstore', dry_run=True )

## Delete components based on a semantic version plus timestamp type version
delete_components_by_age_days( 'RawRepo', 1, component_name=None, dry_run=False )
