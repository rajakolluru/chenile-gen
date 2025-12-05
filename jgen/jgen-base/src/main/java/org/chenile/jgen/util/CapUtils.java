package org.chenile.jgen.util;

public class CapUtils {
    public static String capitalizeFirst(String s){
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
    public static String capitalizeHyphens(String s){
        String[] arr = s.split("-");
        StringBuilder sb = new StringBuilder();
        for (String s1: arr){
            sb.append(capitalizeFirst(s1));
        }
        return sb.toString();
    }

    public static void main(String[] args){
        System.out.println("Capitalize hyphens: " + capitalizeHyphens("file-processor-chain"));
    }
}
