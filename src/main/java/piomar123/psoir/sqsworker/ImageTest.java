package piomar123.psoir.sqsworker;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Testing images libraries.
 * Created by Piomar on 2017-01-29.
 */
public class ImageTest {
    public static void main(String[] args) throws IOException {
        BufferedImage buff = ImageIO.read(new FileInputStream("spacex.jpg"));
        Thumbnails.of("barcode-red.png").scale(1).useOriginalFormat().outputQuality(0.9).toFile("barcode.png");
//        MarvinPluginCollection.gaussianBlur(img, img, 30);
//        ImageUtils.writeToStream(resized, "png", new FileOutputStream("spacex2.png"));
//        ImageUtils.saveImageAsJPEG(resized, 90, new FileOutputStream("spacex2.jpg"));
//        ImageIO.write(resized, "jpg", new File("spacex2.jpg"));
//        MarvinImageIO.saveImage(img, "spacex-marvin.png");
    }
}
