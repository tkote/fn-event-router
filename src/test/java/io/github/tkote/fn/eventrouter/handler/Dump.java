package io.github.tkote.fn.eventrouter.handler;

import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnInit;
import io.github.tkote.fn.eventrouter.FnHttpEvent;
import io.github.tkote.fn.eventrouter.HttpEventHelper;
import com.fnproject.fn.api.InputEvent;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;

@FnBean
public class Dump{

    private RuntimeContext rctx;

    @FnInit
    public void onInit(RuntimeContext rctx) {
        this.rctx = rctx;
    }

    @FnHttpEvent(method = "ANY", path = ".*/dump", outputType = "text")
    public String handleRequest(InputEvent inputEvent, HTTPGatewayContext hctx) {
        final StringBuilder sb = new StringBuilder();
        sb.append("FN DUMP\n");
        sb.append("===================================\n");
        System.getenv().entrySet().forEach(s -> sb.append(String.format("ENV: %s=%s\n", s.getKey(), s.getValue())));
        sb.append("\n");
        sb.append(String.format("RTCTX: AppID=%s\n", rctx.getAppID()));
        sb.append(String.format("RTCTX: AppName=%s\n", rctx.getAppName()));
        sb.append(String.format("RTCTX: FunctionID=%s\n", rctx.getFunctionID()));
        sb.append(String.format("RTCTX: FunctionName=%s\n", rctx.getFunctionName()));
        sb.append("\n");
        sb.append(String.format("HGCTX: Method=%s\n", hctx.getMethod()));
        sb.append(String.format("HGCTX: RequestURL=%s\n", hctx.getRequestURL()));
        hctx.getHeaders().asMap().entrySet().forEach(s -> {
            s.getValue().iterator().forEachRemaining(v -> sb.append(String.format("HGCTX/HEADER: %s=%s\n", s.getKey(), v)));
        });
        hctx.getQueryParameters().getAll().entrySet().forEach(s -> {
            s.getValue().iterator().forEachRemaining(v -> sb.append(String.format("HGCTX/QPARAM: %s=%s\n", s.getKey(), v)));
        });
        sb.append("CONTENT:\n");
        sb.append(HttpEventHelper.getInputBodyAsString(inputEvent));
        sb.append("\n[EOF]\n");

        return sb.toString();
    }


}