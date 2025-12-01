package org.chenile.jgen;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.text.StringSubstitutor;
import org.chenile.jgen.util.CapUtils;
import org.chenile.jgen.util.FileProcessingHelper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

/**
 * Copies a template to an output folder
 */
public class TemplateCopier {

    public static final String MUSTACHE_EXTENSION = ".mustache";
    public static final String FILE_LIST_EXTENSION = ".filelist";
    public static final String START = "--START--";
    public static final String END_FILE = "--END--";
    MustacheFactory mf = new DefaultMustacheFactory();
    CopyFromJar copyFromJar = new CopyFromJar();
    public void process(String input, String output, boolean isJar,
                        Map<String,Object> map) throws Exception{
        if(isJar)
            copyFromJar.copyFromJar(input, Paths.get(output));
        else
            copyFolder(new File(input),new File(output));
        File out = new File(output);
        loadUsefulLambdas(map);
        processTemplates(out,map);
        substituteVarsInName(out,map);
        processConditionals(out,map);
    }

    /**
     * Processes a folder of the form %%key=value%%
     * If the key value (in map) is equal to value then all the folders and files under this folder
     * are copied to the parent of this folder. Else the parent is left untouched.<br/>
     * This folder is deleted irrespective
     * @param output output folder
     * @param map the input map
     * @throws Exception if there is an exception
     */
    private void processConditionals(File output, Map<String, Object> map) throws Exception{
        FileProcessingHelper.iterateFiles((file) -> processConditional(file,map),true,output);
    }

    /**
     * Enhance the map with useful lambda functions that can be used by the
     * templates.
     */
    private void loadUsefulLambdas(Map<String,Object> map){
        Function<String,String> capitalize = CapUtils::capitalizeFirst;
        map.put("capitalize", capitalize);
    }

    private void processConditional(File file, Map<String, Object> map) throws Exception {
        String filename = file.getName();
        if (!filename.startsWith("%%")){
            return;
        }
        String expr = filename.substring(2);
        expr = expr.substring(0,expr.length() - 2);
        int index = expr.indexOf("=");
        String lhs = expr.substring(0,index);
        Object lhs_value = map.get(lhs);
        String rhs = expr.substring(index+1);
        if (lhs_value != null && lhs_value.toString().equals(rhs)){
            moveContentsToParent(file);
        }
        FileUtils.deleteDirectory(file);
    }

    private void moveContentsToParent(File file) {
        for(File f: file.listFiles()){
            File parent = new File(file.getParent() + File.separator + f.getName());
            boolean ignoreMe = f.renameTo(parent);
            if (!ignoreMe){
               System.out.println("Move unsuccessful");
            }
        }
    }

    public void copyFolder(File input, File output) throws Exception{
        FileUtils.copyDirectory(input, output);
    }

    public void processTemplates(File output,Map<String,Object> map) throws Exception{
        FileProcessingHelper.iterateFiles((file) -> processTemplate(file,map),true,output);
    }

    /**
     * In every file under the output folder, look for files/folders whose names
     * are of the form __XXX__ where XXX is any key found in the map that is passed to this method.<br/>
     * Substitute __XXX__ with the value from the map. <br/>
     * For example, if a file called __XXX__Foo.java is a file and if the value of XXX in map is "Abc"
     * then the file would be renamed to AbcFoo.java
     * @param output the folder to look under
     * @param map map of the name value pairs
     * @throws Exception if there is an exception in processing
     */
    public void substituteVarsInName(File output,Map<String,Object> map) throws Exception{
        FileProcessingHelper.iterateFiles((file) -> substituteName(file,map),true,output);
    }

    private void substituteName(File name, Map<String, Object> map) {
        StringSubstitutor substitutor = new StringSubstitutor(map,"__","__");
        String folder = name.getParent();
        String filename = name.getName();
        String newName = substitutor.replace(filename);
        if (!newName.equals(filename)) {
            String destination = folder + File.separator + newName;
            boolean ignore = name.renameTo(new File(destination));
        }
    }

    private void processTemplate(File name,Map<String,Object> map) {
        String n = name.getPath();
        if(!n.endsWith(MUSTACHE_EXTENSION)) return;
        String out = n.substring(0,n.length() - MUSTACHE_EXTENSION.length());
        try (java.io.FileReader fileReader = new FileReader(n)) {
            Mustache m = mf.compile(fileReader,n);
            FileWriter fileWriter = new FileWriter(out);
            m.execute(fileWriter,map).flush();
            fileWriter.close();
            FileUtils.deleteQuietly(new File(n));
            if(out.endsWith(FILE_LIST_EXTENSION))
                handleFileList(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param fileList
     * Given a file (sample.filelist)  that has the following format:
     * <code>
     * --START--abc.txt--
     * Contents of abc.txt
     * --END--
     * --START--xyz.txt--
     * Contents of xyz.txt
     * --END--
     * </code>
     * This script breaks the file into abc.txt and xyz.txt and deletes
     * sample.filelist. Both the files are created in the same folder
     * where the sample.filelist file resides.
     */
    private void handleFileList(String fileList) throws IOException {
        File file = new File(fileList);
        String folder = file.getParent();
        FileWriter writer = null;
        LineIterator iterator = FileUtils.lineIterator(file);
        while(iterator.hasNext()){
            String line = iterator.next();
            if (line.trim().startsWith(START)){
                String filename = folder + File.separator + line.substring(START.length());
                writer = new FileWriter(filename);
                continue;
            }
            if (line.trim().startsWith(END_FILE)){
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
                continue;
            }
            if (writer != null){
                writer.append(line);
                writer.append("\n");
            }
        }
        boolean ignoreMe = file.delete();
    }

    public static void main(String[] args) throws Exception{
        TemplateCopier copier = new TemplateCopier();
        Map<String,Object> map = new HashMap<>();
        List<String> list = new ArrayList<>();
        list.add("p");
        list.add("q");
        list.add("r");
        map.put("abc","def");
        map.put("pqr",list);
        String a = "src/test/resources/trial";
        String b = "target/b";
        File b1 = new File(b);
        if (b1.exists())
            b1.delete();
        System.out.println(map);
        copier.process(a,b,false,map);
    }

}
