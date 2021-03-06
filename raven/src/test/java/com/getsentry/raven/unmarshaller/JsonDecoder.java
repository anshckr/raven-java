package com.getsentry.raven.unmarshaller;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.InflaterInputStream;

/**
 * Decodes a Stream as a JSON stream.
 * <p>
 * The supported stream formats are:
 * <ul>
 * <li>JSON Stream (nothing to do)
 * <li>Base 64'd JSON streams (base64 decoded)
 * <li>Base 64'd and deflated JSON streams (base64 decoded and inflated)
 * </ul>
 */
public class JsonDecoder {
    private static Logger logger = Logger.getLogger(JsonDecoder.class.getCanonicalName());

    /**
     * Attempts to read the content of the stream and determine if it's compressed, encoded or simple JSON.
     * <p>
     * This method isn't efficient but it isn't really a problem as this part of the project is not about performances.
     *
     * @param originalStream origin stream of information that can be compressed or encoded in base64.
     * @return a Stream containing pure JSON.
     * @throws IOException if it's impossible to read the content of the Stream.
     */
    public InputStream decapsulateContent(InputStream originalStream) throws IOException {
        //Hopefully the sent content isn't bigger than 1MB...
        final int messageSize = 1048576;
        //Make it uncloseable to avoid issues with the InflaterInputStream.
        originalStream = new Uncloseable(new BufferedInputStream(originalStream));
        originalStream.mark(messageSize);
        InputStream inputStream = originalStream;
        if (!isJson(originalStream)) {
            inputStream = new Base64InputStream(inputStream, Base64.NO_WRAP);
            originalStream.reset();
            if (!isJson(new Base64InputStream(originalStream, Base64.NO_WRAP))) {
                inputStream = new InflaterInputStream(inputStream);
                originalStream.reset();
                if (!isJson(new InflaterInputStream(new Base64InputStream(originalStream, Base64.NO_WRAP)))) {
                    throw new IllegalArgumentException("The given Stream is neither JSON, Base64'd JSON "
                        + "nor Base64'd deflated JSON.");
                }
            }
        }

        originalStream.reset();
        return inputStream;
    }

    /**
     * Checks that the parsed content is JSON content.
     *
     * @param inputStream data source.
     * @return true if the content is in JSON, false otherwise.
     */
    private boolean isJson(InputStream inputStream) {
        boolean valid = false;
        try {
            final JsonParser parser = new JsonFactory().createParser(inputStream);
            do {
                parser.nextToken();
            } while (parser.hasCurrentToken());
            valid = true;
        } catch (Exception e) {
            logger.log(Level.FINE, "An exception occurred while trying to parse an allegedly JSON document", e);
        }

        return valid;
    }

    /**
     * InputStream that delegates everything but the {@link #close()} method.
     */
    private static final class Uncloseable extends InputStream {
        private final InputStream original;

        private Uncloseable(InputStream original) {
            this.original = original;
        }

        @Override
        public int read() throws IOException {
            return original.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return original.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return original.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return original.skip(n);
        }

        @Override
        public int available() throws IOException {
            return original.available();
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void mark(int readlimit) {
            original.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            original.reset();
        }

        @Override
        public boolean markSupported() {
            return original.markSupported();
        }
    }
}