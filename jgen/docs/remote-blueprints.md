# Blueprints from the web

Status: GitHub releases since 2.1.35; template-only blueprints (from a repository or a local folder) since 2.1.36. catalogs since 2.1.36. Maven references and signatures are the plan.

JGen finds blueprints the way Maven finds artifacts: installed ones first, then the web. A blueprint is one
jar (its input contract, templates and hooks), so "fetching a blueprint" means fetching one jar, checking it, and
loading it through its own class loader.

## References

A blueprint is named either by its installed name or by a reference:

| Form | Meaning |
| --- | --- |
| `chenile-service` | an installed blueprint (built in, or a jar in a blueprints folder) |
| `terraform`, `terraform@1.2.0`, `acme/terraform` | a short name from a [catalog](https://jgen.chenile.org/catalogs/) (2.1.36), if not installed |
| `github:acme/jgen-terraform` | the latest release of that repository |
| `github:acme/jgen-terraform@1.2.0` | the release tagged `1.2.0` |
| `github:acme/jgen-pack/terraform@1.2.0` | one blueprint, when the jar contains several |
| `…#sha256:<64 hex>` | pins the SHA-256 (of the jar, or of a template-only blueprint's files); recommended in CI and scripts |
| `dir:./jgen-terraform` | a template-only blueprint in a local folder (2.1.36), for authoring |

References work wherever a blueprint name does: `jgen -g`, the `blueprint` key of an input file for `jgen -f`,
`jgen -r --blueprint`, and `jgen --fetch`. A sample emitted with `jgen -g github:…` keeps the reference, so
`jgen -f` resolves the same blueprint.

Installed blueprints always win, and a fetched jar may not replace a blueprint of the same name from somewhere
else: JGen refuses and names both sources.

## Publishing a blueprint on GitHub

The quickest start is `jgen -g jgen-blueprint` with `standaloneRepo` set to `y`. It generates a self-contained
blueprint project that is already a local Git repository with one commit, including a release workflow that
follows the convention below. Create the GitHub repository, add it as `origin`, push, and push a version tag.
See [Write your own blueprint](https://jgen.chenile.org/authoring/).

The convention, if you set a repository up by hand:

- Keep the blueprint in its own repository (the convention is `jgen-<name>`).
- Build the jar with `mvn package` (a plain jar: never shade JGen's own libraries into it; do shade any other
  library your hooks use).
- Attach it to a GitHub release. The release must have exactly one blueprint jar asset, or exactly one named
  `<repo>-*.jar`; `-sources`, `-javadoc` and `-tests` jars are ignored. Tag releases with plain versions.
- Add the topic `jgen-blueprint` to the repository so people can find it.

## Template-only blueprints (2.1.36)

A blueprint that needs no Java (no init hook; [JavaScript hooks](https://jgen.chenile.org/scripting/) are fine) can be used straight from its repository, with no jar to build or
release. This suits most blueprints for things that are not Chenile: a Terraform module, a docs site, a GitHub
Actions starter.

**Layout.** A template-only repository is laid out exactly like a blueprint jar's resources, so you can add Java
and build it into a jar later without moving anything:

```text
jgen-terraform/
  README.md
  src/main/resources/META-INF/blueprint.json      # declares templateFolder: terraform-template, no initHook
  src/main/resources/terraform-template/__project__/main.tf.mustache
  src/main/resources/terraform-template/__project__/_DoT_gitignore
```

All the template markup works: Mustache, `__key__`, `_DoT_`, `%%key=value%%`, `.filelist` and declared
validation rules. Child blueprint markers also work, but the children must be installed. The generated project can
also become a Git repository through [`initGit`](https://jgen.chenile.org/authoring/#generating-a-git-repository-initgit).

**Publishing.** Push a version tag. A GitHub release is optional. For `github:owner/repo@tag`, JGen uses the
release's blueprint jar if it has one; otherwise it downloads the repository's source archive at that tag. Without a
version, it uses the latest release, or else the highest version tag.

**Trust and hashes.** These are exactly as for jars:

- the source must be trusted, or you are asked first;
- the hash is locked on first use;
- `#sha256:` pins work.

The hash is taken over the blueprint's files, not over the archive, so it stays stable if GitHub re-packs the tag.
A descriptor that declares an `initHook` is refused: Java hooks need classes, so publish that blueprint as a jar,
or write its hooks in [JavaScript](https://jgen.chenile.org/scripting/) instead.

**Local folders while you write one.** `dir:<folder>` uses a template-only blueprint from your disk. The folder can
be the repository or its `src/main/resources`. It is not cached or locked, and your edits apply on the next run:

```bash
jgen -g dir:./jgen-terraform -o tf.json
jgen -f tf.json
jgen -r ./output/infra            # after editing the templates: see what changed
```

## Catalogs (2.1.36)

A catalog is a `catalog.json` at the root of a GitHub repository's default branch. It maps short names to `github:`
sources, with optional description, tags, version pins and the blueprint name inside a multi-blueprint jar.

- **Search order:** the official catalog, `github:ajapros/jgen-catalog`, comes first and is trusted. Then come the
  catalogs listed under `catalogs` in `~/.jgen/settings.json`, as `{source, alias, trusted}`.
- **Naming:** a plain name that is not installed is looked up in order. `alias/name` picks a catalog, and
  `name@version` a version.
- **CLI:** `jgen --search [text]` lists matches.
- **Caching:** catalogs are cached for an hour under `~/.jgen/cache/catalogs/`.
- **Code:** `Catalog`, `Catalogs`, and `BlueprintResolver.resolveName`. Full description:
  https://jgen.chenile.org/catalogs/

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

2. **Catalogs.** Shipped in 2.1.36: short names, `jgen --search`, the official catalog and team catalogs. See
   [blueprint catalogs](https://jgen.chenile.org/catalogs/). Still open: resolving child blueprints named by a fetched parent through the
   catalogs (today they must be installed or live in the same jar).
3. **Maven.** `maven:group:artifact:version` references, from Maven Central or configured repositories (the jar
   only; blueprints bundle their own libraries).
4. **Signatures.** Optional verification of GitHub artifact attestations or Sigstore signatures. Template-only
   blueprints, originally planned here, shipped in 2.1.36.
