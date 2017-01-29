package piomar123.psoir.sqsworker;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import piomar123.psoir.sqsworker.SimpleLogger.LogLevel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main SQS image processing worker.
 * Created by Piomar on 2017-01-25.
 */
public class SQSWorker {
    private final Config config;
    private final AmazonEC2Client ec2;
    private final AmazonS3Client s3;
    private final AmazonSQSClient sqs;
    private final SimpleLogger simpleLog;
    private final Logger log = Logger.getLogger(SQSWorker.class.getCanonicalName());

    public SQSWorker(Config config) {
        this.config = config;
        ec2 = new AmazonEC2Client(config.getCredentialsProvider()).withRegion(config.REGION);
        s3  = new AmazonS3Client(config.getCredentialsProvider()).withRegion(config.REGION);
        sqs = new AmazonSQSClient(config.getCredentialsProvider()).withRegion(config.REGION);
        simpleLog = SimpleLogger.getFor("worker");
    }

    /**
     * Do your job in loop and never end.
     */
    public void loop() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Stopping server. Bye bye!");
            }
        });
        simpleLog.log(LogLevel.info, "SQS worker started");
        log.info(String.format("SQS worker started on %s", config.getHostname()));

        while(true){
            try {
                receiveMessage();
            } catch (Throwable t){
                log.log(Level.SEVERE, "Error in main loop. Restarting in a few seconds", t);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    log.warning(String.format("Sleep interrupted: %s", e.getMessage()));
                }
            }
        }
    }

    private void receiveMessage() {
        log.info("Waiting for orders..");
        ReceiveMessageRequest request = new ReceiveMessageRequest(config.SQS_URL);
        request.setMaxNumberOfMessages(1);
        request.setWaitTimeSeconds(20);
        request.setVisibilityTimeout(30);
        request.setMessageAttributeNames(Collections.singletonList("All"));
        ReceiveMessageResult result = sqs.receiveMessage(request);
        List<Message> messages = result.getMessages();
        if(messages.isEmpty()){
            log.info("No messages for you, Commander.");
            return;
        }
        Message msg = messages.get(0);
        sqs.deleteMessage(config.SQS_URL, msg.getReceiptHandle());
        Map<String, MessageAttributeValue> attrs = msg.getMessageAttributes();
        if(!attrs.containsKey("id")){
            log.warning("Message without id. Ignoring.");
            return;
        }
        String id = attrs.get("id").getStringValue();
        if(!id.equals(config.SQS_MSG_ID)){
            log.warning(String.format("Unknown SQS id: %s", id));
            return;
        }

        String s3bucket = attrs.get("s3bucket").getStringValue();
        String s3key = attrs.get("s3key").getStringValue();
        System.out.printf("Bucket: %s, Key: %s%n",
                s3bucket,
                s3key);
        S3Object file = s3.getObject(s3bucket, s3key);
//        file.getObjectContent().getHttpRequest()
    }
}
