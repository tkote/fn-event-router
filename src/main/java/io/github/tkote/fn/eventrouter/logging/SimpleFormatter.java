package io.github.tkote.fn.eventrouter.logging;

import java.net.InetAddress;
import java.util.Objects;
import java.util.logging.LogRecord;

public class SimpleFormatter extends java.util.logging.SimpleFormatter{

    private static String hostname;

    static{
        hostname = System.getenv("HOSTNAME");
        if(Objects.isNull(hostname)){
            try{
                hostname = InetAddress.getLocalHost().getHostName();
            }catch(Exception e){
                hostname = "unknown";
            }
        }
    }

    @Override
    public String format(LogRecord record) {
        return super.format(record).replace("!host!", hostname);
    }

}
