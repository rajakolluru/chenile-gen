package org.chenile.jgen.remote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A blueprint catalog: {@code catalog.json} at the root of a GitHub repository's default branch. It maps short
 * names to blueprint sources, so users can write {@code jgen -g terraform} instead of a {@code github:} reference.
 *
 * <pre>
 * {
 *   "schema": "jgen.catalog/1",
 *   "description": "Blueprints curated by Acme",
 *   "blueprints": {
 *     "terraform": {
 *       "source": "github:acme/jgen-terraform",
 *       "description": "A Terraform module with CI",
 *       "tags": ["terraform", "iac"],
 *       "versions": { "1.2.0": "&lt;sha256&gt;" }
 *     }
 *   }
 * }
 * </pre>
 * {@code versions} is optional: a listed version's SHA-256 is enforced like a {@code #sha256:} pin. {@code blueprint}
 * (optional) names the blueprint inside a jar that holds several.
 */
public class Catalog {
    public static final String SCHEMA = "jgen.catalog/1";

    public String schema;
    public String description;
    public Map<String, Entry> blueprints = new LinkedHashMap<>();

    public static class Entry {
        /** github:owner/repo — a jar release or a template-only repository. */
        public String source;
        public String description;
        public List<String> tags = new ArrayList<>();
        /** Optional: the name of the blueprint inside the source, when it holds several. */
        public String blueprint;
        /** Optional pins: version to SHA-256. */
        public Map<String, String> versions = new LinkedHashMap<>();
    }
}
