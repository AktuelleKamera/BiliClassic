package tv.biliclassic;

import android.text.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.CookieHelper;

public class SpecialLoginActivity extends BaseActivity {

    private EditText textInput;
    private Button confirmBtn;
    private Button refuseBtn;
    private Button copyBtn;
    private TextView descText;
    private TextView hintText;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_special_login);

        textInput = (EditText) findViewById(R.id.loginInput);
        confirmBtn = (Button) findViewById(R.id.confirm);
        refuseBtn = (Button) findViewById(R.id.refuse);
        copyBtn = (Button) findViewById(R.id.copy);
        descText = (TextView) findViewById(R.id.desc);
        hintText = (TextView) findViewById(R.id.hint_text);

        final boolean fromSetup = getIntent().getBooleanExtra("from_setup", false);
        final boolean isLoginMode = getIntent().getBooleanExtra("login", true);

        if (isLoginMode) {
            descText.setText(getString(R.string.specialloginactivity_settext_8bf7));
            if (hintText != null) {
                hintText.setText("支持格式：\n• 浏览器复制的 Cookie 字符串\n• JSON 格式 { \"cookies\": \"...\" }\n• 任意包含 SESSDATA 的文本");
                hintText.setVisibility(View.VISIBLE);
            }

            refuseBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (fromSetup) {
                        startActivity(new Intent(SpecialLoginActivity.this, MainActivity.class));
                    }
                    finish();
                }
            });

            confirmBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String input = textInput.getText().toString().trim();
                    if (input == null || input.length() == 0) {
                        Toast.makeText(SpecialLoginActivity.this, SpecialLoginActivity.this.getString(R.string.specialloginactivity_toast_8bf7), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 先尝试用 CookieHelper 智能解析
                    String cookies = CookieHelper.parseAndBuildCookie(input);
                    if (cookies == null) {
                        // 如果智能解析失败，尝试 JSON 解析
                        try {
                            JSONObject json = new JSONObject(input);
                            cookies = json.optString("cookies", "");
                            if (cookies == null || cookies.length() == 0) {
                                Toast.makeText(SpecialLoginActivity.this, SpecialLoginActivity.this.getString(R.string.specialloginactivity_toast_672a), Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } catch (JSONException e) {
                            Toast.makeText(SpecialLoginActivity.this, SpecialLoginActivity.this.getString(R.string.specialloginactivity_toast_65e0), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    if (cookies == null || cookies.length() == 0) {
                        Toast.makeText(SpecialLoginActivity.this, SpecialLoginActivity.this.getString(R.string.specialloginactivity_toast_672a), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 保存 Cookie
                    String mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies);
                    String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);

                    if (mid != null && mid.length() > 0) {
                        try {
                            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                        } catch (NumberFormatException e) {
                            // 忽略
                        }
                    }
                    if (csrf != null && csrf.length() > 0) {
                        SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, csrf);
                    }
                    SharedPreferencesUtil.putString("cookies", cookies);

                    NetWorkUtil.refreshHeaders();
                    saveUserName();

                    Toast.makeText(SpecialLoginActivity.this, SpecialLoginActivity.this.getString(R.string.specialloginactivity_toast_767b), Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(SpecialLoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            });

            copyBtn.setVisibility(View.GONE);

        } else {
            descText.setText(getString(R.string.specialloginactivity_settext_5f53));
            if (hintText != null) {
                hintText.setVisibility(View.GONE);
            }

            JSONObject json = new JSONObject();
            try {
                String cookies = SharedPreferencesUtil.getString("cookies", "");
                String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
                json.put("cookies", cookies);
                json.put("refresh_token", refreshToken);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            textInput.setText(json.toString());
            textInput.setFocusable(false);
            textInput.setFocusableInTouchMode(false);

            confirmBtn.setText(getString(R.string.specialloginactivity_settext_5bfc));
            confirmBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String input = textInput.getText().toString().trim();
                    try {
                        JSONObject inputJson = new JSONObject(input);
                        String cookies = inputJson.optString("cookies", "");
                        if (cookies != null && cookies.length() > 0) {
                            SharedPreferencesUtil.putString("cookies", cookies);
                            String mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies);
                            if (mid != null && mid.length() > 0) {
                                try {
                                    SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                                } catch (NumberFormatException e) {
                                }
                            }
                            NetWorkUtil.refreshHeaders();
                            Toast.makeText(SpecialLoginActivity.this, SpecialLoginActivity.this.getString(R.string.specialloginactivity_toast_5bfc), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(SpecialLoginActivity.this, SpecialLoginActivity.this.getString(R.string.specialloginactivity_toast_683c), Toast.LENGTH_SHORT).show();
                    }
                }
            });

            refuseBtn.setVisibility(View.GONE);

            copyBtn.setVisibility(View.VISIBLE);
            copyBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setText(textInput.getText().toString());
                    Toast.makeText(SpecialLoginActivity.this, SpecialLoginActivity.this.getString(R.string.specialloginactivity_toast_5df2), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void saveUserName() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String response = NetWorkUtil.get("https://api.bilibili.com/x/web-interface/nav");
                    if (response == null || response.length() == 0) {
                        return;
                    }
                    JSONObject json = new JSONObject(response);
                    if (json.optInt("code") == 0) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            String uname = data.optString("uname", "");
                            if (uname != null && uname.length() > 0) {
                                SharedPreferencesUtil.putString("uname", uname);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // ===== 遥控器按键导航：方向键在按钮间移动（选中变暗），确认键触发 =====
    private final java.util.List<View> mNavButtons = new java.util.ArrayList<View>();
    private int mNavIndex = -1;

    private void rebuildNavButtons() {
        mNavButtons.clear();
        if (confirmBtn != null && confirmBtn.getVisibility() == View.VISIBLE) mNavButtons.add(confirmBtn);
        if (refuseBtn != null && refuseBtn.getVisibility() == View.VISIBLE) mNavButtons.add(refuseBtn);
        if (copyBtn != null && copyBtn.getVisibility() == View.VISIBLE) mNavButtons.add(copyBtn);
        if (mNavButtons.size() > 0 && mNavIndex < 0) {
            mNavIndex = 0;
        }
        if (mNavIndex >= mNavButtons.size()) {
            mNavIndex = mNavButtons.size() - 1;
        }
    }

    /** 按钮选中变暗：选中深粉，未选中恢复原背景色。 */
    private void applyNavHighlight() {
        for (int i = 0; i < mNavButtons.size(); i++) {
            View v = mNavButtons.get(i);
            if (v == null) continue;
            if (i == mNavIndex) {
                if (v.getId() == R.id.refuse) {
                    v.setBackgroundColor(0xFF6B6B6B);
                } else {
                    v.setBackgroundColor(0xFFC06090);
                }
            } else {
                if (v.getId() == R.id.refuse) {
                    v.setBackgroundColor(0xFF999999);
                } else {
                    v.setBackgroundColor(0xFFD86DA5);
                }
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
            if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                    || action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                    || action == tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT
                    || action == tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT
                    || action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
                if (mNavButtons.size() == 0) {
                    rebuildNavButtons();
                }
                if (mNavButtons.size() == 0) {
                    return super.dispatchKeyEvent(event);
                }
                if (mNavIndex < 0 || mNavIndex >= mNavButtons.size()) {
                    mNavIndex = 0;
                }
                if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP
                        || action == tv.biliclassic.util.KeyBindingUtil.ACTION_LEFT) {
                    mNavIndex = Math.max(0, mNavIndex - 1);
                    applyNavHighlight();
                    return true;
                } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN
                        || action == tv.biliclassic.util.KeyBindingUtil.ACTION_RIGHT) {
                    mNavIndex = Math.min(mNavButtons.size() - 1, mNavIndex + 1);
                    applyNavHighlight();
                    return true;
                } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
                    View v = mNavButtons.get(mNavIndex);
                    if (v != null) {
                        v.performClick();
                    }
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
}