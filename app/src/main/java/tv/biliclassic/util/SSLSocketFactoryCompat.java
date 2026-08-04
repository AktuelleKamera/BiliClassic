/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年6月19日
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * 兼容低版本 Android 的 SSLSocketFactory。
 *
 * 问题背景：
 * - Android 1.6-4.x 系统默认只启用 TLSv1 与旧套件；B 站服务器现已要求 TLS 1.2+。
 * - Android 2.x-4.x 底层 Conscrypt 实际支持 TLSv1.1/TLSv1.2，但默认不启用，
 *   导致握手慢或失败（"部分设备能用、部分设备网络极慢"）。
 *
 * 本类在创建每个 SSLSocket 时显式启用 socket 支持的最高 TLS 协议，
 * 并优先现代加密套件，从而改善低版本设备的连接成功率与速度。
 */
public class SSLSocketFactoryCompat extends SSLSocketFactory {
    private final SSLSocketFactory defaultFactory;
    private static String[] protocols = null;
    private static String[] cipherSuites = null;

    static {
        // 从默认 SSLSocketFactory 探测可用的协议与套件；任何一步失败都不致命，
        // 后续 upgradeTLS 会退化为"不修改"，避免老 ROM 上直接崩溃。
        try {
            SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            if (socket != null) {
                try {
                    protocols = buildProtocols(socket);
                    if (SdkHelper.getSdkInt() < 21) {
                        cipherSuites = buildCipherSuites(socket);
                    }
                    android.util.Log.d("NetDiag", "SSLSocketFactoryCompat sdk=" + SdkHelper.getSdkInt()
                            + " 协议=" + java.util.Arrays.toString(protocols)
                            + " 套件数=" + (cipherSuites == null ? -1 : cipherSuites.length));
                } catch (Exception e) {
                    android.util.Log.e("NetDiag", "SSLSocketFactoryCompat 探测失败: " + e.getMessage());
                } finally {
                    try { socket.close(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            android.util.Log.e("NetDiag", "SSLSocketFactoryCompat 创建socket失败: " + e.getMessage());
        }
    }

    private static String[] buildProtocols(SSLSocket socket) {
        // 显式加入 TLSv1.2 / TLSv1.1（老系统 Conscrypt 支持但默认关闭），
        // 再补充 socket 原生支持的非 SSL 协议，最后去重。
        List<String> list = new ArrayList<String>();
        String[] desired = {"TLSv1.2", "TLSv1.1", "TLSv1"};
        String[] supported = socket.getSupportedProtocols();
        HashSet<String> supportedSet = new HashSet<String>(Arrays.asList(supported));
        for (int i = 0; i < desired.length; i++) {
            if (supportedSet.contains(desired[i])) {
                list.add(desired[i]);
            }
        }
        for (int i = 0; i < supported.length; i++) {
            String p = supported[i];
            if (!p.toUpperCase().contains("SSL") && !list.contains(p)) {
                list.add(p);
            }
        }
        return list.toArray(new String[0]);
    }

    private static String[] buildCipherSuites(SSLSocket socket) {
        // 优先现代套件（GCM/ECDHE），再补充 socket 已启用套件，去重。
        List<String> allowedCiphers = Arrays.asList(
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_128_CBC_SHA",
                "TLS_RSA_WITH_AES_256_CBC_SHA",
                "TLS_RSA_WITH_3DES_EDE_CBC_SHA");
        List<String> availableCiphers = Arrays.asList(socket.getSupportedCipherSuites());
        HashSet<String> availableSet = new HashSet<String>(availableCiphers);
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < allowedCiphers.size(); i++) {
            if (availableSet.contains(allowedCiphers.get(i)) && !result.contains(allowedCiphers.get(i))) {
                result.add(allowedCiphers.get(i));
            }
        }
        String[] enabled = socket.getEnabledCipherSuites();
        for (int i = 0; i < enabled.length; i++) {
            if (!result.contains(enabled[i])) {
                result.add(enabled[i]);
            }
        }
        return result.toArray(new String[0]);
    }

    public SSLSocketFactoryCompat(X509TrustManager tm) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            X509TrustManager[] tmArray = (tm != null) ? new X509TrustManager[]{tm} : null;
            sslContext.init(null, tmArray, null);
            defaultFactory = sslContext.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private void upgradeTLS(SSLSocket ssl) {
        try {
            if (protocols != null && protocols.length > 0) {
                ssl.setEnabledProtocols(protocols);
            }
            if (SdkHelper.getSdkInt() < 21 && cipherSuites != null && cipherSuites.length > 0) {
                ssl.setEnabledCipherSuites(cipherSuites);
            }
        } catch (Exception ignored) {
            // 个别 ROM 不支持某些组合，忽略即可，连接仍会继续
        }
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return cipherSuites;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return cipherSuites;
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        Socket ssl = defaultFactory.createSocket(s, host, port, autoClose);
        if (ssl instanceof SSLSocket) {
            upgradeTLS((SSLSocket) ssl);
        }
        return ssl;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket ssl = defaultFactory.createSocket(host, port);
        if (ssl instanceof SSLSocket) {
            upgradeTLS((SSLSocket) ssl);
        }
        return ssl;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        Socket ssl = defaultFactory.createSocket(host, port, localHost, localPort);
        if (ssl instanceof SSLSocket) {
            upgradeTLS((SSLSocket) ssl);
        }
        return ssl;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        Socket ssl = defaultFactory.createSocket(host, port);
        if (ssl instanceof SSLSocket) {
            upgradeTLS((SSLSocket) ssl);
        }
        return ssl;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket ssl = defaultFactory.createSocket(address, port, localAddress, localPort);
        if (ssl instanceof SSLSocket) {
            upgradeTLS((SSLSocket) ssl);
        }
        return ssl;
    }
}
