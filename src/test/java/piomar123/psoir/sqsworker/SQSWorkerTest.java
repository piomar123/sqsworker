package piomar123.psoir.sqsworker;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * Test for SQS Worker.
 * Created by Piomar on 2017-01-31.
 */
public class SQSWorkerTest {
    private SQSWorker sut;
    private AmazonS3Client s3;
    private AmazonSQSClient sqs;
    private AmazonEC2Client ec2;
    private AmazonSimpleDBClient simpleDB;
    private Config config;

    @Before
    public void setUp() throws Exception {
        s3 = mock(AmazonS3Client.class);
        sqs = mock(AmazonSQSClient.class);
        ec2 = mock(AmazonEC2Client.class);
        simpleDB = mock(AmazonSimpleDBClient.class);
        config = new Config(null, "tester", ec2, s3, sqs, simpleDB);
        SimpleLogger.config(config);
        System.out.println(config.THUMBS_SIZE);
        sut = new SQSWorker(config);
    }

    @Test
    public void shouldReceiveAndDeleteMessage() throws Exception {
        ReceiveMessageResult result = new ReceiveMessageResult();
        String messageHandle = "Mocked_Receipt_Handle";
        result.setMessages(Collections.singletonList(new Message().withReceiptHandle(messageHandle)));

        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).then(invocationOnMock -> {
            ReceiveMessageRequest request = invocationOnMock.getArgument(0);
            assertEquals("SQS url", config.SQS_URL, request.getQueueUrl());
            assertTrue("Long polling >= 20 sec", request.getWaitTimeSeconds() >= 20);
            return result;
        });
        when(sqs.deleteMessage(anyString(), anyString())).then(invocationOnMock -> {
            String url = invocationOnMock.getArgument(0);
            String actualHandle = invocationOnMock.getArgument(1);
            assertEquals("SQS url", config.SQS_URL, url);
            assertEquals("Use handle of mock message", actualHandle, messageHandle);
            return null;
        });
        sut.receiveMessage();
    }

}