/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年6月19日
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic;

import android.text.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.ActivityInfo;

import org.json.JSONException;
import org.json.JSONObject;

import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class SpecialLoginActivity extends FragmentActivity {

    private EditText textInput;
    private Button confirmBtn;
    private Button refuseBtn;
    private Button copyBtn;
    private TextView descText;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_special_login);

        textInput = (EditText) findViewById(R.id.loginInput);
        confirmBtn = (Button) findViewById(R.id.confirm);
        refuseBtn = (Button) findViewById(R.id.refuse);
        copyBtn = (Button) findViewById(R.id.copy);
        descText = (TextView) findViewById(R.id.desc);

        final boolean fromSetup = getIntent().getBooleanExtra("from_setup", false);
        final boolean isLoginMode = getIntent().getBooleanExtra("login", true);

        if (isLoginMode) {
            descText.setText("请输入JSON格式的登录信息：");

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
                    String loginInfo = textInput.getText().toString().trim();
                    if (loginInfo == null || loginInfo.length() == 0) {
                        Toast.makeText(SpecialLoginActivity.this, "请输入内容", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        JSONObject json = new JSONObject(loginInfo);
                        String cookies = json.optString("cookies", "");
                        String refreshToken = json.optString("refresh_token", "");

                        if (cookies == null || cookies.length() == 0) {
                            Toast.makeText(SpecialLoginActivity.this, "cookies 不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies);
                        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);

                        if (mid != null && mid.length() > 0) {
                            try {
                                SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid));
                            } catch (NumberFormatException e) {
                            }
                        }
                        if (csrf != null && csrf.length() > 0) {
                            SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, csrf);
                        }
                        SharedPreferencesUtil.putString("cookies", cookies);
                        if (refreshToken != null && refreshToken.length() > 0) {
                            SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshToken);
                        }

                        NetWorkUtil.refreshHeaders();
                        saveUserName();

                        Toast.makeText(SpecialLoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(SpecialLoginActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();

                    } catch (JSONException e) {
                        Toast.makeText(SpecialLoginActivity.this, "JSON格式错误，请检查输入", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            copyBtn.setVisibility(View.GONE);

        } else {
            descText.setText("当前登录信息（JSON格式）：");

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
                    // 使用 Android 2.1 兼容的 ClipboardManager
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