# Chenile process management blueprint

`chenile-process-management` generates a mini-monolith for any number of process
definitions, with arbitrary hierarchy depth and multiple child types per parent.
It does not depend on the old `batch` blueprint or assume one root/child chain.

## Try the FileUpload example

First build/install the current `chenile-process-management` runtime workspace.
The new host and process APIs must be available locally until published.
From `chenile-gen/jgen`:

```sh
mvn install -pl jgen-cli -am
jgen-cli/bin/jgen.sh -g chenile-process-management -o process-input.json
# Or generate the supplied complete FileUpload example directly:
jgen-cli/bin/jgen.sh -f bp-process-management/examples/file-upload-input.json
cd output/uploads
mvn install
export PROCESS_MANAGEMENT_API_KEY="$(openssl rand -hex 32)"
java -jar uploads-package/target/uploads-exec.jar --spring.profiles.active=dev
```

The CLI input references a separate `processSpec` JSON file. The supplied example
creates `FileUploadIn(filename:string)`, `ChunkUploadIn(filename:string)`, a file
splitter, a chunk executor, and `FileUploadAggregator`. It uses byte chunks and
computes SHA-256/byte counts; replace the executor's processing with a real upload
when your destination/protocol is known. It aggregates failed chunk errors, too.
Create `work/incoming/upload.dat`, then call:

```sh
curl -X POST http://127.0.0.1:8080/fileUpload \
  -H "X-Process-Management-Key: $PROCESS_MANAGEMENT_API_KEY" \
  -H 'x-chenile-tenant-id: default' -H 'Content-Type: application/json' \
  -d '{"filename":"incoming/upload.dat"}'
```

Alternatively use the existing React management UI with this host and the same
key/tenant. Enable the seeded disabled `daily-file-upload` cron after the input
file is ready; it generates `ProcessCreate` and records trigger/process lineage.

## Input contract

CLI fields:

- `application`: lowercase Java identifier for project/package/module names.
- `applicationVersion`, `destFolder`: standard JGen coordinates/destination.
- `processSpec`: existing JSON file describing processes and optional cron triggers.
- `definitionSource`: `json` (default) or `database`.
- `registerInServiceRegistry`: `y` or `n` (default `n`). When enabled,
  `serviceRegistryUrl` is required; the mini-monolith includes the registry delegate.

The global JGen config supplies package segments and `chenileVersion` (minimum
2.1.31, with the current enhanced runtime artifacts). Generation refuses to
overwrite an existing application directory.

Specification structure:

```json
{
  "processes": [
    {
      "processType": "Import",
      "inputType": "ImportIn",
      "fields": {"batchId":"string"},
      "children": ["Partition"],
      "config": {"partitionSize":"100"}
    },
    {
      "processType": "Partition",
      "fields": {"partitionId":"long"},
      "children": ["Record","Audit"]
    },
    {"processType":"Record", "fields":{"recordId":"string","overwrite":"boolean"}},
    {"processType":"Audit", "fields":{"recordId":"string"}}
  ],
  "cronTriggers": []
}
```

Each process accepts:

- `processType`: unique Java identifier beginning with an uppercase letter.
- `inputType`: unique model name, default `<ProcessType>In`.
- `fields`: object mapping Java field names to `string`, `integer`/`int`, `long`,
  `boolean` or `double` (corresponding boxed Java type names also accepted).
- `children`: child process type names; absent/empty means leaf. Parents are
  derived automatically. The runtime has a single `parentProcessType`, so a child
  type cannot be declared under different parent types. Multiple roots are allowed.
- `implementation`: `custom` (default), `fileSplit` or `fileRead`. `fileSplit`
  requires exactly one child type and `filename:string` on both inputs;
  `fileRead` requires a leaf with `filename:string`.
- `config`: string-valued framework configuration shared by splitter/executor/aggregator.
- `predecessorProcessType`, `predecessorArgs`: optional completion chaining,
  with `INPUT`, `OUTPUT` or `BOTH` (default). Customize typed successor inputs
  to match predecessor arguments; BOTH arrives as framework input/output arguments.

Custom splitters/executors are extension points, not invented domain behavior.
They throw an explicit exception until implemented. Each composite receives an
aggregator that summarizes direct child outputs and errors. Typed services can
start any generated process, including a child as an independent root.

Cron records accept `name`, `processType`, `cronExpression` (Quartz syntax),
`timezone` (default UTC), `tenant`, typed `args`, and `enabled` (default false).
Arguments must match the declared input fields. Cycles, dangling references,
unsafe names, duplicate models/cron names and invalid cron/timezone/type values
are rejected before template copying.

## Output and persistence

Output is one Maven reactor with `<app>-api`, `<app>-service`,
`<app>-configurations`, and `<app>-package`. The package imports the framework's
tested API host and runs generated workers on the JDBC queue in the background.
Definitions are always emitted as framework `defs.json`. JSON mode loads it;
database mode inserts missing `process_definition` rows and clears the cache.
Cron seeds are persisted through CrontabService. Restarts preserve administrator
edits to definitions/schedules rather than overwriting them.

Every generated process is a Chenile HTTP service with registry registration
controlled by the blueprint input. Registry-free services remain fully callable.
All HTTP routes retain the management host's administrator-key/tenant checks.
Production requires a provisioned PostgreSQL schema and secret configuration;
the local dev H2 profile is not a production migration strategy. See the
generated README for deployment, worker storage, retries and lifecycle details.

## Verification

The blueprint module tests validation and actual template processing for both
registration options and definition sources. Generated projects include startup,
seeding/registry metadata and cron correlation tests. The fileSplit/fileRead
example also includes real HTTP multi-chunk success, missing input and failed
chunk aggregation tests. The generated file test adapts to `config.chunkBytes`.
For custom hierarchies, implement the generated worker hooks and add domain tests.

`examples/three-level-file-processes.json` is also executable as generated:
BatchUpload splits into FileUpload processes, which split again into ChunkUpload
processes. Each level rolls up outputs and failures. Its small chunk sizes keep
the generated success/error tests quick; use appropriate sizes for real files.
