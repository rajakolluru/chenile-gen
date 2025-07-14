dir=$(which $0)
dir=${dir%/*}

if ls ${dir}/../target/jgen-cli-*.jar  > /dev/null 2>&1
then
	jarfile=$(ls -tr ${dir}/../target/jgen-cli-*.jar | tail -1)
fi

if [[ -z ${jarfile} ]] 
then
	echo "Missing JAR file. Perhaps you skipped making it? Issue the command 'make clean all' in jgen folder."
	exit 1 
fi

java -jar ${jarfile} $*
exit $?
