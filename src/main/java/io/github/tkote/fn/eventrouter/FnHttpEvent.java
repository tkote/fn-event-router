package io.github.tkote.fn.eventrouter;

import java.lang.annotation.Retention;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.MODULE, ElementType.METHOD, ElementType.TYPE})
public @interface FnHttpEvent {

    String method();
    String path();
    String outputType() default "json"; // text or json

    String value() default ""; // default value must be constant, not null

}
