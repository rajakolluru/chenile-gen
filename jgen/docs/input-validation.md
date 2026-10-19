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
