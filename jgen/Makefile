
.DEFAULT_GOAL := all

## build: Build the jar from source
.PHONY: build
build:
	mvn install
	

## copy-scripts: copies all scripts from the scripts folder to bin
.PHONY: copy-scripts
copy-scripts: 
	cd jgen-cli; make 


## clean: Clean everything
.PHONY: clean
clean:
	mvn clean
	cd jgen-cli; make clean	

## all: 
.PHONY: all
all: build copy-scripts

## help: type for getting this help
.PHONY: help
help: Makefile
	@echo 
	@echo " Choose a command to run in "$(PROJECTNAME)":"
	@echo
	@sed -n 's/^##//p' $< | column -t -s ':' |  sed -e 's/^/ /'
	@echo
