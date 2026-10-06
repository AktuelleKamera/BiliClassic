package tv.biliclassic;

import tv.biliclassic.util.DeviceUtil;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.support.v4.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.api.DynamicApi;
import tv.biliclassic.api.UserInfoApi;
import tv.biliclassic.model.Dynamic;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.widget.LoadingBarView;

public class UserProfileActivity extends BaseActivity {

    private ImageView ivAvatar;
    private TextView tvUserNameTitle;
    private TextView tvUserSign;
    private TextView tvFans;
    private ImageView ivLevel;          // 改为 ivLevel，表示等级图标
    private TextView tvFollowing;
    private View loadingLayout;
    private View contentLayout;
    private ListView listView;
    // 统一加载条（公共组件）
    private LoadingBarView videoProgressBar;
    private TextView videoEmptyView;
    private View footerView;
    private ProgressBar footerProgressBar;
    private TextView footerText;

    private long mid;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private ExecutorService executor;
    private LruCache<String, Bitmap> imageCache;
    private Map<Integer, Boolean> loadingMap = new java.util.HashMap<Integer, Boolean>();

    private List<VideoCard> videoList = new ArrayList<VideoCard>();
    private VideoListAdapter videoAdapter;
    private int currentPage = 1;
    private boolean isLoadingVideos = false;
    private boolean isVideoEnd = false;
    private boolean isLoadingMore = false;

    private Set<Long> videoIdSet = new HashSet<Long>();

    // ===== 顶栏内容切换：视频 / 动态 / 专栏 =====
    private static final int MODE_VIDEO = 0;
    private static final int MODE_DYNAMIC = 1;
    private static final int MODE_ARTICLE = 2;
    private static final String[] SPACE_TAB_NAMES = {"视频", "动态", "专栏"};

    private int currentMode = MODE_VIDEO;
    private UserInfo mUserInfo;

    private List<Dynamic> dynamicList = new ArrayList<Dynamic>();
    private DynamicAdapter dynamicAdapter;
    private String dynamicOffset = null;
    private boolean isLoadingDynamics = false;
    private boolean isDynamicEnd = true;
    private boolean mDynamicLoaded = false;

    private List<VideoCard> articleList = new ArrayList<VideoCard>();
    private VideoListAdapter articleAdapter;
    private int articlePage = 1;
    private boolean isLoadingArticles = false;
    private boolean isArticleEnd = false;
    private boolean mArticleLoaded = false;

    private boolean isDestroyed = false;

    // 滚动中暂缓应用新图，避免每张图到达都触发整屏软件重绘（仅主线程访问）
    private volatile boolean mScrolling = false;
    private final java.util.ArrayList<Runnable> pendingBitmapSets = new java.util.ArrayList<Runnable>();

    /** 滚动状态变化时由 ListView 的 OnScrollListener 调用 */
    private void setScrolling(boolean scrolling) {
        this.mScrolling = scrolling;
        if (!scrolling) {
            flushPendingBitmapSets();
        }
    }

    private void flushPendingBitmapSets() {
        if (pendingBitmapSets.isEmpty()) return;
        final java.util.ArrayList<Runnable> pending = new java.util.ArrayList<Runnable>(pendingBitmapSets);
        pendingBitmapSets.clear();
        // 分批应用（每帧最多 2 张）
        final int[] idx = {0};
        final Runnable drain = new Runnable() {
            @Override
            public void run() {
                if (executor == null || executor.isShutdown()) {
                    return;
                }
                int applied = 0;
                while (idx[0] < pending.size() && applied < 2) {
                    try {
                        pending.get(idx[0]).run();
                    } catch (Throwable t) {
                    }
                    idx[0]++;
                    applied++;
                }
                if (idx[0] < pending.size()) {
                    mainHandler.postDelayed(this, 16);
                }
            }
        };
        drain.run();
    }


    private void initCache() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // 2.x 位图像素在独立 ~13MB native 堆，按 Java 堆 1/8 会撑爆 → OOM
        int cacheSize = (tv.biliclassic.util.SdkHelper.getSdkInt() < 11)
                ? Math.min(maxMemory / 16, 2048)
                : maxMemory / 8;
        if (cacheSize < 1024) {
            cacheSize = 1024;
        }
        imageCache = new LruCache<String, Bitmap>(cacheSize);
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        // 统一走 SdkHelper：优先用户设置，未设置再按设备内存给默认值，不写死
        int threadCount = tv.biliclassic.util.SdkHelper.getImageLoadThreads();
        executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        initRoundTitleBar();

        mid = getIntent().getLongExtra("mid", 0);

        if (mid == 0) {
            Toast.makeText(this, this.getString(R.string.invalid_user_id), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initCache();
        initExecutor();

        initViews();
        loadUserInfo();
        loadUserVideos();
    }

    private void initViews() {
        ivAvatar = (ImageView) findViewById(R.id.iv_avatar);
        tvUserNameTitle = (TextView) findViewById(R.id.tv_user_name_title);
        tvUserSign = (TextView) findViewById(R.id.tv_user_sign);
        tvFans = (TextView) findViewById(R.id.tv_fans);
        ivLevel = (ImageView) findViewById(R.id.tv_level);      // 注意 id 为 tv_level
        tvFollowing = (TextView) findViewById(R.id.tv_following);
        loadingLayout = findViewById(R.id.loading_layout);
        contentLayout = findViewById(R.id.content_layout);
        listView = (ListView) findViewById(R.id.list_view);
        videoProgressBar = (LoadingBarView) findViewById(R.id.video_progress);
        videoProgressBar.bindBackground(listView);
        videoEmptyView = (TextView) findViewById(R.id.video_empty_view);

        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerProgressBar = (ProgressBar) footerView.findViewById(R.id.footer_progress);
        footerText = (TextView) footerView.findViewById(R.id.footer_text);
        if (footerProgressBar != null) {
            footerProgressBar.setVisibility(View.GONE);
        }
        if (footerText != null) {
            footerText.setText(getString(R.string.login_working_hard));
        }
        footerView.setVisibility(View.GONE);
        listView.addFooterView(footerView);

        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setEmptyView(videoEmptyView);

        tvUserSign.setMaxWidth(Integer.MAX_VALUE);
        tvUserSign.setMaxLines(Integer.MAX_VALUE);
        tvUserSign.setEllipsize(null);

        videoAdapter = new VideoListAdapter(videoList);
        articleAdapter = new VideoListAdapter(articleList);
        listView.setAdapter(videoAdapter);

        dynamicAdapter = new DynamicAdapter(this, dynamicList, new DynamicAdapter.Listener() {
            @Override
            public void onLike(Dynamic d) {
                doLikeDynamic(d);
            }

            @Override
            public void onDelete(Dynamic d) {
                confirmDeleteDynamic(d);
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    setScrolling(false);
                    if (currentMode == MODE_DYNAMIC) {
                        if (!isLoadingDynamics && !isDynamicEnd && dynamicList.size() > 0) {
                            int lastVisible = view.getLastVisiblePosition();
                            if (lastVisible >= dynamicAdapter.getCount() - 1) {
                                loadMoreDynamics();
                            }
                        }
                    } else if (currentMode == MODE_ARTICLE) {
                        if (!isLoadingArticles && !isArticleEnd && articleList.size() > 0) {
                            int lastVisible = view.getLastVisiblePosition();
                            if (lastVisible >= articleAdapter.getCount() - 1) {
                                loadMoreArticles();
                            }
                        }
                    } else if (!isLoadingMore && !isLoadingVideos && !isVideoEnd) {
                        int lastVisible = view.getLastVisiblePosition();
                        int totalCount = videoAdapter.getCount();
                        if (lastVisible >= totalCount - 1 && totalCount > 0) {
                            isLoadingMore = true;
                            loadMoreVideos();
                        }
                    }
                } else {
                    setScrolling(true);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (currentMode == MODE_DYNAMIC) {
                    if (!isLoadingDynamics && !isDynamicEnd && totalItemCount > 0
                            && firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        loadMoreDynamics();
                    }
                } else if (currentMode == MODE_ARTICLE) {
                    if (!isLoadingArticles && !isArticleEnd && totalItemCount > 0
                            && firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        loadMoreArticles();
                    }
                } else if (!isLoadingMore && !isLoadingVideos && !isVideoEnd && totalItemCount > 0) {
                    if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                        isLoadingMore = true;
                        loadMoreVideos();
                    }
                }
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // bilibili 图标：点击返回（带粉色点击特效）
        View logoContainer = findViewById(R.id.logo_container);
        if (logoContainer != null) {
            logoContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    /** 切换顶栏内容：视频列表 / 用户动态 / 用户专栏 */
    private void switchMode(int mode) {
        if ((mode != MODE_VIDEO && mode != MODE_DYNAMIC && mode != MODE_ARTICLE) || mode == currentMode) {
            return;
        }
        currentMode = mode;
        listView.setSelection(0);
        if (mode == MODE_VIDEO) {
            listView.setAdapter(videoAdapter);
            videoEmptyView.setText(getString(R.string.user_has_no_videos));
            footerView.setVisibility(
                    (videoList.size() > 0 && !isVideoEnd) ? View.VISIBLE : View.GONE);
        } else if (mode == MODE_DYNAMIC) {
            listView.setAdapter(dynamicAdapter);
            videoEmptyView.setText(getString(R.string.no_dynamics));
            footerView.setVisibility(View.GONE);
            if (!mDynamicLoaded) {
                loadSpaceDynamics();
            } else if (dynamicList.size() > 0 && !isDynamicEnd) {
                footerView.setVisibility(View.VISIBLE);
                if (footerProgressBar != null) footerProgressBar.setVisibility(View.GONE);
                if (footerText != null) {
                    footerText.setText(getString(R.string.login_working_hard));
                    footerText.setVisibility(View.VISIBLE);
                }
            }
        } else {
            listView.setAdapter(articleAdapter);
            videoEmptyView.setText(getString(R.string.no_articles));
            footerView.setVisibility(View.GONE);
            if (!mArticleLoaded) {
                loadUserArticles();
            } else if (articleList.size() > 0 && !isArticleEnd) {
                footerView.setVisibility(View.VISIBLE);
                if (footerProgressBar != null) footerProgressBar.setVisibility(View.GONE);
                if (footerText != null) {
                    footerText.setText(getString(R.string.login_working_hard));
                    footerText.setVisibility(View.VISIBLE);
                }
            }
        }
        listView.setEmptyView(videoEmptyView);
    }

    @Override
    protected boolean includeDefaultOverflowMenu() {
        return false;
    }

    @Override
    protected int onAppendOverflowItems(int[] ids, String[] titles, int n) {
        long myMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        // 不能关注自己（否则接口报 22001），自己的空间不显示关注项
        if (n < ids.length && mid != 0 && mid != myMid) {
            boolean followed = mUserInfo != null && mUserInfo.followed;
            ids[n] = R.id.menu_space_follow;
            titles[n] = followed ? "取关TA" : "关注TA";
            n++;
        }
        if (n < ids.length && mid != 0 && mid != myMid) {
            ids[n] = R.id.menu_space_message;
            titles[n] = "私信TA";
            n++;
        }
        if (n < ids.length && mid != 0 && mid != myMid) {
            boolean blocked = mUserInfo != null && mUserInfo.blocked;
            ids[n] = R.id.menu_space_block;
            titles[n] = blocked ? "取消拉黑" : "拉黑TA";
            n++;
        }
        for (int mode = MODE_VIDEO; mode <= MODE_ARTICLE; mode++) {
            if (mode == currentMode || n >= ids.length) {
                continue;
            }
            ids[n] = modeMenuId(mode);
            titles[n] = SPACE_TAB_NAMES[mode];
            n++;
        }
        return n;
    }

    private static int modeMenuId(int mode) {
        if (mode == MODE_DYNAMIC) {
            return R.id.menu_space_dynamic;
        }
        if (mode == MODE_ARTICLE) {
            return R.id.menu_space_article;
        }
        return R.id.menu_space_video;
    }

    @Override
    protected void onMenuAction(int id) {
        if (id == R.id.menu_space_block) {
            toggleBlock();
            return;
        }
        if (id == R.id.menu_space_message) {
            Intent it = new Intent(this, PrivateMsgActivity.class);
            it.putExtra("uid", mid);
            if (mUserInfo != null) {
                it.putExtra("name", mUserInfo.name);
                it.putExtra("face", mUserInfo.avatar);
            }
            startActivity(it);
            return;
        }
        if (id == R.id.menu_space_follow) {
            toggleFollow();
            return;
        }
        if (id == R.id.menu_space_video) {
            switchMode(MODE_VIDEO);
            return;
        }
        if (id == R.id.menu_space_dynamic) {
            switchMode(MODE_DYNAMIC);
            return;
        }
        if (id == R.id.menu_space_article) {
            switchMode(MODE_ARTICLE);
            return;
        }
        super.onMenuAction(id);
    }

    /** 关注/取关当前 UP。 */
    private void toggleFollow() {
        final boolean wantFollow = !(mUserInfo != null && mUserInfo.followed);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int code;
                try {
                    code = UserInfoApi.followUser(mid, wantFollow);
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isDestroyed) {
                                Toast.makeText(UserProfileActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    return;
                }
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        if (code == 0) {
                            if (mUserInfo != null) mUserInfo.followed = wantFollow;
                            Toast.makeText(UserProfileActivity.this,
                                    wantFollow ? "已关注" : "已取消关注", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(UserProfileActivity.this, "操作失败(" + code + ")", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    /** 拉黑/取消拉黑当前 UP。 */
    private void toggleBlock() {
        final boolean wantBlock = !(mUserInfo != null && mUserInfo.blocked);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int code;
                try {
                    code = UserInfoApi.blockUser(mid, wantBlock);
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isDestroyed) Toast.makeText(UserProfileActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        if (code == 0) {
                            if (mUserInfo != null) mUserInfo.blocked = wantBlock;
                            Toast.makeText(UserProfileActivity.this,
                                    wantBlock ? "已拉黑" : "已取消拉黑", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(UserProfileActivity.this, "操作失败(" + code + ")", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void loadUserInfo() {
        if (isDestroyed) return;
        loadingLayout.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final UserInfo userInfo = UserInfoApi.getUserInfo(mid);
                    if (userInfo != null) {
                        int attr = UserInfoApi.getRelationAttribute(mid);
                        if (attr >= 0) {
                            userInfo.followed = (attr & 2) != 0;
                            userInfo.blocked = (attr & 128) != 0;
                        }
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            loadingLayout.setVisibility(View.GONE);
                            if (userInfo == null) {
                                Toast.makeText(UserProfileActivity.this, UserProfileActivity.this.getString(R.string.load_user_failed), Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }
                            displayUserInfo(userInfo);
                            contentLayout.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            loadingLayout.setVisibility(View.GONE);
                            Toast.makeText(UserProfileActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void displayUserInfo(UserInfo userInfo) {
        if (isDestroyed) return;
        mUserInfo = userInfo;

        if (tvUserNameTitle != null) {
            tvUserNameTitle.setText(userInfo.name + "的空间");
        }

        if (userInfo.sign != null && userInfo.sign.length() > 0) {
            tvUserSign.setText(userInfo.sign);
            tvUserSign.setVisibility(View.VISIBLE);
        } else {
            tvUserSign.setText(getString(R.string.user_lazy_bio));
            tvUserSign.setVisibility(View.VISIBLE);
        }

        tvFans.setText("粉丝: " + userInfo.fans);
        tvFollowing.setText("关注: " + userInfo.following);

        // 等级图标
        int levelResId = getLevelDrawable(userInfo.level, userInfo.isSeniorMember);
        if (levelResId != 0) {
            ivLevel.setImageResource(levelResId);
            ivLevel.setVisibility(View.VISIBLE);
        } else {
            ivLevel.setVisibility(View.GONE);
        }

        if (userInfo.avatar != null && userInfo.avatar.length() > 0) {
            loadAvatar(userInfo.avatar);
        }

        addAvatarBorder(ivAvatar);
    }

    /**
     * 根据等级获取对应的 drawable 资源
     */
    private int getLevelDrawable(int level, boolean isSeniorMember) {
        // 硬核会员优先
        if (isSeniorMember && level >= 6) {
            return R.drawable.level_h;
        }
        switch (level) {
            case 0: return R.drawable.level_0;
            case 1: return R.drawable.level_1;
            case 2: return R.drawable.level_2;
            case 3: return R.drawable.level_3;
            case 4: return R.drawable.level_4;
            case 5: return R.drawable.level_5;
            case 6: return R.drawable.level_6;
            default: return 0;
        }
    }

    private void addAvatarBorder(ImageView imageView) {
        if (imageView == null || isDestroyed) return;
        try {
            Drawable borderDrawable = getResources().getDrawable(R.drawable.avatar_border_overlay);
            imageView.setBackgroundDrawable(borderDrawable);
            int paddingPx = DeviceUtil.dpToPx(2);
            imageView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void loadAvatar(String urlStr) {
        if (isDestroyed || executor == null || executor.isShutdown() || executor.isTerminated()) {
            return;
        }

        if (urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr.substring(8);
        }

        if (urlStr != null && urlStr.indexOf(".webp") > 0) {
            urlStr = urlStr.replace(".webp", ".jpg");
            Log.e("UserProfile", "webp 转换为 jpg: " + urlStr);
        }

        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
        }

        final String finalUrl = urlStr;
        final ImageView avatarView = ivAvatar;
        avatarView.setTag(finalUrl);

        Bitmap cachedBitmap = GlobalImageCache.getInstance().get(finalUrl);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            avatarView.setImageBitmap(cachedBitmap);
            addAvatarBorder(avatarView);
            return;
        }
        cachedBitmap = imageCache.get(finalUrl);
        if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
            avatarView.setImageBitmap(cachedBitmap);
            addAvatarBorder(avatarView);
            return;
        }

        Log.e("UserProfile", "加载头像: " + finalUrl);

        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (isDestroyed || executor == null || executor.isShutdown()) {
                        return;
                    }
                    final Bitmap bitmap = downloadImage(finalUrl, true);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        // 写透全局缓存，后续页面共用
                        GlobalImageCache.getInstance().put(finalUrl, bitmap);
                        imageCache.put(finalUrl, bitmap);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (isDestroyed) return;
                                Object tag = avatarView.getTag();
                                if (tag != null && tag.equals(finalUrl)) {
                                    if (bitmap != null && !bitmap.isRecycled()) {
                                        avatarView.setImageBitmap(bitmap);
                                        addAvatarBorder(avatarView);
                                    } else {
                                        avatarView.setImageResource(R.drawable.bili_default_avatar);
                                    }
                                }
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            // 忽略
        }
    }

    private Bitmap downloadImage(String urlStr, boolean isAvatar) {
        return ImageLoader.fetchBitmap(this, urlStr, isAvatar ? 80 : 160, isAvatar ? 80 : 120);
    }

    private void loadUserVideos() {
        videoList.clear();
        videoIdSet.clear();
        currentPage = 1;
        isVideoEnd = false;
        isLoadingMore = false;
        videoProgressBar.showLoading();
        footerView.setVisibility(View.GONE);

        Log.d("UserProfile", "开始加载视频列表，mid=" + mid);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    Log.d("UserProfile", "调用 API: getUserVideos, page=1, mid=" + mid);
                    UserInfoApi.getUserVideos(mid, 1, "", items);
                    Log.d("UserProfile", "API 返回，视频数量: " + (items == null ? "null" : items.size()));

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            videoProgressBar.hide();
                            Log.d("UserProfile", "UI 线程更新，视频数量=" + (items == null ? 0 : items.size()));

                            if (items == null || items.size() == 0) {
                                videoEmptyView.setText(getString(R.string.user_has_no_videos));
                                footerView.setVisibility(View.GONE);
                                Log.d("UserProfile", "视频列表为空，显示空视图");
                                return;
                            }

                            int added = 0;
                            for (VideoCard item : items) {
                                if (item.aid != 0 && !videoIdSet.contains(item.aid)) {
                                    videoIdSet.add(item.aid);
                                    videoList.add(item);
                                    added++;
                                    Log.d("UserProfile", "添加视频: aid=" + item.aid + ", title=" + item.title);
                                }
                            }
                            Log.d("UserProfile", "新增视频数=" + added + ", 当前总数=" + videoList.size());

                            if (added > 0) {
                                videoAdapter.notifyDataSetChanged();
                                currentPage = 2;
                                if (!isVideoEnd) {
                                    footerView.setVisibility(View.VISIBLE);
                                    if (footerProgressBar != null) {
                                        footerProgressBar.setVisibility(View.GONE);
                                    }
                                    if (footerText != null) {
                                        footerText.setText(getString(R.string.login_working_hard));
                                        footerText.setVisibility(View.VISIBLE);
                                    }
                                }
                                Log.d("UserProfile", "视频列表更新成功，当前页=" + currentPage);
                            } else {
                                videoEmptyView.setText(getString(R.string.no_videos));
                                footerView.setVisibility(View.GONE);
                                Log.d("UserProfile", "无可添加的视频");
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e("UserProfile", "加载视频列表异常: " + e.getMessage(), e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            videoProgressBar.hide();
                            videoEmptyView.setText("加载失败: " + e.getMessage());
                            footerView.setVisibility(View.GONE);
                            Log.e("UserProfile", "UI 显示错误: " + e.getMessage());
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void showLoadEndTip() {
        if (footerView == null) return;
        footerView.setVisibility(View.VISIBLE);
        if (footerProgressBar != null) {
            footerProgressBar.setVisibility(View.GONE);
        }
        if (footerText != null) {
            footerText.setText(getString(R.string.emoticon__no_more_data));
            footerText.setVisibility(View.VISIBLE);
        }
    }

    private void loadMoreVideos() {
        if (isLoadingVideos || isVideoEnd || isDestroyed) return;
        isLoadingVideos = true;

        if (footerProgressBar != null) {
            footerProgressBar.setVisibility(View.VISIBLE);
        }
        if (footerText != null) {
            footerText.setText(getString(R.string.login_working_hard));
            footerText.setVisibility(View.VISIBLE);
        }
        footerView.setVisibility(View.VISIBLE);

        final int page = currentPage;
        Log.d("UserProfile", "加载更多视频，页码=" + page);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<VideoCard> items = new ArrayList<VideoCard>();
                    Log.d("UserProfile", "调用 API: getUserVideos, page=" + page);
                    final int result = UserInfoApi.getUserVideos(mid, page, "", items);
                    Log.d("UserProfile", "API 返回结果码=" + result + ", 视频数=" + (items == null ? 0 : items.size()));

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            footerProgressBar.setVisibility(View.GONE);
                            isLoadingVideos = false;
                            isLoadingMore = false;

                            if (items == null || items.size() == 0 || result == 1) {
                                isVideoEnd = true;
                                showLoadEndTip();
                                Log.d("UserProfile", "已加载到末尾或没有更多数据");
                                return;
                            }

                            int added = 0;
                            for (VideoCard item : items) {
                                if (item.aid != 0 && !videoIdSet.contains(item.aid)) {
                                    videoIdSet.add(item.aid);
                                    videoList.add(item);
                                    added++;
                                    Log.d("UserProfile", "加载更多添加视频: aid=" + item.aid);
                                }
                            }
                            Log.d("UserProfile", "加载更多新增视频数=" + added);

                            if (added > 0) {
                                videoAdapter.notifyDataSetChanged();
                                currentPage = page + 1;
                                footerView.setVisibility(View.VISIBLE);
                                if (footerProgressBar != null) {
                                    footerProgressBar.setVisibility(View.GONE);
                                }
                                if (footerText != null) {
                                    footerText.setVisibility(View.GONE);
                                }
                                Log.d("UserProfile", "加载更多成功，当前页=" + currentPage);
                            } else {
                                if (items.size() > 0 && !isVideoEnd) {
                                    currentPage = page + 1;
                                    Log.d("UserProfile", "没有新视频但未结束，继续加载下一页");
                                    loadMoreVideos();
                                } else {
                                    isVideoEnd = true;
                                    showLoadEndTip();
                                    Log.d("UserProfile", "没有可添加的视频，标记结束");
                                }
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e("UserProfile", "加载更多异常: " + e.getMessage(), e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            footerProgressBar.setVisibility(View.GONE);
                            isLoadingVideos = false;
                            isLoadingMore = false;
                            footerView.setVisibility(View.GONE);
                            Toast.makeText(UserProfileActivity.this, "加载更多失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e("UserProfile", "加载更多 UI 错误: " + e.getMessage());
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // ===== 用户专栏 =====

    private void loadUserArticles() {
        articleList.clear();
        articlePage = 1;
        isArticleEnd = false;
        isLoadingArticles = false;
        mArticleLoaded = true;
        videoProgressBar.showLoading();
        footerView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<VideoCard> items = new ArrayList<VideoCard>();
                int result = -1;
                String error = null;
                try {
                    result = UserInfoApi.getUserArticles(mid, 1, items);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final int fResult = result;
                final String fError = error;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        videoProgressBar.hide();
                        if (fError != null) {
                            mArticleLoaded = false;
                            videoEmptyView.setText("加载失败: " + fError);
                            footerView.setVisibility(View.GONE);
                            return;
                        }
                        articleList.addAll(items);
                        articleAdapter.notifyDataSetChanged();
                        if (articleList.size() == 0) {
                            videoEmptyView.setText(getString(R.string.no_articles));
                            footerView.setVisibility(View.GONE);
                        } else if (fResult == 1) {
                            isArticleEnd = true;
                            showLoadEndTip();
                        } else {
                            articlePage = 2;
                            footerView.setVisibility(View.VISIBLE);
                            if (footerProgressBar != null) footerProgressBar.setVisibility(View.GONE);
                            if (footerText != null) {
                                footerText.setText(getString(R.string.login_working_hard));
                                footerText.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void loadMoreArticles() {
        if (isLoadingArticles || isArticleEnd || isDestroyed) return;
        isLoadingArticles = true;
        if (footerProgressBar != null) footerProgressBar.setVisibility(View.VISIBLE);
        if (footerText != null) {
            footerText.setText(getString(R.string.login_working_hard));
            footerText.setVisibility(View.VISIBLE);
        }
        footerView.setVisibility(View.VISIBLE);

        final int page = articlePage;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<VideoCard> items = new ArrayList<VideoCard>();
                int result = -1;
                String error = null;
                try {
                    result = UserInfoApi.getUserArticles(mid, page, items);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final int fResult = result;
                final String fError = error;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        isLoadingArticles = false;
                        if (footerProgressBar != null) footerProgressBar.setVisibility(View.GONE);
                        if (fError != null) {
                            Toast.makeText(UserProfileActivity.this,
                                    "加载更多失败: " + fError, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        articleList.addAll(items);
                        articleAdapter.notifyDataSetChanged();
                        if (items.size() == 0 || fResult == 1) {
                            isArticleEnd = true;
                            showLoadEndTip();
                        } else {
                            articlePage = page + 1;
                            footerView.setVisibility(View.VISIBLE);
                            if (footerText != null) footerText.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }).start();
    }

    // ===== 用户动态 =====

    private boolean isLoggedIn() {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        return mid != 0 && cookies != null && cookies.length() > 0;
    }

    /** 加载用户动态（第一页） */
    private void loadSpaceDynamics() {
        if (isLoadingDynamics || isDestroyed) return;
        isLoadingDynamics = true;
        mDynamicLoaded = true;
        dynamicOffset = null;
        isDynamicEnd = false;

        dynamicList.clear();
        dynamicAdapter.notifyDataSetChanged();
        videoProgressBar.showLoading();
        footerView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Dynamic> items = new ArrayList<Dynamic>();
                String next = "";
                String error = null;
                try {
                    next = DynamicApi.getSpaceDynamics(mid, items, null);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final String fNext = next;
                final String fError = error;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        isLoadingDynamics = false;
                        videoProgressBar.hide();
                        if (fError != null) {
                            mDynamicLoaded = false;
                            videoEmptyView.setText("加载失败: " + fError);
                            footerView.setVisibility(View.GONE);
                            return;
                        }
                        dynamicOffset = fNext;
                        dynamicList.addAll(items);
                        dynamicAdapter.notifyDataSetChanged();
                        if (dynamicList.size() == 0) {
                            videoEmptyView.setText(getString(R.string.no_dynamics));
                            footerView.setVisibility(View.GONE);
                        } else if (fNext == null || fNext.length() == 0) {
                            isDynamicEnd = true;
                            showLoadEndTip();
                        } else {
                            isDynamicEnd = false;
                            footerView.setVisibility(View.VISIBLE);
                            if (footerProgressBar != null) footerProgressBar.setVisibility(View.GONE);
                            if (footerText != null) {
                                footerText.setText(getString(R.string.login_working_hard));
                                footerText.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });
            }
        }).start();
    }

    /** 加载更多用户动态 */
    private void loadMoreDynamics() {
        if (isLoadingDynamics || isDynamicEnd || isDestroyed) return;
        if (dynamicOffset == null || dynamicOffset.length() == 0) return;
        isLoadingDynamics = true;

        if (footerProgressBar != null) footerProgressBar.setVisibility(View.VISIBLE);
        if (footerText != null) {
            footerText.setText(getString(R.string.login_working_hard));
            footerText.setVisibility(View.VISIBLE);
        }
        footerView.setVisibility(View.VISIBLE);

        final String pageOffset = dynamicOffset;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Dynamic> items = new ArrayList<Dynamic>();
                String next = "";
                String error = null;
                try {
                    next = DynamicApi.getSpaceDynamics(mid, items, pageOffset);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final String fNext = next;
                final String fError = error;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        isLoadingDynamics = false;
                        if (footerProgressBar != null) footerProgressBar.setVisibility(View.GONE);
                        if (fError != null) {
                            Toast.makeText(UserProfileActivity.this,
                                    "加载更多失败: " + fError, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        dynamicOffset = fNext;
                        dynamicList.addAll(items);
                        dynamicAdapter.notifyDataSetChanged();
                        if (items.size() == 0 || fNext == null || fNext.length() == 0) {
                            isDynamicEnd = true;
                            showLoadEndTip();
                        } else {
                            footerView.setVisibility(View.VISIBLE);
                            if (footerText != null) footerText.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }).start();
    }

    private void doLikeDynamic(final Dynamic d) {
        if (d == null) return;
        if (!isLoggedIn()) {
            Toast.makeText(this, getString(R.string.dynamicfragment_settext_login),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean target = !d.liked;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code;
                try {
                    code = DynamicApi.likeDynamic(d.dynamicId, target);
                } catch (Exception e) {
                    code = -1;
                }
                final int fCode = code;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        if (fCode == 0) {
                            d.liked = target;
                            d.likeCount += target ? 1 : -1;
                            if (d.likeCount < 0) d.likeCount = 0;
                            dynamicAdapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(UserProfileActivity.this,
                                    getString(R.string.dynamicfragment_toast_op_fail) + " (" + fCode + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void confirmDeleteDynamic(final Dynamic d) {
        if (d == null) return;
        new AlertDialog.Builder(tv.biliclassic.util.SdkHelper.dialogContext(
                tv.biliclassic.util.DialogUtil.wrap(this)))
                .setTitle(getString(R.string.dynamicfragment_settitle_delete))
                .setMessage(getString(R.string.dynamicfragment_setmessage_delete))
                .setPositiveButton(getString(R.string.dynamicfragment_btn_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                doDeleteDynamic(d);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doDeleteDynamic(final Dynamic d) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code;
                try {
                    code = DynamicApi.deleteDynamic(d.dynamicId);
                } catch (Exception e) {
                    code = -1;
                }
                final int fCode = code;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed) return;
                        if (fCode == 0) {
                            dynamicList.remove(d);
                            dynamicAdapter.notifyDataSetChanged();
                            if (dynamicList.size() == 0) {
                                videoEmptyView.setText(getString(R.string.no_dynamics));
                                footerView.setVisibility(View.GONE);
                            }
                            Toast.makeText(UserProfileActivity.this,
                                    getString(R.string.dynamicfragment_toast_op_success),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(UserProfileActivity.this,
                                    getString(R.string.dynamicfragment_toast_op_fail) + " (" + fCode + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
        if (imageCache != null) {
            imageCache.evictAll();
        }
        loadingMap.clear();
        videoIdSet.clear();
        if (videoList != null) {
            videoList.clear();
        }
        if (dynamicList != null) {
            dynamicList.clear();
        }
        if (articleList != null) {
            articleList.clear();
        }
        if (dynamicAdapter != null) {
            dynamicAdapter.clearCache();
        }
    }

    // VideoListAdapter

    class VideoListAdapter extends BaseAdapter {

        private final List<VideoCard> data;

        VideoListAdapter(List<VideoCard> data) {
            this.data = data;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_user_video, parent, false);
                holder = new ViewHolder();
                holder.cover = (ImageView) convertView.findViewById(R.id.cover);
                holder.title = (TextView) convertView.findViewById(R.id.title);
                holder.view = (TextView) convertView.findViewById(R.id.view);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            // 夜间：白底换灰底、封面框深灰、标题调亮
            boolean night = tv.biliclassic.metro.MetroTheme.isNight();
            convertView.setBackgroundResource(night
                    ? R.drawable.item_click_effect_grey : R.drawable.item_click_effect_white);
            holder.title.setTextColor(night ? 0xFFE6E6E6 : 0xFF333333);
            View coverBox = (View) holder.cover.getParent();
            if (coverBox != null) {
                if (night) {
                    coverBox.setBackgroundColor(0xFF484848);
                } else {
                    coverBox.setBackgroundResource(R.drawable.bili_thumb_boarder);
                }
            }

            VideoCard item = data.get(position);
            holder.title.setText(item.title);
            holder.view.setText(item.view);

            holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            holder.cover.setTag(item.cover);

            if (item.cover != null && item.cover.length() > 0) {
                String coverUrl = item.cover;
                if (coverUrl.startsWith("https://")) {
                    coverUrl = "http://" + coverUrl.substring(8);
                }
                final String finalUrl = coverUrl;
                final ImageView coverView = holder.cover;

                boolean alreadySet = false;
                // 先查全局缓存（跨页面共享），命中则不再走私有/网络
                Bitmap cached = GlobalImageCache.getInstance().get(finalUrl);
                if (cached != null && !cached.isRecycled()) {
                    alreadySet = true;
                    android.graphics.drawable.Drawable cur = coverView.getDrawable();
                    if (!(cur instanceof android.graphics.drawable.BitmapDrawable)
                            || ((android.graphics.drawable.BitmapDrawable) cur).getBitmap() != cached) {
                        coverView.setImageBitmap(cached);
                    }
                }
                if (!alreadySet) {
                    cached = imageCache.get(finalUrl);
                    if (cached != null && !cached.isRecycled()) {
                        alreadySet = true;
                        // 已是同一张位图则跳过，避免滚动中重复 invalidate
                        android.graphics.drawable.Drawable cur = coverView.getDrawable();
                        if (!(cur instanceof android.graphics.drawable.BitmapDrawable)
                                || ((android.graphics.drawable.BitmapDrawable) cur).getBitmap() != cached) {
                            coverView.setImageBitmap(cached);
                        }
                    }
                }

                // 命中缓存不再重新下载（原逻辑每 bind 都会重新下载）
                if (!alreadySet) {
                    Boolean isLoading = loadingMap.get(position);
                    if (isLoading == null || !isLoading) {
                        final int currentPos = position;
                        loadingMap.put(currentPos, true);
                        if (executor != null && !executor.isShutdown()) {
                            executor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    if (isDestroyed) return;
                                    final Bitmap bitmap = downloadImage(finalUrl, false);
                                    loadingMap.remove(currentPos);
                                    if (bitmap != null && !bitmap.isRecycled()) {
                                        // 写透全局缓存，详情页等共用，不再重复下载
                                        GlobalImageCache.getInstance().put(finalUrl, bitmap);
                                        imageCache.put(finalUrl, bitmap);
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (isDestroyed) return;
                                                if (mScrolling) {
                                                    // 滚动中暂缓应用，避免每张图到达都整屏软件重绘
                                                    pendingBitmapSets.add(this);
                                                    return;
                                                }
                                                Object tag = coverView.getTag();
                                                if (tag != null && tag.equals(finalUrl)) {
                                                    Bitmap bmp = imageCache.get(finalUrl);
                                                    if (bmp != null && !bmp.isRecycled()) {
                                                        coverView.setImageBitmap(bmp);
                                                    } else {
                                                        coverView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
                                                    }
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }
                }
            }

            final int pos = position;
            final VideoCard clickItem = item;
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (clickItem == null) return;
                    // 专栏：跳文章详情
                    if ("article".equals(clickItem.type) && clickItem.articleId != 0) {
                        Intent articleIntent = new Intent(UserProfileActivity.this, ArticleActivity.class);
                        articleIntent.putExtra("cvid", clickItem.articleId);
                        articleIntent.putExtra("title", clickItem.title);
                        startActivity(articleIntent);
                        return;
                    }
                    Intent intent = new Intent(UserProfileActivity.this, VideoDetailActivity.class);
                    if (clickItem.aid != 0) {
                        intent.putExtra("aid", clickItem.aid);
                    } else if (clickItem.bvid != null && clickItem.bvid.length() > 0) {
                        intent.putExtra("bvid", clickItem.bvid);
                    } else {
                        Toast.makeText(UserProfileActivity.this, UserProfileActivity.this.getString(R.string.load_video_info_failed), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivity(intent);
                }
            });

            return convertView;
        }
    }

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView view;
    }
}