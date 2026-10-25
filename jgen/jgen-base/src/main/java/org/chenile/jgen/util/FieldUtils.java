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
        if (org.chenile.jgen.blueprints.PomInputs.supports(field)) return false;
        return field.defaultValue == null || field.defaultValue.isBlank();
    }

    public static boolean isValid(InputField field, String input) {
        return isValid(field, input, true);
    }

    /**
     * @param checkFiles whether a FILE field's path must exist. Payload validation passes {@code false} so it
     *                   never touches the filesystem; generation re-checks file paths at its own boundary.
     */
    public static boolean isValid(InputField field, String input, boolean checkFiles) {
        if (input == null || input.isEmpty()) return !isRequired(field);
        if (field.type == FieldType.MULTI_SELECT) {
            for (String item : input.split(",", -1))
                if (!InputValidation.isValid(field.validation, item.trim())) return false;
        } else if (!InputValidation.isValid(field.validation, input)) return false;
        if (field.type == FieldType.BOOLEAN){
            input = input.toLowerCase(Locale.ROOT);
            return "y".equals(input) || "n".equals(input) || "true".equals(input) || "false".equals(input);
        }
        if (checkFiles && field.type == FieldType.FILE){
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
        return isValidRecordArray(field, records, true);
    }

    public static boolean isValidRecordArray(InputField field, List<Map<String, Object>> records, boolean checkFiles) {
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
            if (!isValid(anchorField, anchorValue.toString(), checkFiles)) {
                return false;
            }
            for (int i = 1; i < field.childFields.size(); i++) {
                InputField child = field.childFields.get(i);
                Object childValue = record.get(child.name);
                if (childValue != null && !childValue.toString().isBlank() && !isValid(child, childValue.toString(), checkFiles)) {
                    return false;
                }
            }
        }
        return true;
    }

}
