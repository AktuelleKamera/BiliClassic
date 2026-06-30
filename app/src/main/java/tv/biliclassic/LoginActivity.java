package tv.biliclassic;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import tv.biliclassic.tv.util.TvUtil;

public class LoginActivity extends FragmentActivity {

    private LinearLayout loadingContainer;
    private FrameLayout fragmentContainer;
    private Handler handler = new Handler();
    private boolean fragmentAdded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TV 模式：强制横屏 + 全屏
        if (TvUtil.isTv(this)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setContentView(R.layout.activity_login);

        loadingContainer = (LinearLayout) findViewById(R.id.loading_container);
        fragmentContainer = (FrameLayout) findViewById(R.id.fragment_container);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 如果已经有 Fragment 实例，直接显示
        Fragment existingFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (existingFragment != null) {
            loadingContainer.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            fragmentAdded = true;
            return;
        }

        // savedInstanceState 不为 null，等待系统恢复
        if (savedInstanceState != null) {
            loadingContainer.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            fragmentAdded = true;
            return;
        }

        loadingContainer.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);

        final boolean fromSetup = getIntent().getBooleanExtra("from_setup", false);

        // 直接在 onCreate 中加载，不用延迟
        loadingContainer.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.fragment_container, QRLoginFragment.newInstance(fromSetup))
                .commit();

        fragmentAdded = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}