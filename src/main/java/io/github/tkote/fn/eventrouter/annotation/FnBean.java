package io.github.tkote.fn.eventrouter.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.MODULE, ElementType.TYPE})
public @interface FnBean {
    String value() default ""; // default value must be constant, not null
}
