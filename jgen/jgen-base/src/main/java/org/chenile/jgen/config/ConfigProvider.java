package org.chenile.jgen.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.Map;

public class ConfigProvider {
    ObjectMapper objectMapper = new ObjectMapper();
    public Config obtainDefaultConfig(){
        try(InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("config.json")){
            return obtainConfig(inputStream);
        }catch(Exception e){
            return null;
        }
    }

    public Config obtainConfig(File file){
        try(InputStream inputStream = new FileInputStream(file)){
            return obtainConfig(inputStream);
        }catch(Exception e){
            return null;
        }
    }

    private Config obtainConfig(InputStream inputStream) throws Exception{
        return objectMapper.readValue(inputStream, Config.class);
    }

    public Map<String,Object> getConfigAsMap(Config config){
        return objectMapper
                .convertValue(config, new TypeReference<Map<String, Object>>() {});
    }

}
