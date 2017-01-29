package piomar123.psoir.sqsworker;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.regions.Regions;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Server configuration.
 * Created by Piomar on 2017-01-25.
 */
public class Config {
    public final Regions REGION = Regions.US_WEST_2;
    public final String SQS_MSG_ID = "S3labSQS"; //"S3projSQS";
    public final String SQS_URL = "https://sqs.us-west-2.amazonaws.com/983680736795/MarcinczykSQS";
    public final String DB_DOMAIN = "piotr.marcinczyk.logs"; //"piotr.marcinczyk.project.logs";
    private final AWSCredentialsProvider credentialsProvider;
    private final String hostname;

    public Config() throws UnknownHostException {
        File configFile = new File(System.getProperty("user.home"), ".aws/credentials");
        credentialsProvider = new ProfileCredentialsProvider(new ProfilesConfigFile(configFile), "default");

        if (credentialsProvider.getCredentials() == null) {
            throw new RuntimeException(String.format("No AWS security credentials found in %s", configFile.getAbsolutePath()));
        }
        hostname = InetAddress.getLocalHost().getHostName();
    }

    public AWSCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public String getHostname() {
        return hostname;
    }
}
