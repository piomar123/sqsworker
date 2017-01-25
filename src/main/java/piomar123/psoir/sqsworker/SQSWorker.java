package piomar123.psoir.sqsworker;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.*;

import java.util.Arrays;
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
    private final AmazonEC2Client ec2;
    private final AmazonS3Client s3;
    private final AmazonSQSClient sqs;
    private Config config;
    private final Logger log = Logger.getLogger(SQSWorker.class.getCanonicalName());

    public SQSWorker(Config config) {
        this.config = config;
        ec2 = new AmazonEC2Client(config.getCredentialsProvider());
        s3  = new AmazonS3Client(config.getCredentialsProvider());
        sqs = new AmazonSQSClient(config.getCredentialsProvider());
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
        System.out.printf("Bucket: %s, Key: %s%n",
                attrs.get("s3bucket").getStringValue(),
                attrs.get("s3key").getStringValue());
    }
}
