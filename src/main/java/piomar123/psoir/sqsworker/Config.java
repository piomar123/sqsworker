package piomar123.psoir.sqsworker;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;

import java.awt.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Server configuration.
 * Created by Piomar on 2017-01-25.
 */
public class Config {
    public final Regions REGION = Regions.US_WEST_2;
    public final String SQS_MSG_ID = "S3projSQS";
    public final String SQS_URL = "https://sqs.us-west-2.amazonaws.com/983680736795/MarcinczykSQS";
    public final String DB_DOMAIN = "piotr.marcinczyk.logs"; //"piotr.marcinczyk.project.logs";
    public final Dimension THUMBS_SIZE = new Dimension(300, 300);
    public final String S3_KEY_PREFIX_UPLOAD = "piotr.marcinczyk/project/upload/";
    public final String S3_KEY_PREFIX_THUMBS = "piotr.marcinczyk/project/thumbs/";

    private final AWSCredentialsProvider credentialsProvider;
    private final String hostname;

    private final AmazonEC2 ec2;
    private final AmazonS3 s3;
    private final AmazonSQS sqs;
    private final AmazonSimpleDB simpleDB;

    public Config() throws UnknownHostException {
        credentialsProvider = new DefaultAWSCredentialsProviderChain();

        if (credentialsProvider.getCredentials() == null) {
            throw new RuntimeException("No AWS security credentials found by DefaultAWSCredentialsProviderChain");
        }
        hostname = InetAddress.getLocalHost().getHostName();
        ec2 = new AmazonEC2Client(credentialsProvider).withRegion(REGION);
        s3 =  new AmazonS3Client(credentialsProvider).withRegion(REGION);
        sqs = new AmazonSQSClient(credentialsProvider).withRegion(REGION);
        simpleDB = new AmazonSimpleDBClient(credentialsProvider).withRegion(REGION);
    }

    public Config(AWSCredentialsProvider credentialsProvider,
                  String hostname,
                  AmazonEC2Client ec2,
                  AmazonS3Client s3,
                  AmazonSQSClient sqs, AmazonSimpleDBClient simpleDB)
    {
        this.credentialsProvider = credentialsProvider;
        this.hostname = hostname;
        this.ec2 = ec2;
        this.s3 = s3;
        this.sqs = sqs;
        this.simpleDB = simpleDB;
    }

    public AWSCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public String getHostname() {
        return hostname;
    }

    public AmazonEC2 ec2() {
        return ec2;
    }

    public AmazonS3 s3() {
        return s3;
    }

    public AmazonSQS sqs() {
        return sqs;
    }

    public AmazonSimpleDB simpleDB() {
        return simpleDB;
    }
}
