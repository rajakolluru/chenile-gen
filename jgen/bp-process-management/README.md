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
jgen-cli/bin/jgen.sh -g chenile-process-management -e process-specs
# Edit process-specs/input-file.json and its companion process/cron specifications.
jgen-cli/bin/jgen.sh -f process-specs/input-file.json
# Or use the checked-in example:
jgen-cli/bin/jgen.sh -f bp-process-management/examples/file-upload-input.json
cd output/uploads
mvn install
```

The generated README lists the exact executable JARs, deployment URLs, topic names,
startup order and environment settings. Kafka is the currently supported queue
transport. Generated splitter/executor hooks throw until implemented; they are
application code, not example file-upload implementations.

For the process manager UI, start the generated server and run `process-ui` from the
`chenile-process-management` checkout with `PROCESS_API_TARGET` set to the server
URL (default `http://localhost:8000`). Enter tenant `default` and the server's
`PROCESS_MANAGEMENT_API_KEY` in the UI. The generated README includes these steps.

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
| `workTopic` | `process-work`; fills absent `config.queue` values |

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

- `<application>-api`: process-start service contracts, depending on each process type's API.
- `<application>-server-config`: native definitions and cron seeds, server only.
- `<application>-server-service`: typed Chenile HTTP services, process bootstrap,
  runtime services and acknowledged Kafka publication; no business workers.
- `<application>-<processType>-workers`: a Maven parent per process type, containing
  `<application>-<processType>-api` and `<application>-<processType>-service` JARs.
  The API owns that process type's input and output models. Leaf types get a typed
  executor; non-leaf types get a splitter and a typed `AggregatedResult<ChildOutput>`
  aggregator in the service JAR.
- `<application>-worker-listener`: a headless Chenile controller and Chenile Kafka consumer,
  exposing the shared `WorkListenerService` contract from `process-api`. Its service selector
  routes each message to the generated executor, splitter or aggregator using the `processType`
  and `workerType` headers; its body selector binds the payload to the selected worker's typed
  input. Topic-to-event bindings, tenant context restoration, bounded retries and dead-letter
  handling are configured by the process-management blueprint.
- `<application>Server` and `<application>Worker`: composed `minimonolith` projects.
- `<application>Registry`: composed only for a separate generated registry.

The process-management blueprint owns queue selection and defaults. It generates the
listener module's Chenile event subscriptions and Kafka topic bindings from that same
deployment plan. The listener is a single module shared by all generated worker JARs.

The worker has no process datasource, process service, JDBC starter or outbox
worker. The server uses exactly one queue-based starter. Process callbacks use
the registry-backed delegate; the server advertises its deployment URL.

The process-worker blueprint module owns process-definition validation and derives
the typed per-process API/service plan, including worker artifact IDs. Process-management
owns queue assignment, the shared listener, server-side definition seeding and
mini-monolith composition. It invokes `chenile-process-worker-set` once with the
process specification; that blueprint owns the loop which invokes the per-type worker
blueprint. Its template includes:

```text
%{{chenile-process-worker-set}}/
%{{minimonolith role=server}}/
%{{minimonolith role=worker}}/
%%separateRegistry=true%%/
  %{{minimonolith role=registry}}/
```

The `chenile-process-worker-set` blueprint owns iteration over the complete definition
set. For each definition it invokes `chenile-process-workers`, which generates the typed
API and service JARs, including child API dependencies. Each process type's contracts
travel with its implementation without depending on a shared application payload module.

## Process and cron specifications

The process file retains the runtime's native `processMap` structure, with optional
`inputFields` and `outputFields` metadata either at document level or inside a
definition. Output fields apply to leaf process types; their generated output model
is `<ProcessType>Output`. Composite output types are
`AggregatedResult<ImmediateChildOutput>` from `process-delegate`, which carries typed
child outputs, completion counts, and errors. Siblings must have the same output type
so their aggregator can retain a single concrete generic type. The executor implements
the input-to-output transform; the aggregator collects child outputs and errors. Names
and model types are validated before
any generated files are written. Domain config
remains opaque, apart from validating/filling the queue topic required for dispatch.

The checked-in processSpec examples include `outputFields` on their leaf definitions
to show the generated output contract. If omitted, the blueprint still generates an
empty `<ProcessType>Output` model.

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
