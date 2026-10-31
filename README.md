# Chenile App Generator

Chenile is an open source framework for creating Micro services using Java and Spring Boot. 

This repository contains the Chenile Generator. The jgen folder contains Java based code that 
can generate Chenile services and mini monoliths in compliance to various blueprints. 
The list of blue prints can be obtained by typing jgen.sh.
Please see [chenile.org](http://chenile.org) for an 
explanation of how Chenile modules are structured. 

The [Chenile Tutorial section of the site](https://chenile.org/app-gen-landing-page.html) explains the app-gen portion much better. 

The entire chenile code base resides in Maven Central and hence can be downloaded without being compiled from source. Please see detailed instructions in the documentation pages. 

## Install JGen (no build needed)

```bash
brew install ajapros/jgen-tap/jgen                 # macOS / Linux
jbang app install jgen@rajakolluru/chenile-gen      # any OS; JBang fetches Java if needed
```

Or download `jgen-<version>.zip` from the GitHub releases and put its `bin` folder on your PATH. Blueprint jars
dropped into `~/.jgen/blueprints` are picked up without a rebuild. See https://jgen.chenile.org/install/.
Every option is documented in the manual page: `man jgen` (source: `jgen/jgen-cli/src/man/jgen.1.md`), also at
https://jgen.chenile.org/manual/. Tab completion for bash, zsh and fish: Homebrew sets it up; otherwise
`source <(jgen --completion bash)` (or `zsh`), or `jgen --completion fish > ~/.config/fish/completions/jgen.fish`.

## Releasing JGen

Pushing a version tag (e.g. `2.1.35`) runs `.github/workflows/release.yml`, which builds JGen and runs JReleaser
(`jreleaser.yml`). JReleaser publishes the GitHub release (the distribution zip and a version-free `jgen-cli.jar`
that `jbang-catalog.json` points at) and updates the Homebrew formula in `ajapros/homebrew-jgen-tap`. The
workflow needs a `JRELEASER_GITHUB_TOKEN` secret with write access to both repositories, and the Chenile
artifacts of that version must already be on Maven Central.

## Building from source: requirements
You need a power shell in Windows or the MAC zsh / Linux bash to execute these scripts. We have tested these scripts across Linux and MAC and we have seen they work. 

You need maven installed to compile the Java code from source.

## How do you execute this script
$ make clean all # will compile the code from source and make it available
$ source setpath.sh # will set your system PATH variable to point to stm-cli and jgen.sh 

JGen supports optional shared Maven parents and additional dependencies across all
built-in blueprints. See [composable blueprints](jgen/docs/composable-blueprints.md)
for the input contract, dependency destinations, and compatibility rules.

[Aggregated blueprints](jgen/docs/aggregated-blueprints.md) can invoke composable
blueprints from template folders and compute their inputs through a callback.
