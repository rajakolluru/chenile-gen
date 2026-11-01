package org.chenile.jgen.cli;

import org.chenile.jgen.config.ConfigProvider;
import picocli.CommandLine.IVersionProvider;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * {@code jgen --version}: JGen's own version, the released Chenile it was built against, and the Chenile version
 * generated projects target by default. JGen is released on its own cadence, independently of Chenile.
 */
public class VersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        List<String> lines = read("cli-version.txt");
        String jgen = lines.size() > 0 ? lines.get(0) : "unknown";
        String builtOn = lines.size() > 1 ? lines.get(1) : "unknown";
        String target;
        try {
            target = new ConfigProvider().obtainDefaultConfig().chenileVersion;
        } catch (Exception e) {
            target = "unknown";
        }
        return new String[]{
                "jgen " + jgen,
                "generates Chenile " + target + " projects by default (set chenileVersion in a config file to change it)",
                "built with Chenile " + builtOn
        };
    }

    private List<String> read(String resource) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return List.of();
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim).filter(l -> !l.isEmpty()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
