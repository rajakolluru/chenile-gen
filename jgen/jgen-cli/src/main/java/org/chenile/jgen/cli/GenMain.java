package org.chenile.jgen.cli;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@CommandLine.Command(name = "jgen", mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Java Based Blueprint Generator with support for plugins")
public class GenMain implements Runnable {
    @CommandLine.Option(names = {"-c", "--config"}, description = "Name of config file.")
    private File configFile;
    @CommandLine.Option(names = {"-e", "--emit-config"}, description = "Emits a config file for customization")
    private File configFileToEmit;
    private final BlueprintExecutor  blueprintExecutor = new BlueprintExecutor();
    private final ConfigProvider configProvider = new ConfigProvider();
    public static void main(String... args) {
        System.exit(new CommandLine(new GenMain()).execute(args));
    }
    @Override
    public void run() {
        try {
            if (configFileToEmit != null){
                emitConfigFile();
                return;
            }
            blueprint();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void emitConfigFile() throws Exception{
        CopyFromJar copyFromJar = new CopyFromJar();
        Path targetFile = Paths.get(configFileToEmit.getPath());
        copyFromJar.copyFromJar("config.json",targetFile);
        System.out.println("Making a copy of the config file to " + targetFile);
    }

    private void blueprint() throws Exception {
        Config config = obtainConfig();
        Map<String,Object> configMap = configProvider.getConfigAsMap(config);
        List<BlueprintConfig> list = List.copyOf(Registry.blueprints.values());
        if (list.isEmpty()){
            System.err.println("No blueprints found!");
            return;
        }
        List<String> sList = list.stream().map(bp -> {return bp.description;}).toList();
        Scanner scanner = new Scanner(System.in);
        System.out.println("Registered Blueprints:");
        int c = captureOneOfMany(scanner,sList);
        BlueprintConfig blueprintConfig = list.get(c-1);
        Map<String,Object> map = new HashMap<>();
        buildInputMap(map,blueprintConfig,configMap,scanner);
        blueprintExecutor.execute(blueprintConfig, config, map);
        scanner.close();
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
            System.out.print("Choose one:");
            String in = scanner.nextLine();
            input = Integer.parseInt(in);
        }
        return input;
    }

    private void buildInputMap(Map<String,Object> map,BlueprintConfig blueprintConfig,
                               Map<String,Object> configMap,Scanner scanner){
        for(InputField field: blueprintConfig.inputFields){
            String value = captureField(field,configMap,scanner);
            if(field.type == FieldType.BOOLEAN){
                if(value.equals("y"))
                    map.put(field.name,"true");
            }else
                map.put(field.name,value);
        }
    }

    private String captureField(InputField field, Map<String,Object> configMap,Scanner scanner) {
        String defValue = substitute(configMap,field.defaultValue);
        String input = null;
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

    private String substitute(Map<String,Object> configMap,String value){
        if (value == null || value.isEmpty()) return null;
        StringSubstitutor stringSubstitutor = new StringSubstitutor(configMap);
        return stringSubstitutor.replace(value);
    }

}
