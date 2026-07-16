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

import tv.biliclassic.util.SharedPreferencesUtil;

public class SetupActivity extends BaseActivity {

    private View mPageWelcome;
    private View mPageTiles;
    private int mSelectedTab = -1;
    private FrameLayout mLastSelectedTile = null;
    private boolean mAnimating = false;
    private boolean mOnPage2 = false;

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
            titleText.setText("更新完成");
            btnText.setText("欢迎回到 BiliClassic");
        } else {
            titleText.setText("初次见面");
        }

        mPageWelcome = findViewById(R.id.page_welcome);
        mPageTiles = findViewById(R.id.page_tiles);

        TextView btnNext = (TextView) findViewById(R.id.btn_next);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                slideToTiles();
            }
        });

        TextView page2Title = (TextView) findViewById(R.id.page2_title);
        if (isUpgrade) {
            page2Title.setText("更新日志");
            generateChangelog();
        } else {
            page2Title.setText("选择初始主页");
            generateTiles();
        }

        final TextView btnStart = (TextView) findViewById(R.id.btn_start);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAnimating) return;
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
        });
    }

    private void finishSetup(View btnStart) {
        btnStart.setEnabled(false);
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
        Intent intent = new Intent(SetupActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void generateTiles() {
        LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        tileContainer.removeAllViews();

        for (int i = 0; i < TAB_NAMES.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(16, 4, 16, 4);

            row.addView(createTile(i));
            if (i + 1 < TAB_NAMES.length) {
                row.addView(createTile(i + 1));
            }

            tileContainer.addView(row);
        }
    }

    private FrameLayout createTile(final int index) {
        int tileSizePx = (getResources().getDisplayMetrics().widthPixels - 64) / 2;

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

        return tile;
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

        // 磁贴行逐行滑入（更晚、更慢）
        final LinearLayout tileContainer = (LinearLayout) findViewById(R.id.tile_container);
        int rowBase = baseDelay + tileIdx * 80 + 60;
        for (int i = 0; i < tileContainer.getChildCount(); i++) {
            View row = tileContainer.getChildAt(i);
            TranslateAnimation a = new TranslateAnimation(width, 0, 0, 0);
            a.setDuration(400);
            a.setStartOffset(rowBase + i * 100);
            a.setInterpolator(new DecelerateInterpolator());
            a.setFillAfter(true);
            if (i == tileContainer.getChildCount() - 1) {
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
        loadingText.setText("正在获取更新日志...");
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
                        container.removeAllViews();
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
                    }
                });
            }
        }).start();
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
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
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

    @Override
    public void onBackPressed() {
        if (mAnimating) return;
        if (mOnPage2) {
            slideToWelcome();
        } else {
            super.onBackPressed();
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
