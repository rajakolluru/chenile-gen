package org.chenile.jgen.util;

import org.chenile.jgen.blueprints.model.FieldType;
import org.chenile.jgen.blueprints.model.InputField;

import java.io.File;
import java.util.Locale;

public class FieldUtils {
    public static boolean isValid(InputField field, String input) {
        if (input == null || input.isEmpty()) return false;
        if (field.type == FieldType.BOOLEAN){
            input = input.toLowerCase(Locale.ROOT);
            return "y".equals(input) || "n".equals(input);
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
        return true;
    }

}
