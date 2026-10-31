---
title: JGEN
section: 1
header: JGen Manual
footer: jgen 2.1.35
date: October 2026
---

# NAME

jgen - generate projects from blueprints

# SYNOPSIS

**jgen** \[**-c** *config*\]

**jgen** **-f** *input.json* \[**-c** *config*\] \[**\--trust**\] \[**\--offline**\]

**jgen** **-g** *blueprint* \[**-o** *file* | **-e** *folder*\] \[**-c** *config*\] \[**\--trust**\] \[**\--offline**\]

**jgen** **-e** *config-file*

**jgen** **-r** *project* \[**\--into** *folder*\] \[**\--blueprint** *blueprint*\] \[**-c** *config*\] \[**\--trust**\] \[**\--offline**\]

**jgen** **\--fetch** *reference* \[**\--trust**\] \[**\--offline**\]

**jgen** **\--completion** *shell*

**jgen** **-h** | **-V**

# DESCRIPTION

**jgen** generates a project from a *blueprint*. A blueprint is one jar that holds a typed input contract
(*META-INF/blueprint.json*), a template tree, and optional Java hooks. jgen collects the blueprint's inputs, checks
them, copies the template tree into the destination folder, renders it, and runs the blueprint's hooks.

Template files ending in *.mustache* are rendered with Mustache. File and folder names carry further
instructions:

- *\_\_name\_\_* is replaced by an input value;
- a folder named *%%key=value%%* is kept only when the input matches;
- a *.filelist* file is split into several files;
- an empty folder named *%{{blueprint ...}}* generates a child blueprint in its place.

jgen ships with blueprints for Chenile services, workflow services, query modules, mini-monoliths, ecosystems,
process deployments, interceptors, integration tests and new blueprints. It accepts other blueprints without a
rebuild: from blueprint folders (see **FILES**) or straight from a GitHub release (see **BLUEPRINT REFERENCES**).

Every input path (prompts, input files, the web portal, child blueprints) goes through the same validator. The
validator reports every problem at once, before any file is written.

After generating, jgen writes a generation record into the project (see **FILES**). **jgen -r** uses it to
regenerate the project with a newer blueprint and report what changed.

# MODES

jgen does one thing per run. When several mode options are given, the first of **\--fetch**, **-r**, **-g** and
**-e** wins. With none of them, jgen generates a project.

**Interactive generation** (no mode option)
:   If a *config* folder exists in the current directory, jgen offers its files as alternatives to the default
    configuration. It then lists the installed blueprints, asks you to choose one, and prompts for each input.
    Each prompt shows the input's validation rule and default value. Press Enter to accept the default.

**Generation from a file** (**-f**)
:   Reads all inputs from a JSON file and asks nothing. The whole file is validated first, and every error is
    listed. Keys that the blueprint does not declare are reported as warnings and ignored.

**Sample input** (**-g**)
:   Writes a JSON input file for a blueprint, filled with its default values, ready to edit and pass to **-f**.

**Configuration** (**-e** without **-g**)
:   Writes a copy of the default configuration to edit and pass to **-c**.

**Regeneration** (**-r**)
:   Regenerates a project with the current blueprint, into a separate folder, and classifies every file. The
    original project is never modified.

**Fetch** (**\--fetch**)
:   Fetches, verifies and caches a blueprint from GitHub, and describes it.

# OPTIONS

**-c**, **\--config** *file*
:   Use this JGen configuration instead of the bundled default. The configuration sets the Java package segments
    (*com*, *company*, *org*), the Chenile version used in generated projects, and defaults such as the
    destination folder. A missing file is an error. With **-r**, use this configuration instead of the one
    recorded in the project.

**-f**, **\--input-file** *file*
:   Generate from the inputs in *file*, a JSON object. Its **blueprint** key names the blueprint (an installed
    name or a **github:** reference); its other keys are the blueprint's inputs. Values may refer to configuration
    entries with *\${name}*.

**-g**, **\--generate-blueprint-file** *blueprint*
:   Emit a sample input file for *blueprint*, an installed name or a **github:** reference. The sample keeps the
    reference in its **blueprint** key, so **-f** later resolves the same blueprint. Without **-o** or **-e**, the
    sample is printed on standard output.

**-o**, **\--output-file** *file*
:   With **-g**: write the sample to *file*. It cannot be combined with **-e** *folder*.

**-e**, **\--emit-config** \[*name*\]
:   Without **-g**: write the default configuration to *config/name.json*. The *config/* folder is created if
    needed, and *.json* is added if missing.

    With **-g**: *name* is a folder. jgen writes *input-file.json* there, together with any companion sample files
    the blueprint provides (for example a workflow XML or a process definition). It refuses to overwrite existing
    companion files.

**-r**, **\--regenerate** *project*
:   Regenerate *project*, a folder that contains *.jgen/generation.json*. jgen finds the blueprint by its recorded
    name. If that blueprint is not installed, it fetches exactly the recorded reference and checks it against the
    recorded hash. jgen then validates the recorded inputs against the blueprint's current input contract and
    generates into **\--into**. Each file is reported in one of these classes:

    **SAFE_UPDATE** - you did not change it, the blueprint did: take the new file.\
    **KEEP_USER** - you changed it, the blueprint did not: keep yours.\
    **CONFLICT** - changed on both sides: merge.\
    **NEW_FILE** - new in the blueprint: add it.\
    **SAFE_DELETE** - dropped by the blueprint, unchanged by you: delete it.\
    **DELETE_CONFLICT** - dropped by the blueprint, changed by you: decide.\
    **USER_DELETED** - you deleted it, the blueprint still generates it.

    jgen prints the classification, writes it to *project.regeneration.json* next to the regenerated project,
    and suggests a **git diff \--no-index** command.

**\--into** *folder*
:   With **-r**: the folder to regenerate into. Default: *project.regenerated*, next to *project*.

**\--blueprint** *blueprint*
:   With **-r**: regenerate with this blueprint instead of the recorded one, for example
    **github:acme/jgen-terraform@2.0.0**, to see what an upgrade would change. It must have the same name as the
    recorded blueprint.

**\--fetch** *reference*
:   Fetch the blueprint at *reference*, verify it and cache it. jgen prints its name, description, exact source and
    SHA-256, and the **-g** command to use it.

**\--trust**
:   Trust every blueprint source used in this run without asking. Use this in scripts and CI only for sources you
    have reviewed. Prefer listing them in *settings.json* (see **FILES**).

**\--offline**
:   Never contact GitHub. Use only installed and cached blueprints. A reference without a version resolves to the
    newest cached version.

**\--completion** *shell*
:   Print the completion script for *shell*: **bash**, **zsh** or **fish** (see **SHELL COMPLETION**).

**-h**, **\--help**
:   Print a usage summary and exit.

**-V**, **\--version**
:   Print the version and exit.

# BLUEPRINT REFERENCES

A blueprint is named either by its installed name (for example **chenile-service**) or by a reference:

**github:***owner*/*repo*\[/*name*\]\[\@*version*\]\[#sha256:*hex*\]

*owner/repo*
:   The GitHub repository. Its release must carry exactly one blueprint jar, or exactly one named
    *repo-\*.jar*. Jars ending in *-sources*, *-javadoc* and *-tests* are ignored.

*name*
:   Which blueprint to use, when the jar contains several.

*version*
:   A release tag. Without it, jgen uses the latest release (or, with **\--offline**, the newest cached one).

*hex*
:   The expected SHA-256 of the jar, as 64 hexadecimal digits. A mismatch is an error. Pinning is recommended in
    scripts and CI.

Installed blueprints always take precedence. A fetched blueprint whose name is already taken by an installed one
is refused.

# TRUST

A blueprint can contain Java code, which runs with your permissions. jgen therefore:

- downloads only from trusted sources. By default these are the official organisations, **github:ajapros/\***
  and **github:rajakolluru/\***. For any other source jgen asks first: allow once, allow always (which adds the
  repository to *settings.json*), or refuse. Without a terminal it refuses unless the source is listed or
  **\--trust** is given. Nothing is downloaded before trust is granted.
- records the SHA-256 of each fetched version in *blueprints.lock.json* the first time it is used, and refuses
  the version if its jar later changes.
- loads each fetched jar in its own class loader.

# SHELL COMPLETION

Homebrew installs completion for bash, zsh and fish. Otherwise, enable it once:

bash (add to *~/.bashrc*)
:   **source <(jgen \--completion bash)**

zsh (add to *~/.zshrc*, after **compinit**)
:   **source <(jgen \--completion zsh)**

fish
:   **jgen \--completion fish > ~/.config/fish/completions/jgen.fish**

Zip installs also carry the scripts in *completions/*.

Tab completes options. For **-g**, **\--blueprint** and **\--fetch** it offers the installed blueprints,
cached **github:** references and the **github:** prefix. For **-f** and **-c** it offers JSON files, and for
**-r** and **\--into** it offers folders. The blueprint list is cached for ten minutes, or until a jar is added
to *~/.jgen/blueprints*, so Tab does not start Java each time.

# EXIT STATUS

**0**
:   Success.

**1**
:   An error occurred. This includes invalid inputs (every problem is listed), an unknown or untrusted blueprint, a
    hash mismatch, a missing configuration file, a destination folder that already exists, and a failure in a
    blueprint hook. Input and trust errors stop jgen before it writes anything.

# ENVIRONMENT

**JAVA_HOME**
:   The Java runtime to use (Java 25 or later). Otherwise **java** is taken from the PATH, or from Homebrew's
    OpenJDK.

**JAVA_OPTS**
:   Extra JVM options. **-Djgen.record=false** turns off generation records.

**JGEN_BLUEPRINTS**
:   Extra blueprint folders, comma-separated. Every jar in them is loaded at start-up.

**JGEN_HOME**
:   The installation folder. The launcher sets it.

**JGEN_HOME_DIR**
:   Use this folder instead of *~/.jgen* for settings, the lock file and the cache. Useful in CI.

**GITHUB_TOKEN**, **GH_TOKEN**
:   A GitHub token, used to raise the API rate limit when fetching blueprints.

# FILES

*~/.jgen/blueprints/*
:   Your blueprint jars. They are available to every jgen run, including runs through JBang.

*\$JGEN_HOME/blueprints/*
:   Blueprint jars shared by everyone using this installation.

*~/.jgen/settings.json*
:   **trustedSources**: a list of trusted blueprint sources, each **github:***owner*/\* or
    **github:***owner*/*repo*.

*~/.jgen/blueprints.lock.json*
:   The SHA-256 first seen for each fetched blueprint version.

*~/.jgen/cache/github/owner/repo/version/*
:   Cached blueprint jars.

*config/*
:   Configuration files, written by **-e** and offered by interactive generation.

*~/.cache/jgen/blueprint-names*
:   The blueprint list used by shell completion (under *\$XDG_CACHE_HOME* if set). It is safe to delete.

*project/.jgen/generation.json*
:   The generation record. It holds:

    - the blueprint (name, version, jar coordinates, and for fetched blueprints its reference and SHA-256);
    - the configuration;
    - the validated inputs, with copies of file inputs under *.jgen/inputs/*;
    - the SHA-256 of every generated file.

    Commit it with the project.

*project.regeneration.json*
:   The classification written by **-r**.

# EXAMPLES

Generate a Chenile service from an edited sample:

    jgen -g chenile-service -o orders.json
    jgen -f orders.json

Emit a workflow blueprint's input together with its sample workflow XML:

    jgen -g wfcustom -e specs/
    jgen -f specs/input-file.json

Use a blueprint published on GitHub, then pin it for CI (**\--fetch** prints the hash):

    jgen --fetch github:acme/jgen-terraform@1.2.0
    jgen -g github:acme/jgen-terraform@1.2.0#sha256:<hex> -o tf.json
    jgen -f tf.json --trust

Install a blueprint jar of your own:

    cp target/bp-docs-site-1.0.jar ~/.jgen/blueprints/
    jgen -g docs-site -o docs.json

See what a newer blueprint would change in an existing project:

    jgen -r ./output/orders
    jgen -r ./output/infra --blueprint github:acme/jgen-terraform@2.0.0
    git diff --no-index ./output/orders ./output/orders.regenerated/orders

Work without network access:

    jgen -g github:acme/jgen-terraform --offline -o tf.json

# SEE ALSO

**java**(1), **git-diff**(1), **mvn**(1)

The JGen documentation: <https://jgen.chenile.org>. Chenile: <https://chenile.org>.

# BUGS

Report problems at <https://github.com/rajakolluru/chenile-gen/issues>.

# AUTHOR

Raja Shankar Kolluru and the Chenile contributors.

# COPYRIGHT

MIT License. Copyright (c) 2019 Raja Shankar Kolluru.
