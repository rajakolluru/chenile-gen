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

## Versions and releasing JGen

JGen is released on its own cadence, independently of Chenile. JGen versions are 2.5.x and later; Chenile versions
are 2.1.x. Three rules keep the two apart:

- **JGen builds only against released Chenile.** The parent of `jgen/pom.xml` is a released `chenile-parent`
  (2.1.35). Moving JGen to a newer Chenile means changing that parent after the Chenile release is on Maven
  Central. The `Build JGen` workflow (`.github/workflows/build.yml`) builds from Maven Central only, so a dependency
  on an unreleased Chenile fails there.
- **Generated projects target a released Chenile.** The default `chenileVersion` in
  `jgen/jgen-base/src/main/resources/config.json` is the latest released Chenile. A blueprint that needs a newer
  runtime says so with `sinceVersion`. `jgen --version` prints both versions.
- **JGen's artifacts are `org.chenile.jgen:*`.** `jgen-base` and `jgen-ext-pebble`, which standalone blueprints and
  extensions compile against, are published to Maven Central under `org.chenile.jgen`.

To release, for example, 2.5.1:

1. Set `<revision>` in `jgen/pom.xml` if needed, commit and push.
2. Publish the libraries: `make -C jgen deploy-central version=2.5.1 passphrase=<gpg passphrase>`.
3. Tag and push: `git tag -a 2.5.1 -m "JGen 2.5.1" && git push origin 2.5.1`.

The tag runs `.github/workflows/release.yml`, which builds JGen with `-Drevision=<tag>` and runs JReleaser
(`jreleaser.yml`). JReleaser publishes the GitHub release (the distribution zip and a version-free `jgen-cli.jar`
that `jbang-catalog.json` points at) and updates the Homebrew formula in `ajapros/homebrew-jgen-tap`. The release
itself uses the workflow's built-in token; the formula update needs a `JRELEASER_HOMEBREW_GITHUB_TOKEN` secret (a
fine-grained token for `ajapros/homebrew-jgen-tap` with Contents read and write).

`stm-cli`, which used to live here, is now its own repository (`rajakolluru/stm-cli`) with its own releases. It
installs from the same tap: `brew install ajapros/jgen-tap/stm-cli`.

## Building from source: requirements
You need a power shell in Windows or the MAC zsh / Linux bash to execute these scripts. We have tested these scripts across Linux and MAC and we have seen they work. 

You need maven installed to compile the Java code from source.

## How do you execute this script
$ make clean all # will compile the code from source and make it available
$ source setpath.sh # will set your system PATH variable to point to jgen.sh 

JGen supports optional shared Maven parents and additional dependencies across all
built-in blueprints. See [composable blueprints](jgen/docs/composable-blueprints.md)
for the input contract, dependency destinations, and compatibility rules.

[Aggregated blueprints](jgen/docs/aggregated-blueprints.md) can invoke composable
blueprints from template folders and compute their inputs through a callback.
