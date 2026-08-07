package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import tv.biliclassic.util.KeyBindingUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 按键绑定向导。
 *
 * 两种入口：
 *  - 首次启动（mode=first）：询问页→录制页→进入主界面
 *  - 设置页重绑定（mode=rebind）：直接进入录制页→返回设置页
 *
 * 本页完全独立于 SetupActivity 的磁贴流程。
 */
public class KeyBindingSetupActivity extends BaseActivity {

    private View mPageAsk;
    private View mPageRecord;
    private TextView mPromptText;
    private TextView mProgressText;

    // 防抖：记录上一次成功录入的时间（毫秒），1 秒内忽略后续按键
    private long mLastRecordTime = 0L;

    // 录制顺序：软左→软右→上→下→左→右→确认→数字0..9→*→#
    private static final int[] RECORD_ORDER = {
        KeyBindingUtil.ACTION_SOFT_LEFT,
        KeyBindingUtil.ACTION_SOFT_RIGHT,
        KeyBindingUtil.ACTION_UP,
        KeyBindingUtil.ACTION_DOWN,
        KeyBindingUtil.ACTION_LEFT,
        KeyBindingUtil.ACTION_RIGHT,
        KeyBindingUtil.ACTION_CONFIRM,
        KeyBindingUtil.ACTION_NUM_0,
        KeyBindingUtil.ACTION_NUM_1,
        KeyBindingUtil.ACTION_NUM_2,
        KeyBindingUtil.ACTION_NUM_3,
        KeyBindingUtil.ACTION_NUM_4,
        KeyBindingUtil.ACTION_NUM_5,
        KeyBindingUtil.ACTION_NUM_6,
        KeyBindingUtil.ACTION_NUM_7,
        KeyBindingUtil.ACTION_NUM_8,
        KeyBindingUtil.ACTION_NUM_9,
        KeyBindingUtil.ACTION_STAR,
        KeyBindingUtil.ACTION_POUND
    };

    private static final String[] RECORD_NAMES = {
        "左软键", "右软键", "上方向键", "下方向键",
        "左方向键", "右方向键", "确认键",
        "数字键 0", "数字键 1", "数字键 2", "数字键 3", "数字键 4",
        "数字键 5", "数字键 6", "数字键 7", "数字键 8", "数字键 9",
        "* 星号键", "# 井号键"
    };

    private int mRecordIndex = 0;
    private boolean mRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_binding_setup);

        mPageAsk = findViewById(R.id.page_ask);
        mPageRecord = findViewById(R.id.page_record);
        mPromptText = (TextView) findViewById(R.id.prompt_text);
        mProgressText = (TextView) findViewById(R.id.progress_text);

        String mode = getIntent().getStringExtra("mode");
        final boolean isRebind = "rebind".equals(mode);

        TextView btnYes = (TextView) findViewById(R.id.btn_yes);
        btnYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });

        TextView btnNo = (TextView) findViewById(R.id.btn_no);
        btnNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 跳过：保存 setup_shown，使用系统默认键位
                SharedPreferencesUtil.putBoolean("setup_shown", true);
                saveVersion();
                toast(getString(R.string.keybinding_toast_skip));
                enterMain();
            }
        });

        TextView btnExit = (TextView) findViewById(R.id.btn_exit);
        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 退出：保存已录按键并标记 setup_shown，避免死循环
                if (isRebind) {
                    // 重绑定模式下直接返回设置页
                    finish();
                    return;
                }
                SharedPreferencesUtil.putBoolean("setup_shown", true);
                saveVersion();
                toast(getString(R.string.keybinding_toast_exit));
                enterMain();
            }
        });

        if (isRebind) {
            // 从设置进入：先清空旧绑定，直接进入录制页
            KeyBindingUtil.clearAll();
            mPageAsk.setVisibility(View.GONE);
            mPageRecord.setVisibility(View.VISIBLE);
            startRecording();
        } else {
            // 首次启动：先询问
            mPageAsk.setVisibility(View.VISIBLE);
            mPageRecord.setVisibility(View.GONE);
        }
    }

    private void startRecording() {
        mRecording = true;
        mRecordIndex = 0;
        mLastRecordTime = 0L; // 重置防抖计时，第一个键立即可录
        KeyBindingUtil.clearAll();
        mPageAsk.setVisibility(View.GONE);
        mPageRecord.setVisibility(View.VISIBLE);
        updatePrompt();
    }

    private void updatePrompt() {
        if (mRecordIndex >= RECORD_ORDER.length) {
            // 录制完成
            mPromptText.setText(getString(R.string.keybinding_done));
            mProgressText.setText("");
            finishSetup();
            return;
        }
        String keyName = RECORD_NAMES[mRecordIndex];
        mPromptText.setText(getString(R.string.keybinding_prompt_prefix) + " " + keyName);
        mProgressText.setText(getString(R.string.keybinding_progress,
                mRecordIndex + 1, RECORD_ORDER.length));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // 录制页：任何一个按键都只被录入，不参与界面操作。
        // 使用 dispatchKeyEvent（在 view 焦点分发之前）确保 DPAD 方向键也能被捕获，
        // 而不是被系统的焦点遍历消费掉。
        // 防抖：每次录入只取第一个捕获的 keycode；录入完成一个键后，1 秒内
        // 再按下的键一律忽略（消费但不录入），防止连续快速按键录错。
        if (mRecording && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0 && mRecordIndex < RECORD_ORDER.length) {
            long now = System.currentTimeMillis();
            if (now - mLastRecordTime < 1000L) {
                // 距上次录入不足 1 秒：忽略本次按键，等待下一次
                return true;
            }
            mLastRecordTime = now;
            int action = RECORD_ORDER[mRecordIndex];
            KeyBindingUtil.saveKey(action, event.getKeyCode());
            mRecordIndex++;
            updatePrompt();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // 询问页允许返回（等同跳过）；录制页不允许用返回键退出（BACK 可能被绑定），
        // 退出走顶部的触摸"退出"按钮
        if (mRecording) {
            return;
        }
        super.onBackPressed();
    }

    private void finishSetup() {
        boolean isRebind = "rebind".equals(getIntent().getStringExtra("mode"));
        SharedPreferencesUtil.putBoolean("setup_shown", true);
        saveVersion();
        toast(getString(R.string.keybinding_toast_done));
        if (isRebind) {
            finish();
        } else {
            enterMain();
        }
    }

    private void saveVersion() {
        try {
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            SharedPreferencesUtil.putInt("last_version_code", versionCode);
        } catch (Exception e) {
            // 忽略
        }
    }

    private void enterMain() {
        Intent intent = new Intent(KeyBindingSetupActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
