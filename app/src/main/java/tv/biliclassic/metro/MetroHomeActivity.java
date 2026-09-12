package tv.biliclassic.metro;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import tv.biliclassic.BaseActivity;
import tv.biliclassic.R;
import tv.biliclassic.SearchActivity;
import tv.biliclassic.SettingsActivity;

/**
 * Metro 主题主页：仿 WM / PMC 风格。
 * 白底 + 左上角粉色大标题 + 右侧竖排选择菜单 + 右下角 bilibili_tv 水印。
 * 交互：默认无高亮；手指在选项上滑动会逐个高亮、松手高亮消失、仅点击才跳转；
 * 选项超出屏幕时 ScrollView 可滚动；按键机（方向键）仍可用焦点高亮导航。
 */
public class MetroHomeActivity extends BaseActivity {

    private static final int PINK = 0xFFD86DA5;
    private static final int PINK_LIGHT = 0xFFF2C6DE;
    private static final long FLIP_MS = 350;

    private ScrollView mScroll;
    private LinearLayout mMenu;
    private GradientDrawable mHighlightBg;
    private final java.util.List<View> mItems = new java.util.ArrayList<View>();
    private Class<?>[] mTargets;
    private int mFocusIndex = -1;
    private MetroRecommendFragment mRecommendFrag;
    private MetroProfileFragment mProfileFrag;
    private MetroHistoryFragment mHistoryFrag;
    private MetroFavoriteFragment mFavoriteFrag;
    private MetroFolderVideoFragment mFolderVideoFrag;
    // 返回目标：收藏夹视频页按返回时翻回"我的收藏"页
    private android.support.v4.app.Fragment mDetailBackTarget = null;
    // 详情→详情整页转门进行中（期间屏蔽返回/再进入）
    private boolean mDetailTurnAnim = false;
    private MetroTurnPage mCurrentPage;
    private boolean mShowingMenu = true;
    private MetroTiltEffect mTiltEffect;
    private TurnLayout mHomePage;
    private TurnLayout mRecommendPage;
    /** 触摸悬停跟随高亮：按下坐标/行 与 系统最小滑动判定。 */
    private float mTouchDownY = -1f;
    private int mTouchDownIdx = -1;
    private int mTouchSlop = 16;

    // 可打断整页转门状态（mTurn: 0=主页铺平, 1=推荐页铺平）
    private boolean mTurnAnim = false;
    private float mTurn = 0f;
    private float mTurnAmb = 0f;
    private long mTurnStart = 0L;
    private int mTurnTarget = 0;
    private final android.os.Handler mTurnHandler = new android.os.Handler();
    /** 本次转门是否已对 Fragment 容器做过强制重排（避免每帧重复，开头那次就够）。 */
    /** 本次进入推荐页是否已触发磁贴滑入（避免每帧重复播放）。 */
    private boolean mStaggerTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.metro_home);

        mScroll = (ScrollView) findViewById(R.id.menu_scroll);
        mMenu = (LinearLayout) findViewById(R.id.metro_menu);
        mHomePage = (TurnLayout) findViewById(R.id.home_page);
        mRecommendPage = (TurnLayout) findViewById(R.id.recommend_page);

        mHighlightBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PINK_LIGHT, PINK});
        // ViewConfiguration.get(Context)/getScaledTouchSlop() 是 API 3+，直接在字节码里调用会被
        // 低版本 verifier 拒绝整个类，统一走 ViewConfigHelper 反射兼容
        android.view.ViewConfiguration vc = android.support.v4.view.ViewConfigHelper.get(this);
        mTouchSlop = android.support.v4.view.ViewConfigHelper.getScaledTouchSlop(vc);

        loadMetroBackground();

        // 夜间模式：无自定义背景图时自动把底色调成黑色，避免刺眼（有自定义图则优先显示图片）
        String bgPath = tv.biliclassic.SettingsActivity.getMetroBgPath();
        if ((bgPath == null || bgPath.length() == 0) && isNightMode()) {
            View root = findViewById(R.id.root_layout);
            if (root != null) root.setBackgroundColor(Color.BLACK);
        }
        buildMenu();

        // 通用重力/陀螺仪微动：作用于主页/推荐/个人中心共用的内容层（page_holder）
        View tiltTarget = findViewById(R.id.page_holder);
        if (tiltTarget != null) {
            mTiltEffect = new MetroTiltEffect(this, tiltTarget, dpToPx(4));
            mTiltEffect.start();
        }

        // 一次性把详情 Fragment 都 add 进容器（不 replace），之后切换只用 visibility。
        // 视图在 onCreate 时按真实容器尺寸 measure 一次并常驻，永不销毁重建。
        mRecommendFrag = new MetroRecommendFragment();
        mProfileFrag = new MetroProfileFragment();
        mHistoryFrag = new MetroHistoryFragment();
        mFavoriteFrag = new MetroFavoriteFragment();
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.add(R.id.metro_fragment_container, mRecommendFrag, "recommend");
        ft.add(R.id.metro_fragment_container, mProfileFrag, "profile");
        ft.add(R.id.metro_fragment_container, mHistoryFrag, "history");
        ft.add(R.id.metro_fragment_container, mFavoriteFrag, "favorite");
        ft.commitAllowingStateLoss();
        fm.executePendingTransactions();
        hideFragmentView(mRecommendFrag);
        hideFragmentView(mProfileFrag);
        hideFragmentView(mHistoryFrag);
        hideFragmentView(mFavoriteFrag);

        // 右下角电视水印
        ImageView watermark = (ImageView) findViewById(R.id.tv_watermark);
        if (watermark != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int longSidePx = Math.max(dm.widthPixels, dm.heightPixels);
            int size = Math.round(longSidePx * 0.70f);
            int over = Math.round(longSidePx * 0.12f);
            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) watermark.getLayoutParams();
            lp.width = size;
            lp.height = size;
            lp.rightMargin = -over;
            lp.bottomMargin = -over;
            watermark.setLayoutParams(lp);
        }

        // 入场动画
        if (mHomePage != null) {
            mHomePage.post(new Runnable() {
                @Override
                public void run() {
                    playHomeEntry();
                }
            });
        }

        // 崩溃报告检查（与 Classic 主页一致：Metro 用户也要能看到上次崩溃）
        checkAndShowCrashDialog();
    }

    /** 检查并弹出上次崩溃的报告对话框（Classic 主页同款逻辑） */
    private void checkAndShowCrashDialog() {
        if (!tv.biliclassic.util.CrashHandler.hasPendingCrashReport(this)) {
            return;
        }
        final String crashLog = tv.biliclassic.util.CrashHandler.consumeLatestCrashLog(this);
        if (crashLog == null || crashLog.length() == 0) {
            return;
        }
        new android.app.AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(tv.biliclassic.util.DialogUtil.wrap(this)))
                .setTitle(getString(R.string.last_abnormal_exit))
                .setMessage(getString(R.string.last_crash_prompt))
                .setPositiveButton("查看", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Intent intent = new Intent(MetroHomeActivity.this, tv.biliclassic.CrashReportActivity.class);
                        intent.putExtra("crash_info", crashLog);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("忽略", null)
                .show();
    }

    /** 启动入场 */
    private void playHomeEntry() {
        if (mHomePage == null) return;
        mHomePage.setTurn(90f, true);
        mEntryStart = android.os.SystemClock.uptimeMillis();
        mEntryHandler.removeCallbacks(mEntryTick);
        mEntryHandler.post(mEntryTick);
    }

    private final android.os.Handler mEntryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long mEntryStart = 0L;
    private final Runnable mEntryTick = new Runnable() {
        @Override
        public void run() {
            if (mHomePage == null) return;
            float t = (android.os.SystemClock.uptimeMillis() - mEntryStart) / 480f;
            if (t > 1f) t = 1f;
            mHomePage.setTurn(90f * (1f - easeInOut(t)), true);
            if (t < 1f) {
                mEntryHandler.postDelayed(mEntryTick, 16L);
            } else {
                mHomePage.setTurn(0f, true);
            }
        }
    };
    /** Metro 主题自定义背景 */
    private void loadMetroBackground() {
        final String path = tv.biliclassic.SettingsActivity.getMetroBgPath();
        if (path == null || path.length() == 0) return;
        final android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        final int sw = dm.widthPixels;
        final int sh = dm.heightPixels;
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeFile(path, o);
                    int outW = o.outWidth;
                    int outH = o.outHeight;
                    if (outW <= 0 || outH <= 0) return;
                    int sample = 1;
                    while (outW / sample > sw || outH / sample > sh) {
                        sample <<= 1;
                    }
                    android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                    o2.inSampleSize = sample;
                    final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(path, o2);
                    if (bmp == null) return;
                    final View root = findViewById(R.id.root_layout);
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            if (root != null && bmp != null && !bmp.isRecycled()) {
                                android.graphics.drawable.BitmapDrawable bd =
                                        new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
                                root.setBackgroundDrawable(bd);   // BitmapDrawable 默认 FILL，拉伸铺满全屏
                            }
                        }
                    });
                } catch (Exception e) {
                    // 背景加载失败忽略，保持默认白底
                }
            }
        }).start();
    }

    private void buildMenu() {
        final Object[][] entries = {
                {"个人中心", MetroProfileFragment.class},   // index0 由 navigate 走详情页，此处仅占位
                {"搜索视频", SearchActivity.class},
                {"推荐视频", MetroRecommendFragment.class},
                {"历史记录", android.support.v4.app.Fragment.class},   // index3 由 navigate 走详情页（历史 Fragment），此处仅占位
                {"收藏视频", android.support.v4.app.Fragment.class},   // index4 由 navigate 走详情页（收藏 Fragment），此处仅占位
                {"功能设置", SettingsActivity.class},
        };
        mTargets = new Class<?>[entries.length];

        for (int i = 0; i < entries.length; i++) {
            final String label = (String) entries[i][0];
            mTargets[i] = (Class<?>) entries[i][1];

            TextView row = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52));
            lp.setMargins(0, dpToPx(2), 0, dpToPx(2));
            row.setLayoutParams(lp);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(20), 0, dpToPx(12), 0);
            row.setText(label);
            row.setTextSize(28);
            row.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            row.setTextColor(PINK);
            row.setFocusable(true);
            row.setFocusableInTouchMode(false);

            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    applyHighlight(v, hasFocus);
                }
            });

            // 低版本(API<4)老触摸屏抖动大，ScrollView 的 "dy < touchSlop 才算点击" 判定经常失败，
            // 表现出来就是"只有高亮、点了不跳转"。这里给每个条目自身挂点击（点击不受位移抖动影响），
            // 并自行处理按压高亮，低版本上不再依赖父容器的手势判定。
            if (tv.biliclassic.util.SdkHelper.getSdkInt() < 4) {
                final int index = i;
                row.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent ev) {
                        int act = ev.getAction() & MotionEvent.ACTION_MASK;
                        if (act == MotionEvent.ACTION_DOWN) {
                            applyHighlight(v, true);
                        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                            applyHighlight(v, false);
                        }
                        return false;
                    }
                });
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        navigate(index);
                    }
                });
            }

            mMenu.addView(row);
            mItems.add(row);
        }

        // 触摸跟随高亮
        if (mScroll != null) {
            mScroll.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    int act = ev.getAction() & MotionEvent.ACTION_MASK;
                    if (act == MotionEvent.ACTION_DOWN) {
                        mTouchDownY = ev.getY();
                        mTouchDownIdx = findItemAt((int) ev.getY() + mScroll.getScrollY());
                        if (mTouchDownIdx >= 0) highlightOnly(mTouchDownIdx);
                    } else if (act == MotionEvent.ACTION_MOVE) {
                        int idx = findItemAt((int) ev.getY() + mScroll.getScrollY());
                        if (idx >= 0) highlightOnly(idx);
                    } else if (act == MotionEvent.ACTION_UP) {
                        float dy = Math.abs(ev.getY() - mTouchDownY);
                        int idx = findItemAt((int) ev.getY() + mScroll.getScrollY());
                        if (dy < mTouchSlop && idx >= 0 && idx == mTouchDownIdx) {
                            navigate(idx);
                        }
                        clearHighlight();
                    } else if (act == MotionEvent.ACTION_CANCEL) {
                        clearHighlight();
                    }
                    return false;   // ScrollView
                }
            });
        }
    }

    private int findItemAt(int contentY) {
        if (mItems == null) return -1;
        for (int i = 0; i < mItems.size(); i++) {
            View row = mItems.get(i);
            if (row == null || row.getHeight() <= 0) continue;
            int top = row.getTop();
            if (contentY >= top && contentY < top + row.getHeight()) {
                return i;
            }
        }
        return -1;
    }

    private void highlightOnly(int index) {
        for (int i = 0; i < mItems.size(); i++) {
            applyHighlight(mItems.get(i), i == index);
        }
    }

    private void clearHighlight() {
        for (int i = 0; i < mItems.size(); i++) {
            applyHighlight(mItems.get(i), false);
        }
    }

    /** 单个淡入动画（无用的shit；现在是整页转门）。 */
    private Animation buildSlideUpAnimation() {
        AlphaAnimation alpha = new AlphaAnimation(0f, 1f);
        alpha.setDuration(600);
        alpha.setInterpolator(new AccelerateDecelerateInterpolator());
        return alpha;
    }

    private void navigate(int index) {
        if (index == 0) { // 个人中心 → 转门翻入详情页（个人中心 Fragment）
            openDetail(mProfileFrag);
            return;
        }
        if (index == 2) { // 推荐视频 → 转门翻入详情页（推荐视频 Fragment）
            openDetail(mRecommendFrag);
            return;
        }
        if (index == 3) { // 历史记录 → 转门翻入详情页（历史记录 Fragment）
            openDetail(mHistoryFrag);
            return;
        }
        if (index == 4) { // 收藏视频 → 转门翻入详情页（我的收藏 Fragment）
            openDetail(mFavoriteFrag);
            return;
        }
        if (index >= 0 && index < mTargets.length) {
            startActivity(new Intent(MetroHomeActivity.this, mTargets[index]));
        }
    }

    // ===== 整页转门 =====

    /** 打开详情页：隐藏其余详情 Fragment，只显示目标页（从主菜单进入） */
    private void openDetail(android.support.v4.app.Fragment f) {
        if (!mShowingMenu || mTurnAnim || f == null) return;
        if (mHomePage == null || mRecommendPage == null) return;
        mCurrentPage = (MetroTurnPage) f;
        // 从主菜单发起的一级导航：清空详情返回栈
        mDetailBackTarget = null;
        hideFragmentView(mProfileFrag);
        hideFragmentView(mRecommendFrag);
        hideFragmentView(mHistoryFrag);
        hideFragmentView(mFavoriteFrag);
        hideFragmentView(mFolderVideoFrag);
        showFragmentView(f);
        startTurn(1);
    }

    /**
     * 打开指定收藏夹的视频页（从"我的收藏"点收藏夹进入）：
     * 复用已有实例（setArguments 对已添加的 Fragment 会抛 already active 闪退），
     * 返回时翻回"我的收藏"页。详情→详情瞬时切换，无转门/入场动画。
     */
    public void openFavoriteFolder(long fid, String name) {
        if (isFinishing() || mTurnAnim || mDetailTurnAnim) return;
        MetroFolderVideoFragment frag =
                (MetroFolderVideoFragment) getSupportFragmentManager().findFragmentByTag("folder_video");
        if (frag == null) {
            frag = new MetroFolderVideoFragment();
            Bundle args = new Bundle();
            args.putLong("fid", fid);
            args.putString("name", name != null ? name : "");
            frag.setArguments(args); // 仅在添加前调用一次
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.metro_fragment_container, frag, "folder_video");
            ft.commitAllowingStateLoss();
            getSupportFragmentManager().executePendingTransactions();
            // 立即隐藏：否则新页面会在"翻走"阶段就叠在收藏列表上（标题/内容重叠）
            hideFragmentView(frag);
        } else {
            frag.updateFolder(fid, name);
        }
        mFolderVideoFrag = frag;
        // 返回目标 = 我的收藏页
        mDetailBackTarget = mFavoriteFrag;
        // 详情→详情：整页翻走 → 换内容 → 翻入
        startDetailTurnTo(frag);
    }

    /**
     * 详情↔详情整页转门（与主菜单↔详情同感的"整页翻走"）：
     * 当前详情页 0→90 翻走 → 页面侧立时交换内容 → 新页 90→0 翻入。
     */
    private void startDetailTurnTo(final android.support.v4.app.Fragment target) {
        if (mTurnAnim || mDetailTurnAnim) return;
        final TurnLayout page = mRecommendPage;
        if (page == null || target == null) return;
        mDetailTurnAnim = true;
        animateDetailTurn(page, 0f, 90f, new Runnable() {
            @Override
            public void run() {
                // 页面侧立（视觉上不可见）时交换内容
                hideAllDetails();
                showFragmentView(target);
                mCurrentPage = (MetroTurnPage) target;
                animateDetailTurn(page, 90f, 0f, new Runnable() {
                    @Override
                    public void run() {
                        mDetailTurnAnim = false;
                    }
                });
            }
        });
    }

    /** 详情容器按角度段旋转（easeInOut），结束后回调 */
    private void animateDetailTurn(final TurnLayout page, float from, final float to, final Runnable done) {
        final long start = android.os.SystemClock.uptimeMillis();
        final float fFrom = from;
        final float duration = 380f;
        page.setVisibility(View.VISIBLE);
        page.post(new Runnable() {
            @Override
            public void run() {
                float t = (android.os.SystemClock.uptimeMillis() - start) / duration;
                if (t > 1f) t = 1f;
                page.setTurn(fFrom + (to - fFrom) * easeInOut(t), true);
                if (t < 1f) {
                    page.postDelayed(this, 16L);
                } else {
                    done.run();
                }
            }
        });
    }

    /** 隐藏全部详情 Fragment */
    private void hideAllDetails() {
        hideFragmentView(mProfileFrag);
        hideFragmentView(mRecommendFrag);
        hideFragmentView(mHistoryFrag);
        hideFragmentView(mFavoriteFrag);
        hideFragmentView(mFolderVideoFrag);
    }

    /** 显示指定 Fragment 根视图 */
    private void showFragmentView(android.support.v4.app.Fragment f) {
        if (f == null) return;
        android.view.View v = f.getView();
        if (v == null) return;
        v.setVisibility(View.VISIBLE);
        android.view.ViewGroup parent = (android.view.ViewGroup) v.getParent();
        if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
            int cw = parent.getWidth();
            int ch = parent.getHeight();
            if (v.getWidth() != cw || v.getHeight() != ch) {
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(cw, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(ch, android.view.View.MeasureSpec.EXACTLY));
                v.layout(0, 0, cw, ch);
            }
        }
    }

    /** 隐藏指定 Fragment 根视图。 */
    private void hideFragmentView(android.support.v4.app.Fragment f) {
        if (f == null) return;
        android.view.View v = f.getView();
        if (v != null) v.setVisibility(View.GONE);
    }

    /** 关闭详情页：目标 → 0；若正在动画则从当前进度反向回放。 */
    private void closeDetail() {
        if (mShowingMenu || mTurnAnim) return;
        if (mHomePage == null || mRecommendPage == null) return;
        startTurn(0);
    }

    /** 启动/反转转门 支持真打断 */
    private void startTurn(int target) {
        mTurnAmb = mTurn;                               // 动画起点 = 当前进度
        mTurnStart = android.os.SystemClock.uptimeMillis();
        mTurnTarget = target;
        if (!mTurnAnim) {
            mTurnAnim = true;
            mStaggerTriggered = false;
        }
        mTurnHandler.removeCallbacks(mTurnFrame);
        mTurnHandler.post(mTurnFrame);
    }

    private void runTurnFrame() {
        int target = mTurnTarget;
        float dur = (target == 0) ? 420f : 480f;
        float t = (android.os.SystemClock.uptimeMillis() - mTurnStart) / dur;
        if (t > 1f) t = 1f;
        mTurn = mTurnAmb + (target - mTurnAmb) * easeInOut(t);
        renderTurn();

        if (t >= 1f) {
            mTurnAnim = false;
            mTurn = target;
            renderTurn();
            mShowingMenu = (target == 0);
        } else {
            mTurnHandler.postDelayed(mTurnFrame, 16L);
        }
    }

    private final Runnable mTurnFrame = new Runnable() {
        @Override
        public void run() {
            runTurnFrame();
        }
    };

    /** 每段自家缓入缓出（三次曲线：两端慢、中间快），用于各页自身的"飞入/翻出"。 */
    private float easeInOut(float t) {
        if (t < 0.5f) {
            return 4f * t * t * t;
        }
        return 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
    }

    /** 旋转真实页面（双重缓入缓出） */
    private void renderTurn() {
        if (mHomePage == null || mRecommendPage == null) return;
        float p = mTurn;

        if (p <= 0.5f) {
            // 前半段
            float h = easeInOut(p * 2f);
            float homeAngle = 90f * h;
            mHomePage.setVisibility(View.VISIBLE);
            mHomePage.setTurn(homeAngle, true);
            mRecommendPage.setVisibility(View.INVISIBLE);
            mRecommendPage.setTurn(90f, true);
        } else {
            // 后半段
            float q = easeInOut((p - 0.5f) * 2f);
            float recAngle = 90f * (1f - q);
            mHomePage.setVisibility(View.INVISIBLE);
            mHomePage.setTurn(90f, true);
            mRecommendPage.setVisibility(View.VISIBLE);
            mRecommendPage.setTurn(recAngle, true);
            if (!mStaggerTriggered && mCurrentPage != null) {
                mStaggerTriggered = true;
                if (mTurnTarget == 1) {
                    mCurrentPage.animateTurnIn();
                } else {
                    mCurrentPage.animateTurnOut();
                }
            }
        }

        if (mTiltEffect != null) {
            mTiltEffect.reapply();
        }
    }

    /** 打断并回到主页：正在动画则从当前进度反向回放；否则复位到主页。 */
    private void cancelToMenu() {
        if (mTurnAnim) {
            startTurn(0);
            return;
        }
        if (mHomePage != null) { mHomePage.setVisibility(View.VISIBLE); mHomePage.clearTurn(); }
        if (mRecommendPage != null) { mRecommendPage.setVisibility(View.INVISIBLE); mRecommendPage.clearTurn(); }
        mShowingMenu = true;
        mTurn = 0f;
        mDetailBackTarget = null;
    }

    private void applyHighlight(View row, boolean on) {
        if (on) {
            row.setBackgroundDrawable(mHighlightBg);
            ((TextView) row).setTextColor(Color.WHITE);
            row.setPadding(dpToPx(24), 0, dpToPx(12), 0);
        } else {
            row.setBackgroundColor(Color.TRANSPARENT);
            ((TextView) row).setTextColor(PINK);
            row.setPadding(dpToPx(20), 0, dpToPx(12), 0);
        }
    }

    /** 方向键移动菜单焦点（按键机）。 */
    private void moveFocus(int delta) {
        if (mItems.size() == 0) return;
        if (mFocusIndex == -1) {
            mFocusIndex = 0;
            View cur = mItems.get(0);
            applyHighlight(cur, true);
            cur.requestFocus();
            return;
        }
        int next = mFocusIndex + delta;
        if (next < 0) next = 0;
        if (next >= mItems.size()) next = mItems.size() - 1;
        if (next == mFocusIndex) return;
        applyHighlight(mItems.get(mFocusIndex), false);
        mFocusIndex = next;
        View cur = mItems.get(mFocusIndex);
        applyHighlight(cur, true);
        cur.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (mShowingMenu
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0
                && mItems.size() > 0) {
            int action = tv.biliclassic.util.KeyBindingUtil.classify(event.getKeyCode());
            if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_UP) {
                moveFocus(-1);
                return true;
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_DOWN) {
                moveFocus(1);
                return true;
            } else if (action == tv.biliclassic.util.KeyBindingUtil.ACTION_CONFIRM) {
                if (mFocusIndex >= 0 && mFocusIndex < mItems.size()) {
                    navigate(mFocusIndex);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (mTurnAnim) {
                startTurn(0);            // 真打断：从当前进度反向回放
            } else if (mDetailTurnAnim) {
                return true;             // 详情整页翻页进行中，忽略返回
            } else if (!mShowingMenu) {
                if (mDetailBackTarget != null) {
                    // 整页翻回上一层详情页（收藏夹视频 → 我的收藏）
                    android.support.v4.app.Fragment target = mDetailBackTarget;
                    mDetailBackTarget = null;
                    startDetailTurnTo(target);
                } else {
                    closeDetail();
                }
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isNightMode() {
        // 依据 App 自身的"夜间模式"开关（设置里那个），而非系统的 uiMode
        return tv.biliclassic.util.SharedPreferencesUtil.getBoolean(
                tv.biliclassic.util.SharedPreferencesUtil.NIGHT_MODE, false);
    }

    @Override
    protected void onDestroy() {
        if (mTiltEffect != null) {
            mTiltEffect.stop();
            mTiltEffect = null;
        }
        super.onDestroy();
    }
}
