package tv.biliclassic.util;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 极验人机验证渲染（实现方法参考 PiliPlus GeetestWebviewDialog）：
 * 1) GET https://api.geetest.com/gettype.php?gt=xxx 取配置（响应形如 ({...})）
 * 2) WebView 加载内置 HTML，引用 https://static.geetest.com/static/js/fullpage.0.0.0.js
 * 3) 执行 t=Geetest(配置).onSuccess(...).onError(...).onClose(...); t.onReady(()=>t.verify())
 * 4) JS 通过 Bridge.post(name, json) 回传 geetest_challenge/validate/seccode
 * <p>
 * 移植到 BiliClassic：minSdk 1，WebSettings 的较新方法（setDomStorageEnabled 等）
 * 用反射调用，缺失时静默跳过，避免 NoSuchMethodError。
 */
public class GeetestDialog {

    public interface Callback {
        /** 验证通过：challenge/validate/seccode 三个参数直接用于发短信接口 */
        void onSuccess(String challenge, String validate, String seccode);

        /** 取消或失败 */
        void onFail(String msg);
    }

    private static final String GEETEST_JS =
            "https://static.geetest.com/static/js/fullpage.0.0.0.js";

    private final Activity mAct;
    private final String mGt;
    private final String mChallenge;
    private final Callback mCallback;
    private final Handler mUi = new Handler(Looper.getMainLooper());

    private Dialog mDialog;
    private WebView mWeb;
    private volatile boolean mFinished = false;

    private GeetestDialog(Activity act, String gt, String challenge, Callback cb) {
        mAct = act;
        mGt = gt;
        mChallenge = challenge;
        mCallback = cb;
    }

    /** 弹出人机验证；gt/challenge 来自 /x/passport-login/captcha */
    public static void show(final Activity act, final String gt, final String challenge, final Callback cb) {
        if (act == null || gt == null || gt.length() == 0 || challenge == null || challenge.length() == 0) {
            if (cb != null) {
                cb.onFail("人机验证参数为空");
            }
            return;
        }
        final GeetestDialog d = new GeetestDialog(act, gt, challenge, cb);
        d.loadConfigThenShow();
    }

    private void loadConfigThenShow() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String configJson = null;
                String err = null;
                try {
                    String resp = NetWorkUtil.get("https://api.geetest.com/gettype.php?gt=" + mGt);
                    if (resp != null) {
                        String t = resp.trim();
                        // 响应形如 ({...})
                        if (t.startsWith("(") && t.endsWith(")")) {
                            t = t.substring(1, t.length() - 1);
                        }
                        JSONObject cfg = new JSONObject(t);
                        if ("success".equals(cfg.optString("status", ""))) {
                            JSONObject data = cfg.optJSONObject("data");
                            if (data != null) {
                                data.put("gt", mGt);
                                data.put("challenge", mChallenge);
                                data.put("offline", false);
                                data.put("new_captcha", true);
                                data.put("product", "bind");
                                data.put("width", "100%");
                                data.put("https", true);
                                data.put("protocol", "https://");
                                configJson = data.toString();
                            }
                        } else {
                            err = "极验配置失败：" + cfg.optString("status", resp);
                        }
                    }
                } catch (Throwable e) {
                    err = "极验配置请求失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
                final String cfg = configJson;
                final String error = err;
                mUi.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cfg == null) {
                            if (mCallback != null) {
                                mCallback.onFail(error == null ? "人机验证初始化失败" : error);
                            }
                            return;
                        }
                        showWebView(cfg);
                    }
                });
            }
        }).start();
    }

    private void showWebView(final String configJson) {
        try {
            FrameLayout root = new FrameLayout(mAct);
            root.setBackgroundColor(0xFFFFFFFF);

            mWeb = new WebView(mAct);
            WebSettings ws = mWeb.getSettings();
            try {
                ws.setJavaScriptEnabled(true);
            } catch (Throwable t) {
            }
            // setDomStorageEnabled / setJavaScriptCanOpenWindowsAutomatically 在旧 API 不存在，反射调用
            try {
                java.lang.reflect.Method m = ws.getClass().getMethod("setDomStorageEnabled", boolean.class);
                m.invoke(ws, true);
            } catch (Throwable t) {
            }
            try {
                java.lang.reflect.Method m = ws.getClass().getMethod(
                        "setJavaScriptCanOpenWindowsAutomatically", boolean.class);
                m.invoke(ws, true);
            } catch (Throwable t) {
            }
            try {
                java.lang.reflect.Method m = ws.getClass().getMethod("setUserAgentString", String.class);
                m.invoke(ws, "Mozilla/5.0 (Linux; Android 8.0.0; Mobile) AppleWebKit/537.36"
                        + " (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
            } catch (Throwable t) {
            }
            mWeb.addJavascriptInterface(new Bridge(), "Bridge");
            mWeb.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    String js = "t=Geetest(" + configJson + ")"
                            + ".onSuccess(function(){R('success', t.getValidate());})"
                            + ".onError(function(o){R('error', o);})"
                            + ".onClose(function(o){R('close', o);});"
                            + "t.onReady(function(){t.verify();});";
                    // evaluateJavascript 是 API 19+，旧机用反射避免 VerifyError，失败退回 loadUrl
                    try {
                        Class<?> cbClass = Class.forName("android.webkit.ValueCallback");
                        java.lang.reflect.Method m = WebView.class.getMethod(
                                "evaluateJavascript", String.class, cbClass);
                        m.invoke(view, js, null);
                    } catch (Throwable e) {
                        try {
                            view.loadUrl("javascript:" + js);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });

            String html = "<!DOCTYPE html><html><head><meta name=\"viewport\""
                    + " content=\"width=device-width, initial-scale=1\"></head><body style=\"margin:0\">"
                    + "<script src=\"" + GEETEST_JS + "\"></script>"
                    + "<script>function R(n,o){try{Bridge.post(n, JSON.stringify(o));}catch(e){}}</script>"
                    + "</body></html>";
            mWeb.loadDataWithBaseURL("https://api.geetest.com", html, "text/html", "utf-8", null);

            root.addView(mWeb, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView close = new TextView(mAct);
            close.setText("✕");
            close.setTextColor(Color.WHITE);
            close.setTextSize(16);
            close.setGravity(Gravity.CENTER);
            close.setBackgroundColor(0x66000000);
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish(null, "已取消人机验证");
                }
            });
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(34), dp(34),
                    Gravity.RIGHT | Gravity.TOP);
            clp.topMargin = dp(6);
            clp.rightMargin = dp(6);
            root.addView(close, clp);

            mDialog = new Dialog(mAct, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            mDialog.setContentView(root);
            mDialog.setCanceledOnTouchOutside(false);
            mDialog.show();
        } catch (Throwable e) {
            if (mCallback != null) {
                mCallback.onFail("人机验证弹窗失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void post(final String name, final String json) {
            mUi.post(new Runnable() {
                @Override
                public void run() {
                    if ("success".equals(name)) {
                        try {
                            JSONObject o = new JSONObject(json == null ? "{}" : json);
                            String validate = o.optString("geetest_validate", "");
                            String seccode = o.optString("geetest_seccode", "");
                            String challenge = o.optString("geetest_challenge", "");
                            if (validate.length() == 0 || seccode.length() == 0) {
                                finish(null, "人机验证结果无效");
                                return;
                            }
                            if (challenge.length() == 0) {
                                challenge = mChallenge;
                            }
                            finish(new String[]{challenge, validate, seccode}, null);
                        } catch (Throwable e) {
                            finish(null, "人机验证结果解析失败");
                        }
                    } else if ("close".equals(name)) {
                        finish(null, "已取消人机验证");
                    } else {
                        Toast.makeText(mAct, "人机验证出错，请重试", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void finish(String[] result, String err) {
        if (mFinished) {
            return;
        }
        mFinished = true;
        try {
            if (mDialog != null && mDialog.isShowing()) {
                mDialog.dismiss();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (mWeb != null) {
                mWeb.destroy();
                mWeb = null;
            }
        } catch (Throwable ignored) {
        }
        if (mCallback == null) {
            return;
        }
        if (result != null) {
            mCallback.onSuccess(result[0], result[1], result[2]);
        } else {
            mCallback.onFail(err);
        }
    }

    private int dp(float v) {
        return (int) (v * mAct.getResources().getDisplayMetrics().density + 0.5f);
    }
}
