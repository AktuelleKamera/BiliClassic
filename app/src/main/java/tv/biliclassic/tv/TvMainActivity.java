package tv.biliclassic.tv;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;  // 改用 support v4

import tv.biliclassic.R;

public class TvMainActivity extends FragmentActivity {  // 改为继承 FragmentActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()  // 用 getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.tv_content, new TvHomeFragment())
                    .commit();
        }
    }
}