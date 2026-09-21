package org.chenile.jgen.template.chain;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.template.model.TemplateContext;
import org.chenile.owiz.Command;

/**
 * OWIZ processor that verifies a blueprint's optional minimum Chenile runtime
 * version before any blueprint hooks or files are processed.
 */
public class VersionValidator implements Command<TemplateContext> {
	@Override
	public void execute(TemplateContext context) {
		validate(context.blueprintConfig, context.config);
	}

	public static void validate(BlueprintConfig blueprintConfig, Config config) {
		if (blueprintConfig.sinceVersion == null || blueprintConfig.sinceVersion.isBlank()) return;
		String configuredVersion = config == null ? null : config.chenileVersion;
		if (configuredVersion == null || configuredVersion.isBlank()) {
			throw new IllegalArgumentException("Blueprint '" + blueprintConfig.name
					+ "' requires Chenile version " + blueprintConfig.sinceVersion
					+ " or later, but the selected config does not declare chenileVersion.");
		}
		if (new ComparableVersion(configuredVersion).compareTo(new ComparableVersion(blueprintConfig.sinceVersion)) < 0) {
			throw new IllegalArgumentException("Blueprint '" + blueprintConfig.name
					+ "' requires Chenile version " + blueprintConfig.sinceVersion
					+ " or later, but the selected config declares " + configuredVersion + ".");
		}
	}
}
