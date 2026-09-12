package tv.biliclassic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import tv.biliclassic.util.LoginHelper;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.SdkHelper;

/**
 * 网页登录：WebView 打开 B 站手机版登录页，轮询抓取登录后的 Cookie 自动完成登录。
 * 逻辑移植自 BiliMD LoginActivity.showWeb，CookieManager 为 API 9 类，旧机用反射访问。
 */
public class WebLoginFragment extends Fragment {

    private static final String LOGIN_URL =
            "https://passport.bilibili.com/h5-app/passport/login?gourl=https%3A%2F%2Fm.bilibili.com%2F";

    private WebView mWeb;
    private Handler mUi;
    private boolean mLoaded = false;
    private boolean mVerifyBusy = false;
    private String mLastRejected = "";
    private boolean isDestroyed = false;

    private final Runnable mCookiePoller = new Runnable() {
        @Override
        public void run() {
            tryCaptureFromWebView();
            if (!isDestroyed) {
                mUi.postDelayed(this, 1200);
            }
        }
    };

    public WebLoginFragment() {
    }

    public static WebLoginFragment newInstance(boolean fromSetup) {
        Bundle args = new Bundle();
        args.putBoolean("from_setup", fromSetup);
        WebLoginFragment f = new WebLoginFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUi = new Handler(Looper.getMainLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_web_login, container, false);
        mWeb = (WebView) view.findViewById(R.id.login_webview);
        setupWebView();
        loadLoginPage();
        mUi.postDelayed(mCookiePoller, 800);
        return view;
    }

    private void setupWebView() {
        if (mWeb == null) {
            return;
        }
        WebSettings ws = mWeb.getSettings();
        try {
            ws.setJavaScriptEnabled(true);
        } catch (Throwable t) {
        }
        // setDomStorageEnabled（API 7）用反射，旧机缺失时跳过
        try {
            java.lang.reflect.Method m = ws.getClass().getMethod("setDomStorageEnabled", boolean.class);
            m.invoke(ws, true);
        } catch (Throwable t) {
        }
        try {
            java.lang.reflect.Method m = ws.getClass().getMethod("setUserAgentString", String.class);
            m.invoke(ws, "Mozilla/5.0 (Linux; Android 8.0.0; Mobile) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/122.0.6261.95 Mobile Safari/537.36");
        } catch (Throwable t) {
        }
        mWeb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view.loadUrl(url);
                }
                return true;
            }
        });
    }

    private void loadLoginPage() {
        if (mWeb == null || mLoaded) {
            return;
        }
        mLoaded = true;
        try {
            mWeb.loadUrl(LOGIN_URL);
        } catch (Throwable t) {
        }
    }

    /** 反射读取 WebView Cookie（CookieManager 为 API 9 类，避免旧机 VerifyError） */
    private String readWebCookies() {
        if (SdkHelper.getSdkInt() < 9) {
            return "";
        }
        try {
            Class<?> cmClass = Class.forName("android.webkit.CookieManager");
            Object cm = cmClass.getMethod("getInstance").invoke(null);
            java.lang.reflect.Method getCookie = cmClass.getMethod("getCookie", String.class);
            String c1 = (String) getCookie.invoke(cm, "https://www.bilibili.com");
            String c2 = (String) getCookie.invoke(cm, "https://passport.bilibili.com");
            return merge(c1, c2);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String merge(String a, String b) {
        if (a == null) {
            return b == null ? "" : b;
        }
        if (b == null) {
            return a;
        }
        return a + "; " + b;
    }

    private void tryCaptureFromWebView() {
        if (isDestroyed || getActivity() == null) {
            return;
        }
        try {
            String all = readWebCookies();
            if (LoginHelper.hasValidCookie(all)) {
                if (all.equals(mLastRejected) || mVerifyBusy) {
                    return;
                }
                mVerifyBusy = true;
                final String cookie = all;
                LoginHelper.verifyThenSave(getActivity(), cookie, new LoginHelper.Callback() {
                    @Override
                    public void onResult(boolean ok, String msg) {
                        mVerifyBusy = false;
                        if (ok) {
                            onLoginSuccess();
                        } else {
                            mLastRejected = cookie;
                            if (isDestroyed || getActivity() == null) {
                                return;
                            }
                            Toast.makeText(getActivity(),
                                    "网页登录未生效（账号未登录），请在页面上重新登录",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        } catch (Throwable t) {
        }
    }

    private void onLoginSuccess() {
        if (isDestroyed || getActivity() == null) {
            return;
        }
        MsgUtil.showMsg(getActivity(), "登录成功");
        getActivity().setResult(Activity.RESULT_OK);
        getActivity().finish();
    }

    @Override
    public void onDestroyView() {
        isDestroyed = true;
        mUi.removeCallbacksAndMessages(null);
        if (mWeb != null) {
            try {
                ViewGroup parent = (ViewGroup) mWeb.getParent();
                if (parent != null) {
                    parent.removeView(mWeb);
                }
                mWeb.destroy();
            } catch (Throwable t) {
            }
            mWeb = null;
        }
        super.onDestroyView();
    }

    /** 网页登录没有可按键选择的条目，交由 Activity 处理左右切 Tab。 */
    public boolean handleRemoteKey(android.view.KeyEvent event) {
        return false;
    }
}
