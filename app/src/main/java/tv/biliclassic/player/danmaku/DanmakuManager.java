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
import android.widget.RadioGroup;

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

import tv.biliclassic.util.SdkHelper;
public class DanmakuManager {

    private static String getCpuAbi() {
        try { return (String) android.os.Build.class.getField("CPU_ABI").get(null); }
        catch (Exception e) { return ""; }
    }

    private static final String KEY_TEXT_SIZE = "danmaku_text_size";
    private static final String KEY_TRANSPARENCY = "danmaku_transparency";
    private static final String KEY_SPEED = "danmaku_speed";
    private static final String KEY_STROKE_SCALE = "danmaku_stroke_scale";
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
    private SimpleDanmakuEngine mSimpleEngine;
    private String mDanmakuUrl;
    private File mDanmakuCacheFile;
    private boolean mEnabled = true;
    private boolean mLoaded;
    private File mOfflineDanmakuFile;
    private boolean mUseSimpleEngine;

    private IMediaPlayer mMediaPlayer;
    private boolean mVideoPrepared;
    private boolean mSeekPending;
    private long mSeekTarget;
    private boolean mReleased;

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

    public void setOfflineDanmakuFile(File danmakuFile) {
        mOfflineDanmakuFile = danmakuFile;
    }

    public static boolean isSimpleEngineEnabled() {
        int mode = SharedPreferencesUtil.getInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, -1);
        if (mode < 0) {
            String abi = getCpuAbi();
            boolean isLegacyCpu = abi != null && abi.startsWith("armeabi") && !abi.contains("v7");
            mode = isLegacyCpu ? 1 : 0;
            SharedPreferencesUtil.putInt(SharedPreferencesUtil.DANMAKU_ENGINE_MODE, mode);
        }
        return mode == 1;
    }

    public void init() {
        mReleased = false;
        mUseSimpleEngine = isSimpleEngineEnabled();

        if (mUseSimpleEngine) {
            initSimpleEngine();
        } else {
            initFullEngine();
        }

        if (mCid > 0) {
            mDanmakuUrl = "https://comment.bilibili.com/" + mCid + ".xml";
            mDanmakuCacheFile = new File(mActivity.getCacheDir(), "danmaku_" + mCid + ".xml");
        }

        if (mOfflineDanmakuFile != null && mOfflineDanmakuFile.exists() && mOfflineDanmakuFile.length() > 0) {
            mDanmakuCacheFile = mOfflineDanmakuFile;
            mDanmakuUrl = null;
        }

        if (mDanmakuUrl != null || (mDanmakuCacheFile != null && mDanmakuCacheFile.exists())) {
            startLoadDanmaku();
        }
    }

    private void initSimpleEngine() {
        mSimpleEngine = new SimpleDanmakuEngine(mActivity);
        mSimpleEngine.init(mContainer);
    }

    private void initFullEngine() {
        for (int i = mContainer.getChildCount() - 1; i >= 0; i--) {
            View child = mContainer.getChildAt(i);
            if (child instanceof DanmakuView) {
                ((DanmakuView) child).release();
                mContainer.removeView(child);
            }
        }
        mDanmakuView = new DanmakuView(mActivity);
        mContainer.addView(mDanmakuView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        float textSize = SharedPreferencesUtil.getFloat(KEY_TEXT_SIZE, 0.9f);
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

        applyStrokeScale();

        if (mCid > 0) {
            mDanmakuUrl = "https://comment.bilibili.com/" + mCid + ".xml";
            mDanmakuCacheFile = new File(mActivity.getCacheDir(), "danmaku_" + mCid + ".xml");
        }

        if (mOfflineDanmakuFile != null && mOfflineDanmakuFile.exists() && mOfflineDanmakuFile.length() > 0) {
            mDanmakuCacheFile = mOfflineDanmakuFile;
            mDanmakuUrl = null;
        }

        if (mDanmakuUrl != null || (mDanmakuCacheFile != null && mDanmakuCacheFile.exists())) {
            startLoadDanmaku();
        }
    }

    private void applyStrokeScale() {
        float scale = SharedPreferencesUtil.getFloat(KEY_STROKE_SCALE, 1.0f);
        master.flame.danmaku.danmaku.model.android.AndroidDisplayer.setShadowRadius(4.0f * scale);
        master.flame.danmaku.danmaku.model.android.AndroidDisplayer.setPaintStorkeWidth(3.5f * scale);
        master.flame.danmaku.danmaku.model.android.AndroidDisplayer.clearTextHeightCache();
        if (mDanmakuView != null) {
            mDanmakuView.clearCache();
        }
    }

    public void release() {
        mReleased = true;
        dismissAllPanels();
        if (mSimpleEngine != null) {
            mSimpleEngine.releaseDanmaku();
            mSimpleEngine = null;
        }
        if (mDanmakuView != null) {
            mDanmakuView.release();
            mDanmakuView = null;
        }
        mLoaded = false;
    }

    public void onVideoPrepared(IMediaPlayer mp) {
        mMediaPlayer = mp;
        mVideoPrepared = true;
        if (mSimpleEngine != null) {
            mSimpleEngine.setTimeProvider(new SimpleDanmakuEngine.VideoTimeProvider() {
                public long getCurrentPosition() {
                    try {
                        long pos = mMediaPlayer.getCurrentPosition();
                        return pos >= 0 ? pos : 0;
                    } catch (Exception e) {
                        android.util.Log.e("BT-5", "getCurrentPosition error: " + e.getMessage());
                        return 0;
                    }
                }
            });
        }
    }

    public void seekTo(long positionMs) {
        mSeekPending = true;
        mSeekTarget = positionMs;
        if (mSimpleEngine != null && mLoaded) {
            mSimpleEngine.seekTo(positionMs);
        } else if (mDanmakuView != null && mLoaded) {
            mDanmakuView.seekTo(positionMs);
        }
    }

    public void pause() {
        if (mSimpleEngine != null && mLoaded) {
            mSimpleEngine.pauseDanmaku();
        } else if (mDanmakuView != null && mLoaded) {
            mDanmakuView.pause();
        }
    }

    public void resume() {
        if (mSimpleEngine != null && mLoaded) {
            mSimpleEngine.resumeDanmaku();
        } else if (mDanmakuView != null && mLoaded) {
            mDanmakuView.resume();
        }
    }

    public void toggleVisibility() {
        mEnabled = !mEnabled;
        updateVisibility();
    }

    public boolean isEnabled() { return mEnabled; }
    public boolean isLoaded() { return mLoaded; }

    private void updateVisibility() {
        if (mSimpleEngine != null) {
            if (mEnabled != mSimpleEngine.isEnabled()) mSimpleEngine.toggleVisibility();
        } else if (mDanmakuView != null) {
            if (mEnabled) mDanmakuView.show(); else mDanmakuView.hide();
        }
    }

    // 用 FrameLayout 包裹视图并右对齐，MATCH_PARENT PopupWindow 时安全区挤压左侧空白区域
    private android.view.View wrapRight(android.view.View content) {
        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(mActivity);
        wrapper.setBackgroundDrawable(null);
        wrapper.addView(content, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.Gravity.RIGHT));
        return wrapper;
    }

    public void showOptionsPanel() {
        if (mOptionsPanel != null && mOptionsPanel.isShowing()) {
            mOptionsPanel.dismiss();
            return;
        }
        dismissAllPanels();

        if (mUseSimpleEngine) {
            showSimpleOptionsPanel();
            return;
        }

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
        final float savedStroke = SharedPreferencesUtil.getFloat(KEY_STROKE_SCALE, 1.0f);
        wireSeek(panel, R.id.option_danmaku_stroke_width_scaling, 0.5f, 2.0f, savedStroke, "描边大小",
                new SeekCallback() { public void onChanged(float v) {
                    SharedPreferencesUtil.putFloat(KEY_STROKE_SCALE, v);
                    applyStrokeScale();
                }});

        mOptionsPanel = new PopupWindow(wrapRight(panel),
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, true);
        mOptionsPanel.setAnimationStyle(R.style.Animation_SidePannel);
        mOptionsPanel.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        mOptionsPanel.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() { mOptionsPanel = null; }
        });

        View root = mActivity.getWindow().getDecorView();
        mOptionsPanel.showAtLocation(root, Gravity.RIGHT, 0, 0);
        if (SdkHelper.getSdkInt() >= 28) {
            panel.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(android.view.View v) {
                    tv.biliclassic.player.BiliPlayerActivity.applyPopupCutout(mOptionsPanel);
                    v.removeOnAttachStateChangeListener(this);
                }
                public void onViewDetachedFromWindow(android.view.View v) {}
            });
        }
    }

    public void showInputPanel(final PlayControl playControl) {
        if (mUseSimpleEngine) {
            toast("简易引擎暂不支持发送弹幕");
            return;
        }
        if (mInputOverlay == null && mInputStub != null) {
            if (!(mInputStub.getParent() instanceof android.view.ViewGroup)) {
                mInputStub = null;
                return;
            }
            try {
                mInputOverlay = mInputStub.inflate();
                mInputStub = null;
            } catch (Exception e) {
                mInputStub = null;
                return;
            }
            if (mInputOverlay != null) {
                final EditText inputEdit = (EditText) mInputOverlay.findViewById(R.id.input);
                View clearBtn = mInputOverlay.findViewById(R.id.clear);
                View sendBtn = mInputOverlay.findViewById(R.id.send);

                View optionsView = mInputOverlay.findViewById(R.id.danmaku_input_options);
                final RadioGroup modeGroup = (RadioGroup) optionsView.findViewById(R.id.input_options_group_type);
                final RadioGroup sizeGroup = (RadioGroup) optionsView.findViewById(R.id.input_options_group_textsize);

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
                            int mode = 1;
                            if (modeGroup != null) {
                                int checkedId = modeGroup.getCheckedRadioButtonId();
                                if (checkedId == R.id.input_options_top_type) {
                                    mode = 5;
                                } else if (checkedId == R.id.input_options_bottom_type) {
                                    mode = 4;
                                } else {
                                    mode = 1;
                                }
                            }
                            int textSize = 25;
                            if (sizeGroup != null) {
                                int checkedId = sizeGroup.getCheckedRadioButtonId();
                                if (checkedId == R.id.input_options_small_textsize) {
                                    textSize = 18;
                                } else {
                                    textSize = 25;
                                }
                            }
                            hideInputPanel(playControl);
                            sendDanmaku(text, mode, textSize);
                        }
                    });
                }
            }
        }
        mWasPlayingBeforeInput = playControl.isPlaying();
        if (mWasPlayingBeforeInput) {
            playControl.pausePlayer();
            if (mSimpleEngine != null && mLoaded) {
                mSimpleEngine.pauseDanmaku();
            } else if (mDanmakuView != null && mLoaded) {
                mDanmakuView.pause();
            }
        }
        if (mInputOverlay != null) {
            mInputOverlay.setVisibility(View.VISIBLE);
        }
    }

    public void hideInputPanel(PlayControl playControl) {
        if (mInputOverlay != null) {
            mInputOverlay.setVisibility(View.GONE);
        }
        if (mSimpleEngine != null && mLoaded) {
            mSimpleEngine.resumeDanmaku();
        } else if (mDanmakuView != null && mLoaded) {
            mDanmakuView.resume();
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

    private void startLoadDanmaku() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadDanmakuXml();
                    if (!mReleased) {
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!mReleased) prepareDanmakuParser();
                            }
                        });
                    }
                } catch (final Exception e) {
                    android.util.Log.e("DanmakuManager", "弹幕加载失败: " + e.getMessage());
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

    private void prepareSimpleEngine() {
        try {
            mSimpleEngine.setDanmakuData(mDanmakuCacheFile);
            mLoaded = true;
        } catch (Exception e) {
            android.util.Log.e("DanmakuManager", "简易弹幕初始化失败: " + e.getMessage());
        }
    }

    private void prepareDanmakuParser() {
        if (mDanmakuCacheFile == null || !mDanmakuCacheFile.exists()) return;

        if (mUseSimpleEngine) {
            prepareSimpleEngine();
            return;
        }

        if (mDanmakuView == null) return;

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
                    if (mReleased || mDanmakuView == null) return;
                    android.util.Log.e("DanmakuManager", "弹幕引擎准备完毕");
                    mLoaded = true;
                    if (mEnabled) {
                        if (mVideoPrepared && mMediaPlayer != null) {
                            try {
                                long pos = mMediaPlayer.getCurrentPosition();
                                if (pos > 0) mDanmakuView.seekTo(pos);
                            } catch (Exception ignored) {}
                        }
                        mDanmakuView.start();
                    }
                }

                @Override
                public void updateTimer(DanmakuTimer timer) {
                    if (mMediaPlayer != null && mVideoPrepared && mSeekPending) {
                        try {
                            long pos = mMediaPlayer.getCurrentPosition();
                            if (pos >= 0 && Math.abs(pos - mSeekTarget) < 500) {
                                timer.update(pos);
                                mSeekPending = false;
                            }
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

    private void sendDanmaku(final String text, final int mode, final int textSize) {
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
                            DanmakuApi.COLOR_WHITE, mode);
                    final String msg;
                    if (result == 0) {
                        msg = "弹幕发送成功";
                        if (mDanmakuView != null && mLoaded) {
                            BaseDanmaku danmaku = master.flame.danmaku.danmaku.parser.DanmakuFactory
                                    .createDanmaku(getDanmakuType(mode));
                            if (danmaku != null) {
                                danmaku.text = text;
                                danmaku.padding = 5;
                                danmaku.priority = 1;
                                danmaku.textColor = Color.WHITE;
                                danmaku.textSize = textSize * (mDanmakuView.getWidth() / 640f);
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

    private int getDanmakuType(int mode) {
        if (mode == 5) {
            return BaseDanmaku.TYPE_FIX_TOP;
        } else if (mode == 4) {
            return BaseDanmaku.TYPE_FIX_BOTTOM;
        } else {
            return BaseDanmaku.TYPE_SCROLL_RL;
        }
    }

    private void showSimpleOptionsPanel() {
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

        hideIfNotNull(panel, R.id.options_block_group);
        hideIfNotNull(panel, R.id.option_block_top);
        hideIfNotNull(panel, R.id.option_block_scroll);
        hideIfNotNull(panel, R.id.option_block_bottom);
        hideIfNotNull(panel, R.id.option_block_guest);
        hideIfNotNull(panel, R.id.option_block_colorful);
        hideIfNotNull(panel, R.id.options_duplicate_merging_enable);
        hideIfNotNull(panel, R.id.option_danmaku_stroke_width_scaling);

        hideSectionHeaders(panel);

        float textScale = SharedPreferencesUtil.getFloat(KEY_TEXT_SIZE, 0.9f);
        float speed = SharedPreferencesUtil.getFloat(KEY_SPEED, 1.0f);
        float alpha = 1.0f - SharedPreferencesUtil.getFloat(KEY_TRANSPARENCY, 0.4f);
        int maxScreen = SharedPreferencesUtil.getInt(KEY_MAX_SCREEN, 50);

        wireSimpleSeek(panel, R.id.option_danmaku_transparency,
                0f, 1.0f, SharedPreferencesUtil.getFloat(KEY_TRANSPARENCY, 0.4f),
                new SeekCallback() { public void onChanged(float v) {
                    mSimpleEngine.setDanmakuOpacity(1.0f - v);
                    SharedPreferencesUtil.putFloat(KEY_TRANSPARENCY, v);
                }});

        wireSimpleSeek(panel, R.id.option_danmaku_textsize,
                0.5f, 2.0f, textScale, "字号",
                new SeekCallback() { public void onChanged(float v) {
                    mSimpleEngine.setScaleTextSize(v);
                    SharedPreferencesUtil.putFloat(KEY_TEXT_SIZE, v);
                }});

        wireSimpleSeek(panel, R.id.option_danmaku_scroll_speed_factor,
                0.3f, 3.0f, speed, "速度",
                new SeekCallback() { public void onChanged(float v) {
                    mSimpleEngine.setScrollSpeedFactor(v);
                    SharedPreferencesUtil.putFloat(KEY_SPEED, v);
                }});

        wireSimpleSeek(panel, R.id.option_danmaku_max_on_screen,
                1f, 100f, maxScreen > 0 ? maxScreen : 50f, "密度",
                new SeekCallback() { public void onChanged(float v) {
                    mSimpleEngine.setMaximumVisibleSizeInScreen((int) v);
                    SharedPreferencesUtil.putInt(KEY_MAX_SCREEN, (int) v);
                }});

        mOptionsPanel = new PopupWindow(wrapRight(panel),
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, true);
        mOptionsPanel.setAnimationStyle(R.style.Animation_SidePannel);
        mOptionsPanel.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        mOptionsPanel.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() { mOptionsPanel = null; }
        });

        View root = mActivity.getWindow().getDecorView();
        mOptionsPanel.showAtLocation(root, Gravity.RIGHT, 0, 0);
        if (SdkHelper.getSdkInt() >= 28) {
            panel.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(android.view.View v) {
                    tv.biliclassic.player.BiliPlayerActivity.applyPopupCutout(mOptionsPanel);
                    v.removeOnAttachStateChangeListener(this);
                }
                public void onViewDetachedFromWindow(android.view.View v) {}
            });
        }
    }

    private void hideIfNotNull(View panel, int id) {
        View v = panel.findViewById(id);
        if (v != null) v.setVisibility(View.GONE);
    }

    private void hideSectionHeaders(View panel) {
        hideSectionTexts(panel);
    }

    private void hideSectionTexts(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                hideSectionTexts(group.getChildAt(i));
            }
        }
        if (view instanceof TextView && view.getId() == View.NO_ID) {
            CharSequence t = ((TextView) view).getText();
            if (t != null && (t.toString().contains("屏蔽") || t.toString().contains("描边"))) {
                view.setVisibility(View.GONE);
            }
        }
    }

    private void wireSimpleSeek(View panel, int containerId, final float min, final float max,
                                float curr, final SeekCallback callback) {
        wireSimpleSeek(panel, containerId, min, max, curr, null, callback);
    }

    private void wireSimpleSeek(View panel, int containerId, final float min, final float max,
                                float curr, String label, final SeekCallback callback) {
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

    public interface PlayControl {
        boolean isPlaying();
        boolean isPrepared();
        void pausePlayer();
        void resumePlayer();
    }
}