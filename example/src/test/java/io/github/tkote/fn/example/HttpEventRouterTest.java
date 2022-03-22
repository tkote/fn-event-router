package io.github.tkote.fn.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fnproject.fn.api.OutputEvent.Status;
import com.fnproject.fn.testing.*;

import org.apache.commons.io.IOUtils;
import org.junit.*;

import io.github.tkote.fn.eventrouter.EventRouter;
import io.github.tkote.fn.example.handler.Query.QueryRequest;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

public class HttpEventRouterTest {

    private ObjectMapper om = new ObjectMapper();
    private JsonNode funcYaml;
    
    @Rule
    //public final FnTestingRule testing = FnTestingRule.createDefault();
    public final FnTestingRule testing = FnTestingRule.createDefault()
    .setConfig("FN_APP_NAME", "testapp")
    .setConfig("FN_FN_NAME", "testfunc")
    .setConfig("OCI_TRACING_ENABLED", "0")
    .setConfig("OCI_TRACE_COLLECTOR_URL", "");

    public FnTestingRule setFuncYamlSettings(FnTestingRule testing){
        if(Objects.isNull(funcYaml)){
            try{
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                funcYaml = mapper.readValue(new File("func.yaml"), JsonNode.class);
                //System.out.println(om.writeValueAsString(funcYaml));
            }catch(Exception e){
                throw new RuntimeException(e.getMessage());
            }
        }
        JsonNode config = funcYaml.get("config");
        testing
        .setConfig("OCI_REGION", config.get("OCI_REGION").asText())
        //.setConfig("ADB_ID", config.get("ADB_ID").asText())
        //.setConfig("ADB_WALLET_DIR", config.get("ADB_WALLET_DIR").asText())
        //.setConfig("ADB_WALLET_PW", config.get("ADB_WALLET_PW").asText())
        .setConfig("JDBC_URL", config.get("JDBC_URL").asText())
        .setConfig("JDBC_USERNAME", config.get("JDBC_USERNAME").asText())
        .setConfig("JDBC_PASSWORD", config.get("JDBC_PASSWORD").asText())
        .setConfig("LOGGING", config.get("LOGGING").asText());
         return testing;
    }

    @Test
    public void hello() {
        // run only if it's local (not in container)
        if(Files.exists(Path.of(".gitignore"))){
            setFuncYamlSettings(testing)
                .setConfig("QUERY_ENABLED", "false")
                .givenEvent()
                .withHeader("Fn-Http-Method", "GET")
                .withHeader("Fn-Http-Request-Url", "foo/nop")
                .enqueue()
                .givenEvent()
                .withHeader("Fn-Http-Method", "GET")
                .withHeader("Fn-Http-Request-Url", "bar/hello")
                .enqueue();
            testing.thenRun(EventRouter.class, "handleRequest");
            List<FnResult> fnResults = testing.getResults();
            assertEquals(0, fnResults.get(0).getBodyAsBytes().length);
            assertEquals("application/json", fnResults.get(1).getContentType().get());
            assertEquals("{\"message\":\"Hello World!\"}", fnResults.get(1).getBodyAsString());
            String response = fnResults.get(1).getBodyAsString();
            System.out.println(response);
        }
    }

    @Test
    public void dump() {
        // run only if it's local (not in container)
        if(Files.exists(Path.of(".gitignore"))){
            setFuncYamlSettings(testing)
                .setConfig("QUERY_ENABLED", "false")
                .givenEvent()
                .withHeader("Fn-Http-Method", "POST")
                .withHeader("Fn-Http-Request-Url", "/dump?foo=bar")
                .withHeader("Fn-Http-Content-Type", "application/json")
                .withBody("{\"message\":\"hello\"}")
                .enqueue();
            testing.thenRun(EventRouter.class, "handleRequest");
            FnResult fnResult = testing.getOnlyResult();
            assertEquals("text/plain; charset=UTF-8", fnResult.getContentType().get());
            String response = fnResult.getBodyAsString();
            System.out.println(response);
        }
    }

    @Test
    public void forward() throws Exception{
        // run only if it's local (not in container)
        if(Files.exists(Path.of(".gitignore"))){
            try(InputStream in = this.getClass().getResourceAsStream("/request.json")){
                Objects.requireNonNull(in);
                String request = IOUtils.toString(in, "UTF-8");
                //System.out.println(request);
                setFuncYamlSettings(testing)
                    .setConfig("QUERY_ENABLED", "false")
                    .givenEvent()
                    .withHeader("Fn-Http-Method", "POST")
                    .withHeader("Fn-Http-Request-Url", "/demo/forward")
                    .withHeader("Fn-Http-H-Content-Type", "application/json")
                    .withBody(request)
                    .enqueue();
                
                testing.thenRun(EventRouter.class, "handleRequest");
    
                FnResult fnResult = testing.getOnlyResult();
                assertEquals(Status.Success, fnResult.getStatus());
                String response = fnResult.getBodyAsString();
                System.out.println(response);
                assertEquals("Hello World!", response);
            }
        }
    }

    //@Test // comment out if you'd like to test database connection
    public void query() throws Exception{
        // run only if it's local (not in container)
        if(Files.exists(Path.of(".gitignore"))){
            QueryRequest request = new QueryRequest("select * from dual");
            QueryRequest badRequest = new QueryRequest("select * from non_existing_table");

            setFuncYamlSettings(testing)
                .setConfig("QUERY_ENABLED", "true")
                .givenEvent()
                .withHeader("Fn-Http-Method", "POST")
                .withHeader("Fn-Http-Request-Url", "/demo/query")
                .withHeader("Fn-Http-Content-Type", "application/json")
                .withBody(om.writeValueAsString(request))
                .enqueue()
                .givenEvent()
                .withHeader("Fn-Http-Method", "POST")
                .withHeader("Fn-Http-Request-Url", "/demo/query")
                .withHeader("Fn-Http-Content-Type", "application/json")
                .withBody(om.writeValueAsString(badRequest))
                .enqueue();
            testing.thenRun(EventRouter.class, "handleRequest");

            List<FnResult> fnResults = testing.getResults();
            assertEquals("[{\"DUMMY\":\"X\"}]", fnResults.get(0).getBodyAsString());
            assertEquals("500", fnResults.get(1).getHeaders().get("Fn-Http-Status").get());
            System.out.println(fnResults.get(1).getBodyAsString());
        }
    }

    //@Test // comment out if you'd like to test database connection
    public void query2() throws Exception{
        // run only if it's local (not in container)
        if(Files.exists(Path.of(".gitignore"))){
            QueryRequest request = new QueryRequest("select * from dual");

            setFuncYamlSettings(testing)
                .setConfig("QUERY_ENABLED", "false")
                .givenEvent()
                .withHeader("Fn-Http-Method", "POST")
                .withHeader("Fn-Http-Request-Url", "/demo/query")
                .withHeader("Fn-Http-Content-Type", "application/json")
                .withBody(om.writeValueAsString(request))
                .enqueue();
            testing.thenRun(EventRouter.class, "handleRequest");

            List<FnResult> fnResults = testing.getResults();
            assertEquals("503", fnResults.get(0).getHeaders().get("Fn-Http-Status").get());
        }
    }
  

}