package org.chenile.jgen.util;

import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FieldUtils {
    public static boolean isRequired(InputField field) {
        if (field.required != null) return field.required;
        return field.defaultValue == null || field.defaultValue.isBlank();
    }

    public static boolean isValid(InputField field, String input) {
        if (input == null || input.isEmpty()) return !isRequired(field);
        if (field.type == FieldType.BOOLEAN){
            input = input.toLowerCase(Locale.ROOT);
            return "y".equals(input) || "n".equals(input) || "true".equals(input) || "false".equals(input);
        }
        if (field.type == FieldType.FILE){
            File file = new File(input);
            if (!file.exists())return false;
        }
        if (field.type == FieldType.NUMBER){
            try {
                Integer.parseInt(input);
            }catch(NumberFormatException nfe){
                return false;
            }
        }
        if (field.type == FieldType.DATE) {
            try {
                LocalDate.parse(input);
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        if ((field.type == FieldType.DROPDOWN || field.type == FieldType.MULTI_SELECT)
                && field.validValues != null && !field.validValues.isEmpty()) {
            if (field.type == FieldType.DROPDOWN) return field.validValues.contains(input);
            for (String value : input.split(",")) {
                if (!field.validValues.contains(value.trim())) return false;
            }
        }
        return true;
    }

    public static boolean isValidRecordArray(InputField field, List<Map<String, Object>> records) {
        if (records == null) return true;
        if (field.childFields == null || field.childFields.isEmpty()) return false;
        InputField anchorField = field.childFields.get(0);
        for (Map<String, Object> record : records) {
            if (record == null) return false;
            Object anchorValue = record.get(anchorField.name);
            boolean anchorPresent = anchorValue != null && !anchorValue.toString().isBlank();
            if (!anchorPresent) {
                for (int i = 1; i < field.childFields.size(); i++) {
                    InputField child = field.childFields.get(i);
                    Object childValue = record.get(child.name);
                    if (childValue != null && !childValue.toString().isBlank()) {
                        return false;
                    }
                }
                continue;
            }
            if (!isValid(anchorField, anchorValue.toString())) {
                return false;
            }
            for (int i = 1; i < field.childFields.size(); i++) {
                InputField child = field.childFields.get(i);
                Object childValue = record.get(child.name);
                if (childValue != null && !childValue.toString().isBlank() && !isValid(child, childValue.toString())) {
                    return false;
                }
            }
        }
        return true;
    }

}
