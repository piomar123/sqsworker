package piomar123.psoir.sqsworker;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import piomar123.psoir.sqsworker.SimpleLogger.LogLevel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
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
        ec2 = config.ec2();
        s3  = config.s3();
        sqs = config.sqs();
        simpleLog = SimpleLogger.getFor("worker");
    }

    /**
     * Do your job in loop and never end.
     */
    public void loop() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Stopping server. Bye bye!")));
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

    void receiveMessage() {
        log.info("Checking queue..");
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
        log.info("Received message.");
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
        String action = attrs.get("action").getStringValue();
        log.info("Fetching S3 object info..");
        S3Object file = s3.getObject(s3bucket, s3key);
        try {
            executeAction(file, action, attrs);
        } catch (IOException e){
            throw new UncheckedIOException(e);
        }
    }

    private void executeAction(S3Object source, String action, Map<String, MessageAttributeValue> attrs) throws IOException {
        log.info("S3 object downloading..");
        CopyStream cs = new CopyStream(source.getObjectContent());
        ByteArrayInputStream bais = cs.toInputStream();
        switch(action){
            case Actions.Thumbnail:
                String mime = setCorrectMimeType(source, bais);

                log.info(String.format("Creating thumbnail for %s..", source.getKey()));
                CopyStream outputStream = new CopyStream();
                Thumbnails
                        .of(bais)
                        .crop(Positions.CENTER)
                        .size(config.THUMBS_SIZE.width, config.THUMBS_SIZE.height)
                        .useOriginalFormat()
                        .outputQuality(0.9)
                        .toOutputStream(outputStream);
                ByteArrayInputStream inputStream = outputStream.toInputStream();
                String[] keyParts = source.getKey().split("/");
                String filename = keyParts[keyParts.length-1];
                String s3destKey = config.S3_KEY_PREFIX_THUMBS + filename;

                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(inputStream.available());
                if(mime != null) metadata.setContentType(mime);

                Map<String, String> thumbMetadata = new HashMap<>();
                thumbMetadata.put("source-key", source.getKey());
                metadata.setUserMetadata(thumbMetadata);

                PutObjectRequest request = new PutObjectRequest(source.getBucketName(), s3destKey, inputStream, metadata)
                        .withCannedAcl(CannedAccessControlList.Private);
                s3.putObject(request);

                HashMap<String, String> details = new HashMap<>();
                details.put("s3bucket", source.getBucketName());
                details.put("s3srcKey", source.getKey());
                details.put("s3destKey", s3destKey);
                details.put("MIME", (mime != null) ? mime : "?");
                simpleLog.log(LogLevel.info, "Created thumbnail", details);

                break;
            default:
                log.warning("Unsupported action: " + action);
                break;
        }
    }

    private String setCorrectMimeType(S3Object object, ByteArrayInputStream bais) throws IOException {
        String mime = URLConnection.guessContentTypeFromStream(bais);
        bais.reset();
        if (mime == null) {
            log.info("Unknown MIME type");
            return null;
        }
        log.info(String.format("Determined MIME: %s", mime));
        ObjectMetadata metadata = object.getObjectMetadata();
        metadata.setContentType(mime);
        CopyObjectRequest copyRequest = new CopyObjectRequest(
                object.getBucketName(),
                object.getKey(),
                object.getBucketName(),
                object.getKey())
            .withCannedAccessControlList(CannedAccessControlList.Private)
            .withNewObjectMetadata(metadata);
        s3.copyObject(copyRequest);
        return mime;
    }

    public static final class Actions {
        final static String Thumbnail = "thumbnail";
    }
}
