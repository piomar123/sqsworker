package piomar123.psoir.sqsworker;

import marvin.image.MarvinImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Additional utility methods for image processing.
 * Created by Piomar on 2017-01-29.
 */
public class ImageUtils {
    public static void saveImageAsJPEG(BufferedImage image, int qualityPercent, OutputStream stream) throws IOException {
        if ((qualityPercent < 0) || (qualityPercent > 100)) {
            throw new IllegalArgumentException("Quality out of bounds!");
        }
        float quality = qualityPercent / 100f;
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageOutputStream ios = ImageIO.createImageOutputStream(stream);
        writer.setOutput(ios);
        ImageWriteParam param = new JPEGImageWriteParam(Locale.getDefault());
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        writer.write(null, new IIOImage(image, null, null), param);
        ios.flush();
        writer.dispose();
        ios.close();
    }

    public static void writeToStream(MarvinImage marvinImage, String format, OutputStream stream) {
        marvinImage.update();
        format = format.toUpperCase();
        try {
            BufferedImage bufferedImage;
            if (format.equals("JPEG") || format.equals("JPG")) {
                bufferedImage = marvinImage.getBufferedImageNoAlpha();
                saveImageAsJPEG(bufferedImage, 90, stream);
            } else {
                bufferedImage = marvinImage.getBufferedImage();
                ImageIO.write(bufferedImage, format, stream);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
    }
}
