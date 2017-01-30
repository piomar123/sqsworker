package piomar123.psoir.sqsworker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * ByteArrayOutputStream convertible to InputStream without duplicated arrays.
 * http://stackoverflow.com/a/36961688
 * Created by Piomar on 2017-01-30.
 */
public class CopyStream extends ByteArrayOutputStream {
    public CopyStream() {
        super();
    }

    /**
     * Get an input stream based on the contents of this output stream.
     * Do not use the output stream after calling this method.
     * @return an {@link InputStream}
     */
    public ByteArrayInputStream toInputStream() {
        return new ByteArrayInputStream(this.buf, 0, this.count);
    }
}
