#!/bin/bash

function usage(){
  echo "Usage: $prog [-v interceptor-version] [-d dest-folder] <interceptor-name> " >&2
  echo "interceptor version will default to $defaultVersion" >&2
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

function generateInterceptor(){
	constructJsonfile > $json_file
	template_folder=$template_folder_base/interceptor-template
  generateModule $template_folder $dest_folder $json_file "interceptorName com org company InterceptorName"
  if [[ $gitInit == "true" ]]
  then
  	doGitInit $dest_folder/$interceptorName $interceptorVersion
  fi
}

function constructJsonfile(){
	echo "{"
	echo "\"interceptorName\": \"$interceptorName\","
	echo "\"interceptorVersion\": \"$serviceVersion\","
	echo "\"com\": \"$com\","
	echo "\"org\": \"$org\","
	echo "\"company\": \"$company\","
	echo "\"chenilePackage\": \"$chenilePackage\","
	echo "\"chenileVersion\": \"$chenileVersion\","
	echo "\"InterceptorName\": \"$InterceptorName\""
	echo "}"
}

setenv "${0}" 
if (( $# < 1 ))
then
	usage
	_exit 1
fi


json_file=/tmp/$prog.$$
interceptorVersion=${defaultVersion}
dest_folder=${defaultDestFolder}
gitInit=false
while getopts ":d:v:g" opts; do
    case "${opts}" in
        g)
            gitInit=true
            ;;
        d)
            dest_folder=${OPTARG}
            ;;
		    v)
            interceptorVersion=${OPTARG}
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

interceptorName=${1}
[[ -z $interceptorName ]] && {
	echo "Interceptor Name is not specified."
	usage
	_exit 4
}
InterceptorName=$(camelCase $interceptorName)

[[ ! -d $dest_folder ]] && mkdir $dest_folder
echo "Creating interceptor ${interceptorName}(${interceptorVersion}) in folder $dest_folder" >&2
generateInterceptor
_exit 0
