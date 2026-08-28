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
    // 远端实际可用的协议（null=未探测）。mCDN/PCDN 节点（如 *.edge.mountaintoys.cn:443/4483）
    // 只接受 HTTPS，明文请求返回 400 + HTML 错误页；记住结论后后续 Range 请求直连 HTTPS
    private volatile Boolean remotePreferHttps;

    // ===== 顺序预取缓冲 =====
    // OpenCore/PVPlayer 对数据供给延迟极其敏感：直通模式下每次 Range 请求都要
    // 现场连远端（可能含 TLS 握手），网络稍有抖动就缓冲下溢，报
    // "Video track fell behind" → PVMFErrResource(1,48)。预取线程持续把远端
    // 数据拉进环形缓冲，播放器消费与网络抖动解耦；seek 时按目标偏移重置。
    //
    // 【已停用】实测在 QSD8250/MSM7X30 等老设备上引发回归（此前可播的视频
    // 出现 fell behind/卡死），默认关闭以维持旧版直通行为；诊断时置 true。
    private static final boolean PF_ENABLED = false;
    private static final int PF_BUF_SIZE = tv.biliclassic.util.SdkHelper.isLowMemoryDevice()
            ? 512 * 1024 : 1024 * 1024;
    private final byte[] mPfBuf = new byte[PF_BUF_SIZE];
    private final Object mPfLock = new Object();
    private long mPfBase = 0;          // 缓冲区首字节对应的远端文件偏移
    private long mPfEnd = 0;           // 已预读到的远端偏移（exclusive）
    private long mPfServePos = 0;      // 当前服务位置（随播放器请求移动；seek 时重锚定）
    private boolean mPfEof = false;    // 远端流已读到文件尾
    private boolean mPfBroken = false; // 远端连接失效，需要从当前偏移重连
    private long mPfResetTarget = -1;  // seek：要求预读线程改从该偏移拉取
    private long mTotalLength = -1;    // 远端文件总长（自 Content-Range 解析）
    private HttpURLConnection mPfConn;
    private InputStream mPfRemote;
    private Thread mPfThread;
    private long mLastPfLogElapsed;
    private volatile boolean mPrefetchEngaged;
    private boolean mCodecInfoLogged;
    private volatile int mAvcProfileIdc = -1;
    private volatile int mAvcLevelIdc = -1;

    /** 文件头解析出的 H264 profile_idc（100=High）；未知返回 -1 */
    public int getAvcProfileIdc() {
        return mAvcProfileIdc;
    }

    /** 文件头解析出的 H264 level_idc；未知返回 -1 */
    public int getAvcLevelIdc() {
        return mAvcLevelIdc;
    }

    /**
     * 从缓冲头部分析 H264 编码档位（avcC box：profile_idc/level_idc），
     * 用于区分"个别视频解码异常"与"普遍性能不足"。仅文件起始段扫描一次。
     */
    private void logCodecInfoOnce() {
        if (mCodecInfoLogged) return;
        synchronized (mPfLock) {
            if (mPfBase != 0) {
                mCodecInfoLogged = true; // 错过文件头（seek 起播），不再尝试
                return;
            }
            long availLong = mPfEnd - mPfBase;
            if (availLong < 2048) return; // 头部还没到，等下次
            int n = (int) Math.min(availLong, 65536);
            byte[] head = new byte[n];
            for (int i = 0; i < n; i++) {
                head[i] = mPfBuf[(int) ((mPfBase + i) % PF_BUF_SIZE)];
            }
            mCodecInfoLogged = true;
            for (int i = 0; i < n - 24; i++) {
                if (head[i] == 'a' && head[i + 1] == 'v' && head[i + 2] == 'c' && head[i + 3] == 'C') {
                    int p = i + 4;
                    int profileIdc = head[p + 1] & 0xFF;
                    int levelIdc = head[p + 3] & 0xFF;
                    mAvcProfileIdc = profileIdc;
                    mAvcLevelIdc = levelIdc;
                    String prof;
                    switch (profileIdc) {
                        case 66: prof = "Baseline"; break;
                        case 77: prof = "Main"; break;
                        case 88: prof = "Extended"; break;
                        case 100: prof = "High"; break;
                        case 110: prof = "High10"; break;
                        default: prof = "id=" + profileIdc; break;
                    }
                    Log.i(TAG, "codec info: AVC " + prof + "@L" + levelIdc
                            + " (profile_idc=" + profileIdc + ", level_idc=" + levelIdc + ")");
                    return;
                }
            }
            Log.i(TAG, "codec info: avcC not found in first " + n + " bytes");
        }
    }

    /** 起播缓冲门：攒够该水位才回首个响应头（低内存设备减半） */
    private static int pfStartMinBytes() {
        return tv.biliclassic.util.SdkHelper.isLowMemoryDevice()
                ? 128 * 1024 : 256 * 1024;
    }

    /**
     * 播放器消费点之后已预读的字节数（缓冲垫）。
     * 未启用预取返回 -1；已读到文件尾返回 Long.MAX_VALUE（无饥饿风险）。
     */
    public long getBufferedAhead() {
        synchronized (mPfLock) {
            if (!mPrefetchEngaged) return -1;
            if (mPfEof) return Long.MAX_VALUE;
            long pos = Math.max(mPfBase, Math.min(mPfServePos, mPfEnd));
            return Math.max(0, mPfEnd - pos);
        }
    }

    /** 预读是否已到文件尾 */
    public boolean isPfEof() {
        synchronized (mPfLock) {
            return mPfEof;
        }
    }

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
                // 单流（MP4 直链）：维持与旧版一致的逐请求直通转发。
                // 预取缓冲路径（serveWithPrefetch）在部分老机型上引发
                // "Video track fell behind" 回归，已默认停用；
                // 需要重新诊断时将 PF_ENABLED 置 true
                if (PF_ENABLED) {
                    serveWithPrefetch(client, rangeHeader);
                } else {
                    serveTo(client, remoteUrl, requestLine, rangeHeader);
                }
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

    // ===== 预取缓冲服务 =====

    /** 惰性启动预读线程（首个请求到达时） */
    private void ensurePrefetchThread(final long startOffset) {
        synchronized (mPfLock) {
            if (mPfThread != null) return;
            mPrefetchEngaged = true;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    pfLoop(startOffset);
                }
            }, "LocalStreamProxyPrefetch");
            t.setDaemon(true);
            mPfThread = t;
            t.start();
        }
    }

    /**
     * 预读线程主循环：维护一条远端连接，顺序把数据拉进环形缓冲。
     * seek（mPfResetTarget）时关旧连接、按目标偏移重开；连接失效时从
     * 当前偏移自动重连。网络读取不持锁，避免阻塞播放器消费线程。
     */
    private void pfLoop(long next) {
        while (running) {
            boolean needOpen = false;
            long openAt = next;
            synchronized (mPfLock) {
                if (mPfResetTarget >= 0) {
                    // seek：丢弃缓冲，改从目标偏移拉取
                    next = mPfResetTarget;
                    mPfResetTarget = -1;
                    closePfRemoteLocked();
                    mPfEof = false;
                    mPfBroken = false;
                    mPfLock.notifyAll();
                    needOpen = true;
                    openAt = next;
                } else if (mPfRemote == null || mPfBroken) {
                    // 断线重连：从当前已预读偏移继续
                    mPfBroken = false;
                    mPfEof = false;
                    needOpen = true;
                    openAt = next;
                } else if (mPfEof) {
                    try { mPfLock.wait(800); } catch (InterruptedException ie) { return; }
                } else {
                    // 缓冲满：淘汰播放位置之前的前段腾空间，仍满则等待
                    while (running && mPfEnd - mPfBase >= PF_BUF_SIZE && mPfResetTarget < 0) {
                        if (mPfServePos > mPfBase) {
                            long nb = Math.min(mPfServePos, mPfEnd);
                            if (nb > mPfBase) mPfBase = nb;
                        }
                        if (mPfEnd - mPfBase >= PF_BUF_SIZE) {
                            try { mPfLock.wait(700); } catch (InterruptedException ie) { return; }
                        }
                    }
                    if (!running) return;
                    if (mPfResetTarget >= 0) continue;
                }
            }
            if (needOpen) {
                try {
                    openPfRemote(openAt);
                    next = openAt;
                } catch (Throwable t) {
                    Log.w(TAG, "pf open failed: " + t);
                    try { Thread.sleep(800); } catch (InterruptedException ie) { return; }
                }
                continue;
            }
            if (mPfRemote == null || mPfEof) continue;

            // 计算写入参数（持锁），随后的网络读取不持锁
            final int writeIdx;
            final int space;
            synchronized (mPfLock) {
                if (mPfResetTarget >= 0 || mPfBroken || mPfRemote == null) continue;
                writeIdx = (int) (mPfEnd % PF_BUF_SIZE);
                long filled = mPfEnd - mPfBase;
                int toWrap = PF_BUF_SIZE - writeIdx;
                space = (int) Math.min((long) toWrap, PF_BUF_SIZE - filled);
                if (space <= 0) continue;
            }
            int n;
            try {
                n = mPfRemote.read(mPfBuf, writeIdx, space);
            } catch (Throwable t) {
                synchronized (mPfLock) {
                    mPfBroken = true;
                    closePfRemoteLocked();
                    mPfLock.notifyAll();
                }
                continue;
            }
            synchronized (mPfLock) {
                if (mPfResetTarget >= 0) {
                    // 读期间发生 seek，本次数据作废
                    mPfLock.notifyAll();
                    continue;
                }
                if (n < 0) {
                    mPfEof = true;
                } else if (n > 0) {
                    mPfEnd += n;
                    next = mPfEnd;
                }
                // 周期性输出缓冲水位，供卡顿排查（每 5 秒至多一条）
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - mLastPfLogElapsed > 5000) {
                    mLastPfLogElapsed = now;
                    Log.d(TAG, "pf state base=" + mPfBase + " end=" + mPfEnd
                            + " serve=" + mPfServePos + " eof=" + mPfEof
                            + " broken=" + mPfBroken + " total=" + mTotalLength);
                }
                mPfLock.notifyAll();
            }
        }
    }

    /**
     * 从预取缓冲读取指定偏移的数据。偏移落在缓冲窗口外（seek/已被淘汰）
     * 时请求预读线程重定位；数据未就绪时阻塞等待。返回 -1 表示文件尾。
     */
    private int readPrefetch(long offset, byte[] out, int outOff, int len) throws InterruptedException {
        synchronized (mPfLock) {
            while (running) {
                if (offset < mPfBase || offset > mPfEnd) {
                    // seek/窗口外：请求预读线程重定位，并把服务位置重锚定到新目标
                    mPfResetTarget = offset;
                    mPfServePos = offset;
                    closePfRemoteLocked();
                    mPfLock.notifyAll();
                }
                if (offset >= mPfBase && offset < mPfEnd) {
                    int idx = (int) (offset % PF_BUF_SIZE);
                    int toWrap = PF_BUF_SIZE - idx;
                    long avail = mPfEnd - offset;
                    int n = (int) Math.min((long) len, Math.min((long) toWrap, avail));
                    System.arraycopy(mPfBuf, idx, out, outOff, n);
                    mPfServePos = offset + n;
                    return n;
                }
                if (offset == mPfEnd) {
                    if (mPfEof) return -1;
                    if (mPfBroken && mPfRemote == null) {
                        // 断线且消费端已追上缓冲尾：唤醒生产者从当前位置重连
                        mPfBroken = false;
                        mPfLock.notifyAll();
                    }
                }
                mPfLock.wait(1000);
            }
            return -1;
        }
    }

    /** 打开从 offset 开始的远端流，解析总长度并复位缓冲窗口 */
    private void openPfRemote(long offset) throws IOException {
        HttpURLConnection conn = openRemoteConnection(remoteUrl, "bytes=" + offset + "-");
        // 预读连接用短读超时：远端静默断连时最多阻塞 10 秒就走断线重连，
        // 避免默认 60 秒超时期间消费端无数据可读（表现为播放冻结）
        try {
            conn.setReadTimeout(10 * 1000);
        } catch (Throwable t) {
        }
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            try { conn.disconnect(); } catch (Throwable t) {}
            throw new IOException("remote status " + code);
        }
        long total = parseTotalFromContentRange(conn.getHeaderField("Content-Range"));
        if (total <= 0 && offset == 0) {
            total = getContentLength(conn);
        }
        InputStream in = conn.getInputStream();
        synchronized (mPfLock) {
            if (total > 0) mTotalLength = total;
            mPfConn = conn;
            mPfRemote = in;
            mPfBase = offset;
            mPfEnd = offset;
            mPfServePos = offset;
            mPfEof = false;
            mPfBroken = false;
            mPfLock.notifyAll();
        }
    }

    private static long parseTotalFromContentRange(String contentRange) {
        if (contentRange == null) return -1;
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash == contentRange.length() - 1) return -1;
        try {
            return Long.parseLong(contentRange.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void closePfRemoteLocked() {
        try { if (mPfRemote != null) mPfRemote.close(); } catch (Throwable t) {}
        try { if (mPfConn != null) mPfConn.disconnect(); } catch (Throwable t) {}
        mPfRemote = null;
        mPfConn = null;
    }

    /**
     * 单流播放的预取版响应：响应头由本地构造（总长来自首次探测缓存），
     * 数据全部从预取缓冲供给，播放器不再感知远端延迟。
     */
    private void serveWithPrefetch(Socket client, String rangeHeader) {
        OutputStream out = null;
        try {
            long start = 0;
            long reqEnd = -1;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] seg = rangeHeader.substring(6).split("-");
                try { start = Long.parseLong(seg[0].trim()); } catch (Exception e) {}
                if (seg.length > 1) {
                    try { reqEnd = Long.parseLong(seg[1].trim()); } catch (Exception e) {}
                }
            }
            ensurePrefetchThread(start);

            // 起播缓冲门：先攒出一段缓冲垫再回响应头，避免起播即被消费端追尾
            long gateDeadline = android.os.SystemClock.elapsedRealtime() + 6000;
            synchronized (mPfLock) {
                int startMin = pfStartMinBytes();
                while (running && !mPfEof && (mPfEnd - start) < startMin
                        && android.os.SystemClock.elapsedRealtime() < gateDeadline) {
                    try { mPfLock.wait(300); } catch (InterruptedException ie) {}
                    if ((mPfEnd - start) >= 4096) logCodecInfoOnce();
                }
                logCodecInfoOnce();
            }

            // 等预读线程给出总长（首个远端响应头）
            long total;
            synchronized (mPfLock) {
                long waited = 0;
                while (running && mTotalLength <= 0 && waited < 10000) {
                    try { mPfLock.wait(500); } catch (InterruptedException ie) {}
                    waited += 500;
                }
                total = mTotalLength;
            }
            if (total <= 0) {
                // 预读未就绪：退化为旧的逐请求直通转发
                Log.w(TAG, "prefetch total unavailable, fallback to raw passthrough");
                serveTo(client, remoteUrl, "GET /video HTTP/1.0", rangeHeader);
                return;
            }

            long endPos = (reqEnd >= 0 && reqEnd < total - 1) ? reqEnd : total - 1;
            StringBuilder resp = new StringBuilder();
            resp.append("HTTP/1.0 206 Partial Content\r\n");
            resp.append("Accept-Ranges: bytes\r\n");
            resp.append("Content-Range: bytes ").append(start).append("-")
                    .append(endPos).append("/").append(total).append("\r\n");
            resp.append("Content-Type: video/mp4\r\n");
            resp.append("Content-Length: ").append(endPos - start + 1).append("\r\n");
            resp.append("Connection: close\r\n");
            resp.append("\r\n");
            out = client.getOutputStream();
            out.write(resp.toString().getBytes());
            out.flush();

            byte[] chunk = new byte[16 * 1024];
            long pos = start;
            while (running && pos <= endPos) {
                int want = (int) Math.min((long) chunk.length, endPos - pos + 1);
                int n;
                try {
                    n = readPrefetch(pos, chunk, 0, want);
                } catch (InterruptedException ie) {
                    break;
                }
                if (n < 0) break;
                try {
                    out.write(chunk, 0, n);
                } catch (IOException e) {
                    break; // 播放器断开（seek/退出）
                }
                pos += n;
            }
            out.flush();
        } catch (Exception e) {
            if (!(e instanceof java.net.SocketException)) {
                Log.e(TAG, "serveWithPrefetch error", e);
            }
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
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

        // 已探测过该远端只收 HTTPS 时直接走 HTTPS，避免每个 Range 请求都白跑一趟明文
        Boolean preferHttps = remotePreferHttps;
        if (preferHttps != null && preferHttps.booleanValue()) {
            return openRemoteConnectionImpl(targetUrl, rangeHeader);
        }

        try {
            HttpURLConnection conn = openRemoteConnectionImpl(primary, rangeHeader);
            int code = conn.getResponseCode();
            boolean ok = (code >= 200 && code < 300);
            if (!ok && !primary.equals(targetUrl)) {
                // mCDN 等节点对明文请求"成功建立连接但返回 400/403"，
                // 不抛 IOException，必须显式按状态码回退 HTTPS
                try { conn.disconnect(); } catch (Throwable ignored) {}
                Log.w(TAG, "HTTP fetch got status " + code + ", retry over HTTPS");
                HttpURLConnection sslConn = openRemoteConnectionImpl(targetUrl, rangeHeader);
                remotePreferHttps = Boolean.TRUE;
                return sslConn;
            }
            if (ok) {
                remotePreferHttps = Boolean.FALSE;
            }
            return conn;
        } catch (IOException e) {
            if (primary != null && !primary.equals(targetUrl)) {
                Log.w(TAG, "HTTP fetch failed (" + e.getMessage() + "), retry over HTTPS");
                HttpURLConnection sslConn = openRemoteConnectionImpl(targetUrl, rangeHeader);
                remotePreferHttps = Boolean.TRUE;
                return sslConn;
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
        synchronized (mPfLock) {
            closePfRemoteLocked();
            mPfLock.notifyAll();
        }
        Thread pf = mPfThread;
        mPfThread = null;
        if (pf != null) {
            pf.interrupt();
        }
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
