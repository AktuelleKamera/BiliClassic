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
        this.requestHeaders = headers;
    }

    public String start() throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        int port = server.getLocalPort();
        localUrl = "http://127.0.0.1:" + port + "/video";
        running = true;

        serverThread = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try {
                        final Socket client = server.accept();
                        activeClient = client;
                        new Thread(new Runnable() {
                            public void run() {
                                handleClient(client);
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

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(60000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                try { client.close(); } catch (Exception ignored) {}
                return;
            }

            String rangeHeader = null;
            String line;
            while ((line = reader.readLine()) != null && line.length() > 0) {
                if (line.toLowerCase().startsWith("range:")) {
                    rangeHeader = line.substring(6).trim();
                }
            }

            OutputStream out = client.getOutputStream();
            serveRaw(out, requestLine, rangeHeader);

            out.flush();
            out.close();
        } catch (Exception e) {
            if (!(e instanceof java.net.SocketException)) {
                Log.e(TAG, "handleClient error", e);
            }
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    // ===== 原始直通 =====
    private void serveRaw(OutputStream out, String requestLine, String rangeHeader) throws IOException {
        // 始终带 Range 请求远端：客户端没带时就请求 bytes=0-，
        // 这样远端必然返回 Content-Range（含文件总长度）。
        String remoteRange = (rangeHeader != null) ? rangeHeader : "bytes=0-";

        HttpURLConnection conn = openRemoteConnection(remoteRange);

        int respCode = conn.getResponseCode();
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

    private HttpURLConnection openRemoteConnection(String rangeHeader) throws IOException {
        URL url = new URL(remoteUrl);
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
