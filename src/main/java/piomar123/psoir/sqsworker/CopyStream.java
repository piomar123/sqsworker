package piomar123.psoir.sqsworker;

import com.amazonaws.services.s3.model.S3ObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Efficient and unsafe ByteArrayOutputStream convertible to InputStream without duplicated arrays.
 * http://stackoverflow.com/a/36961688
 * Created by Piomar on 2017-01-30.
 */
public class CopyStream extends ByteArrayOutputStream {
    public CopyStream() {
        super();
    }

    /**
     * Reads given stream using {@link #readStream(InputStream)}.
     */
    public CopyStream(InputStream stream) throws IOException {
        readStream(stream);
    }

    /**
     * Get an input stream based on the contents of this output stream.
     * Do not use the output stream after calling this method.
     * @return an {@link InputStream}
     */
    public ByteArrayInputStream toInputStream() {
        return new ByteArrayInputStream(this.buf, 0, this.count);
    }

    /**
     * Raw reference to buffer. Any changes to data will cause unexpected behaviour.
     * @return direct reference to buffer
     */
    public byte[] referenceToBuffer() {
        return this.buf;
    }

    /**
     * Reads given stream.
     * @param stream input stream
     * @throws IOException propagates {@link InputStream#read(byte[], int, int)}
     */
    public void readStream(InputStream stream) throws IOException {
        int nRead;
        byte[] buffer = new byte[65536];

        while ((nRead = stream.read(buffer, 0, buffer.length)) != -1) {
            this.write(buffer, 0, nRead);
        }
    }
}
