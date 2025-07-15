
.DEFAULT_GOAL := all

## clean: Delete the bin folder
.PHONY: clean
clean:
	# cd app-gen && $(MAKE) clean
	cd stmcli && $(MAKE) clean
	cd jgen && $(MAKE) clean
## all: 
.PHONY: all
all: 
	# cd app-gen && $(MAKE) all
	cd stmcli && $(MAKE) all
	cd jgen && $(MAKE) all

## help: type for getting this help
.PHONY: help
help: Makefile
	@echo 
	@echo " Choose a command to run in "$(PROJECTNAME)":"
	@echo
	@sed -n 's/^##//p' $< | column -t -s ':' |  sed -e 's/^/ /'
	@echo
