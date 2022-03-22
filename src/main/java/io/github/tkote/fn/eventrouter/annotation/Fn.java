package io.github.tkote.fn.eventrouter.annotation;

import java.util.Map;
import java.util.Objects;

public class Fn {
    private static Map<String, Object> fnBeans = null;

    public static void setFnBeans(Map<String, Object> fb){
        if(Objects.nonNull(fnBeans)){
            throw new IllegalStateException("You cannot set fnBeans more than once.");
        }
        fnBeans = fb;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFnBean(Class<T> clazz){
        Objects.requireNonNull(fnBeans);
        return (T)fnBeans.get(clazz.getName());
    }
}
