package io.github.tkote.fn.example.handler;

import java.util.Optional;
import java.util.logging.Logger;

import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnHttpEvent;
import io.github.tkote.fn.eventrouter.HttpEventHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnproject.fn.api.InputEvent;
import com.fnproject.fn.api.OutputEvent;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;
import com.fnproject.fn.api.tracing.TracingContext;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

@FnBean
public class Forward{
    private final static Logger logger = Logger.getLogger(Forward.class.getName());

    private RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(5000)
        .setConnectionRequestTimeout(30000)
        .setSocketTimeout(30000)
        .build();

    @FnHttpEvent(method="POST", path=".*/forward")
    public OutputEvent handleRequest(InputEvent inputEvent, HTTPGatewayContext hctx, TracingContext tctx) {

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build()) {
            String inputData = HttpEventHelper.getInputBodyAsString(inputEvent);
            logger.info(inputData);
            // mwthod, url, data
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(inputData);
            String url = json.get("url").asText();
            String m = json.get("method").asText();
            String body = mapper.writeValueAsString(json.get("body"));
            logger.fine("url: " + url);
            logger.fine("method: " + m);
            logger.fine("body:\n" + body);
    
            HttpUriRequest request = null;
            if (m.equalsIgnoreCase("GET")) {
                HttpGet requestGet = new HttpGet(url);
                request = requestGet;
            } else if (m.equalsIgnoreCase("POST")) {
                HttpPost requestPost = new HttpPost(url);
                requestPost.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
                request = requestPost;
            } else {
                throw new RuntimeException("Unsupported method: " + m);
            }

            final HttpUriRequest r = request;
            r.setHeader("X-B3-Sampled", tctx.isSampled() ? "1" : "0");
            Optional.ofNullable(tctx.getTraceId()).ifPresent(h -> r.setHeader("X-B3-TraceId", h));
            Optional.ofNullable(tctx.getParentSpanId()).ifPresent(h -> r.setHeader("X-B3-ParentSpanId", h));
            Optional.ofNullable(tctx.getSpanId()).ifPresent(h -> r.setHeader("X-B3-SpanId", h));
            Optional.ofNullable(tctx.getFlags()).ifPresent(h -> r.setHeader("X-B3-Flages", h));
           
            try (CloseableHttpResponse response = httpclient.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                logger.fine("Response status code: " + status);

                hctx.setStatusCode(status);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    byte[] byteBody = EntityUtils.toByteArray(entity);
                    if (byteBody.length > 0) {
                        String contentType = response.getLastHeader("Content-Type").getValue();
                        logger.fine("Content-Type: " + contentType);
                        return OutputEvent.fromBytes(byteBody, OutputEvent.Status.Success, contentType);
                    }
                }
                return OutputEvent.emptyResult(OutputEvent.Status.Success);
            }catch(Exception e){
                throw new RuntimeException("Failed to send request - " + e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Couldn't forward message - " + e.getMessage(), e);
        }
    }
   

}