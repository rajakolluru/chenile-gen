# Declarative input validation

Blueprints select reusable validation rules in `META-INF/blueprint.json`. The
optional `validation` property belongs to an input field, including child fields:

```json
{
  "name": "serviceName",
  "type": "STRING",
  "validation": "JAVA_IDENTIFIER",
  "description": "Java identifier for the service"
}
```

The framework checks the rule during interactive CLI capture, input-file and
portal submission, and again before blueprint hooks or generated file writes.
The execution check also covers inputs supplied by an aggregated blueprint.
Portal field metadata includes the rule; the server enforces it. Failures identify
the field and rule, with indexed paths for nested records.

| Rule | Accepted values |
| --- | --- |
| `JAVA_IDENTIFIER` | An ASCII letter followed by letters, digits or underscores; Java keywords are rejected. |
| `JAVA_TYPE` | A `JAVA_IDENTIFIER` starting with an uppercase letter. |
| `JAVA_PACKAGE` | Dot-separated `JAVA_IDENTIFIER` segments; no empty segments. |
| `PROJECT_NAME` | A letter followed by letters, digits, underscores or hyphens. |
| `VERSION` | Letters, digits, dots, underscores and hyphens. |
| `HTTP_URL` | An absolute HTTP or HTTPS URL with a host. |
| `KAFKA_TOPIC` | 1–249 ASCII letters, digits, dots, underscores or hyphens, excluding `.` and `..`. |

Rule names are case-sensitive. An unknown rule is a blueprint configuration error.
Leaving `validation` out preserves existing field-type validation. Existing
project destination checks and runtime-version checks still run independently.
`required`, defaults, dropdown `validValues`, and `visibleWhen` keep their existing
meaning: hidden fields are skipped, absent optional values are accepted, and
resolved values are validated. Validation does not rewrite a name to make it valid.

For a record array, put rules on its child fields:

```json
{
  "name": "fields",
  "type": "RECORD_ARRAY",
  "required": false,
  "childFields": [
    {"name": "name", "type": "STRING", "validation": "JAVA_IDENTIFIER"}
  ]
}
```

A rule on a multi-select input applies to each selected value. Record children
are validated recursively. Structured Maven inputs retain their `SuperPOM` and
`POM_Dependencies` normalization and coordinate validation.

Blueprint hooks should handle relationships and domain-specific input formats,
such as process hierarchy and cron payload contracts. Common scalar validation
belongs in these declarations. To add another shared rule, extend
`ValidationType` and `InputValidation` in `jgen-base` and document its contract here.

## Validating a whole payload from code

`BlueprintPayloadValidator` (in `jgen-base`, 2.1.35) is the one validator every input path uses: `jgen -f`,
the portal, composed child blueprints through `BlueprintInputService.buildInputMap`, and external callers such
as ChenAIle. It resolves a payload as generation will (config defaults, `${...}` substitution, `visibleWhen`,
boolean and Maven-coordinate normalization) and reports **every** problem, not just the first.

```java
PayloadValidation result = BlueprintPayloadValidator.validate(blueprint, config, json); // config may be null
if (!result.valid()) result.errors().forEach(System.err::println);   // e.g. "fields[0].name: Field fields[0].name must satisfy JAVA_IDENTIFIER"
Map<String, Object> inputs = result.orThrow();                       // resolved inputs, or an exception listing all errors
```

Each `PayloadIssue` has a `path` (`$`, a field name, or a nested path such as `fields[0].name`), a `code` and a
`message`:

| Code | Kind | Meaning |
| --- | --- | --- |
| `INVALID_JSON` | error | Not valid JSON, empty, or a key appears twice (with line and column). |
| `NOT_AN_OBJECT` | error | Valid JSON, but not an object of field values. |
| `REQUIRED` | error | A required field that is visible is missing or empty. |
| `INVALID_VALUE` | error | Wrong type or shape, a value outside `validValues`, or a broken `validation` rule. Booleans must be `true`/`false`/`y`/`n`; other text is no longer silently read as false. |
| `INVALID_INPUT` | error | A generic input every blueprint shares: `com`/`company`/`org` segments, the project name, a `*Version`, or the registry URL. |
| `UNKNOWN_FIELD` | warning | A key the blueprint does not declare; generation ignores it. The `blueprint` key of an input file is exempt. |
| `NOT_VISIBLE` | warning | A field supplied while its `visibleWhen` condition is false; generation ignores it. |

The validator never touches the filesystem. Two checks stay at the generation boundary: the destination folder
must not already exist, and a `FILE` field must name an existing file. `validateFields(...)` runs the field
checks without the generic ones; `buildInputMap` uses it because the generic checks run on the final, composed
map before generation. The older fail-fast helpers (`InputValidation.validateBlueprint` and `validateField`,
`BlueprintValidationUtils.validateGenericInputs`) are now built on the same collect-all checks and throw the
first problem.

With `jgen -f`, the CLI runs the validator on the input file before generating, prints warnings, and stops with
the full list of errors.

## Built-in blueprint declarations

Service, headless service, batch, interceptor, integration-test application,
query namespace, mini-monolith and generated-blueprint names use
`JAVA_IDENTIFIER`: their templates insert those names directly into Java
packages. Ecosystem inputs for each child service, query namespace and monolith
use the same rule, so invalid child names fail before ecosystem generation.
The integration-test entity input uses `PROJECT_NAME` for its route and feature
filename, retaining hyphenated entity names.

Workflow service names retain `PROJECT_NAME` and their existing name
transformations. The ecosystem's outer project and the process worker JAR name
also retain `PROJECT_NAME`, since these are Maven project names. Existing
version, URL, Kafka topic and package-array declarations remain in place.
Free-text instructions, runtime property values, file paths, booleans and
dropdowns continue to use their existing field-type checks.
