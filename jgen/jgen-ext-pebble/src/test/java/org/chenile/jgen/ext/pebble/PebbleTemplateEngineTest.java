package org.chenile.jgen.ext.pebble;

import org.chenile.jgen.ext.Extensions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PebbleTemplateEngineTest {
    private String render(String template, Map<String, Object> data) throws Exception {
        StringWriter out = new StringWriter();
        new PebbleTemplateEngine().render(new StringReader(template), "test.peb", data, out);
        return out.toString();
    }

    @Test void isFoundAsAnExtensionAndSelectedByItsFileExtension() {
        Extensions.reset();
        assertTrue(Extensions.all().containsKey("pebble"));
        assertEquals("pebble", Extensions.engineFor("main.py.peb").name());
        assertEquals("mustache", Extensions.engineFor("Main.java.mustache").name());
    }

    @Test void rendersVariablesLoopsConditionsAndHelperFilters() throws Exception {
        String out = render("""
                class {{ entity | pascalCase }}:
                {% for f in fields %}    {{ f | snakeCase }}: str
                {% endfor %}{% if jpa %}# jpa{% endif %}""",
                Map.of("entity", "purchase order", "fields", List.of("orderId", "customerName"), "jpa", true));
        assertEquals("""
                class PurchaseOrder:
                    order_id: str
                    customer_name: str
                # jpa""", out);
    }

    @Test void doesNotEscapeAndToleratesMissingValues() throws Exception {
        assertEquals("a < b && c [] ", render("{{ text }} [{{ missing }}] ", Map.of("text", "a < b && c")));
    }
}
