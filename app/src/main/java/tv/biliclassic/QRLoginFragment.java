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
        Button btnBack = (Button) view.findViewById(R.id.btn_back);
        Button btnManualLogin = (Button) view.findViewById(R.id.btn_manual_login);

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
        if (crossUrl != null && crossUrl.length() > 0) {
            try {
                NetWorkUtil.get(crossUrl);
                Log.e(TAG, "跨域请求成功");
                saveUserInfoFromUrl(crossUrl);
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
        String dedeUserID = extractQueryParam(url, "DedeUserID");
        String sessData = extractQueryParam(url, "SESSDATA");
        String biliJct = extractQueryParam(url, "bili_jct");

        if (dedeUserID == null || dedeUserID.length() == 0) {
            Log.e(TAG, "从 URL 解析用户信息失败");
            return;
        }

        try {
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(dedeUserID));
        } catch (NumberFormatException e) {
            Log.e(TAG, "解析 DedeUserID 失败: " + e.getMessage());
        }

        if (biliJct != null && biliJct.length() > 0) {
            SharedPreferencesUtil.putString("csrf", biliJct);
            Log.e(TAG, "保存 csrf 成功: " + biliJct);
        }

        String manualCookies = "DedeUserID=" + dedeUserID
                + "; SESSDATA=" + sessData
                + "; bili_jct=" + biliJct;
        SharedPreferencesUtil.putString("cookies", manualCookies);
        NetWorkUtil.setCookieString(manualCookies);
        NetWorkUtil.refreshHeaders();

        Log.e(TAG, "保存用户信息成功，mid: " + dedeUserID);
        fetchUserNameWithRetry(0);
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

    private String extractQueryParam(String url, String paramName) {
        if (url == null || url.length() == 0) {
            return null;
        }

        try {
            String[] parts = url.split("\\?");
            if (parts.length < 2) {
                return null;
            }

            String[] params = parts[1].split("&");
            for (int i = 0; i < params.length; i++) {
                String[] kv = params[i].split("=");
                if (kv.length == 2 && kv[0].equals(paramName)) {
                    return kv[1];
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析参数失败: " + e.getMessage());
        }
        return null;
    }

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
}