package org.chenile.jgen.blueprints;

import org.chenile.jgen.blueprints.model.*;
import org.chenile.jgen.config.Config;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PomInputsTest {
    private InputField field(String name, FieldType type) {
        var field = new InputField(); field.name = name; field.type = type; return field;
    }
    @Test void structuredParentsOverrideLegacyCoordinatesAndPreserveEmptyRelativePaths() {
        var parent = field("super_pom", FieldType.SuperPOM);
        var blueprint = new BlueprintConfig(); blueprint.inputFields = List.of(parent);
        var supplied = new HashMap<String, Object>();
        supplied.put("parentArtifactId", "old"); supplied.put("parentGroupId", "old.group"); supplied.put("parentVersion", "1");
        supplied.put("super_pom", Map.of("groupId", "new.group", "artifactId", "new", "version", "${revision}", "relativePath", ""));
        var values = new BlueprintInputService().buildInputMap(blueprint, new Config(), supplied);
        assertEquals("new", values.get("parentArtifactId"));
        assertEquals("${revision}", values.get("parentVersion"));
        assertEquals("", values.get("parentRelativePath"));
        PomInputs.normalizeBlueprintInputs(blueprint, values);
        assertEquals("", values.get("parentRelativePath"));
        supplied.remove("super_pom");
        values = new BlueprintInputService().buildInputMap(blueprint, new Config(), supplied);
        assertEquals("old", values.get("parentArtifactId"));
        assertEquals("../pom.xml", values.get("parentRelativePath"));
    }
    @Test void multipleDependencyFieldsStaySeparateAndAcceptOldCoordinateNames() {
        var blueprint = new BlueprintConfig();
        blueprint.inputFields = List.of(field("runtimeDependencies", FieldType.POM_Dependencies),
                field("otherDependencies", FieldType.POM_Dependencies));
        var values = new BlueprintInputService().buildInputMap(blueprint, new Config(), Map.of(
                "runtimeDependencies", List.of(Map.of("groupId", "example", "artifactId", "runtime", "version", "${revision}")),
                "otherDependencies", List.of(Map.of("dependencyGroup", "example", "dependencyName", "managed"))));
        var runtime = (List<Map<String, Object>>) values.get("runtimeDependencies");
        var other = (List<Map<String, Object>>) values.get("otherDependencies");
        assertEquals("runtime", runtime.get(0).get("artifactId"));
        assertEquals("${revision}", runtime.get(0).get("version"));
        assertEquals("managed", other.get(0).get("artifactId"));
        assertFalse(other.get(0).containsKey("version"));
        PomInputs.normalizeBlueprintInputs(blueprint, values);
        assertEquals(runtime, values.get("runtimeDependencies"));
        assertEquals(other, values.get("otherDependencies"));
    }
    @Test void malformedCoordinatesAndWrongShapesAreRejected() {
        var parent = field("super_pom", FieldType.SuperPOM);
        var dependencies = field("dependencies", FieldType.POM_Dependencies);
        for (Object invalid : List.of("not-an-object", List.of(), Map.of("groupId", "example"),
                Map.of("groupId", "example", "artifactId", "parent", "version", 7)))
            assertThrows(IllegalArgumentException.class, () -> PomInputs.normalize(parent, invalid, Map.of()));
        for (Object invalid : List.of("not-an-array", Map.of(), List.of("dependency"),
                List.of(Map.of("groupId", "example")), List.of(Map.of("artifactId", "library"))))
            assertThrows(IllegalArgumentException.class, () -> PomInputs.normalize(dependencies, invalid, Map.of()));
        dependencies.required = true;
        assertThrows(IllegalArgumentException.class, () -> PomInputs.normalize(dependencies, List.of(), Map.of()));
    }
    @Test void optionalDefaultsAndPortalSchemasExposeStructuredTypes() {
        var blueprint = new BlueprintConfig();
        blueprint.inputFields = List.of(field("super_pom", FieldType.SuperPOM), field("dependencies", FieldType.POM_Dependencies));
        var service = new BlueprintInputService();
        var values = service.buildInputMap(blueprint, new Config(), Map.of());
        assertFalse(values.containsKey("super_pom")); assertEquals(List.of(), values.get("dependencies"));
        var fields = service.describeFields(blueprint, new Config());
        assertEquals(FieldType.SuperPOM, fields.get(0).get("type"));
        assertEquals(4, ((List<?>) fields.get(0).get("childFields")).size());
        assertEquals(3, ((List<?>) fields.get(1).get("childFields")).size());
        assertEquals(false, fields.get(0).get("required"));
        assertEquals(List.of(), fields.get(1).get("defaultValue"));
    }
}
