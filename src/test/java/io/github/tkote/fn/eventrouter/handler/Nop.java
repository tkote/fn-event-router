package io.github.tkote.fn.eventrouter.handler;

import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnInit;
import io.github.tkote.fn.eventrouter.annotation.FnHttpEvent;
import io.github.tkote.fn.eventrouter.HttpResponse;

@FnBean
public class Nop{

    @FnInit
    public void onInit() {
    }

    @FnHttpEvent(method = "GET", path = ".*/nop")
    public void nop() {
    }

    @FnHttpEvent(method = "ANY", path = ".*/not-ready")
    public HttpResponse notReady() {
        return HttpResponse.textResponse("Not ready.", 503);
    }

}