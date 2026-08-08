package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.support.v4.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import tv.biliclassic.api.LoginApi;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class QRLoginFragment extends Fragment {

    private static final String TAG = "QRLoginFragment";

    // 扫码状态码常量
    private static final int SCAN_CODE_SUCCESS = 0;
    private static final int SCAN_CODE_WAITING = 86090;
    private static final int SCAN_CODE_SCANNED = 86091;
    private static final int SCAN_CODE_EXPIRED = 86038;
    private static final int SCAN_CODE_NOT_SCANNED = 86101;

    // 重试配置
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int SCAN_POLL_INTERVAL = 1000;

    // UI组件
    private ImageView qrImageView;
    private TextView scanStat;
    private Button btnManualLogin;
    private Button btnBack;

    // ===== 遥控器按键导航 =====
    private final java.util.ArrayList<View> mNavViews = new java.util.ArrayList<View>();
    private int mNavIndex = -1;
    private boolean mKeyNavActive = false;

    // 状态
    private Timer timer;
    private boolean needRefresh = false;
    private boolean fromSetup = false;
    private boolean isDestroyed = false;

    // Handler
    private Handler mainHandler;

    public QRLoginFragment() {
    }

    public static QRLoginFragment newInstance(boolean fromSetup) {
        Bundle args = new Bundle();
        args.putBoolean("from_setup", fromSetup);
        QRLoginFragment fragment = new QRLoginFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());

        Bundle bundle = getArguments();
        if (bundle != null) {
            fromSetup = bundle.getBoolean("from_setup", false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_qr_login, container, false);

        qrImageView = (ImageView) view.findViewById(R.id.qrImage);
        scanStat = (TextView) view.findViewById(R.id.scanStat);
        btnManualLogin = (Button) view.findViewById(R.id.btn_manual_login);
        btnBack = (Button) view.findViewById(R.id.btn_back);

        // 二维码容器尺寸按屏幕宽度自动适配（与二维码生成尺寸一致）
        final android.view.View qrContainer = view.findViewById(R.id.qr_container);
        if (qrContainer != null) {
            int size = computeQrSize();
            android.view.ViewGroup.LayoutParams lp = qrContainer.getLayoutParams();
            if (lp != null) {
                lp.width = size;
                lp.height = size;
                qrContainer.setLayoutParams(lp);
            }
        }

        rebuildNavViews();

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelTimer();
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        });

        btnManualLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), SpecialLoginActivity.class);
                intent.putExtra("login", true);
                startActivity(intent);
                cancelTimer();
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        });

        qrImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (needRefresh) {
                    refreshQrCode();
                }
            }
        });

        refreshQrCode();
        return view;
    }

    /**
     * 按屏幕宽度自适应二维码容器像素尺寸。
     * 与 LoginApi.computeQrSize 保持一致：屏幕宽 - 左右留白（32dp），
     * 上限取 260dp 与屏幕宽 70% 的较小值，避免小屏超屏。
     */
    private int computeQrSize() {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            float density = dm.density;
            int screenW = dm.widthPixels;
            int marginPx = (int) (32 * density + 0.5f);
            int maxPx = (int) (260 * density + 0.5f);
            int maxRatioPx = (int) (screenW * 0.7f);
            int size = screenW - marginPx;
            if (size > maxPx) {
                size = maxPx;
            }
            if (size > maxRatioPx) {
                size = maxRatioPx;
            }
            if (size < (int) (120 * density + 0.5f)) {
                size = (int) (120 * density + 0.5f);
            }
            return size;
        } catch (Throwable t) {
            return (int) (240 * getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }

    @Override
    public void onDestroy() {
        isDestroyed = true;
        cancelTimer();
        super.onDestroy();
    }

    // 二维码刷新

    private void refreshQrCode() {
        needRefresh = false;
        if (qrImageView == null) return;

        final Context context = getActivity();
        if (context == null) {
            Log.e(TAG, "Activity 已销毁，无法刷新二维码");
            return;
        }

        qrImageView.setEnabled(false);
        updateStatus("正在获取二维码...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 传入 Context 参数
                    final Bitmap qrImage = LoginApi.getLoginQR(context);
                    if (isDestroyed || getActivity() == null) return;

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed || getActivity() == null) return;
                            if (qrImage != null && qrImageView != null) {
                                qrImageView.setImageBitmap(qrImage);
                                qrImageView.setEnabled(true);
                                updateStatus("请使用B站APP扫码登录\n点击二维码可以刷新");
                                needRefresh = true;
                                startLoginDetect();
                            } else {
                                updateStatus("生成二维码失败，请重试");
                                if (qrImageView != null) {
                                    qrImageView.setEnabled(true);
                                }
                                needRefresh = true;
                            }
                        }
                    });
                } catch (final Exception e) {
                    if (isDestroyed || getActivity() == null) return;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed || getActivity() == null) return;
                            updateStatus("获取二维码失败：" + e.getMessage());
                            if (qrImageView != null) {
                                qrImageView.setEnabled(true);
                            }
                            needRefresh = true;
                        }
                    });
                }
            }
        }).start();
    }

    // 登录检测

    private void startLoginDetect() {
        cancelTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isAdded() || isDestroyed || getActivity() == null) {
                    this.cancel();
                    return;
                }
                pollLoginState();
            }
        }, SCAN_POLL_INTERVAL, SCAN_POLL_INTERVAL);
    }

    private void pollLoginState() {
        try {
            String response = LoginApi.getLoginState();
            if (response == null || response.length() == 0) {
                return;
            }

            JSONObject json = new JSONObject(response);
            int apiCode = json.optInt("code", -1);
            if (apiCode != 0) {
                return;
            }

            JSONObject data = json.getJSONObject("data");
            int scanCode = data.optInt("code", -1);

            handleScanCode(scanCode, data);

        } catch (Exception e) {
            Log.e(TAG, "轮询登录状态异常: " + e.getMessage());
            cancelTimer();
        }
    }

    private void handleScanCode(int scanCode, JSONObject data) throws JSONException {
        switch (scanCode) {
            case SCAN_CODE_SUCCESS:
                onLoginSuccess(data);
                break;

            case SCAN_CODE_WAITING:
                updateStatus("请使用B站APP扫码登录");
                break;

            case SCAN_CODE_SCANNED:
                updateStatus("已扫描，请在手机上点击确认登录");
                break;

            case SCAN_CODE_EXPIRED:
                onQrExpired();
                break;

            case SCAN_CODE_NOT_SCANNED:
                // 未扫码，保持当前状态
                break;

            default:
                Log.e(TAG, "未知扫码状态: " + scanCode);
                break;
        }
    }

    // 登录成功处理

    private void onLoginSuccess(JSONObject data) throws JSONException {
        cancelTimer();

        final String crossUrl = data.optString("url", "");

        // 先解析 URL 中的登录凭证并保存：不依赖跨域请求是否成功。
        // 之前 saveUserInfoFromUrl 在 NetWorkUtil.get() 之后同一 try 内，
        // 跨域请求一异常（TLS/超时/重定向）就整段跳过，出现"登录成功但没保存"。
        if (crossUrl != null && crossUrl.length() > 0) {
            saveUserInfoFromUrl(crossUrl);
        } else {
            Log.e(TAG, "登录成功但未返回跨域 URL，无法保存登录信息");
        }

        // 跨域请求用于让服务器种下完整 Cookie，尽力而为，失败不影响已保存的凭证
        if (crossUrl != null && crossUrl.length() > 0) {
            try {
                NetWorkUtil.get(crossUrl);
                Log.e(TAG, "跨域请求成功");
            } catch (Exception e) {
                Log.e(TAG, "请求跨域 URL 失败: " + e.getMessage());
            }
        }

        if (!isDestroyed && getActivity() != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isDestroyed || getActivity() == null) return;
                    MsgUtil.showMsg(getActivity(), "登录成功");
                    if (getActivity() != null) {
                        getActivity().setResult(LoginActivity.RESULT_OK);
                        getActivity().finish();
                    }
                }
            });
        }
    }

    // 二维码过期处理

    private void onQrExpired() {
        cancelTimer();
        if (!isDestroyed && getActivity() != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isDestroyed || getActivity() == null || scanStat == null) return;
                    updateStatus("二维码已过期，点击二维码刷新");
                    needRefresh = true;
                }
            });
        }
    }

    // 用户信息保存

    private void saveUserInfoFromUrl(String url) {
        Map<String, String> params = extractAllQueryParams(url);
        if (params.isEmpty()) {
            Log.e(TAG, "从 URL 解析用户信息失败（无任何凭证参数）");
            return;
        }

        String dedeUserID = params.get("DedeUserID");
        String biliJct = params.get("bili_jct");

        if (dedeUserID != null && dedeUserID.length() > 0) {
            try {
                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(dedeUserID));
            } catch (NumberFormatException e) {
                Log.e(TAG, "解析 DedeUserID 失败: " + e.getMessage());
            }
        }

        if (biliJct != null && biliJct.length() > 0) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, biliJct);
            Log.e(TAG, "保存 csrf 成功: " + biliJct);
        }

        // 跨域 URL 的查询参数就是完整登录 Cookie 集合。
        // 必须全部保存（含 DedeUserID__ckMd5、sid 等风控必需项），
        // 只存 DedeUserID/SESSDATA/bili_jct 会被 B站风控判为未登录（登录不完整）。
        StringBuffer sb = new StringBuffer();
        for (Iterator it = params.keySet().iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            if ("gourl".equals(key) || "go_url".equals(key) || "url".equals(key)) continue;
            String value = params.get(key);
            if (value == null || value.length() == 0) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(key).append("=").append(value);
        }

        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, sb.toString());
        NetWorkUtil.setCookieString(sb.toString());
        NetWorkUtil.refreshHeaders();

        Log.e(TAG, "保存用户信息成功，mid: " + dedeUserID + "，Cookie 数量: " + params.size());
        fetchUserNameWithRetry(0);
    }

    private Map<String, String> extractAllQueryParams(String url) {
        Map<String, String> map = new HashMap<String, String>();
        if (url == null || url.length() == 0) {
            return map;
        }
        try {
            String query = url;
            int q = query.indexOf('?');
            if (q >= 0 && q + 1 < query.length()) {
                query = query.substring(q + 1);
            } else {
                return map;
            }
            String[] params = query.split("&");
            for (int i = 0; i < params.length; i++) {
                String pair = params[i];
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = pair.substring(0, eq);
                    String value = pair.substring(eq + 1);
                    try {
                        value = java.net.URLDecoder.decode(value, "UTF-8");
                    } catch (Exception e) {
                    }
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析参数失败: " + e.getMessage());
        }
        return map;
    }

    // 获取用户名

    private void fetchUserNameWithRetry(final int retryCount) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed) return;

                try {
                    if (retryCount > 0) {
                        Thread.sleep(RETRY_DELAY_MS);
                    }

                    String cookies = NetWorkUtil.getCookieString();
                    Log.e(TAG, "fetchUserName - 当前Cookie长度: "
                            + (cookies == null ? 0 : cookies.length()));

                    String response = NetWorkUtil.get("https://api.bilibili.com/x/web-interface/nav");
                    if (response == null) {
                        return;
                    }

                    Log.e(TAG, "nav 响应长度: " + response.length());

                    JSONObject json = new JSONObject(response);
                    int code = json.optInt("code", -1);

                    if (code == 0) {
                        JSONObject data = json.getJSONObject("data");
                        final String uname = data.optString("uname", "");
                        if (uname != null && uname.length() > 0) {
                            SharedPreferencesUtil.putString("uname", uname);
                            Log.e(TAG, "获取用户名成功: " + uname);
                        }
                    } else if (retryCount < MAX_RETRY_COUNT) {
                        Log.e(TAG, "nav 返回错误码: " + code + "，重试 " + (retryCount + 1));
                        fetchUserNameWithRetry(retryCount + 1);
                    } else {
                        Log.e(TAG, "nav 返回错误码: " + code + "，已达最大重试次数");
                    }

                } catch (InterruptedException e) {
                    Log.e(TAG, "获取用户名被中断");
                } catch (Exception e) {
                    if (retryCount < MAX_RETRY_COUNT) {
                        Log.e(TAG, "获取用户名异常: " + e.getMessage()
                                + "，重试 " + (retryCount + 1));
                        fetchUserNameWithRetry(retryCount + 1);
                    } else {
                        Log.e(TAG, "获取用户名失败: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    // 工具方法

    private void updateStatus(final String text) {
        if (isDestroyed || getActivity() == null || scanStat == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isDestroyed || getActivity() == null || scanStat == null) {
                    return;
                }
                scanStat.setText(text);
            }
        });
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    // ===== 遥控器按键导航 =====

    private void rebuildNavViews() {
        mNavViews.clear();
        if (qrImageView != null) mNavViews.add(qrImageView);
        if (btnManualLogin != null) mNavViews.add(btnManualLogin);
        if (btnBack != null) mNavViews.add(btnBack);
        if (mNavViews.size() > 0 && mNavIndex < 0) {
            mNavIndex = 0;
        }
        if (mNavIndex >= mNavViews.size()) {
            mNavIndex = mNavViews.size() - 1;
        }
    }

    /** 供 LoginActivity.dispatchKeyEvent 调用：方向键移动光标，确认键触发。 */
    public boolean handleRemoteKey(android.view.KeyEvent event) {
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
            return false;
        }
        int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
        if (action != tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT
                && action != tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
            return false;
        }
        if (mNavViews.size() == 0) {
            rebuildNavViews();
        }
        if (mNavViews.size() == 0) {
            return false;
        }
        if (mNavIndex < 0 || mNavIndex >= mNavViews.size()) {
            mNavIndex = 0;
        }
        // 首次按键启用高亮（触屏机不按键不高亮）
        if (!mKeyNavActive) {
            mKeyNavActive = true;
            applyNavHighlight();
        }
        if (event.getRepeatCount() != 0) {
            return true;
        }
        if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                || action == tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT) {
            mNavIndex = Math.max(0, mNavIndex - 1);
            applyNavHighlight();
            return true;
        } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                || action == tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT) {
            mNavIndex = Math.min(mNavViews.size() - 1, mNavIndex + 1);
            applyNavHighlight();
            return true;
        } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
            View v = mNavViews.get(mNavIndex);
            if (v != null) {
                v.performClick();
            }
            return true;
        }
        return true;
    }

    /** 选中变暗：qrImage 加粉色边框提示，按钮变深粉，未选中恢复。 */
    private void applyNavHighlight() {
        for (int i = 0; i < mNavViews.size(); i++) {
            View v = mNavViews.get(i);
            if (v == null) continue;
            if (v == btnManualLogin || v == btnBack) {
                // 按钮：选中深粉，未选中原粉 #D86DA5
                if (i == mNavIndex) {
                    ((Button) v).setBackgroundColor(0xFFC06090);
                } else {
                    ((Button) v).setBackgroundColor(0xFFD86DA5);
                }
            } else if (v == qrImageView) {
                // QR 图：选中叠深粉边框，未选中移除
                if (i == mNavIndex) {
                    v.setBackgroundColor(0x66D86DA5);
                } else {
                    v.setBackgroundDrawable(null);
                }
            }
        }
    }
}