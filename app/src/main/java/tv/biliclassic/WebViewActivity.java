package tv.biliclassic;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
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
            tvTitle.setText("网页");
        }

        if (url == null || url.length() == 0) {
            Toast.makeText(this, "网页地址无效", Toast.LENGTH_SHORT).show();
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
                if (url.endsWith(".apk") || url.endsWith(".zip") || url.endsWith(".rar") ||
                        url.endsWith(".pdf") || url.contains("download")) {
                    if (SdkHelper.getSdkInt() >= 9) {
                        startDownload(url);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    }
                    return true;
                }
                view.loadUrl(url);
                return true;
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
            webView.getSettings().setBuiltInZoomControls(true);
        } catch (Exception e) {}
    }

    private void startDownload(String downloadUrl) {
        try {
            Class<?> downloadManagerClass = Class.forName("android.app.DownloadManager");
            Class<?> requestClass = Class.forName("android.app.DownloadManager$Request");

            Object request = requestClass.getConstructor(Uri.class).newInstance(Uri.parse(downloadUrl));

            java.lang.reflect.Field downloadsField = Environment.class.getField("DIRECTORY_DOWNLOADS");
            String downloadsDir = (String) downloadsField.get(null);

            java.lang.reflect.Method setDestinationInExternalPublicDir = requestClass.getMethod(
                    "setDestinationInExternalPublicDir", String.class, String.class);
            setDestinationInExternalPublicDir.invoke(request, downloadsDir, getFileName(downloadUrl));

            Object downloadManager = getSystemService("download");
            if (downloadManager != null) {
                java.lang.reflect.Method enqueue = downloadManagerClass.getMethod("enqueue", requestClass);
                enqueue.invoke(downloadManager, request);
                Toast.makeText(this, "开始下载: " + getFileName(downloadUrl), Toast.LENGTH_SHORT).show();
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