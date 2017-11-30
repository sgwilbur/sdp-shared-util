#!/usr/bin/env python

from subprocess import check_output, call
import re

## Get all tags in the current repo as list
def get_tags():
  output=check_output("git tag -l", shell=True)
  return output.split('\n')

## Quick and dirty tag delete helpers both local and remote
def delete_tag( tag_name ):
  output=check_output("git tag -d %s" % tag_name, shell=True)
  output=check_output("git push -v origin :refs/tags/%s" % tag_name, shell=True)

def delete_tags( tag_list ):
  for cur_tag in tag_list:
    delete_tag( cur_tag )

def delete_tags_by_regex( reg_ex ):
   delete_tags( filter_tags_regex( get_tags(), reg_ex ))

## Only return tags that look like our RC candidate pattern
def rc_only_tags():
  rc_reg_ex = "^\d+\.\d+.\d+-\d{8}-\d{6}-rc$"
  return filter_tags_regex( get_tags(), rc_reg_ex )

def filter_tags_regex( tag_list, reg_ex ):
  return [cur_tag for cur_tag in tag_list if re.match( reg_ex, cur_tag ) ]

#print( "[%s]" % output )
#reg_ex = "2.0.0-20171013-.*-rc"

all_tags = get_tags()
print "Tags in repository:"
print all_tags

## Remove non-release candidates from tag list
# rc_tags = rc_only_tags()
# print "Tags for Release Candidates:"
# print rc_tags

## Only get me version 2.0.0 from Sept 2017
# reg_ex = "2.0.0-2017\d{4}-\d{6}-rc$"
# filtered_tags = filter_tags_regex( rc_tags, reg_ex )
# print "Tags for deletion:"
# print filtered_tags
# delete_tags( filtered_tags )

# cleanup old tags
reg_exs =  [
        "2.0.0-201709\d{2}-\d{6}$",
        "2.0.0-201709\d{2}-\d{6}-rc$",
        "2.0.0-201710\d{2}-\d{6}$",
        "2.0.0-201710\d{2}-\d{6}-rc$",
        "2.0.0-201711\d{2}-\d{6}-rc$"
        ]

for reg_ex in reg_exs:
  filtered_tags = filter_tags_regex( all_tags, reg_ex )
  print( "Tags for deletion: ( matching - %s)" % (reg_ex) )
  print filtered_tags
  delete_tags( filtered_tags )
