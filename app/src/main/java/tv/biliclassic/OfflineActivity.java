package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView;

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.download.VideoDownloadEntry;
import tv.biliclassic.download.VideoDownloadEnvironment;
import tv.biliclassic.download.VideoDownloadService;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.player.BiliPlayerActivity;

public class OfflineActivity extends BaseActivity {

    private ListView listView;
    private ProgressBar progressBar;
    private ProgressBar storageProgress;
    private TextView emptyView;
    private TextView storageText;
    private OfflineAdapter adapter;
    private List<OfflineItem> videoList = new ArrayList<OfflineItem>();

    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();

    private boolean isScrolling = false;
    private File mDownloadDir;
    private Handler mRefreshHandler = new Handler();
    private Runnable mRefreshRunnable;
    private static final int REFRESH_INTERVAL = 2000;
    private HashSet<Long> mPausedKeys = new HashSet<Long>(); // 跟踪被暂停的下载 // 下载中每2秒刷新

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private int getConfiguredThreadCount() {
        int savedThreads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (savedThreads > 0) return savedThreads;
        return isLowMemoryDevice() ? 1 : 3;
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        int threadCount = getConfiguredThreadCount();
        if (threadCount <= 1) {
            executor = Executors.newSingleThreadExecutor();
        } else {
            executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline);

        initExecutor();

        listView = (ListView) findViewById(R.id.list_view);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        emptyView = (TextView) findViewById(R.id.empty_view);
        storageProgress = (ProgressBar) findViewById(R.id.storage_progress);
        storageText = (TextView) findViewById(R.id.storage_text);

        adapter = new OfflineAdapter();
        listView.setAdapter(adapter);

        ImageView btnBack = (ImageView) findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    isScrolling = false;
                    adapter.notifyDataSetChanged();
                } else {
                    isScrolling = true;
                }
            }
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 点击列表项 → 进入离线详情页
                OfflineItem item = videoList.get(position);
                if (item != null) {
                    openOfflineDetail(item);
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final OfflineItem item = videoList.get(position);
                if (item != null) {
                    showDeleteDialog(item);
                    return true;
                }
                return false;
            }
        });

        refreshList();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从详情页回来时刷新列表和存储空间
        refreshList();
    }

    // ========== 播放 ==========

    private void playVideo(OfflineItem item) {
        File videoFile = item.videoFile;

        if (SettingsActivity.getPlayerPreference() == 8) {
            Intent intent = new Intent(this, BiliPlayerActivity.class);
            String displayTitle = (item.pageTitle != null && item.pageTitle.length() > 0)
                    ? item.pageTitle : item.title;
            intent.putExtra("video_title", displayTitle);
            intent.putExtra("cache_path", videoFile.getAbsolutePath());
            if (item.danmakuFile != null && item.danmakuFile.exists()) {
                intent.putExtra("danmaku_cache_path", item.danmakuFile.getAbsolutePath());
            }
            if (item.cid > 0) {
                intent.putExtra("cid", item.cid);
            }
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(videoFile), "video/mp4");
        String preferredPlayer = SettingsActivity.getPlayerPackageName();
        if (preferredPlayer != null) {
            intent.setPackage(preferredPlayer);
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            try {
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "无法播放视频，请安装播放器", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ========== 离线详情页 ==========

    private void openOfflineDetail(OfflineItem item) {
        if (item.env == null) {
            // 兼容旧版：直接播放
            playVideo(item);
            return;
        }
        // 跳转到视频详情页（离线模式）
        Intent intent = new Intent(this, VideoDetailActivity.class);
        intent.putExtra("aid", item.env.avid);
        intent.putExtra("offline_mode", true);
        startActivity(intent);
    }

    // ========== 删除 ==========

    private void showDeleteDialog(final OfflineItem item) {
        String displayTitle = item.pageTitle != null && item.pageTitle.length() > 0
                ? item.pageTitle : item.title;
        new AlertDialog.Builder(this)
                .setTitle("删除视频")
                .setMessage("确定要删除 \"" + displayTitle + "\" 吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteVideo(item);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteVideo(OfflineItem item) {
        try {
            String cacheKey = item.getCacheKey();
            SoftReference<Bitmap> ref = imageCache.remove(cacheKey);
            if (ref != null) {
                Bitmap bmp = ref.get();
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            }
            if (item.env != null) {
                item.env.deleteEntry();
                Toast.makeText(this, "已删除: " + item.title, Toast.LENGTH_SHORT).show();
            }
            refreshList();
        } catch (Exception e) {
            Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ========== 刷新列表 ==========

    private void refreshList() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                mDownloadDir = getDownloadDir();
                final List<OfflineItem> items = scanOfflineItems();
                final List<OfflineItem> grouped = groupCompletedItems(items);
                final File downloadDir = mDownloadDir;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        videoList.clear();
                        videoList.addAll(grouped);
                        adapter.notifyDataSetChanged();
                        progressBar.setVisibility(View.GONE);

                        if (videoList.size() == 0) {
                            emptyView.setVisibility(View.VISIBLE);
                            listView.setVisibility(View.GONE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                            listView.setVisibility(View.VISIBLE);
                        }

                        updateStorageBar(downloadDir);

                        // 如果有下载中的项，启动定时刷新
                        startAutoRefreshIfNeeded();
                    }
                });
            }
        }).start();
    }

    private void startAutoRefreshIfNeeded() {
        boolean hasInProgress = false;
        for (OfflineItem item : videoList) {
            if (!item.isCompleted) { hasInProgress = true; break; }
        }
        if (hasInProgress) {
            if (mRefreshRunnable == null) {
                mRefreshRunnable = new Runnable() {
                    @Override
                    public void run() {
                        refreshListSilent();
                        mRefreshHandler.postDelayed(this, REFRESH_INTERVAL);
                    }
                };
                mRefreshHandler.postDelayed(mRefreshRunnable, REFRESH_INTERVAL);
            }
        } else {
            stopAutoRefresh();
        }
    }

    private void stopAutoRefresh() {
        if (mRefreshRunnable != null) {
            mRefreshHandler.removeCallbacks(mRefreshRunnable);
            mRefreshRunnable = null;
        }
    }

    /**
     * 静默刷新（不显示加载中）——用于定时刷新进度
     */
    private void refreshListSilent() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mDownloadDir = getDownloadDir();
                final List<OfflineItem> items = scanOfflineItems();
                final List<OfflineItem> grouped = groupCompletedItems(items);
                final File downloadDir = mDownloadDir;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        videoList.clear();
                        videoList.addAll(grouped);
                        adapter.notifyDataSetChanged();
                        updateStorageBar(downloadDir);
                        startAutoRefreshIfNeeded();
                    }
                });
            }
        }).start();
    }

    private List<OfflineItem> scanOfflineItems() {
        List<OfflineItem> items = new ArrayList<OfflineItem>();
        File downloadDir = mDownloadDir;
        if (downloadDir == null || !downloadDir.isDirectory()) return items;

        // 新目录结构（含下载中）
        ArrayList<VideoDownloadEntry> entries = VideoDownloadEnvironment.loadAllEntries(downloadDir);
        if (entries != null) {
            for (VideoDownloadEntry entry : entries) {
                OfflineItem item = new OfflineItem();
                item.env = entry.downloadEnv;
                item.videoFile = entry.getVideoFile();
                item.coverFile = entry.getCoverFile();
                item.danmakuFile = entry.getDanmakuFile();
                item.title = (entry.bvid != null && entry.bvid.length() > 0)
                        ? entry.bvid : "av" + entry.avid;
                item.mainTitle = entry.title;  // 视频主标题
                item.pageTitle = entry.pageTitle;
                item.bvid = entry.bvid;
                item.cid = entry.cid;
                item.qualityName = shortQualityName(entry.qualityName);
                item.isCompleted = entry.isCompleted;
                item.downloadedBytes = entry.downloadedBytes;
                item.totalBytes = entry.totalBytes;
                item.avid = entry.avid;
                item.page = entry.page;
                // 如果有视频文件或正在下载中都显示
                if (item.videoFile != null && item.videoFile.exists()) {
                    item.size = item.videoFile.length();
                    items.add(item);
                } else if (!entry.isCompleted && item.videoFile != null) {
                    // 下载中的也显示（显示进度）
                    item.size = entry.downloadedBytes;
                    items.add(item);
                }
            }
        }

        // 兼容旧版平面文件
        File[] flatFiles = downloadDir.listFiles();
        if (flatFiles != null) {
            for (File file : flatFiles) {
                if (file.isFile() && isVideoFile(file)) {
                    boolean alreadyFound = false;
                    for (OfflineItem item : items) {
                        if (item.videoFile != null && item.videoFile.equals(file)) {
                            alreadyFound = true;
                            break;
                        }
                    }
                    if (!alreadyFound) {
                        OfflineItem item = new OfflineItem();
                        item.videoFile = file;
                        item.title = getFileNameWithoutExtension(file.getName());
                        item.size = file.length();
                        item.isCompleted = true;
                        File coverDir = new File(getCacheDir(), "offline_covers");
                        item.coverFile = new File(coverDir, item.title + ".jpg");
                        items.add(item);
                    }
                }
            }
        }

        return items;
    }

    private File getDownloadDir() {
        if (isSDCardAvailable()) {
            File sdDownload = new File(Environment.getExternalStorageDirectory(), "BiliClassic/Download");
            if (sdDownload.exists()) return sdDownload;
            sdDownload.mkdirs();
            if (sdDownload.isDirectory()) return sdDownload;
        }
        File internalDownload = new File(getFilesDir(), "Download");
        if (!internalDownload.exists()) internalDownload.mkdirs();
        return internalDownload;
    }

    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".flv") || name.endsWith(".mkv")
                || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".3gp");
    }

    private String getFileNameWithoutExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) return fileName.substring(0, lastDot);
        return fileName;
    }

    private boolean isSDCardAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * 合并同一 avid 下已完成的多个分P为一个条目，下载中的保持独立
     */
    private List<OfflineItem> groupCompletedItems(List<OfflineItem> items) {
        List<OfflineItem> result = new ArrayList<OfflineItem>();
        Map<Long, OfflineItem> completedGroups = new HashMap<Long, OfflineItem>();
        List<OfflineItem> inProgressList = new ArrayList<OfflineItem>();

        for (OfflineItem item : items) {
            if (!item.isCompleted) {
                inProgressList.add(item); // 下载中的保持独立
            } else if (item.avid > 0) {
                OfflineItem group = completedGroups.get(item.avid);
                if (group == null) {
                    // 第一个完成的，直接放入结果
                    completedGroups.put(item.avid, item);
                    result.add(item);
                } else {
                    // 已有同 avid 的已完成条目，合并
                    if (group.pages == null) {
                        group.pages = new ArrayList<String>();
                        group.pagesFile = new ArrayList<File>();
                        group.pages.add(group.pageTitle != null ? group.pageTitle : "P" + group.page);
                        group.pagesFile.add(group.videoFile);
                        group.totalPageCount = 1;
                        group.mainTitle = item.mainTitle;
                    }
                    group.pages.add(item.pageTitle != null ? item.pageTitle : "P" + item.page);
                    group.pagesFile.add(item.videoFile);
                    group.totalPageCount++;
                    group.size += item.size; // 累计大小
                    // 用视频主标题代替单P标题
                    group.pageTitle = null; // 清除单P标题，让外层显示 avid 编号
                }
            } else {
                result.add(item); // 旧版兼容
            }
        }

        // 下载中的放前面
        result.addAll(0, inProgressList);
        return result;
    }

    // ========== 存储空间条 ==========

    private void updateStorageBar(File downloadDir) {
        try {
            long totalBytes;
            long availBytes;

            try {
                // 用下载目录所在文件系统的路径
                String path = downloadDir.exists() ? downloadDir.getAbsolutePath()
                        : Environment.getExternalStorageDirectory().getPath();
                StatFs stat = new StatFs(path);
                // 参考原版：先转long再乘，防止int溢出
                totalBytes = ((long) stat.getBlockCount()) * ((long) stat.getBlockSize());
                availBytes = ((long) stat.getAvailableBlocks()) * ((long) stat.getBlockSize());
            } catch (Exception e) {
                totalBytes = 0;
                availBytes = 0;
            }
            long usedBytes = totalBytes - availBytes;

            int percent = totalBytes > 0 ? (int) (usedBytes * 100 / totalBytes) : 0;
            storageProgress.setProgress(percent);

            String availStr = formatFileSize(availBytes);
            String totalStr = formatFileSize(totalBytes);
            storageText.setText("可用 " + availStr + " / 共 " + totalStr);
        } catch (Exception e) {
            storageText.setText("存储信息获取失败");
        }
    }

    private long calculateDirSize(File dir) {
        long size = 0;
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isFile()) {
                size += f.length();
            } else if (f.isDirectory()) {
                size += calculateDirSize(f);
            }
        }
        return size;
    }

    // ========== 封面加载 ==========

    private void loadCoverFromLocal(final OfflineItem item, final ImageView coverView, final int position) {
        if (item == null || coverView == null) return;
        if (isScrolling) return;

        String cacheKey = item.getCacheKey();
        SoftReference<Bitmap> softBitmap = imageCache.get(cacheKey);
        if (softBitmap != null) {
            Bitmap cachedBitmap = softBitmap.get();
            if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                coverView.setImageBitmap(cachedBitmap);
                return;
            } else {
                imageCache.remove(cacheKey);
            }
        }

        if (item.coverFile != null && item.coverFile.exists()) {
            Boolean isLoading = loadingMap.get(position);
            if (isLoading != null && isLoading) return;

            loadingMap.put(position, true);
            final ImageView imageView = coverView;
            final String key = cacheKey;

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        options.inPreferredConfig = Bitmap.Config.RGB_565;
                        final Bitmap bitmap = BitmapFactory.decodeFile(item.coverFile.getAbsolutePath(), options);
                        loadingMap.remove(position);
                        if (bitmap != null && !bitmap.isRecycled()) {
                            imageCache.put(key, new SoftReference<Bitmap>(bitmap));
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    imageView.setImageBitmap(bitmap);
                                }
                            });
                        }
                    } catch (Exception e) {
                        loadingMap.remove(position);
                    } catch (OutOfMemoryError e) {
                        loadingMap.remove(position);
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
        mRefreshHandler.removeCallbacksAndMessages(null);
        if (adapter != null) {
            for (SoftReference<Bitmap> ref : imageCache.values()) {
                Bitmap bmp = ref.get();
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            }
            imageCache.clear();
            loadingMap.clear();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    // ========== Adapter ==========

    class OfflineAdapter extends BaseAdapter {
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
                convertView = getLayoutInflater().inflate(R.layout.item_offline, parent, false);
                holder = new ViewHolder();
                holder.cover = (ImageView) convertView.findViewById(R.id.cover);
                holder.title = (TextView) convertView.findViewById(R.id.title);
                holder.size = (TextView) convertView.findViewById(R.id.size);
                holder.state = (TextView) convertView.findViewById(R.id.state);
                holder.btnPlay = (ImageView) convertView.findViewById(R.id.btn_play);
                holder.itemProgress = (ProgressBar) convertView.findViewById(R.id.progress_bar);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final OfflineItem item = videoList.get(position);
            if (item != null) {
                // 显示标题：合并的多P显示视频主标题，单P显示分P标题
                String displayTitle;
                if (item.totalPageCount > 1) {
                    displayTitle = (item.mainTitle != null && item.mainTitle.length() > 0)
                            ? item.mainTitle : item.title;
                    displayTitle = displayTitle + "  [" + item.totalPageCount + "集]";
                } else {
                    displayTitle = (item.pageTitle != null && item.pageTitle.length() > 0)
                            ? item.pageTitle : item.title;
                }
                holder.title.setText(displayTitle);
                holder.size.setText(formatFileSize(item.size));

                // 画质 / av号标签
                String stateText = item.title;
                if (item.qualityName != null && item.qualityName.length() > 0) {
                    stateText = item.qualityName + "  |  " + stateText;
                }
                holder.state.setText(stateText);

                // 下载进度条
                if (holder.itemProgress != null) {
                    if (!item.isCompleted && item.totalBytes > 0) {
                        holder.itemProgress.setVisibility(View.VISIBLE);
                        int pct = (int) (item.downloadedBytes * 100 / item.totalBytes);
                        holder.itemProgress.setProgress(pct);
                        holder.state.setText("下载中 " + pct + "%");
                    } else {
                        holder.itemProgress.setVisibility(View.GONE);
                    }
                }

                // 播放/暂停/停止按钮
                if (holder.btnPlay != null) {
                    if (!item.isCompleted) {
                        // 下载中或暂停中
                        final long itemKey = item.getKey();
                        final boolean isPaused = mPausedKeys.contains(itemKey);
                        holder.btnPlay.setVisibility(View.VISIBLE);
                        if (isPaused) {
                            // 暂停中 → 显示继续按钮
                            holder.btnPlay.setImageResource(android.R.drawable.ic_media_play);
                        } else {
                            // 下载中 → 显示暂停按钮
                            holder.btnPlay.setImageResource(android.R.drawable.ic_media_pause);
                        }
                        holder.btnPlay.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (mPausedKeys.contains(itemKey)) {
                                    // 继续特定任务
                                    mPausedKeys.remove(itemKey);
                                    Intent intent = new Intent(OfflineActivity.this,
                                            VideoDownloadService.class);
                                    intent.setAction(VideoDownloadService.ACTION_RESUME);
                                    intent.putExtra("key", itemKey);
                                    startService(intent);
                                } else {
                                    // 暂停
                                    mPausedKeys.add(itemKey);
                                    Intent intent = new Intent(OfflineActivity.this,
                                            VideoDownloadService.class);
                                    intent.setAction(VideoDownloadService.ACTION_PAUSE);
                                    startService(intent);
                                }
                                adapter.notifyDataSetChanged();
                            }
                        });
                    } else {
                        // 完成 → 显示播放按钮
                        holder.btnPlay.setVisibility(View.VISIBLE);
                        holder.btnPlay.setImageResource(R.drawable.ic_btn_av_play_chroma);
                        holder.btnPlay.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (item != null && item.videoFile != null && item.videoFile.exists()) {
                                playVideo(item);
                            } else {
                                Toast.makeText(OfflineActivity.this, "视频文件不存在", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    }
                }
            }
            holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            if (item != null && !isScrolling) {
                loadCoverFromLocal(item, holder.cover, position);
            }
            return convertView;
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return (size / 1024) + " KB";
        else if (size < 1024 * 1024 * 1024) return (size / 1024 / 1024) + " MB";
        else return (size / 1024 / 1024 / 1024) + " GB";
    }

    private static String shortQualityName(String name) {
        if (name == null) return "";
        // 360P 流畅 -> 360P, 720P 高清 -> 720P
        int spaceIdx = name.indexOf(' ');
        if (spaceIdx > 0) return name.substring(0, spaceIdx);
        return name;
    }

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView size;
        TextView state;
        ImageView btnPlay;
        ProgressBar itemProgress;
    }

    static class OfflineItem {
        VideoDownloadEnvironment env;
        File videoFile;
        File coverFile;
        File danmakuFile;
        String title;
        String mainTitle;   // 视频主标题
        String pageTitle;
        long cid;
        long avid;
        int page;
        String bvid;
        String qualityName;
        long size;
        long downloadedBytes;
        long totalBytes;
        boolean isCompleted;
        List<String> pages;         // 多P的标题列表
        List<File> pagesFile;       // 多P的视频文件
        int totalPageCount;

        String getCacheKey() {
            if (env != null) return env.avid + "/" + env.page;
            return title;
        }

        long getKey() {
            return (avid << 32) | (page & 0xFFFFFFFFL);
        }
    }
}

