package io.github.tkote.fn.eventrouter;

import com.fnproject.fn.testing.*;
import org.junit.*;
import static org.junit.Assert.*;

public class EventRouterTest {

    @Rule
    //public final FnTestingRule testing = FnTestingRule.createDefault();
    public final FnTestingRule testing = FnTestingRule.createDefault()
    .setConfig("FN_APP_NAME", "testapp")
    .setConfig("FN_FN_NAME", "testfunc")
    .setConfig("OCI_TRACING_ENABLED", "0")
    .setConfig("OCI_TRACE_COLLECTOR_URL", "")
    .setConfig("LOGGING", "io.github.tkote.fn.eventrouter.annotation.level=FINE, com.oracle.bmc.level=WARNING");

    @Test
    public void testDump() {
        testing
        .givenEvent()
        .withHeader("Fn-Http-Method", "POST")
        .withHeader("Fn-Http-Request-Url", "/dump?foo=bar")
        .withHeader("Fn-Http-Content-Type", "application/json")
        .withBody("{\"message\":\"hello\"}")
        .enqueue();
        testing.thenRun(EventRouter.class, "handleRequest");
        FnResult fnResult = testing.getOnlyResult();
        assertEquals("text/plain; charset=UTF-8", fnResult.getContentType().get());
        System.out.println(fnResult.getBodyAsString());
    }

    @Test
    public void testNop() {
        testing
        .givenEvent()
        .withHeader("Fn-Http-Method", "GET")
        .withHeader("Fn-Http-Request-Url", "/nop")
        .enqueue();
        testing.thenRun(EventRouter.class, "handleRequest");
        FnResult fnResult = testing.getOnlyResult();
        assertEquals("", fnResult.getBodyAsString());
    }

    @Test
    public void testNotReady() {
        testing
        .givenEvent()
        .withHeader("Fn-Http-Method", "GET")
        .withHeader("Fn-Http-Request-Url", "/not-ready")
        .enqueue();
        testing.thenRun(EventRouter.class, "handleRequest");
        FnResult fnResult = testing.getOnlyResult();
        assertEquals("503", fnResult.getHeaders().get("Fn-Http-Status").get());
        assertEquals("Not ready.", fnResult.getBodyAsString());
    }

    @Test
    public void testUtil() {
        testing
        .givenEvent()
        .withHeader("Fn-Http-Method", "POST")
        .withHeader("Fn-Http-Request-Url", "/util")
        .withHeader("Fn-Http-Content-Type", "application/json")
        .withBody("{\"param\":\"FN_APP_NAME\"}")
        .enqueue();
        testing.thenRun(EventRouter.class, "handleRequest");
        FnResult fnResult = testing.getOnlyResult();
        assertEquals("application/json", fnResult.getContentType().get());
        assertEquals("{\"param\":\"FN_APP_NAME\",\"result\":\"testapp\"}", fnResult.getBodyAsString());
        System.out.println(fnResult.getBodyAsString());
    }



}