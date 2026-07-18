package tv.biliclassic.tv;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import tv.biliclassic.FavoriteFolderListActivity;
import tv.biliclassic.HistoryActivity;
import tv.biliclassic.LoginActivity;
import tv.biliclassic.MainActivity;
import tv.biliclassic.MetroHomeActivity;
import tv.biliclassic.R;
import tv.biliclassic.SearchActivity;
import tv.biliclassic.tv.adapter.TvGridAdapter;

public class TvMainActivity extends FragmentActivity {

    private GridView gridView;
    private TvGridAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int sdkInt = getSdkInt();
        if (sdkInt < 14) {
            Toast.makeText(this, "TV模式需要 Android 4.0 及以上系统", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_tv_main);

        gridView = (GridView) findViewById(R.id.grid_tiles);
        adapter = new TvGridAdapter(this);
        gridView.setAdapter(adapter);

        // 设置 rootContainer
        FrameLayout rootContainer = (FrameLayout) findViewById(R.id.root_container);
        adapter.setRootContainer(rootContainer);

        adapter.setOnTileClickListener(new TvGridAdapter.OnTileClickListener() {
            @Override
            public void onTileClick(String label) {
                handleTileClick(label);
            }
        });

        TextView tvSettings = (TextView) findViewById(R.id.tv_settings);
        if (tvSettings != null) {
            tvSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(TvMainActivity.this, TvSettingsActivity.class));
                }
            });
        }

        gridView.post(new Runnable() {
            @Override
            public void run() {
                if (gridView.getChildCount() > 0) {
                    gridView.getChildAt(0).requestFocus();
                }
            }
        });
    }

    private int getSdkInt() {
        try {
            java.lang.reflect.Field field = android.os.Build.VERSION.class.getField("SDK_INT");
            return field.getInt(null);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = android.os.Build.VERSION.class.getField("SDK");
                return Integer.parseInt(field.get(null).toString());
            } catch (Exception ex) {
                return 0;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gridView != null) {
            gridView.post(new Runnable() {
                @Override
                public void run() {
                    gridView.requestFocus();
                    if (gridView.getChildCount() > 0) {
                        gridView.getChildAt(0).requestFocus();
                    }
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handleTileClick(String label) {
        if (label == null) return;

        if (label.equals("登录")) {
            startActivity(new Intent(this, LoginActivity.class));
        } else if (label.equals("推荐")) {
            startActivity(new Intent(this, MetroHomeActivity.class));
        } else if (label.equals("时间线")) {
            startActivity(new Intent(this, MainActivity.class));
        } else if (label.equals("收藏")) {
            startActivity(new Intent(this, FavoriteFolderListActivity.class));
        } else if (label.equals("搜索")) {
            startActivity(new Intent(this, SearchActivity.class));
        } else if (label.equals("历史")) {
            startActivity(new Intent(this, HistoryActivity.class));
        } else if (label.equals("设置")) {
            startActivity(new Intent(this, TvSettingsActivity.class));
        }
    }
}