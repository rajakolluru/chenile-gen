# Composable blueprints

Every built-in JGen blueprint accepts `destFolder`, an optional `super_pom`, and
an optional `dependencies` array. These inputs let a caller generate projects
under a shared Maven parent and supply dependencies without changing templates.
Omitting the new inputs preserves the existing generated parent and dependencies.

## Supply a parent and dependencies

```json
{
  "blueprint": "chenile-service",
  "service": "orders",
  "serviceVersion": "1.0.0",
  "destFolder": "./output",
  "jpa": "n",
  "security": "n",
  "super_pom": {
    "groupId": "com.example",
    "artifactId": "application-parent",
    "version": "1.0.0",
    "relativePath": "../pom.xml"
  },
  "dependencies": [
    {
      "groupId": "com.example",
      "artifactId": "shared-contracts",
      "version": "1.0.0"
    }
  ]
}
```

Run this input with `jgen.sh -f input.json`. The parent changes only in the
outermost generated POM. Child modules continue to inherit their generated
project parent. `relativePath` is relative to that outermost POM, not to the
input file or `destFolder`.

The parent requires `groupId`, `artifactId`, and `version`. If `relativePath` is
omitted, it defaults to `../pom.xml`; an explicit empty string emits an empty
`<relativePath>` so Maven resolves the parent from repositories. An omitted,
null, or empty-object `super_pom` retains the default parent when no legacy
parent coordinates are supplied.

A custom parent must provide the dependency and plugin management expected by
the generated project. Typically it inherits `org.chenile:chenile-parent` at the
selected Chenile version.

Dependencies require `groupId` and `artifactId`. A version can be omitted when
Maven dependency management supplies it. Coordinate values support the existing
JGen `${...}` substitutions; unresolved Maven property references are retained.

## Declare inputs in a blueprint

The type names are case-sensitive. Add these entries to `inputFields` in
`META-INF/blueprint.json`:

```json
[
  {
    "name": "super_pom",
    "type": "SuperPOM",
    "description": "Optional parent POM",
    "required": false
  },
  {
    "name": "apiDependencies",
    "type": "POM_Dependencies",
    "description": "Additional dependencies for the API module",
    "required": false
  },
  {
    "name": "serviceDependencies",
    "type": "POM_Dependencies",
    "description": "Additional dependencies for the service module",
    "required": false
  }
]
```

`SuperPOM` and `POM_Dependencies` have built-in coordinate schemas; they do not
need `childFields`. Both types are optional by default. `required: true` requires
a parent object or at least one dependency, respectively. Each dependency field
is processed independently, regardless of its name. An omitted dependency field
becomes an empty array.

The blueprint owns dependency placement. For example, its API POM can render:

```xml
<dependencies>
  {{#apiDependencies}}
  <dependency>
    <groupId>{{groupId}}</groupId>
    <artifactId>{{artifactId}}</artifactId>
    {{#version}}<version>{{version}}</version>{{/version}}
  </dependency>
  {{/apiDependencies}}
</dependencies>
```

Its service POM can render `serviceDependencies` in the same way. The shared
engine validates the values; the templates decide where to emit them.

For `super_pom`, normalization also supplies `parentGroupId`, `parentArtifactId`,
`parentVersion`, and `parentRelativePath`, which the built-in outer POM templates
use to select the supplied parent. New templates may instead render the
structured `super_pom` object directly, with an inverted section for the default
parent. Do not change generated child-module parent references.

## Built-in dependency destinations

Each built-in blueprint declares one `POM_Dependencies` field named
`dependencies`. These entries are added alongside the existing dependencies:

| Blueprint | Destination |
| --- | --- |
| `chenile-service` | `<service>/<service>-service/pom.xml` |
| `chenile-headless-service` | `<service>/<service>-service/pom.xml` |
| `wfservice` | `<service>/<service>-service/pom.xml` |
| `wfcustom` | `<service>/<service>-service/pom.xml` |
| `batch` | `<batch>/<batch>-service/pom.xml` |
| `chenile-process-workers` | `<workerModule>/pom.xml` |
| `chenile-process-management` | `<application>/<application>-server-service/pom.xml` |
| `minimonolith` | `<monolith>/<monolith>-package/pom.xml` |
| `mybatisQuery` | `<namespace>/pom.xml` |
| `chenile-interceptor` | `<interceptorName>/pom.xml` |
| `it` | `bdd-it-<app>/pom.xml` |
| `jgen-blueprint` | `bp-<blueprintName>/pom.xml` |
| `chenile-ecosystem` | Ecosystem root `pom.xml`; inherited by its generated children |

## CLI, portal, and programmatic callers

Input files use the JSON objects and arrays shown above. Interactive CLI mode
asks whether to set a parent, then captures its coordinates, and collects
repeated dependency records. The portal exposes these types as JSON text areas
and publishes their built-in coordinate fields in its blueprint schema.

`jgen.sh -g <blueprint> -o input.json` emits `super_pom: null` and empty dependency
arrays. They can be left unchanged to use the existing defaults.

Programmatic callers can pass the same maps and lists through
`BlueprintInputService.buildInputMap(...)` and `BlueprintExecutor.execute(...)`.
The execution boundary also validates and normalizes these types before any
blueprint hooks or generated file writes. [Aggregated blueprints](aggregated-blueprints.md) use this same input contract
when their callbacks supply inputs to included child blueprints.

## Backward compatibility

Existing flat parent inputs remain supported: `parentGroupId`,
`parentArtifactId`, `parentVersion`, and `parentRelativePath`. A supplied
structured parent takes precedence over these aliases.

Existing mini-monolith dependency entries remain supported:

```json
{
  "dependencies": [
    {
      "dependencyGroup": "com.example",
      "dependencyName": "orders-service",
      "dependencyVersion": "1.0.0"
    }
  ]
}
```

Canonical `groupId`, `artifactId`, and `version` values take precedence when both
forms appear in an entry. Normalized dependency entries retain both sets of keys
so existing template consumers continue to work. The existing ecosystem
composer can therefore continue supplying its legacy parent and dependency
inputs unchanged.

The mini-monolith also accepts an optional `deploymentUrl`, `runtimeProperties` records
(`key`, `value`), and optional `scanPackages`, `entityPackages`, and
`repositoryPackages` records (`packageName`). Persistence scans are emitted only
in a JPA-enabled deployable host. These inputs let aggregates configure their
hosts through the mini-monolith templates.
