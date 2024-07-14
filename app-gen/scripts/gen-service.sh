#!/bin/bash

function usage(){
  echo "Usage: $prog [-v service-version] [-d dest-folder] [-s] [-j] <service-name> " >&2
  echo "service version will default to $defaultVersion" >&2
	echo "dest-folder will default to $defaultDestFolder" >&2
	echo "-s will enable security" >&2
	echo "-j will enable JPA" >&2
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



function generateService(){
	constructJsonfile > $json_file
	template_folder=$template_folder_base/service
  	generateModule $template_folder $dest_folder $json_file "service com org company Service"
  	if [[ $gitInit == "true" ]]
  	then
  	  doGitInit $dest_folder/$service $serviceVersion
  	fi
}

function constructJsonfile(){
	echo "{"
	echo "\"service\": \"$service\","
	echo "\"serviceVersion\": \"$serviceVersion\","
	if [[ $securityEnabled == "true" ]]
  	then
  	  echo "\"securityEnabled\": \"$securityEnabled\","
  	fi

  	if [[ $jpa == "true" ]]
  	then
  	  echo "\"jpa\": \"$jpa\","
  	fi

	echo "\"com\": \"$com\","
	echo "\"org\": \"$org\","
	echo "\"company\": \"$company\","
	echo "\"chenilePackage\": \"$chenilePackage\","
	echo "\"chenileVersion\": \"$chenileVersion\","
	echo "\"Service\": \"$Service\""
	echo "}"
}

setenv "${0}" 
if (( $# < 1 ))
then
	usage
	_exit 1
fi


json_file=/tmp/$prog.$$
serviceVersion=${defaultVersion}
dest_folder=${defaultDestFolder}
securityEnabled=false
jpa=false
gitInit=false

while getopts ":sjd:v:g" opts; do
    case "${opts}" in
    s)
      securityEnabled=true
      ;;
    j)
      jpa=true
      ;;
    d)
			dest_folder=${OPTARG}
			;;
		v)
			serviceVersion=${OPTARG}
			;;
	  g)
	    gitInit=true
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

service=${1}
[[ -z $service ]] && {
	echo "Service is not specified."
	usage
	_exit 4
}
Service=$(camelCase $service)

[[ ! -d $dest_folder ]] && mkdir $dest_folder
echo "Creating service ${service}(${serviceVersion}) in folder $dest_folder" >&2
generateService
_exit 0
