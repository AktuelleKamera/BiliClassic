package util;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class LocalStreamProxy {
    private static final String TAG = "LocalStreamProxy";
    private static final int BUFFER_SIZE = 8192;

    private final String remoteUrl;
    private final String audioUrl;
    private final Map<String, String> requestHeaders;
    private ServerSocket server;
    private String localUrl;
    private volatile boolean running;
    private volatile HttpURLConnection activeRemote;
    private volatile Socket activeClient;
    private Thread serverThread;

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] c, String a) {}
        public void checkServerTrusted(X509Certificate[] c, String a) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    private static final HostnameVerifier TRUST_ALL_HOSTS = new HostnameVerifier() {
        public boolean verify(String h, SSLSession s) { return true; }
    };

    private static SSLSocketFactory trustAllFactory;

    static {
        try {
            // 复用 NetWorkUtil 的兼容 SSL 工厂（显式启用现代 TLS 协议与套件，
            // 否则 Android 2.x-4.x 上视频流握手慢/失败）
            trustAllFactory = tv.biliclassic.util.NetWorkUtil.getTrustAllSSLSocketFactory();
            if (trustAllFactory == null) {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, new X509TrustManager[]{TRUST_ALL}, new java.security.SecureRandom());
                trustAllFactory = sc.getSocketFactory();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create SSL factory", e);
        }
    }

    public LocalStreamProxy(String remoteUrl, Map<String, String> headers) {
        this.remoteUrl = remoteUrl;
        this.audioUrl = null;
        this.requestHeaders = headers;
    }

    public LocalStreamProxy(String videoUrl, String audioUrl, Map<String, String> headers) {
        this.remoteUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.requestHeaders = headers;
    }

    public String start() throws IOException {
        // 监听所有接口（0.0.0.0），URL 用本机局域网 IP 而非 127.0.0.1：
        // Android 4.x 上系统 MediaPlayer 的 native 层（mediaserver 进程）连 loopback
        // 的 http://127.0.0.1 会被某些 ROM 拦截/拒绝（MediaHTTPConnection 兼容问题），
        // 改用本机非 loopback IP 可绕开；拿不到 IP 时回退 127.0.0.1。
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(0));
        int port = server.getLocalPort();
        // Use the device's reachable IPv4 address. Some Android 2.x builds
        // bind ServerSocket's wildcard endpoint to IPv6 only, so 127.0.0.1
        // cannot reach the server even though the socket was created.
        String host = getLocalIpAddress();
        localUrl = "http://" + host + ":" + port + (audioUrl != null ? "/manifest.mpd" : "/video");
        running = true;

        serverThread = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try {
                        final Socket client = server.accept();
                        activeClient = client;
                        new Thread(new Runnable() {
                            public void run() {
                                handleRequest(client);
                            }
                        }, "ProxyClient").start();
                    } catch (IOException e) {
                        if (running) Log.e(TAG, "accept error", e);
                    }
                }
            }
        }, "LocalStreamProxy");
        serverThread.start();

        Log.e(TAG, "Proxy started: " + localUrl + " -> " + remoteUrl);
        return localUrl;
    }

    private void handleRequest(Socket client) {
        if (audioUrl == null) {
            handleClient(client, remoteUrl);
            return;
        }
        try {
            client.setSoTimeout(30000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String requestLine = reader.readLine();
            if (requestLine == null) return;
            String path = "/";
            String[] parts = requestLine.split(" ");
            if (parts.length > 1) path = parts[1];
            Log.e(TAG, "request path=" + path);
            if (path.startsWith("/manifest.mpd")) {
                handleManifest(client, reader);
            } else if (path.startsWith("/audio")) {
                handleClient(client, audioUrl, reader);
            } else {
                handleClient(client, remoteUrl, reader);
            }
        } catch (Exception e) {
            if (!(e instanceof java.net.SocketException)) Log.e(TAG, "request error", e);
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private void handleManifest(Socket client, BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null && line.length() > 0) {
                // Consume request headers before writing the MPD.
            }
            StringBuilder manifest = new StringBuilder();
            manifest.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            manifest.append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\"")
                    .append(" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\"")
                    .append(" minBufferTime=\"PT1S\">");
            manifest.append("<Period id=\"0\">");
            String base = localUrl.substring(0, localUrl.lastIndexOf('/'));
            appendRepresentation(manifest, "video", "video", base + "/video");
            appendRepresentation(manifest, "audio", "audio", base + "/audio");
            manifest.append("</Period></MPD>");
            byte[] body = manifest.toString().getBytes("UTF-8");
            Log.e(TAG, "manifest response bytes=" + body.length);
            OutputStream out = client.getOutputStream();
            String headers = "HTTP/1.0 200 OK\r\nContent-Type: application/dash+xml\r\nContent-Length: "
                    + body.length + "\r\nConnection: close\r\n\r\n";
            out.write(headers.getBytes("US-ASCII"));
            out.write(body);
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "manifest error", e);
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private static void appendRepresentation(StringBuilder manifest, String id, String type, String url) {
        manifest.append("<AdaptationSet contentType=\"").append(type).append("\" mimeType=\"")
                .append(type).append("/mp4\">");
        manifest.append("<Representation id=\"").append(id).append("\" bandwidth=\"1000000\">");
        manifest.append("<BaseURL>").append(escapeXml(url)).append("</BaseURL>");
        manifest.append("</Representation></AdaptationSet>");
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /**
     * 获取本机非 loopback 的 IPv4 地址（优先可用接口）。
     * Android 4.x MediaPlayer native 层连 127.0.0.1 可能被拦截，用真实 IP 更稳。
     * 拿不到（异常/无网络）时回退 127.0.0.1。
     *
     * 注意：NetworkInterface.isUp()/isLoopback() 是 API 9+，直接引用会在 API<9 上
     * VerifyError（见 0.5.0 在 Android 2.2 上的崩溃），这里用反射调用。
     */
    private static String getLocalIpAddress() {
        try {
            java.lang.reflect.Method isUp = null;
            java.lang.reflect.Method isLoopback = null;
            try {
                isUp = java.net.NetworkInterface.class.getMethod("isUp");
            } catch (Throwable t) {
            }
            try {
                isLoopback = java.net.NetworkInterface.class.getMethod("isLoopback");
            } catch (Throwable t) {
            }
            java.util.Enumeration<java.net.NetworkInterface> nifs =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (nifs != null && nifs.hasMoreElements()) {
                java.net.NetworkInterface nif = nifs.nextElement();
                try {
                    if (isUp != null && !((Boolean) isUp.invoke(nif)).booleanValue()) continue;
                    if (isLoopback != null && ((Boolean) isLoopback.invoke(nif)).booleanValue()) continue;
                } catch (Throwable t) {
                    // 反射调用失败时跳过该接口的 isUp/isLoopback 检查
                }
                String name = nif.getName();
                if (name == null) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = nif.getInetAddresses();
                while (addrs != null && addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
                    if (addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "getLocalIpAddress error: " + t.getMessage());
        }
        return "127.0.0.1";
    }

    private void closePrevious() {
        try {
            if (activeRemote != null) {
                activeRemote.disconnect();
                activeRemote = null;
            }
        } catch (Exception ignored) {}
        try {
            if (activeClient != null) {
                activeClient.close();
                activeClient = null;
            }
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket client, String targetUrl, BufferedReader reader) {
        try {
            client.setSoTimeout(30000);
            String rangeHeader = null;
            String line;
            while ((line = reader.readLine()) != null && line.length() > 0) {
                if (line.toLowerCase().startsWith("range:")) {
                    rangeHeader = line.substring(6).trim();
                }
            }

            HttpURLConnection conn = openRemoteConnection(targetUrl, rangeHeader);
            activeRemote = conn;

            int respCode = conn.getResponseCode();
            Log.e(TAG, "remote response=" + respCode + " target="
                    + (targetUrl == audioUrl ? "audio" : "video"));
            String contentType = conn.getContentType();
            int contentLength = conn.getContentLength();
            String contentRange = conn.getHeaderField("Content-Range");
            String acceptRanges = conn.getHeaderField("Accept-Ranges");

            OutputStream out = client.getOutputStream();
            StringBuilder resp = new StringBuilder();

            if (rangeHeader != null && (respCode == 206 || respCode == 200)) {
                resp.append("HTTP/1.0 206 Partial Content\r\n");
                resp.append("Accept-Ranges: bytes\r\n");
                if (contentRange != null) {
                    resp.append("Content-Range: ").append(contentRange).append("\r\n");
                }
            } else {
                resp.append("HTTP/1.0 200 OK\r\n");
                resp.append("Accept-Ranges: ").append(acceptRanges != null ? acceptRanges : "bytes").append("\r\n");
            }
            resp.append("Content-Type: ").append(contentType != null ? contentType : "video/mp4").append("\r\n");
            if (contentLength > 0) {
                resp.append("Content-Length: ").append(contentLength).append("\r\n");
            }
            resp.append("Connection: close\r\n");
            resp.append("\r\n");
            out.write(resp.toString().getBytes());
            out.flush();

            InputStream remoteIn = (respCode >= 200 && respCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (remoteIn != null) {
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                while (running && (n = remoteIn.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted()) break;
                    try {
                        out.write(buf, 0, n);
                    } catch (IOException e) {
                        break;
                    }
                }
                out.flush();
                remoteIn.close();
            }

            out.close();
        } catch (Exception e) {
            if (!(e instanceof java.net.SocketException)) {
                Log.e(TAG, "handleClient error", e);
            }
        } finally {
            if (activeRemote != null) {
                try { activeRemote.disconnect(); } catch (Exception ignored) {}
                if (activeRemote == activeRemote) activeRemote = null;
            }
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private void handleClient(Socket client, String targetUrl) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            if (reader.readLine() == null) return;
            handleClient(client, targetUrl, reader);
        } catch (Exception e) {
            if (!(e instanceof java.net.SocketException)) Log.e(TAG, "client request error", e);
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private HttpURLConnection openRemoteConnection(String targetUrl, String rangeHeader) throws IOException {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        if (conn instanceof HttpsURLConnection && trustAllFactory != null) {
            HttpsURLConnection sslConn = (HttpsURLConnection) conn;
            sslConn.setSSLSocketFactory(trustAllFactory);
            sslConn.setHostnameVerifier(TRUST_ALL_HOSTS);
        }

        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        if (requestHeaders != null) {
            for (Map.Entry<String, String> e : requestHeaders.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "*/*");

        if (rangeHeader != null) {
            conn.setRequestProperty("Range", rangeHeader);
        }

        conn.setInstanceFollowRedirects(true);
        conn.connect();
        return conn;
    }

    public void stop() {
        running = false;
        try {
            if (activeRemote != null) {
                activeRemote.disconnect();
                activeRemote = null;
            }
        } catch (Exception ignored) {}
        try {
            if (activeClient != null) {
                activeClient.close();
                activeClient = null;
            }
        } catch (Exception ignored) {}
        try {
            if (server != null) {
                server.close();
                server = null;
            }
        } catch (Exception ignored) {}
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        Log.e(TAG, "Proxy stopped");
    }

    public String getLocalUrl() {
        return localUrl;
    }
}
