#!/bin/bash

function generateInternationalServiceMonolith(){
    export serviceTemplate="monolith-with-service-international"
    service=$(captureNonNullField "Service Name")
    serviceVersion=$(captureFieldWithDefaultValue "Service Version" "$defaultVersion")
    outfolder=$(captureFieldWithDefaultValue "Output Folder" $defaultDestFolder)
    defaultMonolithName=${service}deploy
    monolith=$(captureFieldWithDefaultValue "Mini Monolith" $defaultMonolithName)
    monolithVersion=${serviceVersion}
    $scripts_folder/gen-service-monolith-international.sh $monolith $monolithVersion $outfolder $service $serviceVersion 
}

function generateService(){   
    service=$(captureNonNullField "Service Name")
    serviceVersion=$(captureFieldWithDefaultValue "Service Version" "$defaultVersion")
    outfolder=$(captureFieldWithDefaultValue "Output Folder" $defaultDestFolder)
    security=$(captureFieldWithDefaultValue "Enable Security?" "n")
    jpa=$(captureFieldWithDefaultValue "Enable JPA?" "y")
    cmdline=$(generateCommandLine $serviceVersion $outfolder $security $jpa)
    $scripts_folder/gen-service.sh $cmdline $service
}

function generateBdd(){
    app=$(captureNonNullField "App Name")
    entity=$(captureNonNullField "Entity Name")
    appVersion=$(captureFieldWithDefaultValue "App Version" "$defaultVersion")
    outfolder=$(captureFieldWithDefaultValue "Output Folder" $defaultDestFolder)
    security=$(captureFieldWithDefaultValue "Enable Security?" "n")
    cmdline=$(generateCommandLine $appVersion $outfolder $security $jpa)
    $scripts_folder/gen-bdd.sh $cmdline -e $entity $app
}

function generateMybatisQuery(){   
    namespace=$(captureNonNullField "Namespace")
    namespaceVersion=$(captureFieldWithDefaultValue "Namespace Version" "$defaultVersion")
    outfolder=$(captureFieldWithDefaultValue "Output Folder" $defaultDestFolder)
    # columns="$(captureList 'Columns to map')"
    cmdline=$(generateCommandLine $namespaceVersion $outfolder "n" "n")
    $scripts_folder/gen-mybatis-query.sh $cmdline $namespace
}

function generateQueryService(){   
    service=$(captureNonNullField "Service Name")
    serviceVersion=$(captureFieldWithDefaultValue "Service Version" "$defaultVersion")
    outfolder=$(captureFieldWithDefaultValue "Output Folder" $defaultDestFolder)
    security=$(captureFieldWithDefaultValue "Security Enabled?" "n")
    generateQueryController=$(captureFieldWithDefaultValue "Generate Query Controller?" "n")
    $scripts_folder/gen-query-service.sh $service $serviceVersion $outfolder $security $generateQueryController
}

function generateWorkflowService(){   
    service=$(captureNonNullField "Workflow Entity Service Name")
    serviceVersion=$(captureFieldWithDefaultValue "Workflow Entity Service Version" "$defaultVersion")
    outfolder=$(captureFieldWithDefaultValue "Output Folder" $defaultDestFolder)
    security=$(captureFieldWithDefaultValue "Enable Security?" "n")
    jpa=$(captureFieldWithDefaultValue "Enable JPA?" "y")
    cmdline=$(generateCommandLine $serviceVersion $outfolder $security $jpa)
    $scripts_folder/gen-workflow-service.sh $cmdline $service
}

# Usage: generateCommandLine version outfolder security persistenceEnabled [included-service [included-service-version]]
function generateCommandLine(){
    cmdline="-v $1 -d $2"
    if [[ $3 == "y" ]]
    then 
        cmdline="$cmdline -s"
    fi
    if [[ $4 == "y" ]]
    then 
        cmdline="$cmdline -j"
    fi
    if [[ -n $5 ]]
    then
      cmdline="$cmdline -S $5"
    fi
    if [[ -n $6 ]]
    then
      cmdline="$cmdline -V $6"
    fi
    if [[ $gitInit == "y" ]]
    then
      cmdline="$cmdline -g"
    fi
    if [[ $cloudSwitchEnabled == "y" ]]
    then
      cmdline="$cmdline -c"
    fi
    echo $cmdline
}

function generateMiniMonolith(){
    monolith=$(captureNonNullField "Mini Monolith Name")
    monolithVersion=$(captureFieldWithDefaultValue "Mini Monolith Version" $defaultVersion)
    [[ -z $outfolder ]] && outfolder=$(captureFieldWithDefaultValue "Output Folder" $defaultDestFolder)
    [[ -z $service ]] && service=$(captureFieldWithDefaultValue "Service Name to bundle" $defaultServiceName)
    [[ -z $serviceVersion ]] && serviceVersion=$(captureFieldWithDefaultValue "Service Version" $defaultVersion)
    [[ -z $jpa ]] && jpa=$(captureFieldWithDefaultValue "Enable JPA?" "y")
    [[ -z $security ]] && security=$(captureFieldWithDefaultValue "Enable Security?" "n")
    cmdline=$(generateCommandLine $monolithVersion $outfolder $security $jpa $service $serviceVersion)
    $scripts_folder/gen-monolith.sh $cmdline $monolith
}



function generateLocalConfig() {
		if [[ -d config ]]
		then 
			echo "Config folder exists already. Please delete it first"
			return 1 
		fi
    mkdir config
    cp $config_folder/setenv.sh config
    echo "Generated a config folder under this folder with defaults in setenv.sh. You can edit setenv.sh!!!"
}

function generateNormalServiceAndMonolith(){
    export serviceTemplate="service"
    generateService
    generateMiniMonolith
}

function generateQueryServiceAndMonolith(){
    export serviceTemplate="queryservice"
    generateQueryService
    generateMiniMonolith
}

function generateWorkflowServiceAndMonolith(){
    export serviceTemplate="workflowservice"
    generateWorkflowService
    generateMiniMonolith
}

function generateInterceptor(){
    export serviceTemplate="interceptor-template"
    interceptorName=$(captureNonNullField "Interceptor Name")
    interceptorVersion=$(captureFieldWithDefaultValue "Interceptor Version" "$defaultVersion")
    outfolder=$(captureFieldWithDefaultValue "Output Folder" $defaultDestFolder)
    cmdline=$(generateCommandLine $interceptorVersion $outfolder "n" "n")
    $scripts_folder/gen-interceptor.sh $cmdline $interceptorName
}

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

setenv "${0}" 
source $scripts_folder/input-capture.sh # init functions that allow us to capture input from user
cat $scripts_folder/banner.txt
usage="${prog}"
function usage {
	echo "$usage" 1>&2
	echo "$prog -? for this usage message" 1>&2
}

function _exit {
	rm -f $tmpfile1 $tmpfile2 $tmpfile3
	exit $1
}

if [[ $1 == "-?" ]]
then
    usage
    _exit 1
fi


gitInit=$(captureYorN "Git Init" "n")
cloudSwitchEnabled=$(captureYorN "Enable Cloud Switch" "n")

choice=$(choices  \
    "N|Generate Normal Service & Mini Monolith"  \
    "W|Generate Workflow Service & Mini Monolith" \
    "I|Generate a Chenile interceptor stub" \
    "Q|Generate a Chenile Mybatis Query Service" \
    "B|Generate a BDD based Integration Test" \
    "C|Create a local config")

case $choice in
    "N") generateNormalServiceAndMonolith ;;
    "W") generateWorkflowServiceAndMonolith;;
    "I") generateInterceptor;;
    "Q") generateMybatisQuery;;
    "C") generateLocalConfig;;
    "B") generateBdd;;
esac

_exit 0
