package io.github.tkote.fn.eventrouter;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnproject.fn.api.InputEvent;
import com.fnproject.fn.api.OutputEvent;

public class HttpEventHelper {

    private static final ObjectMapper om = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static <T> T getInputBody(InputEvent inputEvent, Class<T> clazz){
        return inputEvent.consumeBody(is -> {
            try{
                byte[] input = null;
                try(ByteArrayOutputStream out = new ByteArrayOutputStream()){
                    byte[] buf = new byte[1024];
                    while(true){
                        int len = is.read(buf);
                        if(-1 == len) break;
                        else        out.write(buf, 0, len);;
                    }
                    input = out.toByteArray();
                }

                if(clazz.getName().equals(String.class.getName())){
                    return (T)(new String(input, "UTF-8"));
                }else if(clazz.getName().equals(byte[].class.getName())){
                    return (T)input;
                }else{
                    return (T)om.readValue(input, clazz);
                }
            }catch(Exception e){
                throw new RuntimeException("Couldn't get input data - " + e.getMessage(), e);
            }
        });
    }

    public static String getInputBodyAsString(InputEvent inputEvent){
        return getInputBody(inputEvent, String.class);
    }

    public static OutputEvent createTextOutputEvent(String s){
        try{
            return OutputEvent.fromBytes(s.getBytes("UTF-8"), OutputEvent.Status.Success, "text/plain; charset=UTF-8");        
        }catch(Exception e){
            throw new RuntimeException("Couldn't create OutputEvent - " + e.getMessage(), e);
        }

    }

    public static OutputEvent createJsonOutputEvent(Object obj){
        try{
            String s = obj instanceof CharSequence ? obj.toString() : om.writeValueAsString(obj);
            return OutputEvent.fromBytes(s.getBytes("UTF-8"), OutputEvent.Status.Success, "application/json");        
        }catch(Exception e){
            throw new RuntimeException("Couldn't create OutputEvent - " + e.getMessage(), e);
        }
    }

}
