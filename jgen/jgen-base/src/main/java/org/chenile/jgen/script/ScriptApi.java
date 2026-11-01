package org.chenile.jgen.script;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.util.NameCase;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The {@code jgen} object every hook script receives: small helpers, logging, and read access to the blueprint's own
 * FILE inputs. Nothing else on the machine is reachable from a script.
 */
public class ScriptApi {
    private final BlueprintConfig blueprint;
    private final Map<String, Object> inputs;

    ScriptApi(BlueprintConfig blueprint, Map<String, Object> inputs) {
        this.blueprint = blueprint;
        this.inputs = inputs;
    }

    /** "orders" becomes "Orders". */
    @HostAccess.Export
    public String capitalize(String text) { return NameCase.capitalize(text); }

    /** "Orders" becomes "orders". */
    @HostAccess.Export
    public String uncapitalize(String text) { return NameCase.uncapitalize(text); }

    /** "purchase order", "purchase-order" or "PurchaseOrder" becomes "purchaseOrder". */
    @HostAccess.Export
    public String camelCase(String text) { return NameCase.camelCase(text); }

    /** "purchase order" becomes "PurchaseOrder". */
    @HostAccess.Export
    public String pascalCase(String text) { return NameCase.pascalCase(text); }

    /** "PurchaseOrder" becomes "purchase_order". */
    @HostAccess.Export
    public String snakeCase(String text) { return NameCase.snakeCase(text); }

    /** "PurchaseOrder" becomes "PURCHASE_ORDER". */
    @HostAccess.Export
    public String upperSnakeCase(String text) { return NameCase.upperSnakeCase(text); }

    /** "PurchaseOrder" becomes "purchase-order". */
    @HostAccess.Export
    public String kebabCase(String text) { return NameCase.kebabCase(text); }

    /**
     * Any template helper by name, including helpers added by language packs: {@code jgen.helper("snakeCase", x)}.
     */
    @HostAccess.Export
    public String helper(String name, String text) {
        var helper = org.chenile.jgen.ext.Extensions.helpers().get(name);
        if (helper == null) throw new IllegalArgumentException("No template helper named " + name);
        return helper.apply(text);
    }

    /** Prints a line prefixed with the blueprint name. */
    @HostAccess.Export
    public void log(String message) {
        System.out.println("[" + blueprint.name + "] " + message);
    }

    /**
     * The text of a FILE input of this blueprint, e.g. a workflow XML or a JSON spec the user supplied. Use
     * {@code JSON.parse(jgen.readInput("spec"))} for JSON. Only fields declared with type FILE can be read.
     */
    @HostAccess.Export
    public String readInput(String field) throws IOException {
        InputField declared = blueprint.inputFields == null ? null : blueprint.inputFields.stream()
                .filter(f -> f.name.equals(field)).findFirst().orElse(null);
        if (declared == null || declared.type != FieldType.FILE)
            throw new IllegalArgumentException(field + " is not a FILE input of blueprint " + blueprint.name);
        Object value = inputs.get(field);
        if (value == null || value.toString().isBlank()) return null;
        return Files.readString(Path.of(value.toString()), StandardCharsets.UTF_8);
    }
}
