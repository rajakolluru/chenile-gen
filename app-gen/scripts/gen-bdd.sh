#!/bin/bash

function usage(){
  echo "Usage: $prog [-v app-version] [-d dest-folder] [-e entity-name] [-s] <app-name> " >&2
  echo "App denotes the app that you want to do an integration test on. "
  echo "app version will default to $defaultVersion" >&2
	echo "dest-folder will default to $defaultDestFolder" >&2
	echo "-s will enable security" >&2
	echo "entity is the actual entity for which save and retrieve test cases are generated" >&2
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



function generateBdd(){
    constructJsonfile > $json_file
    template_folder=$template_folder_base/bdd-it
    generateModule $template_folder $dest_folder $json_file "service com org company Service app App entity"
    if [[ $gitInit == "true" ]]
    then
      doGitInit $dest_folder/$service $serviceVersion
    fi
}

function constructJsonfile(){
	echo "{"
	echo "\"app\": \"$app\","
	echo "\"appVersion\": \"$appVersion\","
	if [[ $security == "true" ]]
  	then
  	  echo "\"security\": \"$security\","
  	fi

	echo "\"com\": \"$com\","
	echo "\"org\": \"$org\","
	echo "\"company\": \"$company\","
	echo "\"chenilePackage\": \"$chenilePackage\","
	echo "\"chenileVersion\": \"$chenileVersion\","
	echo "\"chenileBddVersion\": \"$chenileBddVersion\","
	echo "\"App\": \"$App\","
	echo "\"entity\": \"$entity\","
	echo "\"Entity\": \"$Entity\""
	echo "}"
}

setenv "${0}" 
if (( $# < 1 ))
then
	usage
	_exit 1
fi


json_file=/tmp/$prog.$$
appVersion=${defaultVersion}
dest_folder=${defaultDestFolder}
security=false
gitInit=false

while getopts ":se:d:v:g" opts; do
    case "${opts}" in
    s)
      security=true
      ;;
    d)
			dest_folder=${OPTARG}
			;;
		v)
			serviceVersion=${OPTARG}
			;;
	  e)
  			entity=${OPTARG}
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

app=${1}
[[ -z $app ]] && {
	echo "App is not specified."
	usage
	_exit 4
}
App=$(camelCase $app)

[[ -z $entity ]] && {
	echo "Entity is not specified. It is mandatory."
	usage
	_exit 4
}
Entity=$(camelCase $entity)

[[ ! -d $dest_folder ]] && mkdir $dest_folder
echo "Creating BDD for  ${app}(${appVersion}) in folder $dest_folder. " >&2
echo "This includes an entity feature file at src/main/resources/features/${entity}.feature" >&2
generateBdd
_exit 0
