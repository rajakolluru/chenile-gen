# Chenile App Generator

Chenile is an open source framework for creating Micro services using Java and Spring Boot. 

This repository contains the Chenile Generator. The app-gen folder contains shell based code that 
can generate a Chenile service and mini monolith. Please see [chenile.org](http://chenile.org) for an 
explanation of how Chenile modules are structured. 

The [Chenile Tutorial section of the site](https://chenile.org/app-gen-landing-page.html) explains the app-gen portion much better. 

The entire chenile code base resides in Maven Central and hence can be downloaded without being compiled from source. Please see detailed instructions in the documentation pages. 

## Requirements
You need a power shell in Windows or the MAC zsh / Linux bash to execute these scripts. We have tested these scripts across Linux and MAC and we have seen they work. 

You also need to download Node.JS since we use Mustache based templating language to define the code templates. If you want to use the Makefile you need access to Make.

The Makefile automatically downloads Mustache and copies the code to bin folder. 
