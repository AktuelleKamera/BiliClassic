package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import tv.biliclassic.util.KeyBindingRecorder;
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
    private KeyBindingRecorder mRecorder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_binding_setup);

        mPageAsk = findViewById(R.id.page_ask);
        mPageRecord = findViewById(R.id.page_record);
        mPromptText = (TextView) findViewById(R.id.prompt_text);
        mProgressText = (TextView) findViewById(R.id.progress_text);
        mRecorder = new KeyBindingRecorder(new KeyBindingRecorder.Listener() {
            @Override
            public void onRecordingChanged() {
                updatePrompt();
            }

            @Override
            public void onFinished() {
                updatePrompt();
            }
        });

        // 右下角小电视水印：与 MetroHome / Setup 完全一致的尺寸算法（70% 屏幕长边 + 12% 溢出）
        ImageView wm = (ImageView) findViewById(R.id.tv_watermark);
        if (wm != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int longSidePx = Math.max(dm.widthPixels, dm.heightPixels);
            int size = Math.round(longSidePx * 0.70f);
            int over = Math.round(longSidePx * 0.12f);
            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) wm.getLayoutParams();
            lp.width = size;
            lp.height = size;
            lp.rightMargin = -over;
            lp.bottomMargin = -over;
            wm.setLayoutParams(lp);
        }

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
            mPageAsk.setVisibility(View.GONE);
            mPageRecord.setVisibility(View.VISIBLE);
            startRecording();
        } else {
            // 首次启动：先询问
            mPageAsk.setVisibility(View.VISIBLE);
            mPageRecord.setVisibility(View.GONE);
        }
    }

    private boolean isRebindMode() {
        return "rebind".equals(getIntent().getStringExtra("mode"));
    }

    private void startRecording() {
        mPageAsk.setVisibility(View.GONE);
        mPageRecord.setVisibility(View.VISIBLE);
        mRecorder.start();
    }

    private void updatePrompt() {
        if (mRecorder.isFinished()) {
            mPromptText.setText(getString(R.string.keybinding_done));
            mProgressText.setText("");
            finishSetup();
            return;
        }
        mPromptText.setText(getString(R.string.keybinding_prompt_prefix) + " "
                + mRecorder.getCurrentName());
        mProgressText.setText(getString(R.string.keybinding_progress,
                mRecorder.getIndex() + 1, mRecorder.getTotal()));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mRecorder == null) return super.dispatchKeyEvent(event);
        int result = mRecorder.handleKeyEvent(event);
        if (result == KeyBindingRecorder.RESULT_ABORT) {
            if (isRebindMode()) {
                finish();
            } else {
                SharedPreferencesUtil.putBoolean("setup_shown", true);
                saveVersion();
                toast(getString(R.string.keybinding_toast_skip));
                enterMain();
            }
            return true;
        }
        if (result != KeyBindingRecorder.RESULT_NOT_HANDLED) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (!mRecorder.isRecording()) {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
