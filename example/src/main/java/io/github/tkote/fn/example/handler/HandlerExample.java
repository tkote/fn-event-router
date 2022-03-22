package io.github.tkote.fn.example.handler;

import java.util.concurrent.TimeUnit;

import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnInit;
import io.github.tkote.fn.eventrouter.FnHttpEvent;
import io.github.tkote.fn.eventrouter.HttpEventHelper;
import com.fnproject.fn.api.InputEvent;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;
import com.fnproject.fn.api.tracing.TracingContext;

@FnBean
public class HandlerExample{

    private RuntimeContext rctx;

    @FnInit
    public void onInit(RuntimeContext rctx) {
        this.rctx = rctx;
    }

    @FnHttpEvent(method = "GET", path = ".*/nop")
    public void nop() {
    }

    @FnHttpEvent(method = "GET", path = ".*/hello") // outputType = "json"
    public String hello() {
        return "{\"message\":\"Hello World!\"}";
    }

    @FnHttpEvent(method = "POST", path = ".*/dump", outputType = "text")
    public String dump(InputEvent inputEvent, HTTPGatewayContext hctx, TracingContext tctx) {
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
        sb.append(String.format("TCTX: TracingEnabled=%b\n", tctx.isTracingEnabled()));
        sb.append(String.format("TCTX: ServiceName=%s\n", tctx.getServiceName()));
        sb.append(String.format("TCTX: TraceId=%s\n", tctx.getTraceId()));
        sb.append(String.format("TCTX: ParentSpanId=%s\n", tctx.getParentSpanId()));
        sb.append(String.format("TCTX: SpanId=%s\n", tctx.getSpanId()));
        sb.append(String.format("TCTX: Flags=%s\n", tctx.getFlags()));
        sb.append(String.format("TCTX: Sampled=%b\n", tctx.isSampled()));

        sb.append("CONTENT:\n");
        sb.append(HttpEventHelper.getInputBodyAsString(inputEvent));
        sb.append("\n[EOF]\n");

        return sb.toString();
    }

    @FnHttpEvent(method = "GET", path = ".*/sleep")
    public void sleep(InputEvent inputEvent, HTTPGatewayContext hctx) {
        String duration = hctx.getQueryParameters().get("duration").orElse("1000");
        try{
            TimeUnit.MILLISECONDS.sleep(Long.parseLong(duration));
        }catch(InterruptedException e){}
    }

}