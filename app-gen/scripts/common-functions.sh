#!/bin/bash

## This file needs to be sourced to obtain access to common functions


function setBaseVars(){
	base_folder=${scripts_folder%/*}
	template_folder_base=$base_folder/templates
	config_folder=$base_folder/config
	## set up other environment variables
	[[ -f $config_folder/setenv.sh ]] && source $config_folder/setenv.sh
  [[ -f ./config/setenv.sh ]] && source ./config/setenv.sh
}

function processTemplates(){
  folder=$1
  json_file=$2
  find $folder -name "*.mustache" -print |
    while read template_file
    do
			output_file=${template_file%.mustache}
			$scripts_folder/node_modules/mustache/bin/mustache $json_file $template_file > $output_file
      if [[ $? != 0 ]]
      then
        echo "Failure in processing file $template_file"
        exit 0
      fi
      [[ -x $template_file ]] && chmod +x $output_file # if original template file has executable
      # permissions the copied file must also have the same executable perms
      rm $template_file
    done
}

## Converts the argument to camel case and returns the value
function camelCase(){
  A=$1
  A="$(tr '[:lower:]' '[:upper:]' <<< ${A:0:1})${A:1}"
  echo $A
}

function replaceServiceInName(){ 
  folder=$1
  shift
  for pattern in $*
  do
    pattern_value=$(eval echo '$'$pattern)
    
    # The pipeline below ensures that awk matches the lines only if the match 
    # happens in the last part of the path (not somewhere in between)
    # It also reverses the order of the lines using sed '1!G;h;$!d'  so that the 
    # leaves are renamed before the trunks in the tree are renamed
    find $folder | awk -F/ '($NF ~ /__'$pattern'__/ ){print $0}' | sed '1!G;h;$!d'  | 
          while read fname
          do
            last_part=${fname##*/}
            folder_part=${fname%/*}
            renamed_file=$(echo  "$last_part" | sed "s#__${pattern}__#${pattern_value}#")
            rname=$folder_part/${renamed_file}

            mv $fname $rname
          done
  done
  }

## Get All Chenile projects under the folder.
function getAllProjects(){
  folder=$1
  find $folder -name ".chenilegen.json" -print |
    while read project
    do
      project=${project%/.chenilegen.json}
      project=${project##*/}
      echo "\"${project}\""
    done
}

function generateModule(){
  template_folder=$1
  target_folder=$2
  json_file=$3
  subParams="$4"
  cp -r $template_folder/* $target_folder
  processTemplates $target_folder $json_file
  replaceServiceInName $target_folder "$subParams"
  processConditionalFolders $target_folder
  processChenileHeaderFiles $target_folder $json_file
}

## Find all folders that comply to the pattern %%variable=value%%
## if the condition is true then
# mv the contents of the folder to the containing folder. Delete the folder
# Delete the folder and its contents if the condition is false
# Usage: processConditionalFolders folder
function processConditionalFolders(){
  find $1 | awk -F/ '($NF ~ /%%.*%%/ ){print $0}' |
     {
      while read dir
      do
        expr=$(echo $dir | awk -F"%%" '{print $2}' )
        var=$(echo $expr | cut -d= -f1)
        rhs=$(echo $expr | cut -d= -f2)
        lhs=$(eval 'echo $'${var})
         if [[ $lhs == "$rhs" ]]
         then
           folder_up=${dir%/*}
           mv $dir/* $folder_up
           rmdir $dir
          else
           rm -rf $dir
        fi
      done
     }
}

function processChenileHeaderFiles(){
  target_folder=$1
  json_file=$2
  find $target_folder -name .chenilegen.json -print |
    while read file
    do
      emitJsonContentsTofile $file $json_file
    done
}

## Usage: doGitInit $folder $version 
function doGitInit(){
  folder=$1
  version=$2
  cd $1
  git init
  git add .
  git commit -m "First commit. Generated code."
  git tag $version -a -m $version
  echo "Successfully created a Git repo with your generated code. Next steps:"
  echo "Connect to origin using the following command"
  echo "git remote add origin <url-to-remote-server>"
}

## emitJsonContentsToFile file-to-process json_file
## Emits the contents of the file_to_process as is and replaces the string __json__ with
## the contents of the json_file

function emitJsonContentsTofile(){
  tmpfile=/tmp/$RANDOM.$$
  awk -v file="$2" '
    BEGIN{
      x=""; s=""
      while (getline x <file ) {
        s = s"\n"x
      }
    }
    ($1 == "__json__") { print s;next}
    {print $0 }

  ' $1 > $tmpfile
  mv $tmpfile $file
}

