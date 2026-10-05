# Chenile process management blueprint

`chenile-process-management` generates a domain-neutral mini-monolith from the
runtime's native process definitions. It supports multiple roots, arbitrary
hierarchy depth, multiple child types, predecessor chaining, and optional cron
triggers. It uses a `ProcessDefinitionConfigurator` subclass of the runtime JSON
`ProcessConfigurator` for process documents and `JsonCrontabRepository` for a
separate schedule file. The blueprint only adapts those validated objects to templates.

## Generate

Install the current process-management runtime locally until its dependent
artifacts are published. From `chenile-gen/jgen`:

```sh
mvn install -pl jgen-cli -am
jgen-cli/bin/jgen.sh -g chenile-process-management -o process-input.json
# Add -e to also emit process/cron samples and fill their file references:
jgen-cli/bin/jgen.sh -g chenile-process-management -o process-input.json -e
# Alternatively use the supplied complete example:
jgen-cli/bin/jgen.sh -f bp-process-management/examples/file-upload-input.json
cd output/uploads
mvn install
export PROCESS_MANAGEMENT_API_KEY="$(openssl rand -hex 32)"
java -jar uploads-package/target/uploads-exec.jar --spring.profiles.active=dev
```

Every generated splitter and executor is an application hook that throws an
explicit error until implemented. FileUpload/ChunkUpload are example definitions,
not built-in implementations. Implement file splitting and uploading in the
generated application; the blueprint does not interpret filenames, chunk sizes,
checksums, storage paths, or any other domain configuration.

## Input

CLI fields remain `application`, `applicationVersion`, `destFolder`,
`processSpec`, optional `cronSpec`, `definitionSource` (`json` or `database`), and
`registerInServiceRegistry` (`y`/`n`). Registry publication requires an HTTP(S)
`serviceRegistryUrl`. The global JGen config supplies package segments and
`chenileVersion` (minimum 2.1.31 with the enhanced local runtime). Generation
refuses to overwrite an existing application directory.

The process specification is a typed extension of the framework's `Processes`
document:

```json
{
  "processMap": {
    "Import": {"leaf":false, "config":{"partitionSize":"100"}},
    "Partition": {"parentProcessType":"Import", "leaf":false},
    "Record": {"parentProcessType":"Partition", "leaf":true},
    "Audit": {"parentProcessType":"Partition", "leaf":true}
  },
  "inputFields": {
    "Import": {"batchId":"string"},
    "Partition": {"partitionId":"long"},
    "Record": {"recordId":"string", "overwrite":"boolean"},
    "Audit": {"recordId":"string"}
  }
}
```

- `processMap` keys are process-type Java identifiers, including lowercase types
  such as `feed`, `file`, and `chunk` in the runtime test `def.json`. Their keys
  are preserved; Java classes use capitalized names (`FeedSplitter`, `FeedIn`).
  Values are ordinary `ProcessDef` objects: `parentProcessType`, `leaf`,
  `args`, opaque shared `config`, `predecessorProcessType`, and
  `predecessorArgs` (`INPUT`, `OUTPUT`, or `BOTH`).
- Declare hierarchy with `parentProcessType`; children are derived from those
  definitions, never maintained in a second child list. `leaf` must agree with
  the hierarchy. Every referenced parent/predecessor must exist.
- `args` optionally names the generated DTO (for example `ImportIn`) or its
  fully qualified class name. The simple name determines the generated DTO;
  generation writes its new fully qualified model-package name to runtime definitions.
  Omitted values default to `<CapitalizedProcessType>In`.
- `inputFields` maps process types to DTO field-name/type maps, or can be placed
  directly inside each process definition. Do not declare both for the same type. An omitted entry
  generates an empty DTO. Supported types are string, integer/int, long, boolean,
  and double, or the corresponding boxed Java names.

### Separate cron file

Set `cronSpec` to a file containing a JSON array of native crontabs:

```json
[
  {
    "name": "daily-import",
    "tenant": "default",
    "enabled": false,
    "cronExpression": "0 0 2 * * ?",
    "timezone": "UTC",
    "eventName": "ProcessCreate",
    "payload": {"processDefName":"Import", "args":{"batchId":"daily"}}
  }
]
```

`JsonCrontabRepository` accepts structured `payload`/`headers`, or the native
`eventPayloadJson`/`headersJson` strings (not both forms together).
Timezone defaults to UTC and enabled defaults to true, like `Crontab`; the example
explicitly disables its schedule. Missing IDs are generated deterministically.
No cron file is required. Blueprint ingress currently supports `ProcessCreate`;
its payload arguments must match the selected process's fields and types.

The configurator owns process-name/model validation, hierarchy and predecessor
references/cycles, leaf consistency, field types and process/cron payload checks.
The JSON repository owns schedule and duplicate-name/ID checks. Both JSON readers
reject duplicate JSON keys, ignore unknown properties and retain their previous
configuration if validation fails. The standard runtime reader can load definitions
containing generation metadata without failing. Generic application, package,
version, registry and destination validation remains in JGen's processing chain.

### Migrating earlier blueprint inputs

Replace the old `processes` array with native `processMap` entries. Move
`fields` into top-level `inputFields`, use `args` instead of `inputType`,
and express child relationships with each child's `parentProcessType`. Set
`leaf` explicitly. Remove `implementation`: the generator has no
`fileSplit`, `fileRead`, or other domain-specific modes. The supplied examples
use the new contract, including the three-level file-named scaffold. Move old
`cronTriggers` entries into `cronSpec`, using native `eventName` and `payload`
instead of schedule-level `processType` and `args`.

## Generated application

The Maven reactor contains API, service, configurations, and executable-package
modules. Each process gets a typed local Chenile service and HTTP ingress.
Registry registration is optional and does not affect local service availability.

Leaf executors return domain outputs. Composite splitters emit typed
`SubProcessPayload` objects through the framework helper. Generic aggregators
collect child outputs and report child counts/errors without interpreting output
fields. Customize these application hooks for domain behavior.

Startup loads emitted native `defs.json` through `ProcessConfigurator` in
both JSON and database modes. Database mode inserts only missing rows and clears
the definition cache; restart preserves administrator edits. Cron seeds are read
through `JsonCrontabRepository` and saved through the existing `CrontabService`,
preserving existing schedules and tenant context.

The package reuses the protected process-administration host, JDBC queue,
background worker polling, and optional outbox. All HTTP routes require the
administrator key and tenant header. Production requires a provisioned PostgreSQL
schema and secret configuration; persistent local H2 is development-only.
See the generated README for deployment and worker lifecycle instructions.

## Verification

Blueprint tests cover native configurator parsing, hierarchy/predecessor
validation, opaque configuration, generic workers, typed cron arguments, and
actual template processing for registry on/off and JSON/database definitions.
Generated startup tests cover local services, repeatable seeding, administrator
edit preservation, and cron/process correlation. Business execution tests belong
to the application, not to an example-specific generator branch.
