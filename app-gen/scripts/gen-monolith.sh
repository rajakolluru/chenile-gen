#!/bin/bash

function usage(){
  echo "Usage: $prog [-v monolith-version] [-d dest-folder] [-s] [-j] [-S service-to-bundle] [-V service-version-to0-bundle] <monolith-name> " >&2
  echo "service version will default to $defaultVersion" >&2
	echo "dest-folder will default to $defaultDestFolder" >&2
	echo "-s will enable security" >&2
	echo "-j will enable JPA" >&2
	echo "Service to bundle along with the monolith. If this is not provided we will default to $defaultServiceName as the name of the service"
  echo "Service Version to bundle. If this is not provided we will default to $defaultServiceVersion as the version of the service"
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



function generateMonolith(){
	template_folder=$template_folder_base/monolith
  	generateModule $template_folder $dest_folder $json_file "monolith com org company Monolith"
  	generateCurlScript 
  	doGitInit $dest_folder/$monolith $monolithVersion
}

function generateCurlScript(){
	tmpfile=/tmp/$RANDOM.$$.${prog}
	# [[ -z $serviceTemplate ]] && return
	cur_curl_script=${dest_folder}/${monolith}/scripts/curl-scripts.sh
	service_curl_script=${template_folder_base}/${serviceTemplate}/__service__/scripts/curl-scripts.sh.mustache
	[[ ! -f ${cur_curl_script} ]] && return
	[[ ! -f ${service_curl_script} ]] && return
	$scripts_folder/node_modules/mustache/bin/mustache $json_file $service_curl_script > $tmpfile
	sed '1,/#--/d' ${tmpfile} >> ${cur_curl_script}
	rm $tmpfile
}

function constructJsonfile(){
	echo "{"
	echo "\"monolith\": \"$monolith\","
	echo "\"monolithVersion\": \"$monolithVersion\","
	echo "\"com\": \"$com\","
	echo "\"org\": \"$org\","
	if [[ $securityEnabled == "true" ]]
  	then
  	  echo "\"securityEnabled\": \"$securityEnabled\","
  	fi

  	if [[ $jpa == "true" ]]
  	then
  	  echo "\"jpa\": \"$jpa\","
  	fi

	echo "\"company\": \"$company\","
	echo "\"chenilePackage\": \"$chenilePackage\","
	echo "\"chenileVersion\": \"$chenileVersion\","
	echo "\"Monolith\": \"$Monolith\","
	echo "\"service\": \"$service\","
	echo "\"serviceTemplate\": \"$serviceTemplate\","
	echo "\"serviceVersion\": \"$serviceVersion\","
	echo "\"services\": "'['" $services "']'
	echo "}"
}

## Finds the list of services that have been exposed by the serviceTemplate
function getServiceList(){
	tmpfile=/tmp/$RANDOM.$$.${prog}
	filename=${template_folder_base}/${serviceTemplate}/.meta/service-modules.txt
	$scripts_folder/node_modules/mustache/bin/mustache $json_file $filename > $tmpfile
	services=$(sed 's/^\(.*\)$/"\1"/' $tmpfile  | tr '\n' ',') # surround each line with double-quotes
	constructJsonfile > $json_file
	rm $tmpfile
}

setenv "${0}" 
if (( $# < 1 ))
then
	usage
	_exit 1
fi


json_file=/tmp/$prog.$$
monolithVersion=${defaultVersion}
dest_folder=${defaultDestFolder}
service=${defaultServiceName}
serviceVersion=${defaultVersion}

while getopts ":sjd:v:S:V:" opts; do
    case "${opts}" in
        s)
            securityEnabled=true
            ;;
        S)
            service=${OPTARG}
            ;;
        V)
            serviceVersion=${OPTARG}
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

monolith=${1}
[[ -z $monolith ]] && {
	echo "Monolith is not specified."
	usage
	_exit 4
}
Monolith=$(camelCase $service)

[[ ! -d $dest_folder ]] && mkdir $dest_folder
echo "Creating monolith ${monolith}(${monolithVersion}) with included service $service($serviceVersion) in folder $dest_folder" >&2
constructJsonfile > $json_file
getServiceList
generateMonolith
_exit 0
