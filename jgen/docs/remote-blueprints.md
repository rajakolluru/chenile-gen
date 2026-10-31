# Blueprints from the web

Status: phase 1 implemented in 2.1.35 (GitHub releases). Phases 2–4 are the plan.

JGen finds blueprints the way Maven finds artifacts: installed ones first, then the web. A blueprint is one
jar (its input contract, templates and hooks), so "fetching a blueprint" means fetching one jar, checking it, and
loading it through its own class loader.

## References

A blueprint is named either by its installed name or by a reference:

| Form | Meaning |
| --- | --- |
| `chenile-service` | an installed blueprint (built in, or a jar in a blueprints folder) |
| `github:acme/jgen-terraform` | the latest release of that repository |
| `github:acme/jgen-terraform@1.2.0` | the release tagged `1.2.0` |
| `github:acme/jgen-pack/terraform@1.2.0` | one blueprint, when the jar contains several |
| `…#sha256:<64 hex>` | pins the jar's SHA-256; recommended in CI and scripts |

References work wherever a blueprint name does: `jgen -g`, the `blueprint` key of an input file for `jgen -f`,
`jgen -r --blueprint`, and `jgen --fetch`. A sample emitted with `jgen -g github:…` keeps the reference, so
`jgen -f` resolves the same blueprint.

Installed blueprints always win, and a fetched jar may not replace a blueprint of the same name from somewhere
else: JGen refuses and names both sources.

## Publishing a blueprint on GitHub

- Keep the blueprint in its own repository (the convention is `jgen-<name>`).
- Build the jar with `mvn package` (a plain jar: never shade JGen's own libraries into it; do shade any other
  library your hooks use).
- Attach it to a GitHub release. The release must have exactly one blueprint jar asset, or exactly one named
  `<repo>-*.jar`; `-sources`, `-javadoc` and `-tests` jars are ignored. Tag releases with plain versions.
- Add the topic `jgen-blueprint` to the repository so people can find it.

## Trust

A blueprint jar can contain Java hooks, which run with the user's permissions. So:

1. **Trusted sources.** `~/.jgen/settings.json` lists trusted sources as `github:owner/*` or `github:owner/repo`.
   Out of the box only the official organisations (`github:ajapros/*`, `github:rajakolluru/*`) are trusted.
2. **Asking.** Anything else is fetched only after the user agrees: once, or always (which adds the repository
   to `trustedSources`). Without a console (CI), JGen refuses unless the source is listed or `--trust` is passed.
   Nothing is downloaded before trust is granted.
3. **Hashes.** The SHA-256 of every fetched version is written to `~/.jgen/blueprints.lock.json` on first use and
   checked on every later use, so a release asset that is replaced later is refused. A `#sha256:` pin in the
   reference is checked as well.

## Cache and offline use

Jars are cached in `~/.jgen/cache/github/<owner>/<repo>/<version>/`. With `--offline`, JGen uses only installed
and cached blueprints; a reference without a version picks the newest cached one. Set `GITHUB_TOKEN` to raise
GitHub's API rate limit. `JGEN_HOME_DIR` moves `~/.jgen` elsewhere (useful in CI).

## Class loading

Each fetched jar gets its own class loader, a child of JGen's. Its init hook is created through that loader and
its templates are copied from that jar, even if a folder of the same name exists elsewhere on the class path. Two
fetched blueprints that bundle different versions of a library do not collide.

## Reproducibility

The generation record (`.jgen/generation.json`) stores the reference and SHA-256 of a fetched blueprint.
`jgen -r <project>` uses the installed blueprint of that name if there is one, and otherwise fetches exactly
the recorded version, verified against the recorded hash. `jgen -r <project> --blueprint github:acme/jgen-x@2.0.0`
regenerates with a newer version, so you can see what an upgrade would change.

## CLI

```bash
jgen --fetch github:acme/jgen-terraform          # fetch, verify, cache and describe it
jgen -g github:acme/jgen-terraform@1.2.0 -o tf.json
jgen -f tf.json                                  # tf.json says "blueprint": "github:acme/jgen-terraform@1.2.0"
jgen -g github:stranger/jgen-x --trust           # trust a new source for this run only
jgen -g github:acme/jgen-terraform --offline     # cache only
```

## From code

```java
var resolver = BlueprintResolver.defaults(TrustPrompt.DENY, false, false);
BlueprintConfig blueprint = resolver.resolve("github:acme/jgen-terraform@1.2.0");
```

Classes: `org.chenile.jgen.remote` (`BlueprintReference`, `BlueprintResolver`, `BlueprintFetcher`,
`GitHubFetcher`, `JgenSettings`, `TrustPrompt`) and `Registry.loadJar`.

## Later phases

2. **Catalogs.** JSON indexes in GitHub repositories map short names to sources and hashes
   (`jgen -g terraform@acme`, or a plain name found in a configured catalog), with `jgen blueprints search`, and a
   release workflow template for blueprint authors. Child blueprints named by a fetched parent are resolved
   through the catalogs; in phase 1 they must be installed or live in the same jar.
3. **Maven.** `maven:group:artifact:version` references, from Maven Central or configured repositories (the jar
   only; blueprints bundle their own libraries).
4. **Template-only blueprints.** Blueprints without Java hooks fetched straight from a repository folder or zip,
   with no jar and no code to trust; optional signature verification (GitHub attestations or Sigstore).
