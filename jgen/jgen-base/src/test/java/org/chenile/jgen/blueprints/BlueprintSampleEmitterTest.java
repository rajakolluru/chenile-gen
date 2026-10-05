package org.chenile.jgen.blueprints;

import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintSampleEmitterTest {
    @TempDir Path temporary;

    @Test void arbitraryBlueprintOwnsSampleContentAndFrameworkAssignsItsName() throws Exception {
        var field = new InputField(); field.name="source"; field.type=FieldType.FILE;
        var blueprint = new BlueprintConfig(); blueprint.inputFields=List.of(field);
        blueprint.sampleInputFiles.put("source", new SampleInputFile(".dat", target -> Files.writeString(target,"domain sample")));
        var input = new HashMap<String,Object>();
        BlueprintSampleEmitter.emit(blueprint,temporary.resolve("custom.input"),input);
        Path sample=temporary.resolve("custom-source.dat");
        assertEquals(sample.toString(),input.get("source"));
        assertEquals("domain sample",Files.readString(sample));
    }

    @Test void rejectsEmittersForNonFileFieldsBeforeWriting() {
        var field = new InputField(); field.name="source"; field.type=FieldType.STRING;
        var blueprint = new BlueprintConfig(); blueprint.inputFields=List.of(field);
        blueprint.sampleInputFiles.put("source",new SampleInputFile(".json",target -> fail("Emitter must not run")));
        assertThrows(IllegalArgumentException.class,()->BlueprintSampleEmitter.emit(
                blueprint,temporary.resolve("custom.json"),new HashMap<>()));
    }
}
