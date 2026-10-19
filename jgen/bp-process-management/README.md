# Chenile process management blueprint

`chenile-process-management` is an aggregated blueprint that generates a process
server and a worker mini-monolith from native `processMap` definitions. It can
reuse a service registry, host one in the server (recommended), or create a third
mini-monolith containing the registry. It preserves typed inputs, arbitrary
hierarchies, predecessor chaining, and separate optional cron schedules.

## Generate

From `chenile-gen/jgen`:

```sh
mvn install
jgen-cli/bin/jgen.sh -g chenile-process-management -o process-input.json -e
# Edit process-input.json and its companion process/cron specifications.
jgen-cli/bin/jgen.sh -f process-input.json
# Or use the checked-in example:
jgen-cli/bin/jgen.sh -f bp-process-management/examples/file-upload-input.json
cd output/uploads
mvn install
```

The generated README lists the exact executable JARs, deployment URLs, topic names,
startup order and environment settings. Kafka is the currently supported queue
transport. Generated splitter/executor hooks throw until implemented; they are
application code, not example file-upload implementations.

## Inputs

Existing inputs remain: `application`, `applicationVersion`, `destFolder`,
`processSpec`, optional `cronSpec`, `definitionSource` (`json` or `database`),
`super_pom` and additional server-service `dependencies`.

| Input | Default / visibility |
| --- | --- |
| `registryMode` | `create`; alternatively `existing` |
| `existingRegistryUrl` | Required only for `existing` |
| `registryPlacement` | `server` (recommended); alternatively `separate`; asked only for `create` |
| `serverDeploymentUrl` | `http://localhost:8000` |
| `workerDeploymentUrl` | `http://localhost:8001` |
| `registryDeploymentUrl` | `http://localhost:8002`; asked only for a separate registry |
| `transport` | `kafka` |
| `workTopic` | `<application>-work`; fills absent `config.queue` values |

All generated hosts have distinct deployment URLs. A colocated registry uses the
server URL. Existing per-definition queue names are retained, and the worker
subscribes to every resulting topic. Global JGen configuration supplies package
segments and the Chenile version (minimum 2.1.32).

`visibleWhen` controls interactive CLI prompts, portal visibility and server-side
input validation. Non-interactive files can omit inactive fields. Legacy flat
parent coordinates remain supported. Programmatic preparation still recognizes
legacy `registerInServiceRegistry` plus `serviceRegistryUrl`; new input files use
the explicit registry choices above.

## Generated modules

- `<application>-api`: shared typed payloads and process-start service contracts.
- `<application>-server-config`: native definitions and cron seeds, server only.
- `<application>-server-service`: typed Chenile HTTP services, process bootstrap,
  runtime services and acknowledged Kafka publication; no business workers.
- `<application>-<processType>-workers`: one plain JAR per definition. Leaf types
  get an executor; non-leaf types get a splitter and aggregator in the same JAR.
- `<application>-worker-listener`: a headless Chenile listener, Kafka subscription,
  tenant validation/restoration, process delegate and retry handling.
- `<application>Server` and `<application>Worker`: composed `minimonolith` projects.
- `<application>Registry`: composed only for a separate generated registry.

The worker has no process datasource, process service, JDBC starter or outbox
worker. The server uses exactly one queue-based starter. Process callbacks use
the registry-backed delegate; the server advertises its deployment URL.

The aggregate parses the process specification once and computes a deployment
plan. Its template includes:

```text
%{{chenile-process-workers forEach=processes as=process}}/
%{{minimonolith role=server}}/
%{{minimonolith role=worker}}/
%%separateRegistry=true%%/
  %{{minimonolith role=registry}}/
```

The reusable `chenile-process-workers` blueprint consumes the prepared definition
through scalar fields (`processType`, `name`, `inputType`, `leaf`, `children`),
plus `workerModule`, `application`, `packageName`, version, parent and dependency
inputs. Its payload model is supplied by the application's API JAR.

## Process and cron specifications

The process file retains the runtime's native `processMap` structure, with optional
`inputFields` metadata either at document level or inside a definition. Names and
model types are validated before any generated files are written. Domain config
remains opaque, apart from validating/filling the queue topic required for dispatch.

Cron schedules remain a separate JSON array read by `JsonCrontabRepository`.
Only the server packages and seeds these resources. Bootstrap creates missing
records without overwriting saved administrator changes on restart.

Input names, deployment URLs and the default Kafka topic declare shared
`validation` rules in `blueprint.json`; JGen enforces them before blueprint hooks.
Process hierarchy, cron contracts and deployment relationships remain blueprint
checks. See [declarative input validation](../docs/input-validation.md).

## Verification

Generator tests cover registry choices, hierarchy validation and per-definition
worker expansion. Generated projects include a Cucumber REST test with an explicit
tenant header and committed-process publication to Kafka, a publication-failure
test, and Kafka delivery to the headless worker with tenant restoration and no
datasource. The server and worker tests extend `KafkaBaseTest`, following
`bp-batch`: Docker must be running, and Testcontainers starts and stops Kafka
automatically. No separately started Kafka server is needed for `mvn install`.
The complete split/execute/aggregate and remote callback cycle still depends on
your application-specific worker implementations.

The server initializes the runtime's idempotent outbox/receipt SQL schema;
Hibernate manages the process, definition and crontab entities in the local
default configuration. For an externally managed schema, disable automatic SQL
initialization after provisioning those tables and select schema validation.
