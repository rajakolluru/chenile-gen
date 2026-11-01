
.DEFAULT_GOAL := all

## build: Build the jar from source
.PHONY: build
build:
	mvn install
	

## deploy-central: Publishes org.chenile.jgen:jgen (parent pom), jgen-base and jgen-ext-pebble to Maven Central.
## deploy-central: Usage: make deploy-central version=2.5.1 passphrase=<gpg passphrase>
.PHONY: deploy-central
deploy-central:
	mvn -B -DskipTests -Drevision=$(version) -DperformRelease=true -Dgpg.passphrase="${passphrase}" \
		-pl jgen-base,jgen-ext-pebble -am deploy

## make-cli: copies all scripts from the scripts folder to bin
.PHONY: make-cli
make-cli: 
	cd jgen-cli; make 


## clean: Clean everything
.PHONY: clean
clean:
	mvn clean
	cd jgen-cli; make clean	

## all: 
.PHONY: all
all: build make-cli

## help: type for getting this help
.PHONY: help
help: Makefile
	@echo 
	@echo " Choose a command to run in "$(PROJECTNAME)":"
	@echo
	@sed -n 's/^##//p' $< | column -t -s ':' |  sed -e 's/^/ /'
	@echo
