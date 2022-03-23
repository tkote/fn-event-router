package io.github.tkote.fn.eventrouter;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnHttpEvent;
import io.github.tkote.fn.eventrouter.annotation.FnInit;
import io.github.tkote.fn.eventrouter.annotation.FnInject;
import io.github.tkote.fn.eventrouter.logging.Logging;
import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.InputEvent;
import com.fnproject.fn.api.OutputEvent;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;
import com.fnproject.fn.api.tracing.TracingContext;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;


public class EventRouter {
    private final static Logger logger = Logger.getLogger(EventRouter.class.getName());

    // simple class to store relation btw annotations and instances
    public static class Handler{
        public Object obj;
        public Method method;
        public FnHttpEvent annotation;
        public Handler(Object obj, Method method, FnHttpEvent annotation){
            this.obj = obj;
            this.method = method;
            this.annotation = annotation;
        }
        public String toString(){
            return obj.getClass().getName() + "#" + method.getName();
        }
    }


    private Map<String, Object> fnBeans = new LinkedHashMap<>();
    private Map<String, Handler> handlers =  new LinkedHashMap<>();

    @FnConfiguration
    public void setUp(RuntimeContext rctx) throws Exception {
        // update logging settings
        String logOpt = rctx.getConfiguration().getOrDefault("LOGGING", "");
        Logging.update(logOpt);

        logger.info(String.format("Setup: App=%s, Function=%s", rctx.getAppName(), rctx.getFunctionName()));

        Index index = null;
        try(InputStream in = EventRouter.class.getResourceAsStream("/META-INF/jandex.idx")){
            IndexReader reader = new IndexReader(in);
            index = reader.read();
            index.getKnownClasses().stream().forEach(c -> logger.fine("jandex: " + c.toString()));
        }

        // scan classes with FnBean and instanciate
        for (AnnotationInstance annotationInstance : index.getAnnotations(DotName.createSimple(FnBean.class.getName()))) {
            ClassInfo classInfo = annotationInstance.target().asClass();
            logger.fine("FnBean: " + classInfo);
            String className = classInfo.toString();

            Class<?> clazz = Class.forName(className);
            if(Modifier.isAbstract(clazz.getModifiers()) || Modifier.isInterface(clazz.getModifiers())){
                continue;
            }
            Constructor<?> constructor = clazz.getConstructor(new Class[]{});
            Object fnBean = constructor.newInstance(new Object[]{});
            fnBeans.put(className, fnBean);

            for(Method method : clazz.getMethods()){
                // search handler methods
                FnHttpEvent annotation = method.getAnnotation(FnHttpEvent.class);
                if(Objects.nonNull(annotation)){
                    logger.fine("@FnHttpEvent: " + className + "#" + method.getName());
                    Handler handler = new Handler(fnBean, method, annotation);
                    handlers.put(handler.toString(), handler);
                }
            }

        }

        // inject FnBeans
        for(Object fnBean : fnBeans.values()){
            final String className = fnBean.getClass().getName();
            for(Field field : fnBean.getClass().getDeclaredFields()){
                if(Objects.nonNull(field.getAnnotation(FnInject.class))){
                    logger.fine("@FnInject: " + className + "#" + field.getName());
                    String type = field.getType().getName();
                    Object injectee = fnBeans.get(type);
                    if(Objects.isNull(injectee)){
                        throw new IllegalArgumentException("No such FnBean exists: " + type);
                    }
                    field.setAccessible(true);
                    field.set(fnBean, injectee);
                    logger.fine("@FnInject complete: " + className + "#" + field.getName());
                }
            }
        }

        // do FnInit
        for(Object fnBean : fnBeans.values()){
            final String className = fnBean.getClass().getName();
            for(Method method : fnBean.getClass().getDeclaredMethods()){
                if(Objects.nonNull(method.getAnnotation(FnInit.class))){
                    logger.fine("@FnInit: " + className + "#" + method.getName());
                    // do init 
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    int numParams = parameterTypes.length;
                    Object[] parameters = new Object[numParams];
                    for(int i = 0 ; i < numParams ; i++){
                        Class<?> paramType = parameterTypes[i];
                        if(paramType.equals(RuntimeContext.class)){
                            parameters[i] = rctx;
                        }else{
                            throw new IllegalArgumentException("Parameter type not supported for @FnInit: " + paramType);
                        }
                    }
                    method.invoke(fnBean, parameters);
                    logger.fine("@FnInit complete: " + className + "#" + method.getName());
                }
            }
        }

        Fn.setFnBeans(fnBeans);
    }

    public OutputEvent handleRequest(InputEvent inputEvent, HTTPGatewayContext hctx, TracingContext tctx) {

        String requestURL = hctx.getRequestURL();
        final int ndx = requestURL.indexOf("?");
        requestURL = ndx >= 0 ? requestURL.substring(0, ndx) : requestURL; 

        final String method = hctx.getMethod();
        logger.info(String.format("HTTP Request (START): method=%s, requestURL=%s", method, requestURL));

        try{
            final List<Handler> candidates = new ArrayList<>();
            for(Map.Entry<String, Handler> entry : handlers.entrySet()){
                logger.finer("Handler entry: " + entry.getKey());
                final String c = entry.getKey();
                final Handler handler = entry.getValue();
                final FnHttpEvent httpEvent = handler.annotation;
                if(Objects.nonNull(httpEvent)){
                    final String m = httpEvent.method();
                    final String p = httpEvent.path();
                    logger.finer(String.format("Evaluating EventHandler: class=%s, method=%s, path=%s", c, m, p));
                    if((m.equalsIgnoreCase("ANY") || method.equalsIgnoreCase(m)) && requestURL.matches(p)){
                        candidates.add(handler);
                    }
                }else{
                    logger.finer(String.format("No annotation found: " + c));
                }
            }
            if(candidates.size() == 0){
                throw new IllegalStateException(String.format("No handler was found - method=%s, path=%s", method, requestURL));
            }else if(candidates.size() > 1){
                String candidateNames = candidates.stream().map(h -> h.getClass().getName()).collect(Collectors.joining(","));
                throw new IllegalStateException(String.format("Found multiple handlers - method=%s, path=%s >> %s", method, requestURL,candidateNames));
            }
            final Handler handler = candidates.get(0);
            logger.info("Matched handler: " + handler);
            Class<?>[] parameterTypes = handler.method.getParameterTypes();
            int numParams = parameterTypes.length;
            Object[] parameters = new Object[numParams];
            for(int i = 0 ; i < numParams ; i++){
                Class<?> paramType = parameterTypes[i];
                if(paramType.equals(InputEvent.class)){
                    parameters[i] = inputEvent;
                }else if(paramType.equals(HTTPGatewayContext.class)){
                    parameters[i] = hctx;
                }else if(paramType.equals(TracingContext.class)){
                    parameters[i] = tctx;
                }else{
                    parameters[i] = HttpEventHelper.getInputBody(inputEvent, paramType);
                }
            }
            Object result = handler.method.invoke(handler.obj, parameters);
            Class<?> returnType = handler.method.getReturnType();
            if(Objects.isNull(result)){
                return OutputEvent.emptyResult(OutputEvent.Status.Success);
            }else if(returnType.equals(OutputEvent.class)){
                return (OutputEvent)result;
            }else if(returnType.equals(HttpResponse.class)){
                HttpResponse response = (HttpResponse)result;
                hctx.setStatusCode(response.getStatus());
                return response.getOutputEvent();
            }else if(returnType.equals(String.class)){
                String outputType = handler.annotation.outputType();
                if(outputType.equals("text")){
                    return HttpEventHelper.createTextOutputEvent((String)result);
                }else if(outputType.equals("json")){
                    return HttpEventHelper.createJsonOutputEvent((String)result);
                }else{
                    throw new IllegalArgumentException("Unsupported output type: " + outputType);
                }
            }else{
                return HttpEventHelper.createJsonOutputEvent(result);
            }
        } catch (Exception e) {
            Throwable cause = e;
            if(e instanceof InvocationTargetException){
                cause = Optional.ofNullable(e.getCause()).orElse(e);
            }
            logger.log(Level.SEVERE, "Error while processing request - " + cause.getMessage(), cause);
            hctx.setStatusCode(500 /*HttpStatus.SC_INTERNAL_SERVER_ERROR*/);
            return OutputEvent.emptyResult(OutputEvent.Status.Success);
        } finally {
            logger.info(String.format("HTTP Request (END): method=%s, requestURL=%s", method, requestURL));
        }

    }
 
}