package tv.biliclassic.tv;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import tv.biliclassic.HistoryActivity;
import tv.biliclassic.LoginActivity;
import tv.biliclassic.MainActivity;
import tv.biliclassic.R;
import tv.biliclassic.SearchActivity;
import tv.biliclassic.tv.adapter.TvGridAdapter;

public class TvMainActivity extends FragmentActivity {

    private GridView gridView;
    private TvGridAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        gridView = (GridView) findViewById(R.id.grid_tiles);
        adapter = new TvGridAdapter(this);
        gridView.setAdapter(adapter);

        // 注册点击监听
        adapter.setOnTileClickListener(new TvGridAdapter.OnTileClickListener() {
            @Override
            public void onTileClick(String label) {
                handleTileClick(label);
            }
        });

        // OnItemClickListener 备用
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String label = adapter.getItem(position);
                handleTileClick(label);
            }
        });

        // OnKeyListener 拦截 ENTER
        gridView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
                    int pos = gridView.getSelectedItemPosition();
                    if (pos >= 0) {
                        String label = adapter.getItem(pos);
                        handleTileClick(label);
                        return true;
                    }
                }
                return false;
            }
        });

        // 设置入口
        TextView tvSettings = (TextView) findViewById(R.id.tv_settings);
        tvSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TvMainActivity.this, TvSettingsActivity.class));
            }
        });

        // 让第一个磁贴获得焦点
        gridView.post(new Runnable() {
            @Override
            public void run() {
                if (gridView.getChildCount() > 0) {
                    gridView.getChildAt(0).requestFocus();
                }
            }
        });
    }

    private void handleTileClick(String label) {
        if (label == null) return;

        // 加 Toast 确认点击触发
        Toast.makeText(this, "点击: " + label, Toast.LENGTH_SHORT).show();

        if (label.equals("登录")) {
            startActivity(new Intent(this, LoginActivity.class));
        } else if (label.equals("推荐")) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("tab_index", 4);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } else if (label.equals("时间线")) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("tab_index", 3);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } else if (label.equals("分区")) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("tab_index", 1);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } else if (label.equals("搜索")) {
            startActivity(new Intent(this, SearchActivity.class));
        } else if (label.equals("历史")) {
            startActivity(new Intent(this, HistoryActivity.class));
        } else if (label.equals("设置")) {
            startActivity(new Intent(this, TvSettingsActivity.class));
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
}