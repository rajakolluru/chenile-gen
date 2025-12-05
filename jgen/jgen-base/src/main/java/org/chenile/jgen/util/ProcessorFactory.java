package org.chenile.jgen.util;

import org.chenile.owiz.BeanFactoryAdapter;

import java.lang.reflect.Constructor;

public class ProcessorFactory implements BeanFactoryAdapter {
    @Override
    public Object lookup(String componentName) {
        componentName = CapUtils.capitalizeHyphens(componentName);
        try{
            Class<?> compClass = Class.forName("org.chenile.jgen.template.chain." + componentName);
            Constructor<?> constructor = compClass.getDeclaredConstructor();
            return constructor.newInstance();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}
