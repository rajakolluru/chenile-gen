#!/bin/bash

function usage(){
  echo "Usage: $prog [-v namespace-version] [-d dest-folder] <namespace> " >&2
  echo "namespace version will default to $defaultVersion" >&2
	echo "dest-folder will default to $defaultDestFolder" >&2
}

function _exit {
	rm -f $json_file
  	exit $1
}

## Program initialization helper that sets all the environment variables required
function setenv(){
	curprog=${1}
  	prog=${1##*/}
	
	if  [[ $curprog != */* ]] 
	then
		scripts_folder="$PWD"
	else 
		scripts_folder=${curprog%/*}
	fi
	[[ $scripts_folder != /* ]] && scripts_folder=$(pwd)/${scripts_folder} ## handle relative path invocations

  	source $scripts_folder/common-functions.sh
	setBaseVars
}



function generateQueryNamespace(){
	constructJsonfile > $json_file
	template_folder=$template_folder_base/mybatis-query-service
  	generateModule $template_folder $dest_folder $json_file "namespace com org company Namespace"
  	if [[ $gitInit == "true" ]]
  	then
  	  doGitInit $dest_folder/$namespace $namespaceVersion
  	fi
}

function constructJsonfile(){
	echo "{"
	echo "\"namespace\": \"$namespace\","
	echo "\"namespaceVersion\": \"$namespaceVersion\","
	echo "\"com\": \"$com\","
	echo "\"org\": \"$org\","
	echo "\"company\": \"$company\","
	echo "\"chenilePackage\": \"$chenilePackage\","
	echo "\"chenileVersion\": \"$chenileVersion\","
	echo "\"Namespace\": \"$Namespace\""
	# echo "\"columns\": "'['" $columns "']'
	echo "}"
}

## Finds the list of columns that need to be mapped
function getColumnsList(){
	columns=$(sed 's/^\(.*\)$/"\1"/'  | tr '\n ' ',') # surround each line with double-quotes. replace new lines with commas
	# drop the trailing , 
	columns=$(echo $columns | sed 's/,$//')
}

setenv "${0}" 
if (( $# < 1 ))
then
	usage
	_exit 1
fi


json_file=/tmp/$prog.$$
namespaceVersion=${defaultVersion}
dest_folder=${defaultDestFolder}
gitInit=false
cloudSwitchEnabled=false

while getopts ":d:v:gc" opts; do
    case "${opts}" in
        g)
            gitInit=true
            ;;
        c)
            cloudSwitchEnabled=true
            ;;
        d)
            dest_folder=${OPTARG}
            ;;
		    v)
            namespaceVersion=${OPTARG}
            ;;
        :)
            echo "Option $OPTARG requires an argument" >&2
            usage
            _exit 2
            ;;
        \?)
			      echo "Invalid option $OPTARG" >&2
            usage
            _exit 3
            ;;
    esac
done
shift $((OPTIND-1))

namespace=${1}
[[ -z $namespace ]] && {
	echo "Namespace is not specified."
	usage
	_exit 4
}
Namespace=$(camelCase $namespace)

# getColumnsList

[[ ! -d $dest_folder ]] && mkdir $dest_folder
echo "Creating namespace ${namespace}(${namespaceVersion}) in folder $dest_folder" >&2
generateQueryNamespace
_exit 0
