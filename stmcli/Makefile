
.DEFAULT_GOAL := all

## prep-stm-cli: Download and install STM CLI from Maven
.PHONY: prep-stm-cli
prep-stm-cli:
	if [ ! -d lib ]; then mvn dependency:copy; fi
	

## copy-scripts: copies all scripts from the scripts folder to bin
.PHONY: copy-scripts
copy-scripts: 
	cp scripts/* bin
	chmod +x bin/*

## create-bin: create the bin directory if it doesnt exist
.PHONY: create-bin
create-bin:
	if [ ! -d bin ]; then mkdir bin; fi

## clean: Clean everything
.PHONY: clean
clean:
	rm -rf bin
	rm -rf lib

## all: 
.PHONY: all
all: create-bin copy-scripts prep-stm-cli

## help: type for getting this help
.PHONY: help
help: Makefile
	@echo 
	@echo " Choose a command to run in "$(PROJECTNAME)":"
	@echo
	@sed -n 's/^##//p' $< | column -t -s ':' |  sed -e 's/^/ /'
	@echo
