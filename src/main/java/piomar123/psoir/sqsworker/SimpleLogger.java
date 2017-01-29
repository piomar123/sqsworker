package piomar123.psoir.sqsworker;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Logger into AWS SimpleDB.
 * Created by Piomar on 2017-01-26.
 */
public class SimpleLogger {
    private static Config config;
    private final String module, host;
    private final static String prefix = "project-log-";

    private static AmazonSimpleDBClient simpleDB;

    public enum LogLevel {
        info("info"),
        warn("warn"),
        error("error");

        private final String str;

        LogLevel(String str) {
            this.str = str;
        }

        @Override
        public String toString() {
            return str;
        }
    }

    public static void config(Config config){
        SimpleLogger.config = config;
        simpleDB = new AmazonSimpleDBClient(config.getCredentialsProvider()).withRegion(config.REGION);
        CreateDomainRequest request = new CreateDomainRequest(config.DB_DOMAIN);
        simpleDB.createDomain(request);
    }

    private SimpleLogger(String module) {
        this.module = module;
        this.host = config.getHostname();
    }

    public static SimpleLogger getFor(String module) {
        return new SimpleLogger(module);
    }

    public void info(String message) {
        log(LogLevel.info, message);
    }

    public void warn(String message) {
        log(LogLevel.warn, message);
    }

    public void error(String message, Map<String, String> details) {
        log(LogLevel.error, message);
    }

    public void log(LogLevel level, String message) {
        log(level, message, null);
    }

    public void log(LogLevel level, String message, Map<String, String> details){
        List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>();
        attributes.add(new ReplaceableAttribute()
                .withName("timestamp")
                .withValue(new Timestamp(System.currentTimeMillis()).toString()));
        attributes.add(new ReplaceableAttribute()
                .withName("host")
                .withValue(host));
        attributes.add(new ReplaceableAttribute()
                .withName("module")
                .withValue(module));
        attributes.add(new ReplaceableAttribute()
                .withName("level")
                .withValue(level.toString()));
        attributes.add(new ReplaceableAttribute()
                .withName("message")
                .withValue(message));

        if(details != null){
            for(Map.Entry<String, String> entry: details.entrySet()){
                attributes.add(new ReplaceableAttribute()
                        .withName(entry.getKey())
                        .withName(entry.getValue()));
            }
        }

        PutAttributesRequest request = new PutAttributesRequest(
                config.DB_DOMAIN,
                prefix + UUID.randomUUID().toString(),
                attributes);
        simpleDB.putAttributes(request);
    }
}
