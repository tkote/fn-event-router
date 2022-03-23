package io.github.tkote.fn.example.handler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.ArrayList;

import io.github.tkote.fn.eventrouter.annotation.Fn;
import io.github.tkote.fn.eventrouter.annotation.FnBean;
import io.github.tkote.fn.eventrouter.annotation.FnInit;
import io.github.tkote.fn.eventrouter.annotation.FnHttpEvent;
import io.github.tkote.fn.eventrouter.HttpEventHelper;
import io.github.tkote.fn.example.util.DatabaseUtil;
import com.fnproject.fn.api.OutputEvent;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;

@FnBean
public class Query{
    private final static Logger logger = Logger.getLogger(Query.class.getName());

    private Map<String, String> config;
    private boolean enabled;

    @FnInit
    public void onInit(RuntimeContext rctx){
        config = rctx.getConfiguration();
        enabled = Boolean.parseBoolean(config.getOrDefault("QUERY_ENABLED", "true"));
    }

    @FnHttpEvent(method="POST", path=".*/query")
    public OutputEvent handleRequest(QueryRequest request, HTTPGatewayContext hctx) {
        if(!enabled){
            logger.fine("Query is not enabled.");
            hctx.setStatusCode(503);
            return HttpEventHelper.createJsonOutputEvent(new ErrorResponse("Query is not enabled.", 503));
        }
        logger.fine("Query: " + request.statement);
        DatabaseUtil dbUtil = Fn.getFnBean(DatabaseUtil.class);
        try(Connection conn = dbUtil.getConnection()){
            try(Statement statement = conn.createStatement()){
                ResultSet resultSet = statement.executeQuery(request.getStatement());
                List<HashMap<String, Object>> recordList = convertResultSetToList(resultSet);
                return HttpEventHelper.createJsonOutputEvent(recordList);
            }
        }catch(Exception e){
            logger.info("Query error: " + e.getMessage());
            hctx.setStatusCode(500);
            return HttpEventHelper.createJsonOutputEvent(new ErrorResponse(e.getMessage(), 500));
        }
    }

    private List<HashMap<String,Object>> convertResultSetToList(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<HashMap<String,Object>> list = new ArrayList<HashMap<String,Object>>();
        while (rs.next()) {
            HashMap<String,Object> row = new HashMap<String, Object>(columns);
            for(int i=1; i<=columns; ++i) {
                row.put(md.getColumnName(i),rs.getObject(i));
            }
            list.add(row);
        }
        return list;
    }

    public static class QueryRequest{
        private String statement;

        public QueryRequest(){}

        public QueryRequest(String statement){
            this.statement = statement;
        }

        public String getStatement() {
            return statement;
        }

        public void setStatement(String statement) {
            this.statement = statement;
        };
    }

    public static class ErrorResponse{
        private String message;
        private int code;

        public ErrorResponse(){}

        public ErrorResponse(String message, int code){
            this.message = message;
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        };
    }


}