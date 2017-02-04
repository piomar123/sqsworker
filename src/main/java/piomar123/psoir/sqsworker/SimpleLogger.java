package piomar123.psoir.sqsworker;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logger into AWS SimpleDB.
 * Created by Piomar on 2017-01-26.
 */
public class SimpleLogger {
    private static Config config;
    private final String module, host;
    private final static String prefix = "project-log-";
    private final Logger log = Logger.getLogger(SimpleLogger.class.getCanonicalName());

    private static AmazonSimpleDB simpleDB;
    private DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public enum LogLevel {
        debug("debug"),
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
        simpleDB = config.simpleDB();
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

    public void debug(String message) {
        log(LogLevel.debug, message, null);
    }

    public void debug(String message, Map<String, String> details) {
        log(LogLevel.debug, message, details);
    }

    public void info(String message) {
        log(LogLevel.info, message);
    }

    public void warn(String message) {
        log(LogLevel.warn, message);
    }

    public void error(String message, Map<String, String> details) {
        log(LogLevel.error, message, details);
    }

    public void error(String message, Throwable throwable) {
        Map<String, String> details = new HashMap<>();
        details.put("error", throwable.toString());
        log(LogLevel.error, message, details);
    }

    public void log(LogLevel level, String message) {
        log(level, message, null);
    }

    public void log(LogLevel level, String message, Map<String, String> details){
        List<ReplaceableAttribute> attributes = new ArrayList<ReplaceableAttribute>();
        attributes.add(new ReplaceableAttribute()
                .withName("timestamp")
                .withValue(ZonedDateTime.now(ZoneId.of("UTC")).format(dateFormat)));
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
                        .withValue(entry.getValue()));
            }
        }
        log.log(toLoggerLevel(level), message, details);

        PutAttributesRequest request = new PutAttributesRequest(
                config.DB_DOMAIN,
                prefix + UUID.randomUUID().toString(),
                attributes);
        simpleDB.putAttributes(request);
    }

    private Level toLoggerLevel(LogLevel level) {
        switch (level){
            case info: return Level.INFO;
            case warn: return Level.WARNING;
            case error: return Level.SEVERE;
            default: return Level.INFO;
        }
    }
}
