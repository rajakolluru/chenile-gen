package org.chenile.jgen.ext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.BlueprintPayloadValidator;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.blueprints.model.ValidationType;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.provenance.GenerationRecord;
import org.chenile.jgen.provenance.ProvenanceRecorder;
import org.chenile.jgen.provenance.Regenerator;
import org.chenile.jgen.util.NameCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionsTest {
    @TempDir Path temporary;
    private String name;

    /** A toy engine: ".up" templates are upper-cased, with {name} replaced from the inputs. */
    public static class UpperEngine implements TemplateEngine {
        @Override public String name() { return "upper-engine"; }
        @Override public String extension() { return ".up"; }
        @Override public void render(Reader template, String templateName, Map<String, Object> data, Writer out)
                throws Exception {
            String text = new String(readAll(template));
            for (var entry : data.entrySet())
                if (entry.getValue() instanceof String value) text = text.replace("{" + entry.getKey() + "}", value);
            out.write(text.toUpperCase());
        }
        private static char[] readAll(Reader reader) throws Exception {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024];
            for (int n; (n = reader.read(buffer)) > 0; ) builder.append(buffer, 0, n);
            return builder.toString().toCharArray();
        }
    }

    /** A toy pack with one rule and one helper. */
    public static class TerraformPack implements LanguagePack {
        @Override public String name() { return "terraform"; }
        @Override public List<ValidationRule> rules() {
            return List.of(ValidationRule.of("TF_RESOURCE", "a Terraform resource name (e.g. web_server)",
                    "[a-z][a-z0-9_]*", null));
        }
        @Override public Map<String, Function<String, String>> helpers() {
            return Map.of("shout", text -> text.toUpperCase() + "!");
        }
    }

    @AfterEach void forget() {
        Extensions.reset();
        if (name != null) Registry.blueprints.remove(name);
    }

    // ---- names and helpers --------------------------------------------------------------------------------------

    @Test void nameCaseSplitsWordsTheSameWayWhateverTheInput() {
        for (String input : List.of("purchase order", "purchase-order", "purchase_order", "purchaseOrder", "PurchaseOrder")) {
            assertEquals("purchaseOrder", NameCase.camelCase(input), input);
            assertEquals("PurchaseOrder", NameCase.pascalCase(input), input);
            assertEquals("purchase_order", NameCase.snakeCase(input), input);
            assertEquals("PURCHASE_ORDER", NameCase.upperSnakeCase(input), input);
            assertEquals("purchase-order", NameCase.kebabCase(input), input);
        }
        assertEquals("http_server", NameCase.snakeCase("HTTPServer"));
        assertTrue(Extensions.helpers().keySet().containsAll(
                List.of("capitalize", "camelCase", "pascalCase", "snakeCase", "upperSnakeCase", "kebabCase")));
    }

    @Test void helpersWorkAsMustacheLambdasIncludingPackHelpers() throws Exception {
        Extensions.register(new TerraformPack());
        Path templates = temporary.resolve("templates");
        write(templates.resolve("__project__/names.txt.mustache"),
                "{{#snakeCase}}{{entity}}{{/snakeCase}} {{#kebabCase}}{{entity}}{{/kebabCase}} {{#shout}}{{entity}}{{/shout}}");
        Path generated = generate(templates, Map.of("project", "demo", "entity", "PurchaseOrder"), null);
        assertEquals("purchase_order purchase-order PURCHASEORDER!", Files.readString(generated.resolve("names.txt")));
    }

    // ---- language packs -----------------------------------------------------------------------------------------

    @Test void builtInPacksValidateTheirLanguagesNames() {
        assertTrue(Extensions.rule("PYTHON_MODULE").isValid("order_service"));
        assertFalse(Extensions.rule("PYTHON_MODULE").isValid("class"));
        assertFalse(Extensions.rule("PYTHON_MODULE").isValid("Order-Service"));
        assertTrue(Extensions.rule("PYTHON_PACKAGE").isValid("acme.orders"));
        assertTrue(Extensions.rule("PYPI_PROJECT").isValid("acme-orders"));
        assertTrue(Extensions.rule("NPM_PACKAGE").isValid("@acme/orders"));
        assertFalse(Extensions.rule("NPM_PACKAGE").isValid("Acme"));
        assertTrue(Extensions.rule("JS_IDENTIFIER").isValid("orderService"));
        assertFalse(Extensions.rule("JS_IDENTIFIER").isValid("class"));
    }

    @Test void blueprintsNamePackRulesLikeBuiltInOnes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputField field = mapper.readValue("{\"name\":\"module\",\"type\":\"STRING\",\"validation\":\"PYTHON_MODULE\"}",
                InputField.class);
        assertEquals(ValidationType.EXTENSION, field.validation);
        assertEquals("PYTHON_MODULE", field.rule);
        assertTrue(mapper.writeValueAsString(field).contains("\"validation\":\"PYTHON_MODULE\""));
        InputField builtIn = mapper.readValue("{\"name\":\"x\",\"validation\":\"JAVA_IDENTIFIER\"}", InputField.class);
        assertEquals(ValidationType.JAVA_IDENTIFIER, builtIn.validation);

        BlueprintConfig blueprint = load("""
                {"name":"%s","templateFolder":"t","requires":["python"],
                 "inputFields":[{"name":"project","type":"STRING"},
                                {"name":"module","type":"STRING","validation":"PYTHON_MODULE"}]}""");
        Config config = new ConfigProvider().obtainDefaultConfig();
        var bad = BlueprintPayloadValidator.validate(blueprint, config, Map.of("project", "p", "module", "Bad-Name"));
        assertTrue(bad.errors().stream().anyMatch(e -> e.path().equals("module")
                && e.message().contains("PYTHON_MODULE (a Python module name")), bad.errors().toString());
        var good = BlueprintPayloadValidator.validate(blueprint, config, Map.of("project", "p", "module", "orders"));
        assertTrue(good.errors().stream().noneMatch(e -> e.path().equals("module")), good.errors().toString());
    }

    @Test void anUnknownRuleFailsTheBlueprintAtLoad() {
        var error = assertThrows(Exception.class, () -> load("""
                {"name":"%s","templateFolder":"t",
                 "inputFields":[{"name":"project","type":"STRING"},{"name":"x","type":"STRING","validation":"COBOL_NAME"}]}"""));
        assertTrue(error.getMessage().contains("COBOL_NAME"), error.getMessage());
    }

    @Test void packsCannotRedefineBuiltInRulesOrHelpers() {
        Extensions.register(new LanguagePack() {
            @Override public String name() { return "clash"; }
            @Override public List<ValidationRule> rules() {
                return List.of(ValidationRule.of("JAVA_IDENTIFIER", "x", ".*", null));
            }
        });
        assertThrows(IllegalStateException.class, Extensions::all);
        Extensions.reset();
        Extensions.register(new LanguagePack() {
            @Override public String name() { return "clash"; }
            @Override public Map<String, Function<String, String>> helpers() { return Map.of("snakeCase", s -> s); }
        });
        assertThrows(IllegalStateException.class, Extensions::all);
    }

    // ---- template engines ---------------------------------------------------------------------------------------

    @Test void filesAreRenderedByTheEngineTheirExtensionSelects() throws Exception {
        Extensions.register(new UpperEngine());
        Path templates = temporary.resolve("templates");
        write(templates.resolve("__project__/a.txt.up"), "hello {project}");
        write(templates.resolve("__project__/b.txt.mustache"), "hello {{project}}");
        write(templates.resolve("__project__/c.txt"), "plain {{project}}");
        Path generated = generate(templates, Map.of("project", "demo"), null);
        assertEquals("HELLO DEMO", Files.readString(generated.resolve("a.txt")));
        assertEquals("hello demo", Files.readString(generated.resolve("b.txt")));
        assertEquals("plain {{project}}", Files.readString(generated.resolve("c.txt")));
        GenerationRecord record = ProvenanceRecorder.read(generated);
        assertEquals(List.of("engine:upper-engine"), record.extensions);
    }

    @Test void anEngineThatIsNotUsedIsNotRecorded() throws Exception {
        Extensions.register(new UpperEngine());
        Path templates = temporary.resolve("templates");
        write(templates.resolve("__project__/b.txt.mustache"), "hello {{project}}");
        Path generated = generate(templates, Map.of("project", "demo"), null);
        assertNull(ProvenanceRecorder.read(generated).extensions);
    }

    @Test void twoEnginesCannotClaimOneExtension() {
        Extensions.register(new UpperEngine() {
            @Override public String name() { return "fake-mustache"; }
            @Override public String extension() { return ".mustache"; }
        });
        assertThrows(IllegalStateException.class, Extensions::all);
    }

    // ---- requires -----------------------------------------------------------------------------------------------

    @Test void aMissingPackIsReportedAsMissingNotAsAnUnknownRule() {
        var error = assertThrows(IllegalArgumentException.class, () -> load("""
                {"name":"%s","templateFolder":"t","requires":["terraform"],
                 "inputFields":[{"name":"project","type":"STRING"},{"name":"r","type":"STRING","validation":"TF_RESOURCE"}]}"""));
        assertTrue(error.getMessage().contains("terraform is not installed"), error.getMessage());
    }

    @Test void aBlueprintIsRefusedWhenWhatItRequiresIsMissing() throws Exception {
        var error = assertThrows(IllegalArgumentException.class, () -> load("""
                {"name":"%s","templateFolder":"t","requires":["pebble","python"],
                 "inputFields":[{"name":"project","type":"STRING"}]}"""));
        assertTrue(error.getMessage().contains("pebble is not installed"), error.getMessage());
        assertFalse(error.getMessage().contains("python"), "built-in packs are installed");

        Extensions.register(new UpperEngine());
        assertNotNull(load("""
                {"name":"%s","templateFolder":"t","requires":["engine:upper-engine@1.0"],
                 "inputFields":[{"name":"project","type":"STRING"}]}"""), "an unversioned jar satisfies a version");
    }

    @Test void requiredExtensionsAreRecordedAndNeededForRegeneration() throws Exception {
        Extensions.register(new TerraformPack());
        Path templates = temporary.resolve("templates");
        write(templates.resolve("__project__/main.tf"), "x");
        Path generated = generate(templates, Map.of("project", "demo", "resource", "web_server"), "TF_RESOURCE");
        GenerationRecord record = ProvenanceRecorder.read(generated);
        assertEquals(List.of("pack:terraform"), record.extensions);
        Extensions.reset();
        var error = assertThrows(IllegalStateException.class, () -> Regenerator.requireRecordedExtensions(record));
        assertTrue(error.getMessage().contains("pack:terraform"), error.getMessage());
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private BlueprintConfig load(String descriptor) throws Exception {
        name = "ext-demo-" + System.nanoTime();
        Path root = temporary.resolve(name);
        Files.createDirectories(root.resolve("META-INF"));
        Files.createDirectories(root.resolve("t"));
        Files.writeString(root.resolve("META-INF/blueprint.json"), descriptor.formatted(name));
        return Registry.loadTemplateOnly(root).get(0);
    }

    private Path generate(Path templates, Map<String, Object> values, String resourceRule) throws Exception {
        BlueprintConfig blueprint = new BlueprintConfig();
        blueprint.name = "ext-test";
        blueprint.templateFolder = templates.toString();
        InputField project = new InputField();
        project.name = "project"; project.type = FieldType.STRING;
        InputField entity = new InputField();
        entity.name = "entity"; entity.type = FieldType.STRING; entity.required = false;
        InputField resource = new InputField();
        resource.name = "resource"; resource.type = FieldType.STRING; resource.required = false;
        if (resourceRule != null) { resource.validation = ValidationType.EXTENSION; resource.rule = resourceRule; }
        blueprint.inputFields = List.of(project, entity, resource);
        Config config = new ConfigProvider().obtainDefaultConfig();
        Path out = temporary.resolve("out");
        Map<String, Object> map = new HashMap<>(new BlueprintInputService().buildInputMap(blueprint, config, values));
        map.put("destFolder", out.toString());
        new BlueprintExecutor().execute(blueprint, config, map);
        return out.resolve("demo");
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
