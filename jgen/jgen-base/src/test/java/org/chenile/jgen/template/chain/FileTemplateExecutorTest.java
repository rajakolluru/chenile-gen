package org.chenile.jgen.template.chain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.chenile.jgen.template.model.TemplateContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTemplateExecutorTest {
  @TempDir Path temporary;

  @Test
  void generatedShellScriptsAreExecutable() throws Exception {
    Files.writeString(
        temporary.resolve("run.sh.mustache"), "#!/bin/sh\n# {{description}}\n");
    TemplateContext context = new TemplateContext();
    context.destination = temporary.toFile();
    context.map = Map.of("description", "generated launcher");

    new FileTemplateExecutor().execute(context);

    assertTrue(Files.isExecutable(temporary.resolve("run.sh")));
  }
}
