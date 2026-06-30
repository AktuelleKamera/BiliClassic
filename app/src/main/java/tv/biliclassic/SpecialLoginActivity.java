package tv.biliclassic;

import android.text.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.ActivityInfo;

import org.json.JSONException;
import org.json.JSONObject;

import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.CookieHelper;
import tv.biliclassic.tv.util.TvUtil;

public class SpecialLoginActivity extends FragmentActivity {

    private EditText textInput;
    private Button confirmBtn;
    private Button refuseBtn;
    private Button copyBtn;
    private TextView descText;
    private TextView hintText;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TV 模式：强制横屏 + 全屏
        if (TvUtil.isTv(this)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

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
            descText.setText("请粘贴登录信息");
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
                        Toast.makeText(SpecialLoginActivity.this, "请输入内容", Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(SpecialLoginActivity.this,
                                        "未找到有效的登录凭证 (SESSDATA)", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        } catch (JSONException e) {
                            Toast.makeText(SpecialLoginActivity.this,
                                    "无法解析输入，请检查格式是否正确", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    if (cookies == null || cookies.length() == 0) {
                        Toast.makeText(SpecialLoginActivity.this,
                                "未找到有效的登录凭证 (SESSDATA)", Toast.LENGTH_SHORT).show();
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

                    Toast.makeText(SpecialLoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(SpecialLoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            });

            copyBtn.setVisibility(View.GONE);

        } else {
            descText.setText("当前登录信息：");
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

            confirmBtn.setText("导入");
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
                            Toast.makeText(SpecialLoginActivity.this, "导入成功", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(SpecialLoginActivity.this, "JSON格式错误", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            refuseBtn.setVisibility(View.GONE);

            copyBtn.setVisibility(View.VISIBLE);
            copyBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setText(textInput.getText().toString());
                    Toast.makeText(SpecialLoginActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
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
}