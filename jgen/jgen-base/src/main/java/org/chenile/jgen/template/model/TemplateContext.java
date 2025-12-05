package org.chenile.jgen.template.model;

import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.config.Config;

import java.io.File;
import java.util.Map;

/**
 * Constructs the context to be passed to different template commands.
 * Each command does work using this context which is progressively enhanced as it moves from Command to command
 */
public class TemplateContext {
   public BlueprintConfig blueprintConfig;
   public Config config;
   public File destination;
   public Map<String, Object> map;
   private String output;
   public boolean isJar = true;
   public String getOutput(){
      if (output == null) {
         output = (String) map.get("destFolder");
         if (output == null) throw new RuntimeException("Destination folder cannot be null");
      }
      return output;
   }
}
