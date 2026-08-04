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
                        closePrevious();
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

        Log.e(TAG, "Proxy started: " + localUrl + " -> " + remoteUrl);
        return localUrl;
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

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(30000);

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

            HttpURLConnection conn = openRemoteConnection(rangeHeader);
            activeRemote = conn;

            int respCode = conn.getResponseCode();
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

    private HttpURLConnection openRemoteConnection(String rangeHeader) throws IOException {
        URL url = new URL(remoteUrl);
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
