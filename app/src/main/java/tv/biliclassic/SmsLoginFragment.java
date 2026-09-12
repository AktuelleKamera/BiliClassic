package tv.biliclassic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import tv.biliclassic.util.BiliAppLogin;
import tv.biliclassic.util.GeetestDialog;
import tv.biliclassic.util.KeyBindingUtil;
import tv.biliclassic.util.LoginHelper;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 短信验证码登录（app 端接口，人机验证为滑动类型）。
 * 逻辑移植自 BiliMD LoginActivity/BiliAppLogin，界面改为 BiliClassic 的 XML 风格。
 */
public class SmsLoginFragment extends Fragment {

    private EditText telEd;
    private EditText codeEd;
    private TextView sendBtn;
    private Button loginBtn;
    private Button clearBtn;
    private TextView status;

    private String mCaptchaKey = "";
    private boolean mSending = false;

    private Handler mUi;
    private boolean fromSetup = false;
    private boolean isDestroyed = false;

    // 遥控器按键导航
    private final ArrayList<View> mNavViews = new ArrayList<View>();
    private int mNavIndex = -1;
    private boolean mKeyNavActive = false;

    public SmsLoginFragment() {
    }

    public static SmsLoginFragment newInstance(boolean fromSetup) {
        Bundle args = new Bundle();
        args.putBoolean("from_setup", fromSetup);
        SmsLoginFragment f = new SmsLoginFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUi = new Handler(Looper.getMainLooper());
        Bundle b = getArguments();
        if (b != null) {
            fromSetup = b.getBoolean("from_setup", false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sms_login, container, false);
        telEd = (EditText) view.findViewById(R.id.sms_tel);
        codeEd = (EditText) view.findViewById(R.id.sms_code);
        sendBtn = (TextView) view.findViewById(R.id.sms_send);
        loginBtn = (Button) view.findViewById(R.id.sms_login);
        clearBtn = (Button) view.findViewById(R.id.sms_clear);
        status = (TextView) view.findViewById(R.id.sms_status);

        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                telEd.setText("");
                codeEd.setText("");
                telEd.requestFocus();
            }
        });

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tel = telEd.getText() == null ? "" : telEd.getText().toString().trim();
                if (tel.length() != 11) {
                    Toast.makeText(getActivity(), "请输入 11 位手机号", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mSending) {
                    return;
                }
                mSending = true;
                sendBtn.setEnabled(false);
                requestSmsCodeApp(tel);
            }
        });

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tel = telEd.getText() == null ? "" : telEd.getText().toString().trim();
                String code = codeEd.getText() == null ? "" : codeEd.getText().toString().trim();
                if (tel.length() != 11) {
                    Toast.makeText(getActivity(), "请输入 11 位手机号", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (code.length() == 0) {
                    Toast.makeText(getActivity(), "请输入短信验证码", Toast.LENGTH_SHORT).show();
                    return;
                }
                loginBySmsApp(tel, code);
            }
        });

        rebuildNavViews();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isDestroyed = true;
    }

    // ===== 发送验证码 =====

    private void requestSmsCodeApp(final String tel) {
        setStatus("正在发送验证码…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                BiliAppLogin.SendResult r = BiliAppLogin.sendSms(tel, null, null, null, null);
                if (r.ok) {
                    mCaptchaKey = r.captchaKey == null ? "" : r.captchaKey;
                    smsSentUi();
                    return;
                }
                final int code = r.code;
                final String msg = r.message;
                // -105：需要人机验证。先解析响应里的 recaptcha_url，失败再走 /x/safecenter/captcha/pre
                if (!r.hasGee()) {
                    BiliAppLogin.SendResult pre = BiliAppLogin.preCapture();
                    if (pre.hasGee()) {
                        r.geeGt = pre.geeGt;
                        r.geeChallenge = pre.geeChallenge;
                        r.recaptchaToken = pre.recaptchaToken;
                    }
                }
                if (!r.hasGee()) {
                    mUi.post(new Runnable() {
                        @Override
                        public void run() {
                            mSending = false;
                            sendBtn.setEnabled(true);
                            setStatus("发送失败 (" + code + ") " + msg + "，请改用扫码/网页登录");
                        }
                    });
                    return;
                }
                final String gt = r.geeGt;
                final String challenge = r.geeChallenge;
                final String token = r.recaptchaToken;
                mUi.post(new Runnable() {
                    @Override
                    public void run() {
                        setStatus("需要完成滑动验证…");
                        GeetestDialog.show(getActivity(), gt, challenge, new GeetestDialog.Callback() {
                            @Override
                            public void onSuccess(final String c2, final String validate, final String seccode) {
                                setStatus("验证通过，正在发送…");
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        BiliAppLogin.SendResult r2 =
                                                BiliAppLogin.sendSms(tel, c2, validate, seccode, token);
                                        if (r2.ok) {
                                            mCaptchaKey = r2.captchaKey == null ? "" : r2.captchaKey;
                                            smsSentUi();
                                        } else {
                                            final String m = "(" + r2.code + ") " + r2.message;
                                            mUi.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mSending = false;
                                                    sendBtn.setEnabled(true);
                                                    setStatus("发送失败 " + m);
                                                }
                                            });
                                        }
                                    }
                                }).start();
                            }

                            @Override
                            public void onFail(String m) {
                                mSending = false;
                                sendBtn.setEnabled(true);
                                setStatus("人机验证未完成：" + m);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void smsSentUi() {
        mUi.post(new Runnable() {
            @Override
            public void run() {
                mSending = false;
                if (isDestroyed || getActivity() == null) {
                    return;
                }
                Toast.makeText(getActivity(), "验证码已发送，请查收短信", Toast.LENGTH_SHORT).show();
                startSmsCountdown();
                setStatus("验证码已发送（5 分钟内有效）");
            }
        });
    }

    private void startSmsCountdown() {
        sendBtn.setEnabled(false);
        final int[] left = {60};
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (isDestroyed || getActivity() == null) {
                    return;
                }
                if (left[0] <= 0) {
                    sendBtn.setText(R.string.login_sms_send);
                    sendBtn.setEnabled(true);
                    return;
                }
                sendBtn.setText(left[0] + "s 后重发");
                left[0]--;
                mUi.postDelayed(this, 1000);
            }
        };
        mUi.post(tick);
    }

    // ===== 验证码登录 =====

    private void loginBySmsApp(final String tel, final String code) {
        setStatus("正在登录…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BiliAppLogin.LoginResult r = BiliAppLogin.loginBySms(tel, code, mCaptchaKey);
                if (!r.ok) {
                    final String m = "(" + r.code + ") " + r.message;
                    mUi.post(new Runnable() {
                        @Override
                        public void run() {
                            setStatus("登录失败 " + m);
                        }
                    });
                    return;
                }
                if (r.cookieString != null && r.cookieString.length() > 0) {
                    BiliAppLogin.storeLoginCookies(r.cookieString);
                }
                String ck = NetWorkUtil.getCookieString();
                if (ck == null || ck.length() == 0) {
                    ck = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
                }
                final String cookie = ck == null ? "" : ck;
                mUi.post(new Runnable() {
                    @Override
                    public void run() {
                        if (LoginHelper.hasValidCookie(cookie)) {
                            setStatus("登录成功，正在校验…");
                            LoginHelper.verifyThenSave(getActivity(), cookie, new LoginHelper.Callback() {
                                @Override
                                public void onResult(boolean ok, String msg) {
                                    if (ok) {
                                        onLoginSuccess();
                                    } else {
                                        setStatus(msg);
                                    }
                                }
                            });
                        } else {
                            setStatus("登录成功但未获取到凭证，请重试或改用扫码登录");
                        }
                    }
                });
            }
        }).start();
    }

    private void onLoginSuccess() {
        if (isDestroyed || getActivity() == null) {
            return;
        }
        MsgUtil.showMsg(getActivity(), "登录成功");
        getActivity().setResult(Activity.RESULT_OK);
        getActivity().finish();
    }

    private void setStatus(final String msg) {
        mUi.post(new Runnable() {
            @Override
            public void run() {
                if (status != null) {
                    status.setText(msg);
                }
            }
        });
    }

    // ===== 遥控器按键导航 =====

    private void rebuildNavViews() {
        mNavViews.clear();
        if (telEd != null) mNavViews.add(telEd);
        if (codeEd != null) mNavViews.add(codeEd);
        if (sendBtn != null) mNavViews.add(sendBtn);
        if (loginBtn != null) mNavViews.add(loginBtn);
        if (clearBtn != null) mNavViews.add(clearBtn);
        if (mNavViews.size() > 0 && mNavIndex < 0) {
            mNavIndex = 0;
        }
        if (mNavIndex >= mNavViews.size()) {
            mNavIndex = mNavViews.size() - 1;
        }
    }

    /** 供 LoginActivity 调用：上下移动光标，确认键触发/聚焦。左右键留给切 Tab。 */
    public boolean handleRemoteKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int action = KeyBindingUtil.classify(event.getKeyCode());
        if (action != KeyBindingUtil.ACTION_UP
                && action != KeyBindingUtil.ACTION_DOWN
                && action != KeyBindingUtil.ACTION_CONFIRM) {
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
        if (event.getRepeatCount() != 0) {
            return true;
        }
        if (action == KeyBindingUtil.ACTION_UP) {
            mNavIndex = Math.max(0, mNavIndex - 1);
            applyNavHighlight();
        } else if (action == KeyBindingUtil.ACTION_DOWN) {
            mNavIndex = Math.min(mNavViews.size() - 1, mNavIndex + 1);
            applyNavHighlight();
        } else {
            View v = mNavViews.get(mNavIndex);
            if (v instanceof EditText) {
                v.requestFocus();
            } else if (v != null) {
                v.performClick();
            }
        }
        return true;
    }

    private void applyNavHighlight() {
        for (int i = 0; i < mNavViews.size(); i++) {
            View v = mNavViews.get(i);
            if (v == null) continue;
            if (v instanceof EditText) {
                if (i == mNavIndex) {
                    v.requestFocus();
                }
            } else if (v == loginBtn || v == clearBtn) {
                // Holo 按钮：按下态高亮（selector 的 state_pressed）
                v.setPressed(i == mNavIndex);
            } else if (v == sendBtn) {
                // 链接式“获取验证码”：选中加深
                ((TextView) v).setTextColor(i == mNavIndex ? 0xFFA94E80 : 0xFFD86DA5);
            }
        }
    }
}
