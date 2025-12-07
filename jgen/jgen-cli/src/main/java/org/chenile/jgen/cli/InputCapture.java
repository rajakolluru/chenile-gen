package org.chenile.jgen.cli;

import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.util.FieldUtils;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Captures inputs from the passed scanners
 */
public abstract class InputCapture {
    public static int captureOneOfMany(Scanner scanner, List<String> values){
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

    public static String captureField(InputField field, Map<String,Object> configMap, Scanner scanner,
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

    private static String captureFromInputMap(Map<String,Object> configMap,InputField field, Map<String, String> inputMap, String defValue) {
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

    private static String substitute(Map<String,Object> configMap,String value){
        if (value == null || value.isEmpty()) return null;
        StringSubstitutor stringSubstitutor = new StringSubstitutor(configMap);
        return stringSubstitutor.replace(value);
    }

}
