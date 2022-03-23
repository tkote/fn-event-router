package io.github.tkote.fn.eventrouter.handler;

import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnInject;
import io.github.tkote.fn.eventrouter.annotation.FnHttpEvent;
import io.github.tkote.fn.eventrouter.util.Util;


@FnBean
public class UtilTest{

    @FnInject
    private Util util;

    @FnHttpEvent(method = "POST", path = ".*/util")
    public Query handleRequest(Query query) {

        System.out.println("Query: " + query);
        System.out.println("Util: " + util);

        String value = util.getRuntimeConfig(query.getParam()).orElse("<undefined>");
        query.setResult(value);

        return query;
    }


    public static class Query{
        private String param;
        private String result;

        public Query(){}

        public String getParam() {
            return param;
        }

        public void setParam(String param) {
            this.param = param;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }
    
}