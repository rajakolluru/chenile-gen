package org.chenile.jgen.util;

import org.chenile.owiz.BeanFactoryAdapter;

import java.lang.reflect.Constructor;

/**
 * A class that looks for beans in a particular package name.
 * It converts an hyphenated name into a class name and looks for the class in the passed package.
 * If it finds it, then the class is instantiated and returned. Each call to lookup results in a new
 * instance of the class being created.
 */
public class ProcessorFactory implements BeanFactoryAdapter {
    private final String packageNameToLook;
    public ProcessorFactory(String packageNameToLook){
        this.packageNameToLook = packageNameToLook;
    }
    @Override
    public Object lookup(String componentName) {
        componentName = CapUtils.capitalizeHyphens(componentName);
        try{
            Class<?> compClass = Class.forName(packageNameToLook + componentName);
            Constructor<?> constructor = compClass.getDeclaredConstructor();
            return constructor.newInstance();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}
