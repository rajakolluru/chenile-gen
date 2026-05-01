package org.chenile.jgen.cli;

import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;
import org.chenile.jgen.util.FieldUtils;

import java.util.ArrayList;
import java.util.HashMap;
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

    public static Object captureField(InputField field, Map<String,Object> configMap, Scanner scanner,
                                      Map<String,Object> inputMap) {
        if (field.type == FieldType.RECORD_ARRAY) {
            return captureRecordArrayField(field, configMap, scanner, inputMap);
        }
        String defValue = substitute(configMap,field.defaultValue);
        String input = null;
        if (inputMap != null){
            return captureScalarFromInputMap(configMap,field,inputMap,defValue);
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

    private static Object captureScalarFromInputMap(Map<String,Object> configMap,InputField field,
                                                    Map<String, Object> inputMap, String defValue) {
        Object raw = inputMap.get(field.name);
        String value = raw != null ? substitute(configMap, raw.toString()) : defValue;
        if (!FieldUtils.isValid(field,value)){
            String message = "Field " + field.name + " has an invalid value " + value + "specified.";
            throw new RuntimeException(message);
        }
        return value;
    }

    private static List<Map<String, Object>> captureRecordArrayField(InputField field, Map<String, Object> configMap,
                                                                     Scanner scanner, Map<String, Object> inputMap) {
        if (inputMap != null) {
            return captureRecordArrayFromInputMap(field, configMap, inputMap);
        }
        List<Map<String,Object>> records = new ArrayList<>();
        InputField anchorField = field.childFields.get(0);
        while (true) {
            System.out.print("Add " + field.name + " record? (y/n) ");
            String add = scanner.nextLine();
            if (!"y".equalsIgnoreCase(add)) {
                break;
            }
            Map<String,Object> record = new HashMap<>();
            Map<String,Object> resolutionMap = new HashMap<>(configMap);
            Object anchor = captureField(anchorField, resolutionMap, scanner, null);
            if (anchor == null || anchor.toString().isBlank()) {
                continue;
            }
            record.put(anchorField.name, anchor);
            resolutionMap.put(anchorField.name, anchor);
            for (int i = 1; i < field.childFields.size(); i++) {
                InputField child = field.childFields.get(i);
                String defValue = substitute(resolutionMap, child.defaultValue);
                String prompt = child.description + "?";
                if (defValue != null) {
                    prompt += " (" + defValue + ")";
                }
                prompt += " ";
                System.out.print(prompt);
                String in = scanner.nextLine();
                String value = (in == null || in.isEmpty()) ? defValue : in;
                if (value != null && !value.isBlank()) {
                    if (!FieldUtils.isValid(child, value)) {
                        throw new RuntimeException("Field " + child.name + " has an invalid value " + value + "specified.");
                    }
                    record.put(child.name, value);
                    resolutionMap.put(child.name, value);
                }
            }
            records.add(record);
        }
        return records;
    }

    private static List<Map<String, Object>> captureRecordArrayFromInputMap(InputField field, Map<String, Object> configMap,
                                                                            Map<String, Object> inputMap) {
        Object raw = inputMap.get(field.name);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> rawRecords)) {
            throw new RuntimeException("Field " + field.name + " must be a list of records.");
        }
        List<Map<String,Object>> records = new ArrayList<>();
        for (Object rawRecord : rawRecords) {
            if (!(rawRecord instanceof Map<?,?> rawMap)) {
                throw new RuntimeException("Field " + field.name + " must contain only record objects.");
            }
            Map<String,Object> resolutionMap = new HashMap<>(configMap);
            Map<String,Object> record = new HashMap<>();
            for (InputField childField : field.childFields) {
                Object childRaw = rawMap.get(childField.name);
                if (childRaw == null) {
                    continue;
                }
                String value = substitute(resolutionMap, childRaw.toString());
                record.put(childField.name, value);
                if (!value.isBlank()) {
                    resolutionMap.put(childField.name, value);
                }
            }
            records.add(record);
        }
        if (!FieldUtils.isValidRecordArray(field, records)) {
            throw new RuntimeException("Field " + field.name + " has invalid record values specified.");
        }
        return records;
    }

    private static String substitute(Map<String,Object> configMap,String value){
        if (value == null || value.isEmpty()) return null;
        StringSubstitutor stringSubstitutor = new StringSubstitutor(configMap);
        return stringSubstitutor.replace(value);
    }

}
