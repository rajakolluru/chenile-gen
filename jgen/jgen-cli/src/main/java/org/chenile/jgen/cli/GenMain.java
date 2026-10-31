package org.chenile.jgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.BlueprintInputService;
import org.chenile.jgen.blueprints.BlueprintPayloadValidator;
import org.chenile.jgen.blueprints.PayloadIssue;
import org.chenile.jgen.blueprints.PayloadValidation;
import org.chenile.jgen.blueprints.BlueprintSampleEmitter;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.provenance.Regenerator;
import org.chenile.jgen.remote.BlueprintResolver;
import org.chenile.jgen.remote.TrustPrompt;
import org.chenile.jgen.util.CopyFromJar;
import org.chenile.workflow.cli.VersionProvider;
import picocli.CommandLine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.chenile.jgen.cli.InputCapture.captureField;
import static org.chenile.jgen.cli.InputCapture.captureOneOfMany;

/**
 * Main method for the Java based Code Generator. It allows the user to identify the
 * blueprint and supply parameters to it. This triggers the blueprint generation.
 */
@CommandLine.Command(name = "jgen", mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Java Based Blueprint Generator with support for plugins")
public class GenMain implements Runnable {
    public static final String DEFAULT_CONFIG_FILE = "config/jgen-config.json";
    private final ObjectMapper objectMapper = new ObjectMapper();
    ObjectWriter prettyWriter = objectMapper.writerWithDefaultPrettyPrinter();
    @CommandLine.Option(names = {"-c", "--config"}, description = "Name of config file.")
    private File configFile;
    @CommandLine.Option(names = {"-e", "--emit-config"}, arity = "0..1", fallbackValue = "",
            description = "Emits config; with -g, argument is a folder for input-file.json and companion samples")
    private File configFileToEmit;
    @CommandLine.Option(names = {"-f", "--input-file"}, description = "Uses the input file instead of prompting")
    private File inputFile;
    @CommandLine.Option(names = {"-o", "--output-file"}, description = "Writes the generated blueprint input file (for -g without a folder-valued -e)")
    private File outputFile;
    @CommandLine.Option(names = {"-g", "--generate-blueprint-file"}, description = "Generates a sample input file for the specified blueprint")
    private String generateBluePrint;
    @CommandLine.Option(names = {"-r", "--regenerate"}, description = "Regenerates a JGen-generated project with the current blueprint version into a separate folder and reports what changed")
    private File regenerateProject;
    @CommandLine.Option(names = {"--into"}, description = "With -r: the folder to regenerate into (default: <project>.regenerated next to the project)")
    private File regenerateInto;
    @CommandLine.Option(names = {"--blueprint"}, description = "With -r: regenerate with this blueprint instead, e.g. a newer github:owner/repo@version")
    private String regenerateWith;
    @CommandLine.Option(names = {"--fetch"}, description = "Fetches and caches a blueprint (github:owner/repo[@version]) and describes it")
    private String fetchBlueprint;
    @CommandLine.Option(names = {"--trust"}, description = "Trusts the blueprint sources used in this run without asking")
    private boolean trustAll;
    @CommandLine.Option(names = {"--offline"}, description = "Uses only installed and cached blueprints; never contacts GitHub")
    private boolean offline;
    @CommandLine.Option(names = {"--completion"}, paramLabel = "SHELL",
            description = "Prints the completion script for bash, zsh or fish, e.g. source <(jgen --completion bash)")
    private String completionShell;
    @CommandLine.Option(names = {"--list-blueprints"}, hidden = true,
            description = "Lists installed and cached blueprints, one per line with a tab-separated description (used by completion)")
    private boolean listBlueprints;
    private BlueprintResolver resolver;
    private final BlueprintExecutor  blueprintExecutor = new BlueprintExecutor();
    private final BlueprintInputService blueprintInputService = new BlueprintInputService();
    private final ConfigProvider configProvider = new ConfigProvider();
    public static void main(String... args) {
        System.exit(new CommandLine(new GenMain()).execute(args));
    }
    @Override
    public void run() {
        try {
            if (completionShell != null){
                printCompletion();
                return;
            }
            if (listBlueprints){
                listBlueprints();
                return;
            }
            if (fetchBlueprint != null){
                fetch();
                return;
            }
            if (regenerateProject != null){
                regenerate();
                return;
            }
            if (generateBluePrint != null){
                generateBluePrintFile();
                return;
            }
            if (configFileToEmit != null) {
                if (configFileToEmit.getPath().isBlank())
                    throw new IllegalArgumentException("-e requires a config filename, or use -g and -o for blueprint samples");
                emitConfigFile();
                return;
            }
            try (Scanner scanner = new Scanner(System.in)) {
                if(inputFile == null) checkConfigFiles(scanner);
                blueprint(scanner);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void regenerate() throws Exception {
        Path project = regenerateProject.toPath().toAbsolutePath().normalize();
        Path into = regenerateInto != null ? regenerateInto.toPath()
                : project.resolveSibling(project.getFileName() + ".regenerated");
        Config override = configFile != null ? obtainConfig() : null;
        Regenerator.RegenerationReport report = new Regenerator().regenerate(project, into, override, resolver(), regenerateWith);
        Path reportFile = Regenerator.writeReport(report);
        System.out.println("Regenerated " + report.previous().blueprint.name + " into " + report.regenerated());
        for (Regenerator.Change change : Regenerator.Change.values()) {
            var files = report.only(change);
            if (files.isEmpty()) continue;
            System.out.println(change + " (" + files.size() + ")");
            files.forEach(f -> System.out.println("  " + f.path()));
        }
        if (report.changes().isEmpty()) System.out.println("No differences: the project matches the current blueprint.");
        System.out.println("Report: " + reportFile);
        System.out.println("Compare with: git diff --no-index " + project + " " + report.regenerated());
    }

    private void generateBluePrintFile() throws Exception{
        Config config = obtainConfig();
        Map<String,Object> configMap = configProvider.getConfigAsMap(config);
        List<BlueprintConfig> list = List.copyOf(Registry.blueprints.values());
        if (list.isEmpty()){
            System.err.println("No blueprints found!");
            return;
        }
        BlueprintConfig blueprintConfig = resolver().resolve(generateBluePrint);
        Map<String,Object> expectedMap = new HashMap<>();
        expectedMap.put("blueprint",generateBluePrint);
        for(InputField inf: blueprintConfig.inputFields){
            expectedMap.put(inf.name, (inf.type == FieldType.RECORD_ARRAY || inf.type == FieldType.POM_Dependencies) ? List.of() : resolveDefaultValue(inf, configMap));
        }
        File destination = outputFile;
        if (configFileToEmit != null && !configFileToEmit.getPath().isBlank()) {
            if (outputFile != null)
                throw new IllegalArgumentException("Use -e <folder> without -o; the input filename is input-file.json");
            Path folder = configFileToEmit.toPath().toAbsolutePath().normalize();
            if (Files.exists(folder) && !Files.isDirectory(folder))
                throw new IllegalArgumentException("The -e destination must be a folder: " + folder);
            Files.createDirectories(folder);
            destination = folder.resolve("input-file.json").toFile();
        } else if (configFileToEmit != null && destination == null) {
            throw new IllegalArgumentException("Use -e <folder> when generating blueprint samples");
        }
        if (configFileToEmit != null) {
            if (destination == null)
                throw new IllegalArgumentException("Companion samples require -e <folder>");
            BlueprintSampleEmitter.emit(blueprintConfig, destination.toPath(), expectedMap);
        }
        String s = prettyWriter.writeValueAsString(expectedMap);
        if (destination == null) {
            System.out.println(s);
            return;
        }
        try (FileWriter writer = new FileWriter(destination)) {
            writer.write(s);
            System.out.println("The file " + destination + " generated successfully with the inputs expected from blueprint " + generateBluePrint);
        } catch (IOException e) {
            throw new IOException("Cannot write blueprint input file: " + destination, e);
        }
    }

    /**
     * Checks if there is a config folder. Gives an option to use one of the configs
     * that are present in the folder.
     */
    private void checkConfigFiles(Scanner scanner) {
        File config = new File("config");
        if (!config.exists() || !config.isDirectory()) return;
        File[] files = config.listFiles();
        if (files == null || files.length == 0) return;
        System.out.println("Found the following files in the config folder. Do you want to use them?");
        List<String> fileNames = new ArrayList<>();
        fileNames.add("Use Default Config");
        for (File file : files){
            fileNames.add(file.getName());
        }
        int c = captureOneOfMany(scanner,fileNames);
        if (c == 1){
            configFile = null;
            return;
        }
        configFile = files[c-2];
    }

    private void emitConfigFile() throws Exception{
        CopyFromJar copyFromJar = new CopyFromJar();
        if (!configFileToEmit.getPath().startsWith("config/")){
            if(!createConfigFolder()){
                System.exit(1);
            };
            System.out.println("Creating the file in the config folder (by convention)");
            configFileToEmit = new File("config/" + configFileToEmit.getPath());
        }
        if (!configFileToEmit.getPath().endsWith(".json")){
            configFileToEmit = new File(configFileToEmit.getPath() + ".json");
        }
        Path targetFile = Paths.get(configFileToEmit.getPath());
        copyFromJar.copyFromJar("config.json",targetFile);
        System.out.println("Making a copy of the config file to " + targetFile);
    }

    private boolean createConfigFolder(){
        File configFolder = new File("config");
        if (configFolder.exists() && !configFolder.isDirectory()){
            System.out.println("Found a config file which is not a folder. Make sure you have a config folder");
            return false;
        }

        if (!configFolder.exists() ){
            return configFolder.mkdirs();
        }
        return true;
    }

    private void blueprint(Scanner scanner) throws Exception {
        Config config = obtainConfig();
        Map<String,Object> configMap = configProvider.getConfigAsMap(config);
        List<BlueprintConfig> list = List.copyOf(Registry.blueprints.values());
        if (list.isEmpty()){
            System.err.println("No blueprints found!");
            return;
        }
        BlueprintConfig blueprintConfig;
        Map<String,Object> inputMap = null;
        if (inputFile == null)
            blueprintConfig = captureBlueprint(scanner,list);
        else {
            inputMap = readJson(inputFile);
            blueprintConfig = getBlueprintFromInput(inputMap,list);
            if (blueprintConfig == null) {
                System.err.println("No blueprints specified in the file. Quitting!");
                return;
            }
        }

        if (inputMap != null) checkInputFile(blueprintConfig, config, inputMap);
        Map<String,Object> map = new HashMap<>(configMap);
        buildInputMap(map,blueprintConfig,configMap,scanner,inputMap);
        blueprintExecutor.execute(blueprintConfig, config, map);
        System.out.println("Blue print " + blueprintConfig.name + " generated for inputs: ");
        System.out.println(map);
    }

    private Map<String,Object> readJson(File inputFile){
        try {
            return objectMapper.readValue(inputFile, Map.class);
        }catch(Exception e){
            throw new RuntimeException("Cannot read the input file " + inputFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reports every problem in an input file at once, using the same validator as every other input path.
     * Warnings (undeclared or hidden keys) are printed; errors stop generation before anything is written.
     */
    private void checkInputFile(BlueprintConfig blueprintConfig, Config config, Map<String,Object> inputMap) {
        PayloadValidation validation = BlueprintPayloadValidator.validate(blueprintConfig, config, inputMap);
        for (PayloadIssue warning : validation.warnings())
            System.err.println("Warning: " + warning);
        if (validation.valid()) return;
        StringBuilder message = new StringBuilder("Input file " + inputFile + " has "
                + validation.errors().size() + " error(s) for blueprint " + blueprintConfig.name + ":");
        for (PayloadIssue error : validation.errors())
            message.append(System.lineSeparator()).append("  - ").append(error);
        throw new IllegalArgumentException(message.toString());
    }

    private BlueprintConfig getBlueprintFromInput(Map<String,Object> map, List<BlueprintConfig> list) throws IOException {
        Object name = map.get("blueprint");
        if (name == null || name.toString().isBlank()) return null;
        // A name, or a github: reference, which is fetched on first use.
        return resolver().resolve(name.toString());
    }

    private void printCompletion() throws IOException {
        String resource = switch (completionShell.toLowerCase()) {
            case "bash" -> "completion/jgen.bash";
            case "zsh" -> "completion/_jgen";
            case "fish" -> "completion/jgen.fish";
            default -> throw new IllegalArgumentException("--completion supports bash, zsh and fish, not " + completionShell);
        };
        try (var in = GenMain.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Missing " + resource + " in the jgen jar");
            System.out.print(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** Installed blueprints, then cached github: references, as "name<TAB>description" lines. */
    private void listBlueprints() throws IOException {
        Registry.blueprints.values().stream()
                .sorted(Comparator.comparing((BlueprintConfig b) -> b.name))
                .forEach(b -> System.out.println(b.name + "\t" + oneLine(b.description)));
        Path cache = org.chenile.jgen.remote.JgenSettings.home().resolve("cache").resolve("github");
        if (!Files.isDirectory(cache)) return;
        try (var versions = Files.find(cache, 3, (p, a) -> a.isDirectory() && cache.relativize(p).getNameCount() == 3)) {
            versions.map(cache::relativize).sorted()
                    .forEach(p -> System.out.println("github:" + p.getName(0) + "/" + p.getName(1) + "@" + p.getName(2)
                            + "\tcached release"));
        }
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private BlueprintResolver resolver() {
        if (resolver == null) resolver = BlueprintResolver.defaults(consolePrompt(), offline, trustAll);
        return resolver;
    }

    private static TrustPrompt consolePrompt() {
        return reference -> {
            java.io.Console console = System.console();
            if (console == null) return TrustPrompt.Decision.DENY;
            String answer = console.readLine("%ngithub:%s is not a trusted blueprint source. Blueprints run Java code on "
                    + "your machine.%nTrust it? [y] this time, [a]lways, [N]o: ", reference.repository());
            if (answer == null) return TrustPrompt.Decision.DENY;
            return switch (answer.trim().toLowerCase()) {
                case "y", "yes" -> TrustPrompt.Decision.ALLOW_ONCE;
                case "a", "always" -> TrustPrompt.Decision.ALLOW_ALWAYS;
                default -> TrustPrompt.Decision.DENY;
            };
        };
    }

    private void fetch() throws Exception {
        BlueprintConfig blueprint = resolver().resolve(fetchBlueprint);
        System.out.println("Blueprint   " + blueprint.name + (blueprint.version == null ? "" : " " + blueprint.version));
        if (blueprint.description != null) System.out.println("            " + blueprint.description);
        if (blueprint.fetchedFrom != null) System.out.println("Source      " + blueprint.fetchedFrom);
        if (blueprint.sha256 != null) System.out.println("SHA-256     " + blueprint.sha256);
        System.out.println("Use it with: jgen -g " + (blueprint.fetchedFrom != null ? blueprint.fetchedFrom : blueprint.name)
                + " -o input.json");
    }

    private BlueprintConfig captureBlueprint(Scanner scanner,List<BlueprintConfig> list){
        List<String> sList = list.stream().map(bp -> bp.description + " (" + bp.name + ")").toList();
        System.out.println("Registered Blueprints:");
        int c = captureOneOfMany(scanner,sList);
        return list.get(c-1);
    }

    private Config obtainConfig(){
        if (configFile != null){
            if (!configFile.exists()){
                System.err.println("Missing config file " + configFile);
                System.exit(1);
            }
            return configProvider.obtainConfig(configFile);
        }
        return configProvider.obtainDefaultConfig();
    }

    static String resolveDefaultValue(InputField field, Map<String,Object> configMap){
        if (field.defaultValue == null || field.defaultValue.isEmpty()) {
            return null;
        }
        if (configMap == null || configMap.isEmpty()) {
            return field.defaultValue;
        }
        // Sample inputs may still reference fields the user has not supplied.
        // Validate only when those inputs are captured for actual generation.
        return new org.apache.commons.text.StringSubstitutor(configMap).replace(field.defaultValue);
    }

    static Object normalizeFieldValue(InputField field, Object value) {
        if (field.type != FieldType.BOOLEAN) {
            return value;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "y".equalsIgnoreCase(value.toString()) || "true".equalsIgnoreCase(value.toString());
    }

    private void buildInputMap(Map<String,Object> map,BlueprintConfig blueprintConfig,
                               Map<String,Object> configMap,Scanner scanner,
                               Map<String,Object> inputMap){

        for(InputField field: blueprintConfig.inputFields){
            if (!blueprintInputService.isVisible(field, map)) continue;
            Object value = captureField(field,map,scanner,inputMap);
            map.put(field.name, normalizeFieldValue(field, value));
        }
    }

}
