#!/bin/bash

function usage(){
  echo "Usage: $prog [-v service-version] [-d dest-folder] [-s] [-j] [-c] [a] [-e] <service-name> " >&2
  echo "service version will default to $defaultVersion" >&2
	echo "dest-folder will default to $defaultDestFolder" >&2
	echo "-s will enable security" >&2
	echo "-j will enable JPA" >&2
	echo "-c will enable cloud switch" >&2
	echo "-a will enable activity tracking" >&2
	echo "-e will generate enablement code" >&2
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
	template_folder=$template_folder_base/workflowservice
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
	if [[ $security == "true" ]]
	then
	  echo "\"security\": \"$security\","
	fi

	if [[ $jpa == "true" ]]
	then
	  echo "\"jpa\": \"$jpa\","
	fi

	if [[ $cloudSwitchEnabled == "true" ]]
  then
    echo "\"cloudSwitchEnabled\": \"$cloudSwitchEnabled\","
  fi

  if [[ $activity == "true" ]]
    then
      echo "\"activity\": \"$activity\","
  fi

  if [[ $enablement == "true" ]]
      then
        echo "\"enablement\": \"$enablement\","
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
security=false
jpa=false
gitInit=false
cloudSwitchEnabled=false
activity=false
enablement=false

while getopts ":sjd:v:gcae" opts; do
    case "${opts}" in
        s)
            security=true
            ;;
        c)
            cloudSwitchEnabled=true
            ;;
        a)
          activity=true
          ;;
        e)
          enablement=true
          ;;
        j)
            jpa=true
            ;;
        g)
            gitInit=true
            ;;
        d)
            dest_folder=${OPTARG}
            ;;
		    v)
            serviceVersion=${OPTARG}
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
echo "Creating service ${service}(${serviceVersion}) in folder $dest_folder. (security = $securityEnabled, jpa = $jpa)" >&2
generateService
_exit 0
