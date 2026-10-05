package org.chenile.jgen.blueprints;

import org.chenile.jgen.blueprints.model.FieldType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.chenile.jgen.util.JavaNameUtils.identifier;
import static org.chenile.jgen.util.ValidationUtils.require;

/** Names companion files and invokes blueprint-owned emitters; knows no file formats. */
public final class BlueprintSampleEmitter {
    private BlueprintSampleEmitter() {}

    public static void emit(BlueprintConfig blueprint, Path output, Map<String,Object> inputs) throws IOException {
        Path main = output.toAbsolutePath().normalize();
        String name = main.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        var destinations = new LinkedHashMap<String,Path>();
        require(!Files.exists(main, LinkOption.NOFOLLOW_LINKS), "Sample output already exists: " + main);
        for (var entry : blueprint.sampleInputFiles.entrySet()) {
            String field = identifier(entry.getKey());
            require(blueprint.inputFields.stream().anyMatch(f -> field.equals(f.name) && f.type == FieldType.FILE),
                    "Sample emitter must name a FILE input: " + field);
            require(entry.getValue().extension().matches("\\.[A-Za-z0-9]+"), "Invalid sample file extension");
            Path target = main.resolveSibling(stem + "-" + field + entry.getValue().extension());
            require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS), "Sample output already exists: " + target);
            destinations.put(field, target);
        }
        Files.createDirectories(main.getParent());
        for (var entry : destinations.entrySet()) {
            blueprint.sampleInputFiles.get(entry.getKey()).emitter().emit(entry.getValue());
            inputs.put(entry.getKey(), entry.getValue().toString());
        }
    }
}
