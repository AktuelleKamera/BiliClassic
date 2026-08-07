package tv.biliclassic;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ScrollView;
import android.widget.FrameLayout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import tv.biliclassic.util.KeyBindingUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class SetupActivity extends BaseActivity {

    private View mPageWelcome;
    private View mPageTiles;
    private View mPageBinding;
    private int mSelectedTab = -1;
    private FrameLayout mLastSelectedTile = null;
    private boolean mAnimating = false;
    private boolean mOnPage2 = false;
    private boolean mOnPage3 = false;
    private JSONArray mPendingChangelog = null;
    private boolean mPendingChangelogFailed = false;

    // ===== 磁贴按键导航 =====
    // 按键机（有物理按键设备）在磁贴页可用方向键移动光标、确认键选中；
    // 触屏机不使用按键导航，文字不高亮。
    private final java.util.List<FrameLayout> mTiles = new java.util.ArrayList<FrameLayout>();
    private int mTileFocusIndex = 0;
    private boolean mTileKeyNavActive = false; // 仅按键机按键后才置 true
    private int mTileCols = 2; // 磁贴列数（按屏幕宽度自适应，平板/TV 更多列）
    private TextView mBtnStart = null; // 磁贴页"开始使用/下一页"按钮（按键导航最后一站）

    // ===== 按键绑定（Setup 第三页，复用 KeyBindingSetupActivity 的录制逻辑） =====
    private static final int[] RECORD_ORDER = {
        KeyBindingUtil.ACTION_SOFT_LEFT,
        KeyBindingUtil.ACTION_SOFT_RIGHT,
        KeyBindingUtil.ACTION_UP,
        KeyBindingUtil.ACTION_DOWN,
        KeyBindingUtil.ACTION_LEFT,
        KeyBindingUtil.ACTION_RIGHT,
        KeyBindingUtil.ACTION_CONFIRM,
        KeyBindingUtil.ACTION_NUM_0,
        KeyBindingUtil.ACTION_NUM_1,
        KeyBindingUtil.ACTION_NUM_2,
        KeyBindingUtil.ACTION_NUM_3,
        KeyBindingUtil.ACTION_NUM_4,
        KeyBindingUtil.ACTION_NUM_5,
        KeyBindingUtil.ACTION_NUM_6,
        KeyBindingUtil.ACTION_NUM_7,
        KeyBindingUtil.ACTION_NUM_8,
        KeyBindingUtil.ACTION_NUM_9,
        KeyBindingUtil.ACTION_STAR,
        KeyBindingUtil.ACTION_POUND
    };
    private static final String[] RECORD_NAMES = {
        "左软键", "右软键", "上方向键", "下方向键",
        "左方向键", "右方向键", "确认键",
        "数字键 0", "数字键 1", "数字键 2", "数字键 3", "数字键 4",
        "数字键 5", "数字键 6", "数字键 7", "数字键 8", "数字键 9",
        "* 星号键", "# 井号键"
    };
    private boolean mRecording = false;
    private int mRecordIndex = 0;
    private long mLastRecordTime = 0L;

    // ===== 绑定页询问页按键导航 =====
    // 按键机在询问页可用方向键选择"是/否"，确认键触发；BACK 回磁贴页
    private int mAskChoiceIndex = 0; // 0 = 是，1 = 否
    private boolean mAskKeyNavActive = false;

    static final int TAB_PROFILE = 0;
    static final int TAB_HOME = 1;
    static final int TAB_NEW_ANIME = 2;
    static final int TAB_TIMELINE = 3;
    static final int TAB_RECOMMEND = 4;
    static final int TAB_ABOUT = 5;

    static final String[] TAB_NAMES = {"个人中心", "分区导航", "新番专题", "放送时间表", "推荐视频", "关于我们"};
    static final int[] TAB_VALUES = {TAB_PROFILE, TAB_HOME, TAB_NEW_ANIME, TAB_TIMELINE, TAB_RECOMMEND, TAB_ABOUT};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        final View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    Animation anim = AnimationUtils.loadAnimation(SetupActivity.this, R.anim.fade_slide_up);
                    if (anim != null) {
                        rootLayout.startAnimation(anim);
                    }
                }
            });
        }

        String mode = getIntent().getStringExtra("mode");
        final boolean isUpgrade = "upgrade".equals(mode);
        TextView titleText = (TextView) findViewById(R.id.title_text);
        TextView btnText = (TextView) findViewById(R.id.btn_text);
        if (isUpgrade) {
            titleText.setText(getString(R.string.setupactivity_settext_66f4));
            btnText.setText(getString(R.string.setupactivity_settext_6b22));
        } else {
            titleText.setText(getString(R.string.setupactivity_settext_521d));
        }

        mPageWelcome = findViewById(R.id.page_welcome);
        mPageTiles = findViewById(R.id.page_tiles);
        mPageBinding = findViewById(R.id.page_binding);

        initBindingPage();

        TextView btnNext = (TextView) findViewById(R.id.btn_next);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                slideToTiles();
            }
        });

        TextView page2Title = (TextView) findViewById(R.id.page2_title);
        if (isUpgrade) {
            page2Title.setText(getString(R.string.setupactivity_settext_66f4_1));
            generateChangelog();
        } else {
            page2Title.setText(getString(R.string.setupactivity_settext_9009));
            generateTiles();
        }

        final TextView btnStart = (TextView) findViewById(R.id.btn_start);
        mBtnStart = btnStart;
        // 触屏机：磁贴页按钮为"开始使用"（选完直接开始）；按键机：为"下一页"（选完进入按键绑定）
        boolean hasHardwareKeys = tv.biliclassic.util.SdkHelper.hasHardwareKeys(SetupActivity.this);
        btnStart.setText(hasHardwareKeys
                ? getString(R.string.activity_setup_4e0b)
                : getString(R.string.activity_setup_5f00));
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAnimating) return;
                // 首次启动（非升级）、设备有物理按键且尚未绑定任何按键 → 滑入绑定页
                boolean isUpgrade = "upgrade".equals(getIntent().getStringExtra("mode"));
                boolean needBinding = !isUpgrade && !KeyBindingUtil.anyBound()
                        && tv.biliclassic.util.SdkHelper.hasHardwareKeys(SetupActivity.this);
                if (needBinding) {
                    slideToBinding();
                } else {
                    mAnimating = true;
                    final int h = mPageTiles.getHeight();
                    if (h > 0) {
                        TranslateAnimation exit = new TranslateAnimation(0, 0, 0, h);
                        exit.setDuration(400);
                        exit.setInterpolator(new AccelerateInterpolator());
                        exit.setFillAfter(true);
                        exit.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {
                            }
                            @Override
                            public void onAnimationEnd(Animation animation) {
                                finishSetup(btnStart);
                            }
                            @Override
                            public void onAnimationRepeat(Animation animation) {
                            }
                        });
                        mPageTiles.startAnimation(exit);
                    } else {
                        finishSetup(btnStart);
                    }
                }
            }
        });
    }

    private void finishSetup(View btnStart) {
        if (btnStart != null) btnStart.setEnabled(false);
        if (mSelectedTab >= 0) {
            SharedPreferencesUtil.putInt("default_tab", TAB_VALUES[mSelectedTab]);
        }
        SharedPreferencesUtil.putBoolean("setup_shown", true);
        int versionCode = 0;
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        SharedPreferencesUtil.putInt("last_version_code", versionCode);
        enterMain();
    }

    private void enterMain() {
        Intent intent = new Intent(SetupActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * 初始化按键绑定页（第三页）的控件与点击事件。
     */
    private void initBindingPage() {
        if (mPageBinding == null) return;
        TextView btnYes = (TextView) findViewById(R.id.btn_yes);
        if (btnYes != null) {
            btnYes.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mAnimating) return;
                    startRecording();
                }
            });
        }
        TextView btnNo = (TextView) findViewById(R.id.btn_no);
        if (btnNo != null) {
            btnNo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mAnimating) return;
                    finishBinding();
                }
            });
        }
        TextView btnExit = (TextView) findViewById(R.id.btn_exit);
        if (btnExit != null) {
            btnExit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mAnimating) return;
                    finishBinding();
                }
            });
        }
    }

    /**
     * 磁贴页 → 绑定页：整页横向滑入（与欢迎→磁贴转场一致）。
     */
    private void slideToBinding() {
        if (mAnimating) return;
        mAnimating = true;
        final int width = mPageTiles.getWidth();
        if (width <= 0) { mAnimating = false; return; }

        findViewById(R.id.btn_start).setEnabled(false);
        // 绑定页滑入动画期间禁用其按钮，防止未完成时点 btn_no 直接跳过向导
        findViewById(R.id.btn_yes).setEnabled(false);
        findViewById(R.id.btn_no).setEnabled(false);
        findViewById(R.id.btn_exit).setEnabled(false);

        final ViewGroup tilesGroup = (ViewGroup) mPageTiles;
        final ViewGroup bindingGroup = (ViewGroup) mPageBinding;
        final ViewGroup askPage = (ViewGroup) findViewById(R.id.page_ask);
        final LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);

        mPageBinding.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_next).setEnabled(false);

        // 先清残留动画（防止返回再进入时空白）
        bindingGroup.clearAnimation();
        tilesGroup.clearAnimation();
        if (askPage != null) {
            for (int i = 0; i < askPage.getChildCount(); i++) {
                askPage.getChildAt(i).clearAnimation();
            }
        }

        // 磁贴行先向左滑出（逐行，与 slideToTiles 的滑入对称）
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, -width, 0, 0);
            a.setDuration(300);
            a.setStartOffset(i * 60);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            row.startAnimation(a);
        }
        int rowBase = tileContainer.getChildCount() * 60 + 80;

        // 磁贴页标题、按钮向左滑出（分割线、ScrollView 不动）
        int[] skipIdx = {1, 2};
        int tileIdx = 0;
        for (int i = 0; i < tilesGroup.getChildCount(); i++) {
            if (contains(skipIdx, i)) continue;
            View child = tilesGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, -width, 0, 0);
            a.setDuration(350);
            a.setStartOffset(rowBase + tileIdx * 60);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
            tileIdx++;
        }

        int baseDelay = rowBase + tileIdx * 60 + 80;

        // 绑定页标题、消息、按钮从右滑入（分割线不动）
        if (askPage != null) {
            int bindIdx = 0;
            for (int i = 0; i < askPage.getChildCount(); i++) {
                if (i == 1) continue; // 分割线不动
                View child = askPage.getChildAt(i);
                TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
                a.setDuration(450);
                a.setStartOffset(baseDelay + bindIdx * 80);
                a.setInterpolator(new DecelerateInterpolator());
                a.setFillAfter(true);
                if (i == askPage.getChildCount() - 1) {
                    a.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }
                        @Override
                        public void onAnimationEnd(Animation animation) {
                            mAnimating = false;
                            mOnPage2 = false;
                            mOnPage3 = true;
                            mPageTiles.setVisibility(View.GONE);
                            tilesGroup.clearAnimation();
                            bindingGroup.clearAnimation();
                            for (int j = 0; j < askPage.getChildCount(); j++) {
                                askPage.getChildAt(j).clearAnimation();
                            }
                            for (int k = 0; k < tileContainer.getChildCount(); k++) {
                                tileContainer.getChildAt(k).clearAnimation();
                            }
                            // 动画完成：启用绑定页按钮（"是，开始录制" / "不需要"）
                            findViewById(R.id.btn_yes).setEnabled(true);
                            findViewById(R.id.btn_no).setEnabled(true);
                            findViewById(R.id.btn_exit).setEnabled(true);
                        }
                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }
                    });
                }
                child.startAnimation(a);
                bindIdx++;
            }
        }
        // 若 page_ask 为空则直接结束转场
        if (askPage == null || askPage.getChildCount() == 0) {
            mAnimating = false;
            mOnPage2 = false;
            mOnPage3 = true;
            mPageTiles.setVisibility(View.GONE);
            findViewById(R.id.btn_yes).setEnabled(true);
            findViewById(R.id.btn_no).setEnabled(true);
            findViewById(R.id.btn_exit).setEnabled(true);
        }
    }

    /**
     * 绑定页 → 磁贴页（返回）：绑定页元素逐行向右滑出，磁贴页元素/磁贴行逐行从左滑入
     * （磁贴页在绑定页左侧，返回时从左回来，与 slideToBinding 对称）。
     */
    private void slideBackToTiles() {
        if (mAnimating) return;
        mAnimating = true;
        final int width = mPageTiles.getWidth();
        if (width <= 0) { mAnimating = false; return; }

        mPageTiles.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_start).setEnabled(true);

        final ViewGroup tilesGroup = (ViewGroup) mPageTiles;
        final ViewGroup bindingGroup = (ViewGroup) mPageBinding;
        final ViewGroup askPage = (ViewGroup) findViewById(R.id.page_ask);

        // 先清残留动画（防止再进入时空白）
        bindingGroup.clearAnimation();
        tilesGroup.clearAnimation();
        if (askPage != null) {
            for (int i = 0; i < askPage.getChildCount(); i++) {
                askPage.getChildAt(i).clearAnimation();
            }
        }
        LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        for (int k = 0; k < tileContainer.getChildCount(); k++) {
            tileContainer.getChildAt(k).clearAnimation();
        }

        // 绑定页标题、消息、按钮向右滑出（分割线不动）
        int bindIdx = 0;
        if (askPage != null) {
            for (int i = 0; i < askPage.getChildCount(); i++) {
                if (i == 1) continue; // 分割线不动
                View child = askPage.getChildAt(i);
                TranslateAnimation a = new TranslateAnimation(0, width, 0, 0);
                a.setDuration(300);
                a.setStartOffset(bindIdx * 60);
                a.setInterpolator(new AccelerateInterpolator());
                a.setFillAfter(true);
                child.startAnimation(a);
                bindIdx++;
            }
        }
        int rowBase = bindIdx * 60 + 80;

        // 磁贴页标题、按钮从左滑入（分割线不动，磁贴容器单独处理）
        int[] skipIdx = {1, 2};
        int tileIdx = 0;
        for (int i = 0; i < tilesGroup.getChildCount(); i++) {
            if (contains(skipIdx, i)) continue;
            View child = tilesGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(-width, 0, 0, 0);
            a.setDuration(350);
            a.setStartOffset(rowBase + tileIdx * 60);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
            tileIdx++;
        }

        // 磁贴行逐行从左滑入（最后一个磁贴行挂完成监听）
        int baseDelay = rowBase + tileIdx * 60 + 80;
        final LinearLayout finalTileContainer = tileContainer;
        int lastRow = tileContainer.getChildCount() - 1;
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(-width, 0, 0, 0);
            a.setDuration(300);
            a.setStartOffset(baseDelay + i * 60);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            if (i == lastRow) {
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mAnimating = false;
                        mOnPage3 = false;
                        mOnPage2 = true;
                        mPageBinding.setVisibility(View.GONE);
                        findViewById(R.id.btn_next).setEnabled(false);
                        tilesGroup.clearAnimation();
                        bindingGroup.clearAnimation();
                        for (int j = 0; j < askPage.getChildCount(); j++) {
                            askPage.getChildAt(j).clearAnimation();
                        }
                        for (int k2 = 0; k2 < finalTileContainer.getChildCount(); k2++) {
                            finalTileContainer.getChildAt(k2).clearAnimation();
                        }
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
            }
            row.startAnimation(a);
        }
    }

    private void startRecording() {
        mRecording = true;
        mRecordIndex = 0;
        mLastRecordTime = 0L;
        KeyBindingUtil.clearAll();
        findViewById(R.id.page_ask).setVisibility(View.GONE);
        findViewById(R.id.page_record).setVisibility(View.VISIBLE);
        updatePrompt();
    }

    private void updatePrompt() {
        TextView promptText = (TextView) findViewById(R.id.prompt_text);
        TextView progressText = (TextView) findViewById(R.id.progress_text);
        if (mRecordIndex >= RECORD_ORDER.length) {
            promptText.setText(getString(R.string.keybinding_done));
            progressText.setText("");
            finishBinding();
            return;
        }
        String keyName = RECORD_NAMES[mRecordIndex];
        promptText.setText(getString(R.string.keybinding_prompt_prefix) + " " + keyName);
        progressText.setText(getString(R.string.keybinding_progress,
                mRecordIndex + 1, RECORD_ORDER.length));
    }

    /**
     * 询问页按键导航高亮：当前选择项（是/否）背景变深粉，另一项恢复原样。
     */
    private void applyAskHighlight() {
        TextView btnYes = (TextView) findViewById(R.id.btn_yes);
        TextView btnNo = (TextView) findViewById(R.id.btn_no);
        if (btnYes != null) {
            if (mAskChoiceIndex == 0) {
                btnYes.setBackgroundColor(0xFFC06090);
                btnYes.setTextColor(0xFFFFFFFF);
            } else {
                btnYes.setBackgroundResource(R.drawable.setup_ask_yes_bg);
                btnYes.setTextColor(0xFFFFFFFF);
            }
        }
        if (btnNo != null) {
            if (mAskChoiceIndex == 1) {
                btnNo.setBackgroundColor(0xFFC06090);
                btnNo.setTextColor(0xFFFFFFFF);
            } else {
                btnNo.setBackgroundResource(R.drawable.setup_ask_no_bg);
                btnNo.setTextColor(0xFFD86DA5);
            }
        }
    }

    private void finishBinding() {
        SharedPreferencesUtil.putBoolean("setup_shown", true);
        try {
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            SharedPreferencesUtil.putInt("last_version_code", versionCode);
        } catch (Exception e) {
        }
        enterMain();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        // 录制页：任意按键被录入（BACK 除外——BACK 作为退出向导的逃生通道）
        if (mOnPage3 && mRecording && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                finishBinding();
                return true;
            }
            if (mRecordIndex < RECORD_ORDER.length) {
                long now = System.currentTimeMillis();
                if (now - mLastRecordTime < 1000L) {
                    return true;
                }
                mLastRecordTime = now;
                int action = RECORD_ORDER[mRecordIndex];
                KeyBindingUtil.saveKey(action, event.getKeyCode());
                mRecordIndex++;
                updatePrompt();
                return true;
            }
            return true;
        }
        // 绑定页询问页按键导航（按键机）：方向键切换"是/否"，确认键触发
        if (mOnPage3 && !mRecording
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            int act = KeyBindingUtil.classify(event.getKeyCode());
            if (act == KeyBindingUtil.ACTION_LEFT
                    || act == KeyBindingUtil.ACTION_RIGHT
                    || act == KeyBindingUtil.ACTION_UP
                    || act == KeyBindingUtil.ACTION_DOWN) {
                if (!mAskKeyNavActive) {
                    mAskKeyNavActive = true;
                    applyAskHighlight();
                }
                mAskChoiceIndex = 1 - mAskChoiceIndex;
                applyAskHighlight();
                return true;
            } else if (act == KeyBindingUtil.ACTION_CONFIRM) {
                if (!mAskKeyNavActive) {
                    mAskKeyNavActive = true;
                    applyAskHighlight();
                }
                if (mAskChoiceIndex == 0) {
                    startRecording();
                } else {
                    finishBinding();
                }
                return true;
            }
        }
        // 磁贴页按键导航（仅按键机，触屏机不响应）：方向键移动光标，确认键选中
        if (mOnPage2 && mTiles.size() > 0
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            int act = KeyBindingUtil.classify(event.getKeyCode());
            if (act == KeyBindingUtil.ACTION_UP
                    || act == KeyBindingUtil.ACTION_DOWN
                    || act == KeyBindingUtil.ACTION_LEFT
                    || act == KeyBindingUtil.ACTION_RIGHT
                    || act == KeyBindingUtil.ACTION_CONFIRM) {
                // 首次按键即进入按键导航模式，文字高亮生效
                if (!mTileKeyNavActive) {
                    mTileKeyNavActive = true;
                    applyTileHighlight();
                }
                if (act == KeyBindingUtil.ACTION_UP) {
                    moveTileFocus(-1);
                } else if (act == KeyBindingUtil.ACTION_DOWN) {
                    moveTileFocus(1);
                } else if (act == KeyBindingUtil.ACTION_LEFT) {
                    moveTileFocus(-2);
                } else if (act == KeyBindingUtil.ACTION_RIGHT) {
                    moveTileFocus(2);
                } else if (act == KeyBindingUtil.ACTION_CONFIRM) {
                    confirmTile();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mAnimating) return;
        if (mOnPage3) {
            // 录制中按 BACK：退出向导（dispatchKeyEvent 已处理，但还是保留XD）
            if (mRecording) {
                finishBinding();
                return;
            }
            slideBackToTiles();
        } else if (mOnPage2) {
            slideToWelcome();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * 按屏幕宽度自适应磁贴列数：
     * 手机（<600dp）2 列；平板（600-900dp）3 列；大屏 TV/横屏（≥900dp）4 列。
     */
    private int computeTileCols() {
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        if (widthDp >= 900) {
            return 4;
        } else if (widthDp >= 600) {
            return 3;
        }
        return 2;
    }

    private void generateTiles() {
        LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        tileContainer.removeAllViews();

        mTileCols = computeTileCols();

        for (int i = 0; i < TAB_NAMES.length; i += mTileCols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            // 铺不满的一行从左边开始排，避免整行居中
            row.setGravity(Gravity.LEFT);
            row.setPadding(8, 4, 8, 4);

            for (int c = 0; c < mTileCols; c++) {
                int idx = i + c;
                if (idx >= TAB_NAMES.length) {
                    break;
                }
                row.addView(createTile(idx));
            }

            tileContainer.addView(row);
        }
    }

    private FrameLayout createTile(final int index) {
        // 磁贴宽度 = (屏幕宽 - 容器padding - 单磁贴左右margin) / 列数
        int paddingPx = dpToPx(8) * 2;
        int marginPx = dpToPx(8) * 2;
        int tileSizePx = (getResources().getDisplayMetrics().widthPixels - paddingPx - marginPx) / mTileCols;
        if (tileSizePx < dpToPx(120)) {
            tileSizePx = dpToPx(120);
        }

        FrameLayout tile = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileSizePx, (int) (tileSizePx * 0.7f));
        lp.setMargins(8, 8, 8, 8);
        tile.setLayoutParams(lp);
        tile.setFocusable(true);
        tile.setClickable(true);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setShape(GradientDrawable.RECTANGLE);
        normalBg.setColor(0xFFD86DA5);

        GradientDrawable pressedBg = new GradientDrawable();
        pressedBg.setShape(GradientDrawable.RECTANGLE);
        pressedBg.setColor(0xFFC06090);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBg);
        sld.addState(new int[]{}, normalBg);

        tile.setBackgroundDrawable(sld);

        TextView label = new TextView(this);
        label.setText(TAB_NAMES[index]);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        labelLp.gravity = Gravity.CENTER;
        label.setLayoutParams(labelLp);
        tile.addView(label);

        final View checkmarkOverlay = createCheckmarkOverlay();
        checkmarkOverlay.setVisibility(View.INVISIBLE);
        tile.addView(checkmarkOverlay);

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLastSelectedTile != null) {
                    View prevCheck = mLastSelectedTile.getChildAt(1);
                    if (prevCheck != null) {
                        prevCheck.setVisibility(View.INVISIBLE);
                    }
                }
                checkmarkOverlay.setVisibility(View.VISIBLE);
                mLastSelectedTile = (FrameLayout) v;
                mSelectedTab = index;
            }
        });

        mTiles.add(tile);
        return tile;
    }

    /**
     * 刷新磁贴按键导航高亮：仅按键机按键后生效（mTileKeyNavActive）。
     * 当前焦点磁贴背景变为深粉色，其余恢复原粉色；
     * 焦点在"下一步/开始使用"按钮时，按钮变为深粉底白字，否则恢复透明粉字。
     */
    private void applyTileHighlight() {
        if (!mTileKeyNavActive) {
            return;
        }
        for (int i = 0; i < mTiles.size(); i++) {
            FrameLayout tile = mTiles.get(i);
            if (tile == null) {
                continue;
            }
            tile.setBackgroundColor(i == mTileFocusIndex ? 0xFFC06090 : 0xFFD86DA5);
        }
        if (mBtnStart != null) {
            boolean focusStart = (mTileFocusIndex >= mTiles.size());
            if (focusStart) {
                mBtnStart.setBackgroundColor(0xFFC06090);
                mBtnStart.setTextColor(0xFFFFFFFF);
            } else {
                mBtnStart.setBackgroundDrawable(null);
                mBtnStart.setTextColor(0xFFD86DA5);
            }
        }
    }

    /**
     * 移动磁贴光标。direction：-1 上，+1 下，-2 左，+2 右。
     * 焦点索引范围 [0, mTiles.size()]，其中 mTiles.size() 表示"下一步"按钮。
     */
    private void moveTileFocus(int direction) {
        if (mTiles.size() == 0) {
            return;
        }
        int next = mTileFocusIndex;
        boolean onStartBtn = (mTileFocusIndex >= mTiles.size());
        if (onStartBtn) {
            // 焦点在"下一步"按钮：只允许向上回最后一排
            if (direction == -1) {
                int lastRowFirst = ((mTiles.size() - 1) / mTileCols) * mTileCols;
                next = Math.min(lastRowFirst + (mTileFocusIndex - mTiles.size()), mTiles.size() - 1);
            } else {
                return;
            }
        } else if (direction == 1 || direction == -1) {
            int nextRow = (mTileFocusIndex / mTileCols) + direction;
            if (nextRow < 0) {
                return; // 已是最上一行
            }
            int firstInRow = nextRow * mTileCols;
            if (firstInRow >= mTiles.size()) {
                // 下一行超出：若向下则移到"下一步"按钮
                if (direction == 1) {
                    next = mTiles.size();
                } else {
                    return;
                }
            } else {
                next = Math.min(firstInRow + (mTileFocusIndex % mTileCols), mTiles.size() - 1);
            }
        } else if (direction == 2) {
            if ((mTileFocusIndex % mTileCols) == mTileCols - 1
                    || mTileFocusIndex == mTiles.size() - 1) {
                return;
            }
            next = mTileFocusIndex + 1;
        } else if (direction == -2) {
            if (mTileFocusIndex % mTileCols == 0) {
                return;
            }
            next = mTileFocusIndex - 1;
        }
        if (next != mTileFocusIndex) {
            mTileFocusIndex = next;
            applyTileHighlight();
        }
    }

    /**
     * 确认选中当前聚焦磁贴（按键机）；焦点在"下一步"按钮时触发该按钮。
     */
    private void confirmTile() {
        if (mTileFocusIndex < 0) {
            return;
        }
        if (mTileFocusIndex >= mTiles.size()) {
            // 焦点在"下一步/开始使用"按钮
            if (mBtnStart != null) {
                mBtnStart.performClick();
            }
            return;
        }
        FrameLayout tile = mTiles.get(mTileFocusIndex);
        if (tile != null) {
            tile.performClick();
        }
    }

    private View createCheckmarkOverlay() {
        ImageView checkIcon = new ImageView(this);
        checkIcon.setImageResource(R.drawable.abs__ic_cab_done_holo_dark);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dpToPx(28), dpToPx(28)
        );
        lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        lp.setMargins(0, 0, dpToPx(8), dpToPx(8));
        checkIcon.setLayoutParams(lp);
        return checkIcon;
    }

    private void slideToTiles() {
        if (mAnimating) return;
        mAnimating = true;
        final int width = mPageWelcome.getWidth();
        if (width <= 0) { mAnimating = false; return; }

        final ScrollView tileScroll = (ScrollView) findViewById(R.id.tile_scroll);
        if (tileScroll != null) tileScroll.scrollTo(0, 0);

        mPageTiles.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_next).setEnabled(false);
        findViewById(R.id.btn_start).setEnabled(false);

        final ViewGroup welcomeGroup = (ViewGroup) mPageWelcome;
        final ViewGroup tilesGroup = (ViewGroup) mPageTiles;

        // 第1页各元素向左滑出，逐行延迟（分割线不动）
        int[] outDurs = {500, 0, 380, 320};
        for (int i = 0; i < welcomeGroup.getChildCount() && i < outDurs.length; i++) {
            if (outDurs[i] == 0) continue;
            View child = welcomeGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, -width, 0, 0);
            a.setDuration(outDurs[i]);
            a.setStartOffset(i * 80);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
        }

        int baseDelay = welcomeGroup.getChildCount() * 80 + 120;

        // 第2页标题、按钮滑入（分割线不动，磁贴容器单独处理）
        int[] skipIdx = {1, 2};
        int tileIdx = 0;
        for (int i = 0; i < tilesGroup.getChildCount(); i++) {
            if (contains(skipIdx, i)) continue;
            View child = tilesGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
            a.setDuration(450);
            a.setStartOffset(baseDelay + tileIdx * 80);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
            tileIdx++;
        }

        // 磁贴行逐行滑入（速度与 slideToBinding 的磁贴行滑出一致：duration 300、行间隔 60），
        // 完成监听器挂到最后一个磁贴行，保证所有行都滑入后才结束转场。
        final LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        int rowBase = baseDelay + tileIdx * 80 + 60;
        int lastRow = tileContainer.getChildCount() - 1;
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
            a.setDuration(300);
            a.setStartOffset(rowBase + i * 60);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            if (i == lastRow) {
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mAnimating = false;
                        mOnPage2 = true;
                        mPageWelcome.setVisibility(View.GONE);
                        findViewById(R.id.btn_start).setEnabled(true);
                        clearChildAnimations(welcomeGroup);
                        clearChildAnimations(tilesGroup);
                        for (int j = 0; j < tileContainer.getChildCount(); j++) {
                            tileContainer.getChildAt(j).clearAnimation();
                        }
                        applyPendingChangelog();
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
            }
            row.startAnimation(a);
        }
    }

    private void generateChangelog() {
        final LinearLayout container = (LinearLayout) findViewById(R.id.tile_container);
        container.removeAllViews();

        final TextView loadingText = new TextView(this);
        loadingText.setText(getString(R.string.setupactivity_settext_6b63));
        loadingText.setTextColor(0xFF999999);
        loadingText.setTextSize(15);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, dpToPx(40), 0, 0);
        loadingText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(loadingText);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final JSONArray changelog = fetchChangelog();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        mPendingChangelog = changelog;
                        renderChangelogIfReady();
                    }
                });
            }
        }).start();
    }

    private void applyPendingChangelog() {
        if (mPendingChangelog != null || mPendingChangelogFailed) {
            renderChangelogIfReady(true);
        }
    }

    private void renderChangelogIfReady() {
        renderChangelogIfReady(false);
    }

    private void renderChangelogIfReady(boolean animateRows) {
        // 转场中不填充，避免 removeAllViews 打断滑入动画
        if (mAnimating || isFinishing()) return;
        final LinearLayout container = (LinearLayout) findViewById(R.id.tile_container);
        container.removeAllViews();
        final JSONArray changelog = mPendingChangelog;
        mPendingChangelog = null;
        mPendingChangelogFailed = false;
        if (changelog == null) {
            TextView errorText = new TextView(SetupActivity.this);
            errorText.setText("\u83B7\u53D6\u66F4\u65B0\u65E5\u5FD7\u5931\u8D25");
            errorText.setTextColor(0xFF999999);
            errorText.setTextSize(15);
            errorText.setGravity(Gravity.CENTER);
            errorText.setPadding(0, dpToPx(40), 0, 0);
            errorText.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            container.addView(errorText);
            return;
        }
        int textColor = 0xFF333333;
        int pinkColor = 0xFFD86DA5;
        for (int i = 0; i < changelog.length(); i++) {
            String line = changelog.optString(i, "");
            if (line.length() == 0) {
                View spacer = new View(SetupActivity.this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(12)));
                container.addView(spacer);
            } else if (line.startsWith("-")) {
                TextView tv = new TextView(SetupActivity.this);
                tv.setText(line);
                tv.setTextColor(textColor);
                tv.setTextSize(15);
                tv.setPadding(dpToPx(24), dpToPx(4), dpToPx(16), dpToPx(4));
                tv.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                container.addView(tv);
            } else {
                TextView tv = new TextView(SetupActivity.this);
                tv.setText(line);
                tv.setTextColor(pinkColor);
                tv.setTextSize(16);
                tv.setTypeface(null, Typeface.BOLD);
                tv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(4));
                tv.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                container.addView(tv);
            }
        }

        // 若日志在转场结束后才填充，则逐行补一次滑入动画
        if (animateRows) {
            animateRowsIn(container);
        }
    }

    private void animateRowsIn(final LinearLayout container) {
        final int width = mPageTiles != null ? mPageTiles.getWidth() : 0;
        if (width <= 0) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
            a.setDuration(300);
            a.setStartOffset(i * 40);
            a.setInterpolator(new DecelerateInterpolator());
            row.startAnimation(a);
        }
    }

    private JSONArray fetchChangelog() {
        String[] urls = {
                "http://www.biliclassic.cn/api/version.json",
                "http://7891vip.top/biliclassic/update.php"
        };
        for (String urlStr : urls) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "BiliClassic");
                if (conn.getResponseCode() != 200) continue;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                JSONObject json = new JSONObject(sb.toString());
                JSONObject versions = json.optJSONObject("versions");
                if (versions == null) continue;
                JSONObject branch = versions.optJSONObject("0.4");
                if (branch == null) continue;
                JSONArray changelog = branch.optJSONArray("changelog");
                if (changelog != null && changelog.length() > 0) {
                    return changelog;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    private static boolean contains(int[] arr, int val) {
        for (int v : arr) if (v == val) return true;
        return false;
    }

    private void clearChildAnimations(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).clearAnimation();
        }
    }

    private void slideToWelcome() {
        if (mAnimating) return;
        mAnimating = true;
        final int width = mPageWelcome.getWidth();
        if (width <= 0) { mAnimating = false; return; }

        mPageWelcome.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_start).setEnabled(false);

        final ViewGroup welcomeGroup = (ViewGroup) mPageWelcome;
        final ViewGroup tilesGroup = (ViewGroup) mPageTiles;
        final LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);

        // 磁贴行先向右滑出
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, width, 0, 0);
            a.setDuration(300);
            a.setStartOffset(i * 60);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            row.startAnimation(a);
        }

        int rowBase = tileContainer.getChildCount() * 60 + 80;

        // 第2页标题、按钮滑出（分割线不动）
        int[] skipIdx = {1, 2};
        int tileIdx = 0;
        for (int i = 0; i < tilesGroup.getChildCount(); i++) {
            if (contains(skipIdx, i)) continue;
            View child = tilesGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(0, width, 0, 0);
            a.setDuration(350);
            a.setStartOffset(rowBase + tileIdx * 60);
            a.setInterpolator(new AccelerateInterpolator());
            a.setFillAfter(true);
            child.startAnimation(a);
            tileIdx++;
        }

        int baseDelay = rowBase + tileIdx * 60 + 80;

        // 第1页各元素从左滑入（分割线不动）
        int[] inDurs = {450, 0, 350, 300};
        for (int i = 0; i < welcomeGroup.getChildCount() && i < inDurs.length; i++) {
            if (inDurs[i] == 0) continue;
            View child = welcomeGroup.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(-width, 0, 0, 0);
            a.setDuration(inDurs[i]);
            a.setStartOffset(baseDelay + i * 80);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            if (i == inDurs.length - 1 || (i == welcomeGroup.getChildCount() - 1)) {
                a.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mAnimating = false;
                        mOnPage2 = false;
                        mPageTiles.setVisibility(View.GONE);
                        findViewById(R.id.btn_next).setEnabled(true);
                        clearChildAnimations(welcomeGroup);
                        clearChildAnimations(tilesGroup);
                        for (int j = 0; j < tileContainer.getChildCount(); j++) {
                            tileContainer.getChildAt(j).clearAnimation();
                        }
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
            }
            child.startAnimation(a);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
