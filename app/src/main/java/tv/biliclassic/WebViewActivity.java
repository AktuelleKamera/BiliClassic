package tv.biliclassic;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import tv.biliclassic.util.SdkHelper;

public class WebViewActivity extends BaseActivity {

    private WebView webView;
    private TextView tvTitle;
    private LinearLayout loadingContainer;
    private String url;
    private String pageTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        initRoundTitleBar();

        webView = (WebView) findViewById(R.id.webview);
        tvTitle = (TextView) findViewById(R.id.tv_title);
        loadingContainer = (LinearLayout) findViewById(R.id.loading_container);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });

        url = getIntent().getStringExtra("url");
        pageTitle = getIntent().getStringExtra("title");

        if (pageTitle != null && pageTitle.length() > 0) {
            tvTitle.setText(pageTitle);
        } else {
            tvTitle.setText(getString(R.string.webpage_2));
        }

        if (url == null || url.length() == 0) {
            Toast.makeText(this, this.getString(R.string.invalid_web_url), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupWebView();
        webView.loadUrl(url);
    }

    private void setupWebView() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 非 http(s) 协议（market://、mailto: 等）交给系统处理
                if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {}
                    return true;
                }
                // http(s) 一律不在这里拦截：APK 等下载多半经由 download.php 之类的
                // 分发脚本返回（Content-Disposition: attachment），真实的文件名/类型
                // 只在响应头里，shouldOverrideUrlLoading 拿不到。放行给 WebCore，
                // 由 onDownloadStart（setDownloadListener）统一处理。
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (loadingContainer != null) {
                    loadingContainer.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (loadingContainer != null) {
                    loadingContainer.setVisibility(View.GONE);
                }
                Toast.makeText(WebViewActivity.this, "加载失败: " + description, Toast.LENGTH_SHORT).show();
            }
        });

        // 下载回调：这里才有 Content-Disposition / MIME（DownloadListener 自 API 1 就有）
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                    String contentDisposition, String mimetype, long contentLength) {
                if (SdkHelper.getSdkInt() >= 9) {
                    startDownload(url, contentDisposition, mimetype);
                } else {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {}
                }
            }
        });

        // JavaScript (API 1 就有)
        try {
            webView.getSettings().setJavaScriptEnabled(true);
        } catch (Exception e) {}

        // DOM Storage 用反射 (API 5)
        try {
            Object settings = webView.getSettings();
            java.lang.reflect.Method setDomStorageEnabled = settings.getClass().getMethod(
                    "setDomStorageEnabled", boolean.class);
            setDomStorageEnabled.invoke(settings, true);
        } catch (Exception e) {}

        // 缩放 (API 1 就有)
        try {
            webView.getSettings().setSupportZoom(true);
            java.lang.reflect.Method setBuiltInZoomControls = webView.getSettings().getClass()
                    .getMethod("setBuiltInZoomControls", boolean.class);
            setBuiltInZoomControls.invoke(webView.getSettings(), true);
        } catch (Exception e) {}
    }

    private void startDownload(String downloadUrl, String contentDisposition, String mimetype) {
        try {
            String fileName = deriveFileName(downloadUrl, contentDisposition, mimetype);

            Class<?> downloadManagerClass = Class.forName("android.app.DownloadManager");
            Class<?> requestClass = Class.forName("android.app.DownloadManager$Request");

            Object request = requestClass.getConstructor(Uri.class).newInstance(Uri.parse(downloadUrl));

            // MIME 也交给 DownloadManager（安装器/通知依赖它）
            if (mimetype != null && mimetype.length() > 0) {
                try {
                    requestClass.getMethod("setMimeType", String.class)
                            .invoke(request, mimetype);
                } catch (Throwable t) {}
            }

            java.lang.reflect.Field downloadsField = Environment.class.getField("DIRECTORY_DOWNLOADS");
            String downloadsDir = (String) downloadsField.get(null);

            java.lang.reflect.Method setDestinationInExternalPublicDir = requestClass.getMethod(
                    "setDestinationInExternalPublicDir", String.class, String.class);
            setDestinationInExternalPublicDir.invoke(request, downloadsDir, fileName);

            Object downloadManager = getSystemService("download");
            if (downloadManager != null) {
                java.lang.reflect.Method enqueue = downloadManagerClass.getMethod("enqueue", requestClass);
                enqueue.invoke(downloadManager, request);
                Toast.makeText(this, "开始下载: " + fileName, Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(downloadUrl));
                startActivity(intent);
            }
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(downloadUrl));
            startActivity(intent);
        }
    }

    /**
     * 文件名优先级：Content-Disposition 的 filename > URL 末段 > 按 MIME 补扩展名。
     * 分发脚本型链接（download.php?id=x）的 URL 末段是脚本名，绝不能直接当文件名。
     */
    private String deriveFileName(String url, String contentDisposition, String mimetype) {
        String name = parseContentDispositionFilename(contentDisposition);
        if (name == null || name.length() == 0) {
            name = getFileName(url);
        }
        if (isUnreliableName(name)) {
            String ext = null;
            if (mimetype != null && mimetype.length() > 0
                    && !"application/octet-stream".equals(mimetype)) {
                try {
                    ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);
                } catch (Throwable t) {}
            }
            if (ext != null && ext.length() > 0) {
                int dot = name.lastIndexOf('.');
                name = (dot > 0 ? name.substring(0, dot) : name) + "." + ext;
            } else if (name.lastIndexOf('.') < 0) {
                name = name + "_" + System.currentTimeMillis();
            }
        }
        return name;
    }

    /** URL 末段是否不可信：无扩展名，或是服务端脚本/网页扩展名 */
    private boolean isUnreliableName(String name) {
        if (name == null || name.length() == 0) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return true;
        }
        String ext = name.substring(dot + 1).toLowerCase();
        return "php".equals(ext) || "asp".equals(ext) || "aspx".equals(ext)
                || "jsp".equals(ext) || "cgi".equals(ext) || "pl".equals(ext)
                || "html".equals(ext) || "htm".equals(ext) || "shtml".equals(ext);
    }

    /** 解析 Content-Disposition: attachment; filename="xxx.apk"（含 filename* 的 %xx 转义） */
    private String parseContentDispositionFilename(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.length() == 0) {
            return null;
        }
        try {
            int idx = contentDisposition.toLowerCase().indexOf("filename");
            if (idx < 0) {
                return null;
            }
            String rest = contentDisposition.substring(idx);
            int eq = rest.indexOf('=');
            if (eq < 0) {
                return null;
            }
            String name = rest.substring(eq + 1).trim();
            if (name.startsWith("\"")) {
                int end = name.indexOf('"', 1);
                if (end > 0) {
                    name = name.substring(1, end);
                }
            } else {
                int semi = name.indexOf(';');
                if (semi > 0) {
                    name = name.substring(0, semi);
                }
            }
            name = name.trim();
            // filename*=UTF-8''xxx 或带路径的情况：取最后一段
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            try {
                name = java.net.URLDecoder.decode(name, "UTF-8");
            } catch (Exception e) {}
            return name.length() > 0 ? name : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private String getFileName(String url) {
        String fileName = "download";
        if (url != null && url.length() > 0) {
            int lastSlash = url.lastIndexOf("/");
            if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                String name = url.substring(lastSlash + 1);
                int queryIndex = name.indexOf("?");
                if (queryIndex > 0) {
                    name = name.substring(0, queryIndex);
                }
                int hashIndex = name.indexOf("#");
                if (hashIndex > 0) {
                    name = name.substring(0, hashIndex);
                }
                if (name.length() > 0) {
                    fileName = name;
                }
            }
        }
        return fileName;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.clearHistory();
                webView.clearCache(true);
                webView.loadUrl("about:blank");
                webView.destroy();
                webView = null;
            } catch (Exception e) {}
        }
    }
}