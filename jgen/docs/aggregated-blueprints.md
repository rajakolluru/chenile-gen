# Aggregated blueprints

An aggregated blueprint includes other registered blueprints using special
placeholder folders in its template tree. The enclosing blueprint computes each
child's inputs through a callback. The child then runs its usual defaults,
validation, hooks, and generation pipeline using the same JGen configuration.

## Marker syntax

```text
%{{chenile-service name=orders}}
%{{minimonolith role=server}}
%{{minimonolith role=worker label="Background workers"}}
```

The first token is the exact registered blueprint name, not its Maven module
name. Remaining tokens are `key=value` attributes, passed to the callback as a
`Map<String, String>`. Values remain strings, including `true` and numbers.
Attributes are not automatically merged into the child's input map.

Use single or double quotes for values containing spaces or equals signs. An
empty value must be quoted, for example `label=''`. Within a quoted value,
backslash can escape the matching quote or a backslash. Duplicate keys,
unterminated quotes, missing values, and malformed markers are rejected.
Attribute names begin with a letter or underscore and may contain letters,
digits, underscores, dots, and hyphens. Use no spaces around `=`.

Markers are literal folder names. Attributes are not Mustache expressions or
JGen filename substitutions; the callback evaluates them using the enclosing
blueprint's current inputs. Folder names must obey the host filesystem's rules.
Pass complex values and paths through the input map rather than placing them in
a marker attribute.

## Template layout and output placement

For example:

```text
aggregate-template/
  __application__/
    pom.xml.mustache
    %{{chenile-service name=orders}}/
      .gitkeep
    %{{minimonolith role=server}}/
      .gitkeep
    %{{minimonolith role=worker}}/
      .gitkeep
```

Markers must be empty directories. An empty `.gitkeep` is allowed so Git can
retain them. Other contents are rejected.

If the aggregate is named `example` and its callback names the mini-monoliths
`server` and `worker`, the output is:

```text
example/
  pom.xml
  orders/
    pom.xml
    orders-api/
    orders-service/
  server/
    pom.xml
    server-configurations/
    server-package/
  worker/
    pom.xml
    worker-configurations/
    worker-package/
```

The marker disappears. Its parent directory receives the child's normal output
tree. JGen controls the effective child `destFolder` from this location; a value
returned by the callback cannot redirect output elsewhere.

The aggregate owns its reactor POM, module list, and dependency relationships.
JGen does not infer or insert them. Supply `super_pom` and the child's declared
`POM_Dependencies` fields using the [composable input contract](composable-blueprints.md).
A child's parent `relativePath` is relative to its generated outermost POM.

## Conditionally include a child

Enclose the marker in a standard conditional folder:

```text
__application__/
  %%includeWorker=true%%/
    %{{minimonolith role=worker}}/
      .gitkeep
```

Declare `includeWorker` as a `BOOLEAN` input in the aggregate's `blueprint.json`.
When it is true, the worker blueprint expands beside the aggregate's other
modules. When it is false, the whole conditional section is removed before
expansion: JGen does not resolve that child blueprint, evaluate its attributes,
invoke the input callback, or generate its files. This also applies when the
marker is several directories below the conditional folder.

## Register the callback

Add `childBlueprintInputs` in the aggregate's existing `InitHook`. Its functional
interface is:

```java
Map<String, Object> computeChildInputs(
    Map<String, Object> currentInputs,
    String blueprintName,
    Map<String, String> attributes);
```

The callback receives the enclosing blueprint's current inputs, including its
computed values, and a separate attribute map for that specific marker. It must
return a non-null map of the child's declared inputs. Required inputs must be
supplied; omitted optional inputs use the child's existing defaults.

For example, an aggregate declaring `application` and `applicationVersion` can
register this callback:

```java
public void init(BlueprintConfig blueprint) {
    blueprint.childBlueprintInputs = (current, childName, attributes) -> {
        Map<String, Object> child = new LinkedHashMap<>();
        String version = current.get("applicationVersion").toString();
        String parentGroup = current.get("com") + "." + current.get("company")
                + "." + current.get("org") + "." + current.get("application");
        child.put("super_pom", Map.of(
                "groupId", parentGroup,
                "artifactId", current.get("application") + "-parent",
                "version", version,
                "relativePath", "../pom.xml"));
        switch (childName) {
            case "chenile-service" -> {
                child.put("service", attributes.get("name"));
                child.put("serviceVersion", version);
            }
            case "minimonolith" -> {
                String role = attributes.get("role");
                if (!Set.of("server", "worker").contains(role))
                    throw new IllegalArgumentException("Unknown mini-monolith role: " + role);
                child.put("monolith", role);
                child.put("monolithVersion", version);
                // Compute this invocation's dependencies and options here.
            }
            default -> throw new IllegalArgumentException("Unexpected child: " + childName);
        }
        return child;
    };
}
```

Use the usual `BlueprintConfig`/`InitHook` imports and `java.util` maps and sets.
No aggregate flag or new JSON hook configuration is needed; the existing
`initHook` registers the callback. Included blueprint modules must be available
on the CLI or portal classpath so their descriptors enter the registry.

## Repeat a child for a prepared list

```text
%{{chenile-process-workers forEach=processes as=process}}/
  .gitkeep
```

`forEach` and `as` are reserved expansion attributes. The first references a list
in the enclosing input map (dotted map paths are supported); the second names the
binding for each element. Both attributes are still passed as strings to the
callback. Each invocation receives an isolated map with `currentInputs.get("process")`
set to its element. The callback computes the child's usual declared inputs.
Existing markers without these attributes invoke once.

An empty list removes the marker without invoking the callback. Missing or
incorrectly typed lists, an invalid alias, and `as` without `forEach` are errors.
All iterations are generated and checked for collisions before this marker's
outputs are published. Conditional folders can enclose loop markers too. Loop
handling remains entirely within the `blueprint-expander` chain command.

## Lifecycle and isolation

1. The enclosing blueprint captures and validates inputs and runs its input hook.
2. Its templates render, filenames resolve, and conditional folders are selected.
3. JGen finds the remaining markers and calls `childBlueprintInputs` for each.
4. Each child runs its own complete generation pipeline in a staging directory.
5. JGen checks for output collisions, moves the generated tree beside the marker,
   and removes the marker.
6. The enclosing blueprint's final post-processing hook runs after all children.

Markers in excluded conditional folders are never invoked. Repeated invocations
of the same blueprint are supported; attributes distinguish their roles. A
child may itself be an aggregate and uses its own callback for its children.
Inclusion cycles such as `A -> B -> A` are rejected.

Maps and lists passed to the callback are copied recursively. Returned maps and
lists are also copied before normalization and child hooks run. They do not
share mutable collection state with the enclosing blueprint or other children.
Custom mutable objects in input maps remain the caller's responsibility.
Attributes are read-only strings. Child output is not rerendered using the
ancestor's inputs.

Unknown child names, missing callbacks, invalid child inputs, nonempty marker
folders, recursion, and output collisions fail generation with identifying
errors. Existing files are never merged or overwritten by child expansion.
Failed child staging directories are cleaned up. Generation is not a transaction
across the entire aggregate: parent files and earlier successful children can
remain after a later child fails; use a fresh output destination when retrying.

Existing blueprints without markers continue to use the same generation flow.
The existing ecosystem composer also remains supported.
