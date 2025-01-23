#!/bin/bash

function usage(){
  echo "Usage: $prog [-v service-version] [-d dest-folder] [-x xml-file] [-s] [-j] [-c] [a] [-e] <service-name> " >&2
  echo "service version will default to $defaultVersion" >&2
	echo "dest-folder will default to $defaultDestFolder" >&2
	echo "xml-file is the states XML file. This will be the basis for code generation" >&2
	echo "-s will enable security" >&2
	echo "-j will enable JPA" >&2
	echo "-c will enable cloud switch" >&2
	echo "-a will enable activity tracking" >&2
	echo "-e will generate enablement code" >&2
	echo "-p will generate customized payloads for every event. Else we will use MinimalPayload" >&2
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
  checkXmlFile
	constructJsFile > $json_file
	cp $json_file /tmp/a.js
	template_folder=$template_folder_base/workflowservice_custom
  generateModule $template_folder $dest_folder $json_file "service com org company Service"
  cp $xmlFile $dest_folder/${service}/${service}-service/src/main/resources/${com}/${company}/${org}/${service}/${service}-states.xml
  if [[ $gitInit == "true" ]]
  then
  	doGitInit $dest_folder/$service $serviceVersion
  fi
}

function checkXmlFile() {
  outfile=/tmp/out.$$
  ${stmcli_bin}/stm-cli -j -o $outfile $xmlFile
  workflow=$(cat $outfile)
  rm $outfile
  ${stmcli_bin}/stm-cli -t -o $outfile $xmlFile
  testcases=$(cat $outfile)
  rm $outfile
}

function constructJsFile(){
	echo "module.exports = {"
	echo "\"capitalize\":  () => {"
  echo "  return (val, render) => {"
  echo "var text = render(val);"
  echo "text = text.substr(0,1).toUpperCase() + text.substr(1);"
  echo "return text;"
  echo "};"
  echo "},"
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
	echo "\"Service\": \"$Service\"",
	echo "\"genCustomizedPayload\": \"$genCustomizedPayload\"",
	echo "\"workflow\": $workflow",
	echo "\"testcases\": $testcases"
	echo "}"
}

setenv "${0}" 
if (( $# < 1 ))
then
	usage
	_exit 1
fi

json_file=$prog.$$.js
serviceVersion=${defaultVersion}
dest_folder=${defaultDestFolder}
security=false
jpa=false
gitInit=false
cloudSwitchEnabled=false
activity=false
enablement=false
xmlFile=""
genCustomizedPayload=false

while getopts ":sjd:v:x:gcaep" opts; do
    case "${opts}" in
        s)
            security=true
            ;;
        p)
            genCustomizedPayload=true
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
        x)
           xmlFile=${OPTARG}
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

[[ -z $xmlFile || ! -f $xmlFile ]] && {
	echo "XML File is either not specified or does not exist."
	usage
	_exit 5
}

[[ ! -d $dest_folder ]] && mkdir $dest_folder
echo "Creating service ${service}(${serviceVersion}) in folder $dest_folder. (security = $securityEnabled, jpa = $jpa)" >&2
generateService
_exit 0
