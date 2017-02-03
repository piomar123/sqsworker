package piomar123.psoir.sqsworker;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import marvin.image.MarvinImage;
import marvin.plugin.MarvinImagePlugin;
import marvin.util.MarvinPluginLoader;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import piomar123.psoir.sqsworker.SimpleLogger.LogLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Main SQS image processing worker.
 * Created by Piomar on 2017-01-25.
 */
public class SQSWorker {
    private final Config config;
    private final AmazonEC2 ec2;
    private final AmazonS3 s3;
    private final AmazonSQS sqs;
    private final SimpleLogger simpleLog;
    private final Logger log = Logger.getLogger(SQSWorker.class.getCanonicalName());

    public SQSWorker(Config config) {
        this.config = config;
        ec2 = config.ec2();
        s3 = config.s3();
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

        while (true) {
            try {
                receiveMessage();
            } catch (Throwable t) {
                Map<String, String> details = Collections.singletonMap("message", t.toString());
                simpleLog.error("Error in main loop. Restarting in a few seconds", details);
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
        if (messages.isEmpty()) {
            log.info("No messages for you, Commander.");
            return;
        }
        log.info("Received message.");
        Message msg = messages.get(0);
        sqs.deleteMessage(config.SQS_URL, msg.getReceiptHandle());
        Map<String, MessageAttributeValue> attrs = msg.getMessageAttributes();
        if (!attrs.containsKey("id")) {
            log.warning("Message without id. Ignoring.");
            return;
        }
        String id = attrs.get("id").getStringValue();
        if (!id.equals(config.SQS_MSG_ID)) {
            simpleLog.warn(String.format("Unknown SQS id: %s", id));
            return;
        }

        String s3bucket = attrs.get("s3bucket").getStringValue();
        String s3key = attrs.get("s3key").getStringValue();
        String action = attrs.get("action").getStringValue();
        log.info("Fetching S3 object info..");
        S3Object file = s3.getObject(s3bucket, s3key);
        try {
            executeAction(file, action, attrs);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void executeAction(S3Object source, String action, Map<String, MessageAttributeValue> attrs) throws IOException {
        log.info(String.format("S3 object downloading [%dkB]: %s..",
                source.getObjectMetadata().getContentLength() / 1024,
                source.getKey()));
        CopyStream cs = new CopyStream(source.getObjectContent());
        ByteArrayInputStream bais = cs.toInputStream();

        if (action.equals(Actions.Thumbnail)) {
            String mime = setCorrectMimeType(source, bais);
            createThumbnail(source.getBucketName(), source.getKey(), bais, mime);
            return;
        }

        try {
            processImage(source, bais, action, attrs);
        } catch (UnsupportedOperationException uoe) {
            simpleLog.warn(uoe.toString());
        }
    }

    private void createThumbnail(String s3bucket, String s3key, InputStream inputStream, String mime) throws IOException {
        log.info(String.format("Creating thumbnail for %s..", s3key));
        CopyStream outputStream = new CopyStream();
        Thumbnails
                .of(inputStream)
                .crop(Positions.CENTER)
                .size(config.THUMBS_SIZE.width, config.THUMBS_SIZE.height)
                .useOriginalFormat()
                .outputQuality(0.9)
                .toOutputStream(outputStream);
        ByteArrayInputStream inputOutputStream = outputStream.toInputStream();
        String s3destKey = config.S3_KEY_PREFIX_THUMBS + filenameFromS3Key(s3key);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(inputOutputStream.available());
        if (mime != null) metadata.setContentType(mime);

        Map<String, String> thumbMetadata = new HashMap<>();
        thumbMetadata.put("source-key", s3key);
        metadata.setUserMetadata(thumbMetadata);

        PutObjectRequest request = new PutObjectRequest(s3bucket, s3destKey, inputOutputStream, metadata)
                .withCannedAcl(CannedAccessControlList.Private);
        s3.putObject(request);

        HashMap<String, String> details = new HashMap<>();
        details.put("s3bucket", s3bucket);
        details.put("s3srcKey", s3key);
        details.put("s3destKey", s3destKey);
        details.put("MIME", (mime != null) ? mime : "?");
        simpleLog.log(LogLevel.info, "Created thumbnail", details);
    }

    private String filenameFromS3Key(String key) {
        String[] keyParts = key.split("/");
        return keyParts[keyParts.length - 1];
    }

    private void processImage(S3Object source, ByteArrayInputStream bais, String action, Map<String, MessageAttributeValue> attrs) throws IOException {
        log.info(String.format("Processing image: %s..", action));
        BufferedImage bufferedImage = ImageIO.read(bais);
        String mime = source.getObjectMetadata().getContentType();
        String format = extensionFromMIME(mime);
        MarvinImage img = new MarvinImage(bufferedImage, format);
        MarvinImage imgOut = img.clone();
        switch (action) {
            case Actions.Blur:
                MarvinImagePlugin blur = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.blur.gaussianBlur");
                blur.setAttribute("radius", 16);
                blur.process(img, imgOut);
                break;
            case Actions.Edge:
                MarvinImagePlugin edge = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.edge.prewitt");
                edge.process(img, imgOut);
                break;
            case Actions.Levels:
                MarvinImagePlugin levels = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.equalization.histogramEqualization");
                levels.process(img, imgOut);
                break;
            case Actions.Noise:
                MarvinImagePlugin noise = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.restoration.noiseReduction");
                noise.process(img, imgOut);
                break;
            case Actions.Emboss:
                MarvinImagePlugin emboss = MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.color.emboss");
                emboss.process(img, imgOut);
                break;
            default:
                throw new UnsupportedOperationException(action);
        }
        CopyStream outputStream = new CopyStream();
        ImageUtils.writeToStream(imgOut, format, outputStream);
        ByteArrayInputStream inputOutputStream = outputStream.toInputStream();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(inputOutputStream.available());
        metadata.setContentType(mime);
        String s3destKey = String.format("%s%s-%s", config.S3_KEY_PREFIX_UPLOAD, action, filenameFromS3Key(source.getKey()));
        PutObjectRequest request = new PutObjectRequest(source.getBucketName(), s3destKey, inputOutputStream, metadata)
                .withCannedAcl(CannedAccessControlList.Private);

        log.info(String.format("Uploading [%dkB] to S3: %s", metadata.getContentLength() / 1024, s3destKey));
        s3.putObject(request);

        HashMap<String, String> details = new HashMap<>();
        details.put("s3bucket", source.getBucketName());
        details.put("s3srcKey", source.getKey());
        details.put("s3destKey", s3destKey);
        details.put("action", action);
        details.put("MIME", (mime != null) ? mime : "?");
        simpleLog.log(LogLevel.info, "Processed image", details);

        log.info("Generating thumbnail..");
        inputOutputStream.reset();
        createThumbnail(source.getBucketName(), s3destKey, inputOutputStream, mime);
    }

    /**
     * Strips "image/" prefix from MIME which gives file extension.
     *
     * @param mime input MIME type
     * @return file extension
     */
    private String extensionFromMIME(String mime) {
        final String PREFIX = "image/";
        if (!mime.startsWith(PREFIX)) {
            return "jpg";
        }
        String ext = mime.substring(PREFIX.length());
        if (ext.equals("jpeg")) ext = "jpg";
        return ext;
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
        public final static String Thumbnail = "thumbnail";
        public final static String Levels = "levels";
        public final static String Edge = "edge";
        public final static String Blur = "blur";
        public final static String Noise = "noise";
        public final static String Emboss = "emboss";
    }
}
