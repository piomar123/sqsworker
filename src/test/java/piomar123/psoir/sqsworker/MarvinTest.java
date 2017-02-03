package piomar123.psoir.sqsworker;

import marvin.util.MarvinPluginLoader;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for Marvin Framework configuration.
 * Created by Piomar on 2017-02-03.
 */
public class MarvinTest {
    @Test
    public void shouldLoadMarvinPlugin() throws Exception {
        System.out.println("Checking Marvin plugins..");
        final String error = "Cannot load Marvin plugin";
        Assert.assertNotNull(error, MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.blur.gaussianBlur"));
        Assert.assertNotNull(error, MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.color.emboss"));
        Assert.assertNotNull(error, MarvinPluginLoader.loadImagePlugin("org.marvinproject.image.restoration.noiseReduction"));
        System.out.println("Marvin plugins loading correctly.");
    }
}
