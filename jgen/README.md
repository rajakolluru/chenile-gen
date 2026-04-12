# JGen

JGen is a Java-based code generator for the Chenile ecosystem. It packages a shared generation engine, a CLI, and a set of blueprint modules that generate different kinds of Chenile projects.

At a high level:

1. The CLI loads blueprint plugins from the classpath.
2. The user selects a blueprint and provides inputs.
3. The generation engine copies that blueprint's template tree into an output folder.
4. The engine renders templates, renames placeholder paths, applies conditional folders, and runs any blueprint-specific hooks.

## Repository Structure

- `jgen-base`: core generation engine, blueprint model, config loading, and file-processing pipeline.
- `jgen-cli`: command-line entrypoint and interactive/non-interactive input capture.
- `bp-*`: blueprint plugins. Each plugin contributes:
  - `META-INF/blueprint.json` to declare the blueprint
  - an optional `Init...Blueprint` hook
  - a template folder under `src/main/resources`

## Available Blueprints

The repository currently packages these built-in blueprints:

- `chenile-service`: generates a Chenile service
- `wfservice`: generates a workflow service
- `wfcustom`: generates a custom workflow service from a workflow XML file
- `chenile-interceptor`: generates a Chenile interceptor
- `batch`: generates a batch process from a batch definition JSON file
- `it`: generates an integration test project
- `minimonolith`: generates a mini monolith
- `mybatisQuery`: generates a MyBatis query module
- `jgen-blueprint`: generates a new JGen blueprint module

These names are the values used with `-g` and in input JSON files under the `blueprint` field.

## Build

Build the full multi-module Maven project:

```bash
make all
```

This builds the modules and prepares the CLI wrapper script under `jgen-cli/bin/`.

## CLI Overview

The CLI entrypoint is `jgen`. The main implementation is in `jgen-cli/src/main/java/org/chenile/jgen/cli/GenMain.java`.

It supports three main workflows:

- interactive generation
- generation from an input JSON file
- utility commands to emit config and sample blueprint input files

The helper launcher script is:

```bash
jgen-cli/bin/jgen.sh
```

It finds the latest built `jgen-cli-*.jar` in `target/` and runs it with `java -jar`.

Internally, the CLI does very little generation work itself. Its responsibility is to:

1. load config
2. discover blueprints
3. collect and validate user input
4. hand the selected blueprint and input map to the shared executor in `jgen-base`

## CLI Options

- `-c, --config`: load a config JSON instead of the bundled default config
- `-e, --emit-config`: copy the default config template into `config/`
- `-f, --input-file`: read blueprint inputs from a JSON file
- `-g, --generate-blueprint-file`: generate a sample input JSON for a specific blueprint
- `-o, --output-file`: write the generated sample input JSON to a file

## Interactive Flow

Running `jgen` without `-f` starts interactive mode.

The flow is:

1. If a local `config/` folder exists, the CLI offers those config files as choices.
2. It loads all registered blueprints from the classpath.
3. It displays the available blueprints as a menu.
4. It prompts for each declared input field.
5. It executes the selected blueprint through the shared generation pipeline.

Input prompting and validation are implemented in `jgen-cli/src/main/java/org/chenile/jgen/cli/InputCapture.java`.

Defaults such as `${defaultVersion}` are resolved against the chosen config. Boolean fields accept `y` or `n`. File fields must point to existing files.

If the user presses enter on a prompt, the default value is used when one exists. If the field has no default, input is required before execution can continue.

## Non-Interactive Flow

Running with `-f <input.json>` skips prompting and reads inputs from JSON.

The input file must include:

```json
{
  "blueprint": "chenile-api"
}
```

along with any other fields required by that blueprint.

The CLI uses the `blueprint` value to select the blueprint definition, fills any missing values from defaults, validates the result, and then runs generation.

A typical non-interactive input file for `chenile-service` looks like this:

```json
{
  "blueprint": "chenile-api",
  "service": "orders",
  "serviceVersion": "0.0.1-SNAPSHOT",
  "destFolder": "./output",
  "security": "n",
  "jpa": "y",
  "cloudSwitchEnabled": "n"
}
```

Boolean values are expected in the CLI's own `y` and `n` format at input time. During processing, enabled booleans are normalized into the template map as `"true"`.

## Generating a Sample Input File

The `-g` option emits a sample JSON file for a named blueprint based on that blueprint's declared input fields.

Example:

```bash
jgen-cli/bin/jgen.sh -g chenile-api -o api-input.json
```

That file can then be edited and used for non-interactive generation:

```bash
jgen-cli/bin/jgen.sh -f api-input.json
```

This is the safest way to learn a blueprint because the generated JSON shows exactly which fields the blueprint expects.

## Configuration

The default generator config is packaged in:

`jgen-base/src/main/resources/config.json`

It provides values such as:

- package segments: `com`, `company`, `org`
- Chenile versions
- default service name
- default version
- default destination folder

These config values are merged into the runtime input map before template rendering, so templates can reference both blueprint inputs and global defaults.

For example, a blueprint field may declare:

```json
{
  "name": "destFolder",
  "defaultValue": "${defaultDestFolder}"
}
```

At runtime, that placeholder is resolved from the selected config file before prompting or validation.

## How Blueprints Plug In

Blueprint discovery is handled by the registry in `jgen-base`. It scans the classpath for every `META-INF/blueprint.json`, deserializes each file into a blueprint definition, and invokes the blueprint's optional init hook.

Each blueprint declares:

- `name`
- `description`
- `templateFolder`
- `inputFields`
- an optional `initHook`

Some blueprints only derive additional template variables such as capitalized names. Others do more advanced work, such as reading workflow XML or batch JSON and injecting derived structures into the template map before generation.

A typical blueprint module looks like this:

```text
bp-something/
  src/main/java/.../InitSomethingBlueprint.java
  src/main/resources/META-INF/blueprint.json
  src/main/resources/something-template/...
```

The JSON file is declarative. It defines the blueprint's public contract. The Java init hook is imperative. It adds any computed values that the templates need.

Examples of blueprint-specific hook behavior in this repository:

- `bp-service`, `bp-wfservice`, and similar modules derive capitalized names like `Service`
- `bp-wfcustom` parses a workflow XML file and injects workflow/testcase data into the template map
- `bp-batch` parses a batch definition file and computes root and child process metadata before generation

## Generation Pipeline

The shared execution pipeline lives in:

`jgen-base/src/main/resources/org/chenile/jgen/template/processors.xml`

The pipeline performs these steps:

1. merge config defaults into the input map
2. run blueprint pre-generation hooks
3. copy the template folder into the destination
4. load helper lambdas for templates
5. render `.mustache` files
6. expand `.filelist` outputs into multiple files
7. replace placeholders in file and folder names such as `__service__`
8. process conditional folders such as `%%jpa=true%%`
9. run blueprint post-processing hooks

The execution path in code is:

1. `GenMain` prepares the `Map<String,Object>` input map
2. `BlueprintExecutor` wraps that map in a `TemplateContext`
3. the orchestration engine runs the processors listed in `processors.xml`
4. each processor mutates the output tree or the context

This means the overall behavior is intentionally data-driven. The CLI does selection and validation, while the processor chain performs the file generation work.

## Template Conventions

The template folders under each `bp-*` module are the most important part of the repository. Several naming conventions are used throughout the templates.

### 1. Mustache files

Files ending in `.mustache` are rendered with Mustache and then written without the suffix.

Example:

- template file: `__Service__Application.java.mustache`
- generated file: `OrdersApplication.java`

### 2. Placeholder names in file and folder paths

File and directory names can contain placeholders of the form `__name__`.

Example:

- template folder: `__service__/__service__-service`
- generated folder for service `orders`: `orders/orders-service`

These replacements happen after file contents are rendered.

### 3. Conditional folders

Folders named like `%%key=value%%` are conditional.

Example:

- `%%jpa=true%%`

If the input map contains `jpa=true`, the contents of that folder are moved into the parent directory. Otherwise the folder is discarded.

This is how one blueprint can package alternative file trees for optional features such as JPA or security.

### 4. File lists

Files ending in `.filelist` are synthetic multi-file containers. After rendering, they are split into separate files.

The internal format is:

```text
--START--abc.txt
contents of abc.txt
--END--
--START--xyz.txt
contents of xyz.txt
--END--
```

This is useful when a template needs to generate a variable number of related files from one source template.

## Worked Example

Suppose you want to generate a Chenile service named `orders`.

1. Build the project:

```bash
make all
```

2. Generate a sample input file:

```bash
jgen-cli/bin/jgen.sh -g chenile-api -o orders.json
```

3. Edit `orders.json`:

```json
{
  "blueprint": "chenile-api",
  "service": "orders",
  "serviceVersion": "0.0.1-SNAPSHOT",
  "destFolder": "./output",
  "security": "n",
  "jpa": "y",
  "cloudSwitchEnabled": "n"
}
```

4. Run generation:

```bash
jgen-cli/bin/jgen.sh -f orders.json
```

What happens next:

1. the service blueprint is selected from the registry
2. its init hook derives `Service=Orders`
3. the `service-template` tree is copied into `./output`
4. `.mustache` files are rendered using the input/config map
5. path placeholders such as `__service__` and `__Service__` are renamed
6. conditional folders such as `%%jpa=true%%` are applied
7. the output becomes a ready-to-build generated service project

## Creating a New Blueprint With `jgen-blueprint`

The repository includes a blueprint named `jgen-blueprint` whose job is to generate the skeleton of a new blueprint module.

This is useful when you want to add a new `bp-*` module without starting from an empty directory. It does not generate a complete blueprint implementation. It generates the module shell that you then finish manually.

### What `jgen-blueprint` Generates

The scaffold generator creates a new module with the usual blueprint layout:

```text
bp-<blueprintName>/
  pom.xml
  src/main/java/org/chenile/jgen/blueprint/<blueprintName>/package-info.java
  src/main/java/org/chenile/jgen/blueprint/<blueprintName>/Init<BlueprintName>Blueprint.java
  src/main/resources/META-INF/blueprint.json
```

The generated files come from the templates under:

`bp-jgen-blueprint/src/main/resources/blueprint-template`

### Step 1: Generate the Skeleton

Build the repository first:

```bash
make all
```

Generate a sample input file for the scaffold generator:

```bash
jgen-cli/bin/jgen.sh -g jgen-blueprint -o new-blueprint.json
```

Edit the file. Example:

```json
{
  "blueprint": "jgen-blueprint",
  "blueprintName": "invoice",
  "destFolder": "./output",
  "security": "n",
  "jpa": "y",
  "cloudSwitchEnabled": "n"
}
```

Run the generator:

```bash
jgen-cli/bin/jgen.sh -f new-blueprint.json
```

That produces a new module such as:

```text
./output/bp-invoice
```

### Step 2: Understand What You Still Need To Do

The generated module is only a starting point. It gives you:

- a Maven module
- a blueprint descriptor
- an init hook class
- a Java package

It does not give you the actual blueprint template tree that defines what your new blueprint should generate.

You still need to:

1. create the template folder declared in the generated `META-INF/blueprint.json`
2. put the actual generated project structure under that folder
3. adjust the input fields for your use case
4. fix and extend the generated init hook
5. wire the new module into the parent build and CLI

### Step 3: Add the Template Tree

In the generated `META-INF/blueprint.json`, you will see a `templateFolder` such as:

```json
"templateFolder": "invoice-template"
```

Create that folder under:

```text
src/main/resources/invoice-template/
```

This folder should contain the file and directory structure that your blueprint will generate. Use the existing modules such as `bp-service` or `bp-wfservice` as references.

For example, if your blueprint is meant to generate a project called `invoice-service`, your resource tree may contain folders like:

```text
src/main/resources/invoice-template/__invoice__/__invoice__-service/...
```

At generation time, placeholders in these paths are replaced from the input map.

### Step 4: Edit the Generated `blueprint.json`

The generated blueprint descriptor is the CLI contract for your blueprint. It defines:

- the blueprint name
- its description
- the template folder
- the fields the user must supply
- optional feature flags

You should edit the generated `src/main/resources/META-INF/blueprint.json` so the fields reflect your domain.

For example, if your blueprint generates an invoice module, you might want fields like:

- `invoice`
- `invoiceVersion`
- `destFolder`
- `security`
- `jpa`

If your blueprint needs an external definition file, you can add a `FILE` field.

### Step 5: Fix and Extend the Init Hook

The generated init class is a stub, not a finished implementation.

Its job is to populate derived values in the template map before file generation starts. Typical examples are:

- capitalized names such as `Invoice`
- derived package fragments
- parsed XML or JSON content
- post-generation copy logic

One important detail: the current scaffolded init template does not fully line up with the generated `blueprint.json`. You should review it immediately after generation and make sure it reads the correct field names from the map.

For a simple blueprint, a good init hook often looks like the existing service blueprint:

```java
public void init(BlueprintConfig blueprintConfig) {
    blueprintConfig.postInputCaptureHook = (Map<String,Object> map) -> {
        String invoice = (String) map.get("invoice");
        map.put("Invoice", CapUtils.capitalizeFirst(invoice));
    };
}
```

If you need more advanced behavior, look at:

- `bp-service` for simple field derivation
- `bp-wfcustom` for XML parsing and structured map enrichment
- `bp-batch` for file-driven preprocessing plus post-generation copy behavior

### Step 6: Use the Template Conventions

Your blueprint templates can use the same conventions as the built-in blueprints:

- `.mustache` files for templated file contents
- `__name__` placeholders in filenames and folder names
- `%%key=value%%` folders for conditional inclusion
- `.filelist` files when one template needs to emit multiple files

These features are implemented by the shared processor chain in `jgen-base`, so your blueprint gets them automatically.

### Step 7: Wire the New Module Into This Repository

If you want your new blueprint to be available from the built CLI in this repository, you need to add it to the build.

Update the root `pom.xml`:

- add the new module under `<modules>`
- add it under `<dependencyManagement>` if you want it managed like the existing blueprints

Update `jgen-cli/pom.xml`:

- add the new blueprint module as a dependency

This is required because blueprint discovery is classpath-based. If the module is not on the CLI classpath, the registry will not see its `META-INF/blueprint.json`.

### Step 8: Test the New Blueprint

Once the module is wired in:

1. rebuild the repository
2. generate a sample input JSON for your new blueprint with `-g`
3. run it with `-f`
4. inspect the generated output tree
5. iterate on the template folder and init hook until the generated output is correct

The fastest way to debug a blueprint is usually:

1. confirm the input map is correct
2. confirm the template resource tree matches the intended output shape
3. inspect conditional folders and placeholder names
4. inspect the generated output tree after one run

## Where The Real Logic Lives

The most important thing to understand when working on this repository is that the Java code is only half of the system.

- `jgen-base` defines the generation framework
- `jgen-cli` handles user interaction and input files
- the blueprint modules define the actual generated output

In practice, many user-visible changes come from editing template resources rather than changing Java classes. If a generated file is wrong, the first place to look is usually the blueprint template tree that produced it.

## Notes for Contributors

- If you want to add a new generator, follow the `bp-*` module pattern.
- If you want to change global generation behavior, start in `jgen-base`.
- If a generated project looks wrong for only one blueprint, inspect that blueprint's resource templates first. Much of the repository's behavior is encoded in templates rather than Java logic.
