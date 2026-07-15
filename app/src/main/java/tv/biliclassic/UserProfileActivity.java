package tv.biliclassic;

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

import tv.biliclassic.api.UserInfoApi;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.model.VideoCard;

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
    private ProgressBar videoProgressBar;
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

    private boolean isDestroyed = false;

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private void initCache() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        if (cacheSize < 1024) {
            cacheSize = 1024;
        }
        imageCache = new LruCache<String, Bitmap>(cacheSize);
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        int threadCount = isLowMemoryDevice() ? 1 : 2;
        executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        mid = getIntent().getLongExtra("mid", 0);

        if (mid == 0) {
            Toast.makeText(this, "用户ID无效", Toast.LENGTH_SHORT).show();
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
        videoProgressBar = (ProgressBar) findViewById(R.id.video_progress);
        videoEmptyView = (TextView) findViewById(R.id.video_empty_view);

        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerProgressBar = (ProgressBar) footerView.findViewById(R.id.footer_progress);
        footerText = (TextView) footerView.findViewById(R.id.footer_text);
        if (footerProgressBar != null) {
            footerProgressBar.setVisibility(View.GONE);
        }
        if (footerText != null) {
            footerText.setText("嘿咻…嘿咻…");
        }
        footerView.setVisibility(View.GONE);
        listView.addFooterView(footerView);

        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setEmptyView(videoEmptyView);

        tvUserSign.setMaxWidth(Integer.MAX_VALUE);
        tvUserSign.setMaxLines(Integer.MAX_VALUE);
        tvUserSign.setEllipsize(null);

        videoAdapter = new VideoListAdapter();
        listView.setAdapter(videoAdapter);

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    if (!isLoadingMore && !isLoadingVideos && !isVideoEnd) {
                        int lastVisible = view.getLastVisiblePosition();
                        int totalCount = videoAdapter.getCount();
                        if (lastVisible >= totalCount - 1 && totalCount > 0) {
                            isLoadingMore = true;
                            loadMoreVideos();
                        }
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoadingMore && !isLoadingVideos && !isVideoEnd && totalItemCount > 0) {
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
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isDestroyed) return;
                            loadingLayout.setVisibility(View.GONE);
                            if (userInfo == null) {
                                Toast.makeText(UserProfileActivity.this, "获取用户信息失败", Toast.LENGTH_SHORT).show();
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

        if (tvUserNameTitle != null) {
            tvUserNameTitle.setText(userInfo.name + "的空间");
        }

        if (userInfo.sign != null && userInfo.sign.length() > 0) {
            tvUserSign.setText(userInfo.sign);
            tvUserSign.setVisibility(View.VISIBLE);
        } else {
            tvUserSign.setText("这个人很懒，什么都没有写~");
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
            int paddingPx = dpToPx(2);
            imageView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
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

        Bitmap cachedBitmap = imageCache.get(finalUrl);
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
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.connect();

            InputStream is = conn.getInputStream();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            int outWidth = options.outWidth;
            int outHeight = options.outHeight;

            if (outWidth <= 0 || outHeight <= 0) {
                outWidth = 800;
                outHeight = 600;
            }

            int targetWidth = isAvatar ? 64 : 160;
            int targetHeight = isAvatar ? 64 : 90;

            int scale = 1;
            if (outWidth > targetWidth || outHeight > targetHeight) {
                int widthRatio = outWidth / targetWidth;
                int heightRatio = outHeight / targetHeight;
                scale = Math.max(widthRatio, heightRatio);
                if (scale < 1) scale = 1;
                if (scale > 8) scale = 8;
            }

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            conn.connect();
            is = conn.getInputStream();

            options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inPurgeable = true;
            options.inInputShareable = true;

            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();

            if (bitmap != null) {
                int bw = bitmap.getWidth();
                int bh = bitmap.getHeight();
                if (bw > targetWidth * 2 || bh > targetHeight * 2) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                    if (scaled != bitmap && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    return scaled;
                }
            }

            return bitmap;
        } catch (Exception e) {
            Log.e("UserProfile", "下载图片失败: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void loadUserVideos() {
        videoList.clear();
        videoIdSet.clear();
        currentPage = 1;
        isVideoEnd = false;
        isLoadingMore = false;
        videoProgressBar.setVisibility(View.VISIBLE);
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
                            videoProgressBar.setVisibility(View.GONE);
                            Log.d("UserProfile", "UI 线程更新，视频数量=" + (items == null ? 0 : items.size()));

                            if (items == null || items.size() == 0) {
                                videoEmptyView.setText("该用户暂无视频");
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
                                        footerText.setText("嘿咻…嘿咻…");
                                        footerText.setVisibility(View.VISIBLE);
                                    }
                                }
                                Log.d("UserProfile", "视频列表更新成功，当前页=" + currentPage);
                            } else {
                                videoEmptyView.setText("暂无视频");
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
                            videoProgressBar.setVisibility(View.GONE);
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
            footerText.setText("嘿咻…嘿咻…");
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
    }

    // VideoListAdapter

    class VideoListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return videoList.size();
        }

        @Override
        public Object getItem(int position) {
            return videoList.get(position);
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

            VideoCard item = videoList.get(position);
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

                Bitmap cached = imageCache.get(finalUrl);
                if (cached != null && !cached.isRecycled()) {
                    coverView.setImageBitmap(cached);
                }

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
                                    imageCache.put(finalUrl, bitmap);
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (isDestroyed) return;
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

            final int pos = position;
            final VideoCard clickItem = item;
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (clickItem == null) return;
                    Intent intent = new Intent(UserProfileActivity.this, VideoDetailActivity.class);
                    if (clickItem.aid != 0) {
                        intent.putExtra("aid", clickItem.aid);
                    } else if (clickItem.bvid != null && clickItem.bvid.length() > 0) {
                        intent.putExtra("bvid", clickItem.bvid);
                    } else {
                        Toast.makeText(UserProfileActivity.this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
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