package tv.biliclassic;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

public class LoginActivity extends FragmentActivity {

    private LinearLayout loadingContainer;
    private FrameLayout fragmentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        loadingContainer = (LinearLayout) findViewById(R.id.loading_container);
        fragmentContainer = (FrameLayout) findViewById(R.id.fragment_container);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        loadingContainer.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);

        if (savedInstanceState == null) {
            final boolean fromSetup = getIntent().getBooleanExtra("from_setup", false);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadingContainer.setVisibility(View.GONE);
                    fragmentContainer.setVisibility(View.VISIBLE);

                    getSupportFragmentManager()
                            .beginTransaction()
                            .add(R.id.fragment_container, QRLoginFragment.newInstance(fromSetup))
                            .commit();
                }
            }, 500);
        } else {
            loadingContainer.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
        }
    }
}