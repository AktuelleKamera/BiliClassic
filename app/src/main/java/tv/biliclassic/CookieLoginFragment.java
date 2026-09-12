package tv.biliclassic;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;

import tv.biliclassic.util.CookieHelper;
import tv.biliclassic.util.KeyBindingUtil;
import tv.biliclassic.util.LoginHelper;
import tv.biliclassic.util.MsgUtil;

/**
 * Cookie 登录：粘贴浏览器登录后的 Cookie（或 JSON），解析后保存登录。
 * 逻辑移植自 BiliMD LoginActivity.showManual。
 */
public class CookieLoginFragment extends Fragment {

    private EditText input;
    private Button saveBtn;
    private Button clearBtn;

    private final ArrayList<View> mNavViews = new ArrayList<View>();
    private int mNavIndex = -1;

    public CookieLoginFragment() {
    }

    public static CookieLoginFragment newInstance(boolean fromSetup) {
        Bundle args = new Bundle();
        args.putBoolean("from_setup", fromSetup);
        CookieLoginFragment f = new CookieLoginFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cookie_login, container, false);
        input = (EditText) view.findViewById(R.id.cookie_input);
        saveBtn = (Button) view.findViewById(R.id.cookie_save);
        clearBtn = (Button) view.findViewById(R.id.cookie_clear);

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSave();
            }
        });

        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
                input.requestFocus();
            }
        });

        rebuildNavViews();
        return view;
    }

    private void doSave() {
        String raw = input.getText() == null ? "" : input.getText().toString().trim();
        if (raw.length() == 0) {
            Toast.makeText(getActivity(), "Cookie 不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1) 智能解析（Cookie 字符串 / JSON / 任意含 SESSDATA 的文本）
        String cookies = CookieHelper.parseAndBuildCookie(raw);
        // 2) JSON 兜底：{"cookies":"..."}
        if (cookies == null || cookies.length() == 0) {
            try {
                JSONObject json = new JSONObject(raw);
                cookies = json.optString("cookies", "");
            } catch (Throwable ignored) {
            }
        }
        if (cookies == null || !LoginHelper.hasValidCookie(cookies)) {
            Toast.makeText(getActivity(), "未找到有效登录凭证（需包含 SESSDATA 与 bili_jct）", Toast.LENGTH_LONG).show();
            return;
        }

        final String finalCookie = cookies;
        LoginHelper.verifyThenSave(getActivity(), finalCookie, new LoginHelper.Callback() {
            @Override
            public void onResult(boolean ok, String msg) {
                if (isAdded() && getActivity() != null) {
                    if (ok) {
                        MsgUtil.showMsg(getActivity(), "登录成功");
                        getActivity().setResult(Activity.RESULT_OK);
                        getActivity().finish();
                    } else {
                        Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    // ===== 遥控器按键导航 =====

    private void rebuildNavViews() {
        mNavViews.clear();
        if (input != null) mNavViews.add(input);
        if (saveBtn != null) mNavViews.add(saveBtn);
        if (clearBtn != null) mNavViews.add(clearBtn);
        if (mNavViews.size() > 0 && mNavIndex < 0) {
            mNavIndex = 0;
        }
        if (mNavIndex >= mNavViews.size()) {
            mNavIndex = mNavViews.size() - 1;
        }
    }

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
            } else if (v == saveBtn || v == clearBtn) {
                // Holo 按钮：按下态高亮（selector 的 state_pressed）
                v.setPressed(i == mNavIndex);
            }
        }
    }
}
