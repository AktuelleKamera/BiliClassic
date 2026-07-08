package tv.biliclassic.tv;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.text.ClipboardManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import tv.biliclassic.DeviceInfoActivity;
import tv.biliclassic.R;
import tv.biliclassic.subsettings.DecoderSettingsActivity;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateUtil;

public class TvSettingsActivity extends FragmentActivity {

    private Button btnThreads, btnPlayer, btnDecoder, btnDecoderSettings, btnRendererType, btnDanmakuEngine;
    private Button btnQuality, btnHomeTab, btnCookie, btnClearCache, btnClearPlayCache, btnCrashLog;
    private Button btnEchoHole, btnDeviceInfo, btnCheckUpdate, btnAbout;
    private CheckBox checkboxOnlinePlay, checkboxLandscape, checkboxModernMode;
    private LinearLayout onlinePlayItem, landscapeItem, modernModeItem;

    private int currentVersionCode = -1;
    private String currentVersionName = "";
    private int mLastEchoIndex = -1;

    private static final int DECODER_SYSTEM = 0;
    private static final int DECODER_IJK_HARD = 1;
    private static final int DECODER_IJK_SOFT = 2;
    private static final int MIN_SDK_FOR_IJK_HARDWARE = 16;
    private static final int QUALITY_360P = 16;
    private static final int QUALITY_480P = 32;
    private static final int QUALITY_720P = 64;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_settings);

        try {
            currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            currentVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            currentVersionCode = 0;
            currentVersionName = "0.0.0";
        }

        btnThreads = (Button) findViewById(R.id.btn_threads);
        btnPlayer = (Button) findViewById(R.id.btn_player);
        btnDecoder = (Button) findViewById(R.id.btn_decoder);
        btnDecoderSettings = (Button) findViewById(R.id.btn_decoder_settings);
        btnRendererType = (Button) findViewById(R.id.btn_renderer_type);
        btnDanmakuEngine = (Button) findViewById(R.id.btn_danmaku_engine);
        btnQuality = (Button) findViewById(R.id.btn_quality);
        btnHomeTab = (Button) findViewById(R.id.btn_home_tab);
        btnCookie = (Button) findViewById(R.id.btn_cookie);
        btnClearCache = (Button) findViewById(R.id.btn_clear_cache);
        btnClearPlayCache = (Button) findViewById(R.id.btn_clear_play_cache);
        btnCrashLog = (Button) findViewById(R.id.btn_crash_log);
        btnEchoHole = (Button) findViewById(R.id.btn_echo_hole);
        btnDeviceInfo = (Button) findViewById(R.id.btn_device_info);
        btnCheckUpdate = (Button) findViewById(R.id.btn_check_update);
        btnAbout = (Button) findViewById(R.id.btn_about);
        checkboxOnlinePlay = (CheckBox) findViewById(R.id.checkbox_online_play);
        onlinePlayItem = (LinearLayout) findViewById(R.id.online_play_item);
        checkboxLandscape = (CheckBox) findViewById(R.id.checkbox_landscape);
        landscapeItem = (LinearLayout) findViewById(R.id.landscape_item);
        checkboxModernMode = (CheckBox) findViewById(R.id.checkbox_modern_mode);
        modernModeItem = (LinearLayout) findViewById(R.id.modern_mode_item);

        Button btnBack = (Button) findViewById(R.id.btn_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 渲染方式 - API < 14 隐藏
        if (Build.VERSION.SDK_INT < 14) {
            btnRendererType.setVisibility(View.GONE);
        }

        // 在线播放
        boolean onlinePlay = SharedPreferencesUtil.getBoolean("online_play", false);
        checkboxOnlinePlay.setChecked(onlinePlay);
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

        // 横屏适配
        boolean landscape = SharedPreferencesUtil.getBoolean("landscape_enabled", true);
        checkboxLandscape.setChecked(landscape);
        landscapeItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkboxLandscape.toggle();
                boolean checked = checkboxLandscape.isChecked();
                SharedPreferencesUtil.putBoolean("landscape_enabled", checked);
                Toast.makeText(TvSettingsActivity.this,
                        checked ? "已开启横屏模式" : "已关闭横屏模式",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // 现代模式
        boolean modernMode = SharedPreferencesUtil.getBoolean("modern_mode", false);
        checkboxModernMode.setChecked(modernMode);
        modernModeItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkboxModernMode.toggle();
                boolean checked = checkboxModernMode.isChecked();
                SharedPreferencesUtil.putBoolean("modern_mode", checked);
                Toast.makeText(TvSettingsActivity.this,
                        checked ? "已开启现代模式，重启后生效" : "已关闭现代模式，重启后生效",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // 各按钮点击事件
        btnThreads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showThreadsDialog(); }
        });
        btnPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showPlayerDialog(); }
        });
        btnDecoder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showDecoderDialog(); }
        });
        btnDecoderSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TvSettingsActivity.this, DecoderSettingsActivity.class));
            }
        });
        btnRendererType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showRendererTypeDialog(); }
        });
        btnDanmakuEngine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showDanmakuEngineDialog(); }
        });
        btnQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showQualityDialog(); }
        });
        btnHomeTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showHomeTabDialog(); }
        });
        btnCookie.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showCookieDialog(); }
        });
        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showClearImageCacheDialog(); }
        });
        btnClearPlayCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showClearPlayCacheDialog(); }
        });
        btnCrashLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showCrashLogDialog(); }
        });
        btnEchoHole.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { loadEchoHole(); }
        });
        btnDeviceInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TvSettingsActivity.this, DeviceInfoActivity.class));
            }
        });
        btnCheckUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { checkForUpdate(); }
        });
        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showAboutDialog(); }
        });

        // 焦点放大
        setupFocusScale(btnThreads);
        setupFocusScale(btnPlayer);
        setupFocusScale(btnDecoder);
        setupFocusScale(btnDecoderSettings);
        setupFocusScale(btnRendererType);
        setupFocusScale(btnDanmakuEngine);
        setupFocusScale(btnQuality);
        setupFocusScale(btnHomeTab);
        setupFocusScale(landscapeItem);
        setupFocusScale(modernModeItem);
        setupFocusScale(onlinePlayItem);
        setupFocusScale(btnCookie);
        setupFocusScale(btnClearCache);
        setupFocusScale(btnClearPlayCache);
        setupFocusScale(btnCrashLog);
        setupFocusScale(btnEchoHole);
        setupFocusScale(btnDeviceInfo);
        setupFocusScale(btnCheckUpdate);
        setupFocusScale(btnAbout);
        setupFocusScale(btnBack);

        btnThreads.post(new Runnable() {
            @Override
            public void run() { btnThreads.requestFocus(); }
        });
    }

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
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    // ---- 解码方式 ----

    private void showDecoderDialog() {
        final boolean hwSupported = Build.VERSION.SDK_INT >= MIN_SDK_FOR_IJK_HARDWARE;
        final String[] decoders;
        final int[] decoderValues;
        if (hwSupported) {
            decoders = new String[]{"系统解码器", "IJK 硬解", "IJK 软解"};
            decoderValues = new int[]{DECODER_SYSTEM, DECODER_IJK_HARD, DECODER_IJK_SOFT};
        } else {
            decoders = new String[]{"系统解码器", "IJK 软解"};
            decoderValues = new int[]{DECODER_SYSTEM, DECODER_IJK_SOFT};
        }

        int current = SharedPreferencesUtil.getInt("decoder_type", hwSupported ? DECODER_IJK_HARD : DECODER_IJK_SOFT);
        int checked = 0;
        for (int i = 0; i < decoderValues.length; i++) {
            if (decoderValues[i] == current) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("解码方式")
                .setSingleChoiceItems(decoders, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt("decoder_type", decoderValues[which]);
                        Toast.makeText(TvSettingsActivity.this, "已设置: " + decoders[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---- 视频渲染方式 ----

    private void showRendererTypeDialog() {
        final String[] modes = {"SurfaceView", "TextureView"};
        final int[] values = {0, 1};
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.RENDERER_TYPE, 0);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("视频渲染方式")
                .setSingleChoiceItems(modes, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.RENDERER_TYPE, values[which]);
                        Toast.makeText(TvSettingsActivity.this, "已设置: " + modes[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---- 弹幕引擎 ----

    private void showDanmakuEngineDialog() {
        final String[] modes = {"完整版（DanmakuFlameMaster）", "简易版（BT-5弹幕引擎）"};
        int current = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, 0);
        int checked = Math.min(current, 1);

        new AlertDialog.Builder(this)
                .setTitle("弹幕引擎")
                .setSingleChoiceItems(modes, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, which);
                        Toast.makeText(TvSettingsActivity.this, "已切换为: " + modes[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---- 图片线程 ----

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

    // ---- 播放器 ----

    private void showPlayerDialog() {
        final String[] allPlayers = {"内置播放器", "自动检测", "MX Player (免费版)", "MX Player (专业版)", "MoboPlayer", "VLC", "VPlayer", "RockPlaye Liter", "QQ影音", "系统播放器"};
        final int[] allValues = {8, -1, 0, 1, 2, 3, 4, 5, 6, 7};
        int current = SharedPreferencesUtil.getInt("player_preference", 8);
        java.util.ArrayList<String> filteredPlayers = new java.util.ArrayList<String>();
        java.util.ArrayList<Integer> filteredValues = new java.util.ArrayList<Integer>();
        boolean builtinSupported = Build.VERSION.SDK_INT >= 9;
        for (int i = 0; i < allPlayers.length; i++) {
            if (allValues[i] == 8 && !builtinSupported) continue;
            filteredPlayers.add(allPlayers[i]);
            filteredValues.add(Integer.valueOf(allValues[i]));
        }
        final String[] players = filteredPlayers.toArray(new String[filteredPlayers.size()]);
        final int[] playerValues = new int[filteredValues.size()];
        for (int i = 0; i < filteredValues.size(); i++) {
            playerValues[i] = ((Integer) filteredValues.get(i)).intValue();
        }
        int checked = 0;
        for (int i = 0; i < playerValues.length; i++) {
            if (playerValues[i] == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("默认播放器")
                .setSingleChoiceItems(players, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferencesUtil.putInt("player_preference", playerValues[which]);
                        Toast.makeText(TvSettingsActivity.this, "已设置: " + players[which], Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---- 画质 ----

    private void showQualityDialog() {
        final String[] qualities = {"流畅 360P", "清晰 480P", "高清 720P"};
        final int[] values = {QUALITY_360P, QUALITY_480P, QUALITY_720P};
        int current = SharedPreferencesUtil.getInt("video_quality", QUALITY_360P);
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

    // ---- 首页 ----

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

    // ---- Cookie 管理 ----

    private void showCookieDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cookie 管理")
                .setItems(new String[]{"保存到本地", "复制到剪切板", "从本地导入", "从剪切板导入"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: exportCookieToFile(); break;
                            case 1: exportCookieToClipboard(); break;
                            case 2: importCookieFromFile(); break;
                            case 3: importCookieFromClipboard(); break;
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean isLoggedIn() {
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        return mid != 0 && cookies != null && cookies.length() > 0;
    }

    private String getCookieJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("cookies", SharedPreferencesUtil.getString("cookies", ""));
            json.put("refresh_token", SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json.toString();
    }

    private File getCookieSaveFile() {
        File dir;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            dir = new File(Environment.getExternalStorageDirectory(), "BiliClassic");
        } else {
            dir = getFilesDir();
        }
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "cookie_backup.json");
    }

    private void exportCookieToFile() {
        if (!isLoggedIn()) { Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show(); return; }
        try {
            File file = getCookieSaveFile();
            java.io.FileWriter fw = new java.io.FileWriter(file);
            fw.write(getCookieJson());
            fw.close();
            Toast.makeText(this, "已保存到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportCookieToClipboard() {
        if (!isLoggedIn()) { Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show(); return; }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setText(getCookieJson());
            Toast.makeText(this, "已复制到剪切板", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void importCookieFromFile() {
        File file = getCookieSaveFile();
        if (!file.exists()) {
            Toast.makeText(this, "未找到备份文件: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            applyCookieJson(sb.toString());
        } catch (Exception e) {
            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importCookieFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        final String clipText = cm.getText() != null ? cm.getText().toString() : "";
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(clipText);
        input.setMinLines(3);

        new AlertDialog.Builder(this)
                .setTitle("粘贴 Cookie 内容")
                .setView(input)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyCookieJson(input.getText().toString().trim());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyCookieJson(String jsonStr) {
        if (jsonStr == null || jsonStr.length() == 0) {
            Toast.makeText(this, "内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject(jsonStr);
            String cookies = json.optString("cookies", "");
            if (cookies == null || cookies.length() == 0) {
                Toast.makeText(this, "无效的 Cookie 数据", Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferencesUtil.putString("cookies", cookies);
            String refreshToken = json.optString("refresh_token", "");
            if (refreshToken != null && refreshToken.length() > 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.refresh_token, refreshToken);
            }
            String mid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookies);
            if (mid != null && mid.length() > 0) {
                try { SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(mid)); } catch (NumberFormatException e) {}
            }
            String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);
            if (csrf != null && csrf.length() > 0) {
                SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, csrf);
            }
            NetWorkUtil.refreshHeaders();

            if (isLoggedIn()) {
                Toast.makeText(this, "导入成功，已登录", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "导入完成，但登录状态异常", Toast.LENGTH_LONG).show();
            }
        } catch (JSONException e) {
            Toast.makeText(this, "格式错误", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- 缓存 ----

    private void showClearImageCacheDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清除图片缓存")
                .setMessage("将清除头像缓存和番剧封面缓存")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearImageCache();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearImageCache() {
        int deleted = 0;
        try {
            File avatarFile;
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                avatarFile = new File(Environment.getExternalStorageDirectory(), "BiliClassic/avatar_cache/avatar_cache.jpg");
            } else {
                avatarFile = new File(getCacheDir(), "avatar_cache.jpg");
            }
            if (avatarFile.exists() && avatarFile.delete()) deleted++;

            File animeCacheDir = new File(getCacheDir(), "anime_cache");
            if (animeCacheDir.exists()) {
                deleteRecursive(animeCacheDir);
                deleted++;
            }
            Toast.makeText(this, "已清除 " + deleted + " 项图片缓存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "清除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showClearPlayCacheDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清除播放缓存")
                .setMessage("确定要清除所有视频缓存吗？")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearPlayCache();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearPlayCache() {
        try {
            File cacheDir;
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                cacheDir = new File(Environment.getExternalStorageDirectory(), "BiliClassic/cache");
            } else {
                cacheDir = getCacheDir();
            }
            if (cacheDir.exists()) {
                int count = 0;
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().endsWith(".mp4") && f.delete()) count++;
                    }
                }
                Toast.makeText(this, "已清除 " + count + " 个视频缓存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "无播放缓存", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "清除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        file.delete();
    }

    // ---- 崩溃日志 ----

    private File getCrashLogDir() {
        return new File(getFilesDir().getParentFile(), "crashlog");
    }

    private void showCrashLogDialog() {
        File crashDir = getCrashLogDir();
        if (crashDir == null || !crashDir.exists()) {
            Toast.makeText(this, "没有崩溃日志", Toast.LENGTH_SHORT).show();
            return;
        }
        File[] files = crashDir.listFiles();
        if (files == null || files.length == 0) {
            Toast.makeText(this, "没有崩溃日志", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = files.length;
        long size = 0;
        for (File f : files) size += f.length();

        final int fileCount = count;
        new AlertDialog.Builder(this)
                .setTitle("崩溃日志")
                .setMessage("共 " + count + " 个文件，总计 " + formatSize(size) + "\n确定要删除吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteCrashLogs();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteCrashLogs() {
        File crashDir = getCrashLogDir();
        if (crashDir == null || !crashDir.exists()) return;
        int deleted = 0;
        File[] files = crashDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.delete()) deleted++;
            }
        }
        Toast.makeText(this, "已删除 " + deleted + " 个崩溃日志", Toast.LENGTH_SHORT).show();
        if (getCrashLogDir().listFiles() == null || getCrashLogDir().listFiles().length == 0) {
            getSharedPreferences("crash", MODE_PRIVATE).edit().putBoolean("has_crash", false).commit();
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / 1024 / 1024) + " MB";
    }

    // ---- 回声洞 ----

    private void loadEchoHole() {
        btnEchoHole.setText("加载中...");
        btnEchoHole.setEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("http://www.biliclassic.cn/api/echo.json");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestMethod("GET");
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    final String jsonStr = sb.toString();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnEchoHole.setText("回声洞");
                            btnEchoHole.setEnabled(true);
                            if (isFinishing()) return;
                            try {
                                JSONArray arr = new JSONArray(jsonStr);
                                if (arr.length() > 0) {
                                    int idx = (int) (Math.random() * arr.length());
                                    if (arr.length() > 1) {
                                        while (idx == mLastEchoIndex) {
                                            idx = (int) (Math.random() * arr.length());
                                        }
                                    }
                                    mLastEchoIndex = idx;
                                    JSONObject item = arr.getJSONObject(idx);
                                    String text = item.optString("text", "");
                                    String author = item.optString("author", "匿名");
                                    String device = item.optString("device", null);
                                    String time = item.optString("time", "未知");
                                    String msg = text + "\n\n" + author;
                                    if (device != null && device.length() > 0) msg += "\n来自 " + device;
                                    msg += "\n" + time;
                                    new AlertDialog.Builder(TvSettingsActivity.this)
                                            .setTitle("回声洞")
                                            .setMessage(msg)
                                            .setPositiveButton("关闭", null)
                                            .show();
                                } else {
                                    Toast.makeText(TvSettingsActivity.this, "回声洞暂无内容", Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                Toast.makeText(TvSettingsActivity.this, "解析失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            btnEchoHole.setText("回声洞");
                            btnEchoHole.setEnabled(true);
                            Toast.makeText(TvSettingsActivity.this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ---- 检查更新 ----

    private void checkForUpdate() {
        btnCheckUpdate.setText("检查中...");
        btnCheckUpdate.setEnabled(false);

        UpdateUtil.checkUpdate(this, currentVersionCode, currentVersionName,
                new UpdateUtil.UpdateCallback() {
                    @Override
                    public void onCheckStart() {}
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

    // ---- 关于 ----

    private void showAboutDialog() {
        String version = "0.4.1";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) {}
        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage("BiliClassic TV\n版本: " + version + "\n\n专为电视遥控器优化的简洁界面")
                .setPositiveButton("确定", null)
                .show();
    }
}
