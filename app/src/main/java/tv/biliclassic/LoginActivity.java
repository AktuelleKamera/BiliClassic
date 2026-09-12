package tv.biliclassic;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import tv.biliclassic.util.KeyBindingUtil;

/**
 * 统一登录页：扫码 / 验证码 / 网页 / Cookie 四种方式。
 * 顶栏下方为 Tab 行，内容区按 Tab 切换 Fragment；左右方向键切换 Tab，
 * 上下/确认键交由当前 Fragment 处理。
 */
public class LoginActivity extends BaseActivity {

    private static final String STATE_TAB = "login_selected_tab";

    private TextView tabQr;
    private TextView tabSms;
    private TextView tabWeb;
    private TextView tabCookie;

    private int selectedTab = 0;
    private boolean fromSetup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);
        initRoundTitleBar();

        fromSetup = getIntent().getBooleanExtra("from_setup", false);

        tabQr = (TextView) findViewById(R.id.tab_qr);
        tabSms = (TextView) findViewById(R.id.tab_sms);
        tabWeb = (TextView) findViewById(R.id.tab_web);
        tabCookie = (TextView) findViewById(R.id.tab_cookie);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tabQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(0);
            }
        });
        tabSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(1);
            }
        });
        tabWeb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(2);
            }
        });
        tabCookie.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(3);
            }
        });

        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_TAB, 0);
        }
        selectTab(selectedTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
    }

    private void selectTab(int idx) {
        if (idx < 0) {
            idx = 0;
        } else if (idx > 3) {
            idx = 3;
        }
        selectedTab = idx;
        updateTabStyle();

        Fragment f;
        if (idx == 1) {
            f = SmsLoginFragment.newInstance(fromSetup);
        } else if (idx == 2) {
            f = WebLoginFragment.newInstance(fromSetup);
        } else if (idx == 3) {
            f = CookieLoginFragment.newInstance(fromSetup);
        } else {
            f = QRLoginFragment.newInstance(fromSetup);
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, f)
                .commitAllowingStateLoss();
    }

    private void updateTabStyle() {
        styleTab(tabQr, selectedTab == 0);
        styleTab(tabSms, selectedTab == 1);
        styleTab(tabWeb, selectedTab == 2);
        styleTab(tabCookie, selectedTab == 3);
    }

    private void styleTab(TextView tv, boolean selected) {
        if (tv == null) {
            return;
        }
        // 背景/文字色由 selector 按 selected 状态切换（与分区页 TabWidget 同款）
        tv.setSelected(selected);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int action = KeyBindingUtil.classify(event.getKeyCode());
            // 左右方向键切换 Tab（输入框聚焦时留给光标移动，不抢键）
            boolean editing = getCurrentFocus() instanceof android.widget.EditText;
            if (!editing
                    && (action == KeyBindingUtil.ACTION_LEFT || action == KeyBindingUtil.ACTION_RIGHT)) {
                if (event.getRepeatCount() == 0) {
                    if (action == KeyBindingUtil.ACTION_LEFT) {
                        selectTab(selectedTab - 1);
                    } else {
                        selectTab(selectedTab + 1);
                    }
                }
                return true;
            }
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (f instanceof QRLoginFragment) {
                if (((QRLoginFragment) f).handleRemoteKey(event)) {
                    return true;
                }
            } else if (f instanceof SmsLoginFragment) {
                if (((SmsLoginFragment) f).handleRemoteKey(event)) {
                    return true;
                }
            } else if (f instanceof WebLoginFragment) {
                if (((WebLoginFragment) f).handleRemoteKey(event)) {
                    return true;
                }
            } else if (f instanceof CookieLoginFragment) {
                if (((CookieLoginFragment) f).handleRemoteKey(event)) {
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
