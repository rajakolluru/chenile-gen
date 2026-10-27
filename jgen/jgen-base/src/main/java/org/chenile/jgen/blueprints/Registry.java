package org.chenile.jgen.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class Registry {
    private static  ObjectMapper objectMapper = null;
    public static Map<String,BlueprintConfig> blueprints = new HashMap<>();
    static {
        objectMapper = new ObjectMapper();
        getAllBlueprints();
    }
    public static void registerBlueprint(BlueprintConfig blueprintConfig){
        blueprints.put(blueprintConfig.name,blueprintConfig);
    }

    public static void getAllBlueprints() {
        URL json = null;
        try {
            Enumeration<URL> e = Thread.currentThread().getContextClassLoader().getResources("META-INF/blueprint.json");
            while (e.hasMoreElements()) {
                json = e.nextElement();
                register(json);
            }
            Enumeration<URL> indexes = Thread.currentThread().getContextClassLoader().getResources("META-INF/blueprints.list");
            while (indexes.hasMoreElements()) {
                URL index = indexes.nextElement();
                try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(index.openStream()))) {
                    for (String resource; (resource = lines.readLine()) != null; ) {
                        String name = resource.trim();
                        if (!name.isEmpty()) {
                            URL blueprint = Thread.currentThread().getContextClassLoader().getResource(name);
                            if (blueprint == null) throw new IOException("Missing blueprint resource " + name);
                            register(blueprint);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Cannot initialize blueprint " + json + " error = " + e.getMessage());
        }
    }

    private static void register(URL url) throws Exception{
        try {
            BlueprintConfig blueprintConfig = objectMapper.readValue(url,BlueprintConfig.class);
            blueprintConfig.source = url.toString();
            if (blueprintConfig.initHook != null) {
                InitHook onBoarder = blueprintConfig.initHook.getDeclaredConstructor(new Class[]{}).newInstance();
                onBoarder.init(blueprintConfig);
            }
            registerBlueprint(blueprintConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
