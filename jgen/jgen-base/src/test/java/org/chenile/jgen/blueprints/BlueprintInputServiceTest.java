package org.chenile.jgen.blueprints;

import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.template.chain.VersionValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlueprintInputServiceTest {
    private final BlueprintInputService service = new BlueprintInputService();

    @Test
    void resolvesDefaultsAndNormalizesBooleans() {
        BlueprintConfig blueprint = new BlueprintConfig();
        blueprint.inputFields = List.of(field("service", FieldType.STRING, "${defaultServiceName}"),
                field("security", FieldType.BOOLEAN, "y"));

        Map<String, Object> values = service.buildInputMap(blueprint, config(), Map.of());

        assertEquals("orders", values.get("service"));
        assertEquals(Boolean.TRUE, values.get("security"));
    }

    @Test
    void validatesDateAndSelectValues() {
        InputField date = field("startDate", FieldType.DATE, null);
        InputField mode = field("mode", FieldType.DROPDOWN, null);
        mode.validValues = List.of("basic", "advanced");
        BlueprintConfig blueprint = new BlueprintConfig();
        blueprint.inputFields = List.of(date, mode);

        Map<String, Object> values = service.buildInputMap(blueprint, config(), Map.of(
                "startDate", "2026-06-12",
                "mode", "advanced"));

        assertEquals("2026-06-12", values.get("startDate"));
        assertThrows(IllegalArgumentException.class, () -> service.buildInputMap(blueprint, config(), Map.of(
                "startDate", "12/06/2026",
                "mode", "advanced")));
    }

    @Test
    void omitsFieldsHiddenByVisibleWhen() {
        InputField enabled = field("enabled", FieldType.BOOLEAN, "n");
        InputField hidden = field("hiddenValue", FieldType.STRING, null);
        hidden.visibleWhen = Map.of("enabled", true);
        BlueprintConfig blueprint = new BlueprintConfig();
        blueprint.inputFields = List.of(enabled, hidden);

        Map<String, Object> values = service.buildInputMap(blueprint, config(), Map.of("hiddenValue", "ignored"));

        assertEquals(false, values.get("enabled"));
        assertEquals(false, values.containsKey("hiddenValue"));
    }

	@Test
	void owizChainRejectsBlueprintWhenConfiguredChenileVersionIsTooOld() {
		BlueprintConfig blueprint = new BlueprintConfig();
		blueprint.name = "headless";
		blueprint.sinceVersion = "2.1.31";
		Config config = config();
		config.chenileVersion = "2.1.8";

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> new BlueprintExecutor().execute(blueprint, config, new HashMap<>()));

		assertEquals("Blueprint 'headless' requires Chenile version 2.1.31 or later, but the selected config declares 2.1.8.",
				exception.getMessage());
	}

	@Test
	void acceptsEqualOrNewerChenileVersions() {
		BlueprintConfig blueprint = new BlueprintConfig();
		blueprint.name = "headless";
		blueprint.sinceVersion = "2.1.31";
		Config config = config();
		config.chenileVersion = "2.1.31";
		VersionValidator.validate(blueprint, config);
		config.chenileVersion = "2.2.0";
		VersionValidator.validate(blueprint, config);
	}

    private InputField field(String name, FieldType type, String defaultValue) {
        InputField field = new InputField();
        field.name = name;
        field.description = name;
        field.type = type;
        field.defaultValue = defaultValue;
        return field;
    }

    private Config config() {
        Config config = new Config();
        config.defaultServiceName = "orders";
        config.defaultVersion = "1.0.0";
        config.defaultDestFolder = "./output";
        config.com = "com";
        config.company = "example";
        config.org = "demo";
        return config;
    }
}
