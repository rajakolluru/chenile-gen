package org.chenile.jgen.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * JGen's own version, and the released Chenile version it was built with. JGen is released on its own cadence
 * (2.5.x), independently of Chenile; the Chenile version that generated projects target is config.json's
 * {@code chenileVersion}, not this.
 */
public final class JgenVersion {
    private static final Properties PROPERTIES = load();

    private JgenVersion() { }

    /** JGen's version, e.g. 2.5.1, or "unknown" when running from an unfiltered source tree. */
    public static String current() {
        return value("version");
    }

    /** The released Chenile version JGen was built against. */
    public static String builtWithChenile() {
        return value("chenileVersion");
    }

    private static String value(String key) {
        String value = PROPERTIES.getProperty(key);
        return value == null || value.isBlank() || value.startsWith("@") ? "unknown" : value.trim();
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = JgenVersion.class.getResourceAsStream("/org/chenile/jgen/jgen-version.properties")) {
            if (in != null) properties.load(in);
        } catch (Exception ignored) {
            // "unknown"
        }
        return properties;
    }
}
