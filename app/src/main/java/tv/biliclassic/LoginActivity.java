package tv.biliclassic;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class LoginActivity extends BaseActivity {

    private LinearLayout loadingContainer;
    private FrameLayout fragmentContainer;
    private Handler handler = new Handler();
    private boolean fragmentAdded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);
        initRoundTitleBar();

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

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof QRLoginFragment) {
            if (((QRLoginFragment) f).handleRemoteKey(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}