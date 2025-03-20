package org.chenile.jgen.util;

import java.io.File;
import java.util.Objects;

public class FileProcessingHelper {
    @FunctionalInterface
    public interface CheckedConsumer<T> {
        void apply(T t) throws Exception;
    }

    public static void iterateFiles(CheckedConsumer<File> consumer, boolean depthFirst, File startDir)
            throws Exception{
        doIterateFiles(consumer,depthFirst,startDir);
    }

    /**
     *
     * @param consumer - a consumer that accepts a file/folder and does some processing on it
     * @param depthFirst - indicates if a directory will be processed after the files/folders in it are processed
     * @param startDirs - the directories to do the processing from
     * @throws Exception if there is an exception
     */
    private static void doIterateFiles(CheckedConsumer<File> consumer, boolean depthFirst, File... startDirs)
            throws Exception{
        for (File startDir: startDirs){
            if (startDir.isDirectory()) {
                if (!depthFirst)
                    consumer.apply(startDir);
                doIterateFiles(consumer, depthFirst,Objects.requireNonNull(startDir.listFiles()));
                if(depthFirst)
                    consumer.apply(startDir);
            }else {
                consumer.apply(startDir);
            }
        }
    }
}
