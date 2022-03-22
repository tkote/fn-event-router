package io.github.tkote.fn.eventrouter;

import com.fnproject.fn.api.OutputEvent;

public class HttpResponse {
    private final int status;
    private final OutputEvent outputEvent;
    
    private HttpResponse(OutputEvent outputEvent, int status){
        this.outputEvent = outputEvent;
        this.status = status;
    }

    public static HttpResponse jsonResponse(Object obj, int status){
        return new HttpResponse(HttpEventHelper.createJsonOutputEvent(obj), status);
    }
    public static HttpResponse jsonResponse(Object obj){
        return new HttpResponse(HttpEventHelper.createJsonOutputEvent(obj), 200);
    }
    public static HttpResponse textResponse(String str, int status){
        return new HttpResponse(HttpEventHelper.createTextOutputEvent(str), status);
    }
    public static HttpResponse textResponse(String str){
        return new HttpResponse(HttpEventHelper.createTextOutputEvent(str), 200);
    }
    
    public OutputEvent getOutputEvent(){
        return outputEvent;
    }

    public int getStatus(){
        return status;
    }


}
