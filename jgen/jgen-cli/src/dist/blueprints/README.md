# Blueprints folder

Drop blueprint jars here and JGen picks them up on its next run: no rebuild needed.

A blueprint jar contains `META-INF/blueprint.json`, its template folder and any hook classes. Build one with
`jgen -g jgen-blueprint`, then `mvn package` in the generated module.

JGen also reads `~/.jgen/blueprints`, and any comma-separated folders in the `JGEN_BLUEPRINTS` environment
variable. The plain jar from `mvn package` is what you want: do not shade `jgen-base` or JGen's other libraries into it.

See https://jgen.chenile.org/authoring/
