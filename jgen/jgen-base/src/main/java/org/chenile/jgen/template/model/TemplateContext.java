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
   /** Invocation ancestry is local to this generation, so independent runs do not share state. */
   public java.util.List<String> blueprintPath = java.util.List.of();
   /** The validated inputs before hooks ran; captured for top-level generations only, for the generation record. */
   public Map<String, Object> inputSnapshot;
   /** True when this context generates a child blueprint inside a parent. */
   public boolean isChild() { return blueprintPath.size() > 1; }
   public String getOutput(){
      if (output == null) {
         output = (String) map.get("destFolder");
         if (output == null) throw new RuntimeException("Destination folder cannot be null");
      }
      return output;
   }
}
