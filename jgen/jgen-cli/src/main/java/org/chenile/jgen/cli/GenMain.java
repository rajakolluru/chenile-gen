package org.chenile.jgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.CopyFromJar;
import org.chenile.jgen.blueprints.BlueprintConfig;
import org.chenile.jgen.blueprints.BlueprintExecutor;
import org.chenile.jgen.blueprints.Registry;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.config.Config;
import org.chenile.jgen.config.ConfigProvider;
import org.chenile.jgen.util.FieldUtils;
import org.chenile.workflow.cli.VersionProvider;
import picocli.CommandLine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
    @CommandLine.Option(names = {"-e", "--emit-config"}, description = "Emits a config file for customization")
    private File configFileToEmit;
    @CommandLine.Option(names = {"-f", "--input-file"}, description = "Uses the input file instead of prompting")
    private File inputFile;
    @CommandLine.Option(names = {"-o", "--output-file"}, description = "Writes to the output file (applicable for the g option) ")
    private File outputFile;
    @CommandLine.Option(names = {"-g", "--generate-blueprint-file"}, description = "Generates a sample input file for the specified blueprint")
    private String generateBluePrint;
    private final BlueprintExecutor  blueprintExecutor = new BlueprintExecutor();
    private final ConfigProvider configProvider = new ConfigProvider();
    public static void main(String... args) {
        System.exit(new CommandLine(new GenMain()).execute(args));
    }
    @Override
    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            if (configFileToEmit != null) {
                emitConfigFile();
                return;
            }
            if (generateBluePrint != null){
                generateBluePrintFile();
                return;
            }
            if(inputFile == null)
                checkConfigFiles(scanner);
            blueprint(scanner);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void generateBluePrintFile() throws Exception{
        List<BlueprintConfig> list = List.copyOf(Registry.blueprints.values());
        if (list.isEmpty()){
            System.err.println("No blueprints found!");
            return;
        }
        Optional<BlueprintConfig> bpc = list.stream().filter(bp -> bp.name.equals(generateBluePrint)).findFirst();
        if (bpc.isEmpty()){
            System.err.println("Specified blue print " + generateBluePrint + " does not exist!");
            return;
        }
        BlueprintConfig blueprintConfig = bpc.get();
        Map<String,String> expectedMap = new HashMap<>();
        expectedMap.put("blueprint",generateBluePrint);
        for(InputField inf: blueprintConfig.inputFields){
            expectedMap.put(inf.name,inf.defaultValue);
        }
        String s = prettyWriter.writeValueAsString(expectedMap);
        if (outputFile == null) {
            System.out.println(s);
            return;
        }
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(s);
            System.out.println("The file " + outputFile + " generated successfully with the inputs expected from blueprint " + generateBluePrint);
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the file: " + e.getMessage());
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
        Path targetFile = Paths.get(configFileToEmit.getPath());
        copyFromJar.copyFromJar("config.json",targetFile);
        System.out.println("Making a copy of the config file to " + targetFile);
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
        Map<String,String> inputMap = null;
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

        Map<String,Object> map = new HashMap<>();
        buildInputMap(map,blueprintConfig,configMap,scanner,inputMap);
        blueprintExecutor.execute(blueprintConfig, config, map);
    }

    private Map<String,String> readJson(File inputFile){
        try {
            return objectMapper.readValue(inputFile, Map.class);
        }catch(Exception e){
            throw new RuntimeException("Cannot read the input file " + inputFile);
        }
    }

    private BlueprintConfig getBlueprintFromInput(Map<String,String> map, List<BlueprintConfig> list) {
        Optional<BlueprintConfig> optional = list.stream().filter(bp -> bp.name.equals(map.get("blueprint"))).findFirst();
        return optional.orElse(null);
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

    public int captureOneOfMany(Scanner scanner, List<String> values){
        int max = values.size();
        int input = 0;
        while (input < 1 || input > max) {
            int index = 1;
            for (String value : values) {
                System.out.println(index++ + ")" + value);
            }
            System.out.print("Choose one:(q or Q to quit)");
            String in = scanner.nextLine();
            if (in != null && in.equalsIgnoreCase("q")){
                System.out.println("Quitting!");
                System.exit(0);
            }
            try {
                input = Integer.parseInt(in);
            }catch(Exception e){
                System.err.println("Invalid input " + in + ". Try again.");
            }
            if (input < 1 || input > max){
                System.err.println("Invalid input. Value must be between 1 and " + max);
            }
        }
        return input;
    }

    private void buildInputMap(Map<String,Object> map,BlueprintConfig blueprintConfig,
                               Map<String,Object> configMap,Scanner scanner,
                               Map<String,String> inputMap){
        for(InputField field: blueprintConfig.inputFields){
            String value = captureField(field,configMap,scanner,inputMap);
            if(field.type == FieldType.BOOLEAN){
                if(value.equals("y"))
                    map.put(field.name,"true");
            }else
                map.put(field.name,value);
        }
    }

    private String captureField(InputField field, Map<String,Object> configMap,Scanner scanner,
                                Map<String,String> inputMap) {
        String defValue = substitute(configMap,field.defaultValue);
        String input = null;
        if (inputMap != null){
           return captureFromInputMap(configMap,field,inputMap,defValue);
        }
        String prompt = field.description;
        if (field.type == FieldType.BOOLEAN) prompt += "(y/n)";
        prompt += "?";
        if (defValue != null)
            prompt += " (" + defValue + ")";
        prompt += " ";
        do {
            System.out.print(prompt);
            String in = scanner.nextLine();
            if (in == null || in.isEmpty()) input = defValue;
            else input = in;
        }while(!FieldUtils.isValid(field,input));
        return input;
    }

    private String captureFromInputMap(Map<String,Object> configMap,InputField field, Map<String, String> inputMap, String defValue) {
        String value = inputMap.get(field.name);
        if(value != null)
            value = substitute(configMap,value);
        else
            value = defValue;
        if (!FieldUtils.isValid(field,value)){
            String message = "Field " + field.name + " has an invalid value " + value + "specified.";
            throw new RuntimeException(message);
        }
        return value;
    }

    private String substitute(Map<String,Object> configMap,String value){
        if (value == null || value.isEmpty()) return null;
        StringSubstitutor stringSubstitutor = new StringSubstitutor(configMap);
        return stringSubstitutor.replace(value);
    }

}
