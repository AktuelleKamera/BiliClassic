package tv.biliclassic.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class WolfTlsConnection {
    private final String url;
    private final ArrayList headers;
    private final Map requestHeaders = new HashMap();
    private final Map responseHeaders = new HashMap();
    private int connectTimeout = 15000;
    private int readTimeout = 15000;
    private long handle;
    private int responseCode = -1;
    private long contentLength = -1;
    private final byte[] buffer = new byte[16384];
    private int bufferPos;
    private int bufferLen;
    private boolean connected;

    private final class TlsInputStream extends InputStream {
        private long remaining;

        private TlsInputStream() {
            this.remaining = contentLength;
        }

        @Override
        public int available() {
            return bufferLen - bufferPos;
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            try {
                return read(one, 0, 1) <= 0 ? -1 : one[0] & 0xFF;
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public int read(byte[] data, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            if (bufferPos < bufferLen) {
                int count = Math.min(length, bufferLen - bufferPos);
                if (remaining > 0 && count > remaining) count = (int) remaining;
                System.arraycopy(buffer, bufferPos, data, offset, count);
                bufferPos += count;
                if (remaining > 0) remaining -= count;
                return count;
            }
            if (remaining > 0 && length > remaining) length = (int) remaining;
            int count = WolfTls.read(handle, data, offset, length);
            if (count < 0) throw new IOException("TLS read failed");
            if (count == 0) {
                if (remaining > 0) throw new IOException("TLS connection closed");
                return -1;
            }
            if (remaining > 0) remaining -= count;
            return count;
        }

        @Override
        public void close() {
            disconnect();
        }
    }

    public WolfTlsConnection(String url, ArrayList headers) {
        this.url = url;
        this.headers = headers;
    }

    public void setConnectTimeout(int timeoutMs) {
        this.connectTimeout = timeoutMs;
    }

    public void setReadTimeout(int timeoutMs) {
        this.readTimeout = timeoutMs;
    }

    public void setRequestProperty(String name, String value) {
        if (name != null) requestHeaders.put(name.toLowerCase(), value);
    }

    public void connect() throws IOException {
        if (connected) return;
        URL parsed = new URL(url);
        String host = parsed.getHost();
        int port = parsed.getPort() <= 0 ? 443 : parsed.getPort();
        String file = parsed.getFile();
        if (file == null || file.length() == 0) file = "/";
        handle = WolfTls.connect(host, port, connectTimeout);
        if (handle == 0) throw new IOException("TLS connect failed");
        if (headers != null) {
            for (int i = 0; i + 1 < headers.size(); i += 2) {
                Object key = headers.get(i);
                Object value = headers.get(i + 1);
                if (key != null && value != null) {
                    requestHeaders.put(key.toString().toLowerCase(), value.toString());
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("GET ").append(file).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(host);
        if (port != 443) sb.append(':').append(port);
        sb.append("\r\n");
        sb.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) ")
                .append("AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36\r\n");
        sb.append("Accept: */*\r\n");
        sb.append("Accept-Encoding: identity\r\n");
        sb.append("Connection: close\r\n");
        appendHeader(sb, "range");
        appendHeader(sb, "referer");
        appendHeader(sb, "cookie");
        sb.append("\r\n");
        try {
            byte[] request = sb.toString().getBytes("ISO-8859-1");
            int offset = 0;
            while (offset < request.length) {
                int count = WolfTls.write(handle, request, offset, request.length - offset);
                if (count <= 0) throw new IOException("TLS write failed");
                offset += count;
            }
            String status = readLine();
            if (status == null) throw new IOException("empty HTTP response");
            int first = status.indexOf(' ');
            if (first < 0) throw new IOException("bad HTTP status: " + status);
            int second = status.indexOf(' ', first + 1);
            try {
                responseCode = Integer.parseInt((second > 0
                        ? status.substring(first + 1, second)
                        : status.substring(first + 1)).trim());
                while (true) {
                    String line = readLine();
                    if (line == null || line.length() == 0) break;
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String key = line.substring(0, colon).trim().toLowerCase();
                        String value = line.substring(colon + 1).trim();
                        Object old = responseHeaders.get(key);
                        responseHeaders.put(key, old == null ? value : old + ", " + value);
                    }
                }
                String length = getHeader("Content-Length");
                if (length != null) {
                    try {
                        contentLength = Long.parseLong(length.trim());
                    } catch (NumberFormatException ignored) {
                        contentLength = -1;
                    }
                }
                connected = true;
            } catch (NumberFormatException e) {
                throw new IOException("bad HTTP status: " + status);
            }
        } catch (UnsupportedEncodingException e) {
            throw new IOException("encoding failed");
        }
    }

    private void appendHeader(StringBuilder sb, String name) {
        Object value = requestHeaders.get(name);
        if (value == null) return;
        sb.append(name.substring(0, 1).toUpperCase()).append(name.substring(1))
                .append(": ").append(value).append("\r\n");
    }

    private int readByte() throws IOException {
        if (bufferPos >= bufferLen) {
            if (handle == 0) return -1;
            int count = WolfTls.read(handle, buffer, 0, buffer.length);
            if (count < 0) throw new IOException("TLS read failed");
            if (count == 0) return -1;
            bufferPos = 0;
            bufferLen = count;
        }
        return buffer[bufferPos++] & 0xFF;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            int value = readByte();
            if (value == -1 || value == 10) {
                if (value == -1 && out.size() == 0) return null;
                try {
                    return new String(out.toByteArray(), "ISO-8859-1");
                } catch (UnsupportedEncodingException e) {
                    return new String(out.toByteArray());
                }
            }
            if (value != 13) out.write(value);
            if (out.size() > 8192) throw new IOException("HTTP header too long");
        }
    }

    public String getHeader(String name) {
        if (name == null) return null;
        Object value = responseHeaders.get(name.toLowerCase());
        return value == null ? null : value.toString();
    }

    public int getResponseCode() throws IOException {
        if (!connected) connect();
        return responseCode;
    }

    public long getContentLength() {
        return contentLength;
    }

    public InputStream getInputStream() throws IOException {
        if (!connected) connect();
        return new TlsInputStream();
    }

    public void disconnect() {
        connected = false;
        if (handle != 0) {
            WolfTls.close(handle);
            handle = 0;
        }
    }
}
