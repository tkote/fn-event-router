package io.github.tkote.fn.eventrouter.logging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.LogManager;

public class Logging {

    // setup logging
    static{
        try(InputStream in = Logging.class.getResourceAsStream("/logging.properties")){
            if(Objects.nonNull(in)){
                final Properties prop = new Properties();
                prop.load(in);
    
                System.getenv().entrySet().forEach(entry -> {
                    final String key = entry.getKey();
                    final String val = entry.getValue();
                    if(key.startsWith("logging:")) prop.setProperty(key.substring("logging:".length()), val);
                });
                System.getProperties().entrySet().forEach(entry -> {
                    final String key = (String)entry.getKey();
                    final String val = (String)entry.getValue();
                    if(key.startsWith("logging:")) prop.setProperty(key.substring("logging:".length()), val);
                });
    
                //prop.list(System.err);
                try(ByteArrayOutputStream bout = new ByteArrayOutputStream()){
                    prop.store(bout, "logging.properties");
                    bout.flush();
                    try(ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray())){
                        LogManager.getLogManager().readConfiguration(bin);
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    // update logging settings
    public static void update(String logOptions){
        if(Objects.isNull(logOptions) || 0 == logOptions.trim().length()) return;
        final Properties prop = new Properties();
        String[] logEntries = logOptions.trim().split(",");
        Arrays.stream(logEntries).forEach(e -> {
            String[] keyval = e.split("=");
            if(2 == keyval.length)  prop.setProperty(keyval[0].trim(), keyval[1].trim());
        });
        try(ByteArrayOutputStream bout = new ByteArrayOutputStream()){
            prop.store(bout, "logging.properties");
            bout.flush();
            try(ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray())){
                LogManager.getLogManager().updateConfiguration(bin, (k) -> ((o, n) -> n == null ? o : n)); // merge
            }
        }catch(Exception e){
            throw new RuntimeException("Failed to update log settings - " + e.getMessage(), e);
        }
    }


    

}
