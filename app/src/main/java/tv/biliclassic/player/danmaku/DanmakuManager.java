package tv.biliclassic.player.danmaku;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.Inflater;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.IllegalDataException;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.android.DanmakuGlobalConfig;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser;
import master.flame.danmaku.ui.widget.DanmakuView;
import tv.biliclassic.R;
import tv.biliclassic.api.DanmakuApi;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.danmaku.ijk.media.player.IMediaPlayer;

public class DanmakuManager {

    // SharedPreferences keys
    private static final String KEY_TEXT_SIZE = "danmaku_text_size";
    private static final String KEY_TRANSPARENCY = "danmaku_transparency";
    private static final String KEY_SPEED = "danmaku_speed";
    private static final String KEY_MAX_SCREEN = "danmaku_max_screen";
    private static final String KEY_BLOCK_TOP = "danmaku_block_top";
    private static final String KEY_BLOCK_SCROLL = "danmaku_block_scroll";
    private static final String KEY_BLOCK_BOTTOM = "danmaku_block_bottom";
    private static final String KEY_BLOCK_GUEST = "danmaku_block_guest";
    private static final String KEY_BLOCK_COLORFUL = "danmaku_block_colorful";
    private static final String KEY_DUP_MERGE = "danmaku_duplicate_merge";

    private final Activity mActivity;
    private final FrameLayout mContainer;
    private final long mAid;
    private final long mCid;

    private DanmakuView mDanmakuView;
    private String mDanmakuUrl;
    private File mDanmakuCacheFile;
    private boolean mEnabled = true;
    private boolean mLoaded;
    private File mOfflineDanmakuFile; // 离线弹幕文件路径

    private IMediaPlayer mMediaPlayer;
    private boolean mVideoPrepared;

    private PopupWindow mOptionsPanel;
    private ViewStub mInputStub;
    private View mInputOverlay;
    private boolean mWasPlayingBeforeInput;

    private final Resources mRes;

    public DanmakuManager(Activity activity, FrameLayout container, long aid, long cid,
                          ViewStub danmakuInputStub) {
        mActivity = activity;
        mContainer = container;
        mAid = aid;
        mCid = cid;
        mInputStub = danmakuInputStub;
        mRes = activity.getResources();
    }

    /**
     * 设置离线弹幕文件路径（用于已缓存视频播放）
     */
    public void setOfflineDanmakuFile(File danmakuFile) {
        mOfflineDanmakuFile = danmakuFile;
    }

    // lifecycle

    public void init() {
        mDanmakuView = new DanmakuView(mActivity);
        mContainer.addView(mDanmakuView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        // 持久化设置
        float textSize = SharedPreferencesUtil.getFloat(KEY_TEXT_SIZE, 0.8f);
        float transparency = SharedPreferencesUtil.getFloat(KEY_TRANSPARENCY, 0.4f);
        float speed = SharedPreferencesUtil.getFloat(KEY_SPEED, 1.0f);
        int maxScreen = SharedPreferencesUtil.getInt(KEY_MAX_SCREEN, -1);
        boolean blockTop = SharedPreferencesUtil.getBoolean(KEY_BLOCK_TOP, false);
        boolean blockScroll = SharedPreferencesUtil.getBoolean(KEY_BLOCK_SCROLL, false);
        boolean blockBottom = SharedPreferencesUtil.getBoolean(KEY_BLOCK_BOTTOM, false);
        boolean dupMerge = SharedPreferencesUtil.getBoolean(KEY_DUP_MERGE, false);

        DanmakuGlobalConfig.DEFAULT.setScrollSpeedFactor(1.0f / speed);
        DanmakuGlobalConfig.DEFAULT.setScaleTextSize(textSize);
        DanmakuGlobalConfig.DEFAULT.setDanmakuTransparency(1.0f - transparency);
        DanmakuGlobalConfig.DEFAULT.setMaximumVisibleSizeInScreen(maxScreen);
        DanmakuGlobalConfig.DEFAULT.setFTDanmakuVisibility(!blockTop);
        DanmakuGlobalConfig.DEFAULT.setR2LDanmakuVisibility(!blockScroll);
        DanmakuGlobalConfig.DEFAULT.setFBDanmakuVisibility(!blockBottom);
        DanmakuGlobalConfig.DEFAULT.setL2RDanmakuVisibility(true);
        DanmakuGlobalConfig.DEFAULT.setSpecialDanmakuVisibility(true);
        DanmakuGlobalConfig.DEFAULT.setDuplicateMergingEnabled(dupMerge);

        if (mCid > 0) {
            mDanmakuUrl = "https://comment.bilibili.com/" + mCid + ".xml";
            mDanmakuCacheFile = new File(mActivity.getCacheDir(), "danmaku_" + mCid + ".xml");
        }

        // 离线弹幕优先：如果提供了离线弹幕文件路径且存在，直接使用
        if (mOfflineDanmakuFile != null && mOfflineDanmakuFile.exists() && mOfflineDanmakuFile.length() > 0) {
            mDanmakuCacheFile = mOfflineDanmakuFile;
            mDanmakuUrl = null; // 不需要在线获取
        }

        if (mDanmakuUrl != null || (mDanmakuCacheFile != null && mDanmakuCacheFile.exists())) {
            startLoadDanmaku();
        }
    }

    public void release() {
        dismissAllPanels();
        if (mDanmakuView != null) {
            mDanmakuView.release();
            mDanmakuView = null;
        }
        mLoaded = false;
    }

    // 视频同步

    public void onVideoPrepared(IMediaPlayer mp) {
        mMediaPlayer = mp;
        mVideoPrepared = true;
    }

    public void seekTo(long positionMs) {
        if (mDanmakuView != null && mLoaded) {
            mDanmakuView.seekTo(positionMs);
        }
    }

    public void pause() {
        if (mDanmakuView != null && mLoaded) {
            mDanmakuView.pause();
        }
    }

    public void resume() {
        if (mDanmakuView != null && mLoaded && mEnabled) {
            mDanmakuView.resume();
        }
    }

    // 可见性

    public void toggleVisibility() {
        mEnabled = !mEnabled;
        updateVisibility();
    }

    public boolean isEnabled() { return mEnabled; }
    public boolean isLoaded() { return mLoaded; }

    private void updateVisibility() {
        if (mDanmakuView == null) return;
        if (mEnabled) {
            mDanmakuView.show();
        } else {
            mDanmakuView.hide();
        }
    }

    // 弹幕设置

    public void showOptionsPanel() {
        if (mOptionsPanel != null && mOptionsPanel.isShowing()) {
            mOptionsPanel.dismiss();
            return;
        }
        dismissAllPanels();

        LayoutInflater inflater = LayoutInflater.from(mActivity);
        View panel = inflater.inflate(R.layout.bili_app_player_options_pannel_danmaku, null);

        TextView titleView = (TextView) panel.findViewById(R.id.title);
        if (titleView != null) {
            titleView.setText(R.string.Player_danmaku_options_pannel_title);
        }

        View closeBtn = panel.findViewById(R.id.close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { dismissAllPanels(); }
            });
        }

        wireBlockCb(panel, R.id.option_block_top,
                SharedPreferencesUtil.getBoolean(KEY_BLOCK_TOP, false), "顶部弹幕",
                new BlockToggle() { public void set(boolean b) {
                    DanmakuGlobalConfig.DEFAULT.setFTDanmakuVisibility(!b);
                    SharedPreferencesUtil.putBoolean(KEY_BLOCK_TOP, b);
                }});
        wireBlockCb(panel, R.id.option_block_scroll,
                SharedPreferencesUtil.getBoolean(KEY_BLOCK_SCROLL, false), "滚动弹幕",
                new BlockToggle() { public void set(boolean b) {
                    DanmakuGlobalConfig.DEFAULT.setR2LDanmakuVisibility(!b);
                    SharedPreferencesUtil.putBoolean(KEY_BLOCK_SCROLL, b);
                }});
        wireBlockCb(panel, R.id.option_block_bottom,
                SharedPreferencesUtil.getBoolean(KEY_BLOCK_BOTTOM, false), "底部弹幕",
                new BlockToggle() { public void set(boolean b) {
                    DanmakuGlobalConfig.DEFAULT.setFBDanmakuVisibility(!b);
                    SharedPreferencesUtil.putBoolean(KEY_BLOCK_BOTTOM, b);
                }});
        wireBlockCb(panel, R.id.option_block_guest, false, "游客弹幕",
                new BlockToggle() { public void set(boolean b) {
                    DanmakuGlobalConfig.DEFAULT.blockGuestDanmaku(b);
                    SharedPreferencesUtil.putBoolean(KEY_BLOCK_GUEST, b);
                }});

        wireBlockCb(panel, R.id.option_block_colorful, false, "彩色弹幕",
                new BlockToggle() { public void set(boolean b) {
                    if (b) {
                        DanmakuGlobalConfig.DEFAULT.setColorValueWhiteList(0xFFFFFF, 0xFFFFFFFF);
                    } else {
                        DanmakuGlobalConfig.DEFAULT.setColorValueWhiteList((Integer[]) null);
                    }
                    SharedPreferencesUtil.putBoolean(KEY_BLOCK_COLORFUL, b);
                }});

        CheckBox dupCb = (CheckBox) panel.findViewById(R.id.options_duplicate_merging_enable);
        if (dupCb != null) {
            dupCb.setChecked(SharedPreferencesUtil.getBoolean(KEY_DUP_MERGE, false));
            dupCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean c) {
                    DanmakuGlobalConfig.DEFAULT.setDuplicateMergingEnabled(c);
                    SharedPreferencesUtil.putBoolean(KEY_DUP_MERGE, c);
                    toast((c ? "已开启" : "已关闭") + "合并重复弹幕");
                }
            });
        }

        wireSeek(panel, R.id.option_danmaku_textsize, 0.5f, 2.0f,
                DanmakuGlobalConfig.DEFAULT.scaleTextSize, "字号缩放",
                new SeekCallback() { public void onChanged(float v) {
                    DanmakuGlobalConfig.DEFAULT.setScaleTextSize(v);
                    SharedPreferencesUtil.putFloat(KEY_TEXT_SIZE, v);
                }});
        wireSeek(panel, R.id.option_danmaku_max_on_screen, 1f, 100f,
                DanmakuGlobalConfig.DEFAULT.maximumNumsInScreen < 0 ? 50f : DanmakuGlobalConfig.DEFAULT.maximumNumsInScreen,
                "同屏密度",
                new SeekCallback() { public void onChanged(float v) {
                    DanmakuGlobalConfig.DEFAULT.setMaximumVisibleSizeInScreen((int) v);
                    SharedPreferencesUtil.putInt(KEY_MAX_SCREEN, (int) v);
                }});
        wireSeek(panel, R.id.option_danmaku_scroll_speed_factor, 0.5f, 3.0f,
                1.0f / DanmakuGlobalConfig.DEFAULT.scrollSpeedFactor, "弹幕速度",
                new SeekCallback() { public void onChanged(float v) {
                    DanmakuGlobalConfig.DEFAULT.setScrollSpeedFactor(1.0f / v);
                    SharedPreferencesUtil.putFloat(KEY_SPEED, v);
                }});
        wireSeek(panel, R.id.option_danmaku_transparency, 0f, 1.0f,
                1.0f - DanmakuGlobalConfig.DEFAULT.transparency / 255f, "弹幕透明度",
                new SeekCallback() { public void onChanged(float v) {
                    DanmakuGlobalConfig.DEFAULT.setDanmakuTransparency(1.0f - v);
                    SharedPreferencesUtil.putFloat(KEY_TRANSPARENCY, v);
                }});
        wireSeek(panel, R.id.option_danmaku_stroke_width_scaling, 0.5f, 2.0f, 1.0f, "描边大小",
                new SeekCallback() { public void onChanged(float v) { /* TODO */ } });

        mOptionsPanel = new PopupWindow(panel,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, true);
        mOptionsPanel.setAnimationStyle(R.style.Animation_SidePannel);
        mOptionsPanel.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        mOptionsPanel.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() { mOptionsPanel = null; }
        });

        View root = mActivity.findViewById(android.R.id.content);
        mOptionsPanel.showAtLocation(root, Gravity.RIGHT, 0, 0);
    }

    // 输入并发送弹幕

    public void showInputPanel(final PlayControl playControl) {
        if (mInputOverlay == null && mInputStub != null) {
            mInputOverlay = mInputStub.inflate();
            if (mInputOverlay != null) {
                final EditText inputEdit = (EditText) mInputOverlay.findViewById(R.id.input);
                View clearBtn = mInputOverlay.findViewById(R.id.clear);
                View sendBtn = mInputOverlay.findViewById(R.id.send);
                if (clearBtn != null) {
                    clearBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) { hideInputPanel(playControl); }
                    });
                }
                if (sendBtn != null) {
                    sendBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            String text = inputEdit != null ? inputEdit.getText().toString().trim() : "";
                            if (text.length() == 0) {
                                toast("请输入弹幕内容");
                                return;
                            }
                            hideInputPanel(playControl);
                            sendDanmaku(text);
                        }
                    });
                }
            }
        }
        mWasPlayingBeforeInput = playControl.isPlaying();
        if (mWasPlayingBeforeInput) {
            playControl.pausePlayer();
        }
        if (mInputOverlay != null) {
            mInputOverlay.setVisibility(View.VISIBLE);
        }
    }

    public void hideInputPanel(PlayControl playControl) {
        if (mInputOverlay != null) {
            mInputOverlay.setVisibility(View.GONE);
        }
        if (mWasPlayingBeforeInput && playControl.isPrepared()) {
            playControl.resumePlayer();
        }
    }

    public boolean isInputVisible() {
        return mInputOverlay != null && mInputOverlay.getVisibility() == View.VISIBLE;
    }

    public boolean isOptionsPanelShowing() {
        return mOptionsPanel != null && mOptionsPanel.isShowing();
    }

    public void dismissAllPanels() {
        if (mOptionsPanel != null && mOptionsPanel.isShowing()) {
            mOptionsPanel.dismiss();
            mOptionsPanel = null;
        }
    }

    // 加载弹幕

    private void startLoadDanmaku() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadDanmakuXml();
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() { prepareDanmakuParser(); }
                    });
                } catch (final Exception e) {
                    android.util.Log.e("DanmakuManager", "弹幕加载失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void downloadDanmakuXml() throws Exception {
        if (mDanmakuCacheFile != null && mDanmakuCacheFile.exists() && mDanmakuCacheFile.length() > 0) {
            android.util.Log.e("DanmakuManager", "使用缓存的弹幕文件: " + mDanmakuCacheFile.getAbsolutePath());
            return;
        }

        java.net.URL url = new java.net.URL(mDanmakuUrl);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();

        if (mDanmakuUrl.startsWith("https")) {
            try {
                javax.net.ssl.SSLSocketFactory sslFactory = NetWorkUtil.getTrustAllSSLSocketFactory();
                if (sslFactory != null) {
                    ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(sslFactory);
                }
                ((javax.net.ssl.HttpsURLConnection) conn).setHostnameVerifier(NetWorkUtil.TRUST_ALL_HOSTNAMES);
            } catch (Exception e) {
                android.util.Log.e("DanmakuManager", "SSL设置失败: " + e.getMessage());
            }
        }

        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.connect();

        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            is = conn.getInputStream();
            baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            byte[] rawData = baos.toByteArray();
            byte[] decompressed = decompress(rawData);
            if (decompressed != null) {
                rawData = decompressed;
            }
            if (mDanmakuCacheFile != null) {
                FileOutputStream fos = new FileOutputStream(mDanmakuCacheFile);
                fos.write(rawData);
                fos.close();
            }
        } finally {
            if (baos != null) baos.close();
            if (is != null) is.close();
            conn.disconnect();
        }
    }

    private byte[] decompress(byte[] data) {
        if (data.length > 0 && data[0] == '<') return null;
        Inflater decompresser = new Inflater(true);
        decompresser.reset();
        decompresser.setInput(data);
        ByteArrayOutputStream o = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[2048];
            while (!decompresser.finished()) {
                o.write(buf, 0, decompresser.inflate(buf));
            }
            return o.toByteArray();
        } catch (Exception e) {
            android.util.Log.e("DanmakuManager", "弹幕解压失败: " + e.getMessage());
            return null;
        } finally {
            try { o.close(); } catch (Exception e) {}
            decompresser.end();
        }
    }

    private void prepareDanmakuParser() {
        if (mDanmakuView == null) return;
        if (mDanmakuCacheFile == null || !mDanmakuCacheFile.exists()) return;

        try {
            ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);
            if (loader == null) return;
            loader.load(mDanmakuCacheFile.getAbsolutePath());
            BaseDanmakuParser parser = new BiliDanmukuParser();
            IDataSource<?> dataSource = loader.getDataSource();
            parser.load(dataSource);

            mDanmakuView.setCallback(new DrawHandler.Callback() {
                @Override
                public void prepared() {
                    android.util.Log.e("DanmakuManager", "弹幕引擎准备完毕");
                    mLoaded = true;
                    if (mEnabled) mDanmakuView.start();
                }

                @Override
                public void updateTimer(DanmakuTimer timer) {
                    if (mMediaPlayer != null && mVideoPrepared) {
                        try {
                            long pos = mMediaPlayer.getCurrentPosition();
                            if (pos >= 0) timer.update(pos);
                        } catch (Exception ignored) {}
                    }
                }
            });

            mDanmakuView.enableDanmakuDrawingCache(true);
            mDanmakuView.prepare(parser);
            android.util.Log.e("DanmakuManager", "弹幕 prepare 已调用");
        } catch (IllegalDataException e) {
            android.util.Log.e("DanmakuManager", "弹幕数据异常: " + e.getMessage());
        } catch (Exception e) {
            android.util.Log.e("DanmakuManager", "弹幕解析异常: " + e.getMessage());
        }
    }

    // 发送弹幕

    private void sendDanmaku(final String text) {
        if (mCid <= 0) {
            toast("无法发送弹幕：缺少视频信息");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long progress = 0;
                    if (mMediaPlayer != null && mVideoPrepared) {
                        progress = mMediaPlayer.getCurrentPosition();
                    }
                    int result = DanmakuApi.sendVideoDanmakuByAid(
                            mCid, text, mAid, progress,
                            DanmakuApi.COLOR_WHITE, DanmakuApi.MODE_SCROLL);
                    final String msg;
                    if (result == 0) {
                        msg = "弹幕发送成功";
                        if (mDanmakuView != null && mLoaded) {
                            BaseDanmaku danmaku = master.flame.danmaku.danmaku.parser.DanmakuFactory
                                    .createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
                            if (danmaku != null) {
                                danmaku.text = text;
                                danmaku.padding = 5;
                                danmaku.priority = 1;
                                danmaku.textColor = Color.WHITE;
                                danmaku.textSize = 25 * (mDanmakuView.getWidth() / 640f);
                                danmaku.time = mDanmakuView.getCurrentTime() + 100;
                                danmaku.isLive = false;
                                mDanmakuView.addDanmaku(danmaku);
                            }
                        }
                    } else {
                        msg = "弹幕发送失败，code=" + result;
                    }
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() { toast(msg); }
                    });
                } catch (final Exception e) {
                    android.util.Log.e("DanmakuManager", "发送弹幕异常: " + e.getMessage());
                    final String errMsg = e.getMessage();
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() { toast("弹幕发送失败: " + errMsg); }
                    });
                }
            }
        }).start();
    }

    // UI管理

    private interface BlockToggle { void set(boolean blocked); }
    private interface SeekCallback { void onChanged(float value); }

    private void wireBlockCb(View panel, int cbId, boolean initChecked, final String label, final BlockToggle toggle) {
        CheckBox cb = (CheckBox) panel.findViewById(cbId);
        if (cb == null) return;
        cb.setChecked(initChecked);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                toggle.set(checked);
                toast((checked ? "已屏蔽" : "已取消屏蔽") + label + "弹幕");
            }
        });
    }

    private void wireSeek(View panel, int containerId, final float min, final float max,
                          float curr, final String label, final SeekCallback callback) {
        View container = panel.findViewById(containerId);
        if (container == null) return;
        SeekBar sb = (SeekBar) container.findViewById(R.id.seekbar);
        final TextView labelView = (TextView) container.findViewById(R.id.label);
        if (sb == null) return;
        final float range = max - min;
        sb.setMax(100);
        sb.setProgress((int) ((curr - min) / range * 100));
        if (labelView != null) labelView.setText(sb.getProgress() + "%");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (labelView != null) labelView.setText(progress + "%");
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {
                callback.onChanged(min + range * seekBar.getProgress() / 100f);
            }
        });
    }

    private void toast(String msg) {
        Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
    }

    // 播放控制接口

    public interface PlayControl {
        boolean isPlaying();
        boolean isPrepared();
        void pausePlayer();
        void resumePlayer();
    }
}
