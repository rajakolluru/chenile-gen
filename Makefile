
.DEFAULT_GOAL := all

## clean: Delete the bin folder
.PHONY: delete-bin
clean:
	cd app-gen && $(MAKE) clean
	cd stmcli && $(MAKE) clean
## all: 
.PHONY: all
all: 
	cd app-gen && $(MAKE) all
	cd stmcli && $(MAKE) all

## help: type for getting this help
.PHONY: help
help: Makefile
	@echo 
	@echo " Choose a command to run in "$(PROJECTNAME)":"
	@echo
	@sed -n 's/^##//p' $< | column -t -s ':' |  sed -e 's/^/ /'
	@echo
