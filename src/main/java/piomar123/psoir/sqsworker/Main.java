package piomar123.psoir.sqsworker;

import java.net.UnknownHostException;
import java.util.logging.Logger;

/**
 * Main worker class.
 * Created by Piomar on 2017-01-25.
 */
public class Main {
    private static final Logger log = Logger.getLogger(Main.class.getCanonicalName());

    public static void main(String[] args) throws UnknownHostException {
        log.info("Server initializing..");
        Config config = new Config();
        SQSWorker worker = new SQSWorker(config);

        worker.loop();

    }
}
