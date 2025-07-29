# Chenile App Generator

Chenile is an open source framework for creating Micro services using Java and Spring Boot. 

This repository contains the Chenile Generator. The jgen folder contains Java based code that 
can generate Chenile services and mini monoliths in compliance to various blueprints. 
The list of blue prints can be obtained by typing jgen.sh.
Please see [chenile.org](http://chenile.org) for an 
explanation of how Chenile modules are structured. 

The [Chenile Tutorial section of the site](https://chenile.org/app-gen-landing-page.html) explains the app-gen portion much better. 

The entire chenile code base resides in Maven Central and hence can be downloaded without being compiled from source. Please see detailed instructions in the documentation pages. 

## Requirements
You need a power shell in Windows or the MAC zsh / Linux bash to execute these scripts. We have tested these scripts across Linux and MAC and we have seen they work. 

You need maven installed to compile the Java code from source.

## How do you execute this script
$ make clean all # will compile the code from source and make it available
$ source setpath.sh # will set your system PATH variable to point to stm-cli and jgen.sh 
