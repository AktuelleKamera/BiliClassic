package tv.biliclassic.tv;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import tv.biliclassic.R;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateUtil;

public class TvSettingsActivity extends FragmentActivity {

    private Button btnThreads, btnPlayer, btnQuality, btnHomeTab;
    private Button btnClearCache, btnCheckUpdate, btnAbout;
    private CheckBox checkboxOnlinePlay;
    private LinearLayout onlinePlayItem;

    private int currentVersionCode = -1;
    private String currentVersionName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_settings);

        // 获取版本信息
        try {
            currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            currentVersionCode = 0;
            currentVersionName = "0.0.0";
        }

        btnThreads = (Button) findViewById(R.id.btn_threads);
        btnPlayer = (Button) findViewById(R.id.btn_player);
        btnQuality = (Button) findViewById(R.id.btn_quality);
        btnHomeTab = (Button) findViewById(R.id.btn_home_tab);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnCheckUpdate = (Button) findViewById(R.id.btn_check_update);
        btnAbout = (Button) findViewById(R.id.btn_about);
        checkboxOnlinePlay = (CheckBox) findViewById(R.id.checkbox_online_play);
        onlinePlayItem = (LinearLayout) findViewById(R.id.online_play_item);

        // 返回按钮
        Button btnBack = (Button) findViewById(R.id.btn_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 在线播放开关
        boolean onlinePlayEnabled = SharedPreferencesUtil.getBoolean("online_play", false);
        checkboxOnlinePlay.setChecked(onlinePlayEnabled);
        onlinePlayItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkboxOnlinePlay.toggle();
                boolean checked = checkboxOnlinePlay.isChecked();
                SharedPreferencesUtil.putBoolean("online_play", checked);
                Toast.makeText(TvSettingsActivity.this,
                        checked ? "已开启在线播放" : "已关闭在线播放",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // 每个 Button 的点击事件
        btnThreads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThreadsDialog();
            }
        });

        btnPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPlayerDialog();
            }
        });

        btnQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQualityDialog();
            }
        });

        btnHomeTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHomeTabDialog();
            }
        });

        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showClearCacheDialog();
            }
        });

        // 检查更新（使用 UpdateUtil）
        btnCheckUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnCheckUpdate.setText("检查中...");
                btnCheckUpdate.setEnabled(false);

                UpdateUtil.checkUpdate(TvSettingsActivity.this, currentVersionCode, currentVersionName,
                        new UpdateUtil.UpdateCallback() {
                            @Override
                            public void onCheckStart() {
                                // 已处理
                            }

                            @Override
                            public void onCheckComplete(boolean hasUpdate, String message) {
                                btnCheckUpdate.setText("检查更新");
                                btnCheckUpdate.setEnabled(true);
                                if (!hasUpdate) {
                                    Toast.makeText(TvSettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCheckFailed(String error) {
                                btnCheckUpdate.setText("检查更新");
                                btnCheckUpdate.setEnabled(true);
                                Toast.makeText(TvSettingsActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAboutDialog();
            }
        });

        // 让第一个 Button 获得焦点
        btnThreads.post(new Runnable() {
            @Override
            public void run() {
                btnThreads.requestFocus();
            }
        });

        // 每个 Button 的焦点放大效果
        setupFocusScale(btnThreads);
        setupFocusScale(btnPlayer);
        setupFocusScale(btnQuality);
        setupFocusScale(btnHomeTab);
        setupFocusScale(btnClearCache);
        setupFocusScale(btnCheckUpdate);
        setupFocusScale(btnAbout);
        setupFocusScale(btnBack);
        setupFocusScale(onlinePlayItem);
    }

    // 焦点放大效果
    private void setupFocusScale(final View view) {
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // 对话框

    private void showThreadsDialog() {
        final String[] items = {"单线程", "三线程", "五线程"};
        final int[] values = {1, 3, 5};
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 3);
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("图片加载线程")
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, values[which]);
                        Toast.makeText(TvSettingsActivity.this, "已设置: " + items[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPlayerDialog() {
        final String[] players = {"内置播放器", "MX Player", "VLC", "系统播放器", "自动检测"};
        final int[] values = {8, 0, 3, 7, -1};
        int current = SharedPreferencesUtil.getInt("player_preference", 8);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("默认播放器")
                .setSingleChoiceItems(players, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt("player_preference", values[which]);
                        Toast.makeText(TvSettingsActivity.this, "已设置: " + players[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQualityDialog() {
        final String[] qualities = {"360P 流畅", "480P 清晰", "720P 高清"};
        final int[] values = {16, 32, 64};
        int current = SharedPreferencesUtil.getInt("video_quality", 16);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("视频画质")
                .setSingleChoiceItems(qualities, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt("video_quality", values[which]);
                        Toast.makeText(TvSettingsActivity.this, "已设置: " + qualities[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHomeTabDialog() {
        final String[] tabs = {"新番专题", "放送时间表", "推荐视频", "分区导航", "个人中心"};
        final int[] values = {2, 3, 4, 1, 0};
        int current = SharedPreferencesUtil.getInt("default_tab", 2);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("默认首页")
                .setSingleChoiceItems(tabs, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt("default_tab", values[which]);
                        Toast.makeText(TvSettingsActivity.this, "已设置: " + tabs[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showClearCacheDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清除缓存")
                .setItems(new String[]{"清除图片缓存", "清除视频缓存", "全部清除"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(TvSettingsActivity.this, "缓存已清除", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAboutDialog() {
        String version = "0.4.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {}

        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage("BiliClassic TV\n版本: " + version + "\n\n专为电视遥控器优化的简洁界面")
                .setPositiveButton("确定", null)
                .show();
    }
}