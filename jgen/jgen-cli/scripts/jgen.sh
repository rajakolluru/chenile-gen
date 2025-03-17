dir=$(which $0)
dir=${dir%/*}

if ls ${dir}/../target/jgen-cli-*.jar  > /dev/null 2>&1
then
	jarfile=$(ls -tr ${dir}/../target/jgen-cli-*.jar | tail -1)
fi

if [[ -z ${jarfile} ]] 
then
	echo "Missing JAR file. Please check if you downloaded correctly"
	exit 1 
fi

java -jar ${jarfile} $*
exit $?
