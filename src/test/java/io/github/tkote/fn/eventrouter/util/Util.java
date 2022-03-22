package io.github.tkote.fn.eventrouter.util;

import java.util.Optional;

import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnInit;
import com.fnproject.fn.api.RuntimeContext;

@FnBean
public class Util{

    private RuntimeContext rctx;

    @FnInit
    public void configure(RuntimeContext rctx) {
        this.rctx = rctx;
    }

    public Optional<String> getRuntimeConfig(String key){
        return rctx.getConfigurationByKey(key);
    }
}
