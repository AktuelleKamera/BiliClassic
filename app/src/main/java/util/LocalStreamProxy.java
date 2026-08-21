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

/**
 * 本地 HTTP 流代理（带防盗链请求头转发）。
 *
 * 系统 MediaPlayer 在线播放无法自定义请求头（B 站 CDN 防盗链需要 Referer/Cookie），
 * 所以用本地 HTTP 代理带请求头转发：MediaPlayer 连 127.0.0.1，代理转发到远端。
 * 支持 Range / 206 / Content-Range / Content-Length 原样透传。
 *
 * 注意：必须保持 HTTP/1.0 + Connection: close 的逐请求独立连接模型，
 * 系统 MediaPlayer/Stagefright 依赖该行为，改用 HTTP/1.1 keep-alive 会导致
 * OMX 解码器读到不连续数据而崩溃（mediaserver died）。
 */
public class LocalStreamProxy {
    private static final String TAG = "LocalStreamProxy";
    private static final int BUFFER_SIZE = 8192;

    private final String remoteUrl;
    private final String audioUrl;
    private final long durationMs;
    private final Map<String, String> requestHeaders;
    private ServerSocket server;
    private String localUrl;
    private volatile boolean running;
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
        this.durationMs = 0;
        this.requestHeaders = headers;
    }

    /**
     * DASH 模式构造器：videoUrl/audioUrl 分别为音视频分离流的 m4s 直链。
     * 代理会在 /manifest.mpd 生成聚合两者的 MPD，在 /video、/audio 转发对应流。
     */
    public LocalStreamProxy(String videoUrl, String audioUrl, Map<String, String> headers) {
        this(videoUrl, audioUrl, 0, headers);
    }

    /**
     * 同上，并携带视频总时长（毫秒）：极简 MPD 自身不含时长信息，
     * 缺少 mediaPresentationDuration 时 ijkplayer getDuration() 返回 0，
     * 进度条与手势 seek 都会异常。
     */
    public LocalStreamProxy(String videoUrl, String audioUrl, long durationMs, Map<String, String> headers) {
        this.remoteUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.durationMs = durationMs;
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

        Log.d(TAG, "Proxy started: " + localUrl + " -> " + remoteUrl);
        return localUrl;
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

    private void handleRequest(Socket client) {
        try {
            client.setSoTimeout(60000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }

            String rangeHeader = null;
            String line;
            while ((line = reader.readLine()) != null && line.length() > 0) {
                if (line.toLowerCase().startsWith("range:")) {
                    rangeHeader = line.substring(6).trim();
                }
            }

            if (audioUrl == null) {
                serveTo(client, remoteUrl, requestLine, rangeHeader);
                return;
            }

            // DASH 模式：按路径分发 manifest / audio / video
            String path = "/";
            String[] parts = requestLine.split(" ");
            if (parts.length > 1) path = parts[1];
            Log.e(TAG, "request path=" + path);
            if (path.startsWith("/manifest.mpd")) {
                handleManifest(client);
            } else if (path.startsWith("/audio")) {
                serveTo(client, audioUrl, requestLine, rangeHeader);
            } else {
                serveTo(client, remoteUrl, requestLine, rangeHeader);
            }
        } catch (Exception e) {
            if (!(e instanceof java.net.SocketException)) {
                Log.e(TAG, "handleRequest error", e);
            }
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private void serveTo(Socket client, String targetUrl, String requestLine, String rangeHeader) throws IOException {
        OutputStream out = client.getOutputStream();
        serveRaw(out, targetUrl, requestLine, rangeHeader);
        out.flush();
        out.close();
    }

    /**
     * 生成聚合音视频两路流的极简静态 MPD。
     * ffmpeg 的 dash demuxer 会按 BaseURL 把 /video、/audio 当作单 segment 拉取，
     * Range 转发由 serveRaw 透传完成。
     */
    private void handleManifest(Socket client) {
        try {
            StringBuilder manifest = new StringBuilder();
            manifest.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            manifest.append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\"");
            if (durationMs > 0) {
                long whole = durationMs / 1000;
                long frac = durationMs % 1000;
                manifest.append(" mediaPresentationDuration=\"PT").append(whole);
                if (frac > 0) {
                    String fracStr = "00" + frac;
                    manifest.append(".").append(fracStr.substring(fracStr.length() - 3));
                }
                manifest.append("S\"");
            }
            manifest.append(" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\"")
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

    // ===== 原始直通 =====
    private void serveRaw(OutputStream out, String targetUrl, String requestLine, String rangeHeader) throws IOException {
        // 始终带 Range 请求远端：客户端没带时就请求 bytes=0-，
        // 这样远端必然返回 Content-Range（含文件总长度）。
        String remoteRange = (rangeHeader != null) ? rangeHeader : "bytes=0-";

        HttpURLConnection conn = openRemoteConnection(targetUrl, remoteRange);

        int respCode = conn.getResponseCode();
        Log.d(TAG, "remote response=" + respCode + " target="
                + (targetUrl == audioUrl ? "audio" : "video"));
        String contentType = conn.getContentType();
        long contentLength = getContentLength(conn);
        String contentRange = conn.getHeaderField("Content-Range");

        Log.d(TAG, "req line=" + requestLine + " clientRange=" + rangeHeader
                + " -> remoteRange=" + remoteRange + " remoteCode=" + respCode
                + " remoteLen=" + contentLength + " remoteRangeHeader=" + contentRange);

        StringBuilder resp = new StringBuilder();

        if (respCode == 206) {
            resp.append("HTTP/1.0 206 Partial Content\r\n");
            resp.append("Accept-Ranges: bytes\r\n");
            if (contentRange != null) {
                resp.append("Content-Range: ").append(contentRange).append("\r\n");
            }
        } else {
            resp.append("HTTP/1.0 200 OK\r\n");
            resp.append("Accept-Ranges: bytes\r\n");
        }
        resp.append("Content-Type: ").append(contentType != null ? contentType : "video/mp4").append("\r\n");
        if (contentLength >= 0) {
            resp.append("Content-Length: ").append(contentLength).append("\r\n");
        }
        resp.append("Connection: close\r\n");
        resp.append("\r\n");
        Log.d(TAG, "resp -> " + resp.toString().replace("\r\n", " "));
        out.write(resp.toString().getBytes());
        out.flush();

        InputStream remoteIn = (respCode >= 200 && respCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        if (remoteIn != null) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            try {
                while (running && (n = remoteIn.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted()) break;
                    try {
                        out.write(buf, 0, n);
                    } catch (IOException e) {
                        break;
                    }
                }
            } catch (Throwable t) {
                // Android 2.2 HttpURLConnection LimitedInputStream 在连接被
                // 中断时 read() 可能抛 NPE；此处吞掉，让连接自然收尾，
                // 避免 handleClient 崩掉导致 ffmpeg av_read_frame 拿不到数据。
                Log.d(TAG, "read loop ended: " + t.getClass().getSimpleName());
            } finally {
                try { remoteIn.close(); } catch (Throwable ignored) {}
                try { conn.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    private HttpURLConnection openRemoteConnection(String targetUrl, String rangeHeader) throws IOException {
        // 优先明文 HTTP：本 app 目标设备（Android 2.x~4.x）的 TLS 栈最高只支持
        // TLS 1.0，B 站 CDN 要求 TLS 1.2+ 会直接拒绝握手；upos CDN 对明文 HTTP
        // 完全可用（流地址为带签名的临时链接）。HTTP 失败时再回退 HTTPS。
        String primary = targetUrl;
        if (primary != null && primary.startsWith("https://")) {
            primary = "http://" + primary.substring("https://".length());
        }
        try {
            return openRemoteConnectionImpl(primary, rangeHeader);
        } catch (IOException e) {
            if (primary != null && !primary.equals(targetUrl)) {
                Log.w(TAG, "HTTP fetch failed (" + e.getMessage() + "), retry over HTTPS");
                return openRemoteConnectionImpl(targetUrl, rangeHeader);
            }
            throw e;
        }
    }

    private HttpURLConnection openRemoteConnectionImpl(String targetUrl, String rangeHeader) throws IOException {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        if (conn instanceof HttpsURLConnection && trustAllFactory != null) {
            HttpsURLConnection sslConn = (HttpsURLConnection) conn;
            sslConn.setSSLSocketFactory(trustAllFactory);
            sslConn.setHostnameVerifier(TRUST_ALL_HOSTS);
        }

        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        // 先应用防盗链请求头（Referer/Cookie/User-Agent 等）
        if (requestHeaders != null) {
            for (Map.Entry<String, String> e : requestHeaders.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        // 覆盖 UA 为现代浏览器 UA（B 站 CDN 对老 Android UA 返回 403）
        conn.setRequestProperty("User-Agent", tv.biliclassic.util.NetWorkUtil.USER_AGENT_WEB);
        conn.setRequestProperty("Accept", "*/*");

        if (rangeHeader != null) {
            conn.setRequestProperty("Range", rangeHeader);
        }

        conn.setInstanceFollowRedirects(true);
        conn.connect();
        return conn;
    }

    /**
     * 读取 Content-Length（长整型，避免 int 溢出；chunked 时返回 -1）。
     */
    private static long getContentLength(HttpURLConnection conn) {
        try {
            String v = conn.getHeaderField("Content-Length");
            if (v != null && v.length() > 0) {
                return Long.parseLong(v.trim());
            }
        } catch (Exception ignored) {}
        return conn.getContentLength();
    }

    public void stop() {
        running = false;
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
        Log.d(TAG, "Proxy stopped");
    }

    public String getLocalUrl() {
        return localUrl;
    }
}
