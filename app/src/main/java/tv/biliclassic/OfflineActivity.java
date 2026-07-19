package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView;
import android.widget.AdapterView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import tv.biliclassic.download.VideoDownloadEntry;
import tv.biliclassic.download.VideoDownloadEnvironment;
import tv.biliclassic.download.VideoDownloadService;
import tv.biliclassic.util.FileProviderCompat;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.player.BiliPlayerActivity;

import tv.biliclassic.util.SdkHelper;
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

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private int getConfiguredThreadCount() {
        int savedThreads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (savedThreads > 0) {
            return savedThreads;
        }
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

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= videoList.size()) return false;
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
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
        mRefreshHandler.removeCallbacksAndMessages(null);
        if (adapter != null) {
            for (SoftReference<Bitmap> ref : imageCache.values()) {
                Bitmap bmp = ref.get();
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }
            }
            imageCache.clear();
            loadingMap.clear();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    public void onOfflineItemClick(OfflineItem item, int position) {
        if (item != null) {
            playVideo(item);
        }
    }

    // 播放视频
    private void playVideo(OfflineItem item) {
        if (!item.isCompleted && item.avid > 0) {
            Intent intent = new Intent(this, VideoDetailActivity.class);
            intent.putExtra("aid", item.avid);
            startActivity(intent);
            return;
        }

        if (item.videoFile == null || !item.videoFile.exists()) {
            Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show();
            refreshList();
            return;
        }

        if (item.env != null && item.env.avid > 0) {
            Intent intent = new Intent(this, VideoDetailActivity.class);
            intent.putExtra("aid", item.env.avid);
            intent.putExtra("offline_mode", true);
            startActivity(intent);
            return;
        }

        int playerPref = SettingsActivity.getPlayerPreference();

        if (playerPref == 8) {
            Intent intent = new Intent(this, BiliPlayerActivity.class);
            String displayTitle = (item.pageTitle != null && item.pageTitle.length() > 0)
                    ? item.pageTitle : item.title;
            intent.putExtra("video_title", displayTitle);
            intent.putExtra("cache_path", item.videoFile.getAbsolutePath());
            if (item.danmakuFile != null && item.danmakuFile.exists()) {
                intent.putExtra("danmaku_cache_path", item.danmakuFile.getAbsolutePath());
            }
            if (item.cid > 0) {
                intent.putExtra("cid", item.cid);
            }
            intent.putExtra("offline_mode", true);
            if (item.qualityName != null && item.qualityName.length() > 0) {
                intent.putExtra("qn_str_array", new String[]{item.qualityName});
            }
            intent.putExtra("current_qn", 0);
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri videoUri = SdkHelper.getSdkInt() >= 24
                ? FileProviderCompat.getUriForFile(this, item.videoFile)
                : Uri.fromFile(item.videoFile);
        intent.setDataAndType(videoUri, "video/mp4");
        if (SdkHelper.getSdkInt() >= 24) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        String preferredPlayer = SettingsActivity.getPlayerPackageName();
        if (preferredPlayer != null) {
            intent.setPackage(preferredPlayer);
            if (getPackageManager().queryIntentActivities(intent, 0).size() == 0) {
                intent.setPackage(null);
            }
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            // 首选播放器失败，尝试不带 package 重新解析（弹出系统选择器）
            if (preferredPlayer != null) {
                intent.setPackage(null);
                try {
                    startActivity(intent);
                    return;
                } catch (Exception e2) {
                }
            }
            // 全部失败 → 用内置播放器
            Intent builtin = new Intent(this, BiliPlayerActivity.class);
            builtin.putExtra("video_title", item.title);
            builtin.putExtra("video_url", "");
            builtin.putExtra("cache_path", item.videoFile.getAbsolutePath());
            builtin.putExtra("aid", item.avid);
            builtin.putExtra("cid", item.cid);
            builtin.putExtra("offline_mode", true);
            builtin.putExtra("online_mode", false);
            startActivity(builtin);
        }
    }

    // 删除对话框
    private void showDeleteDialog(final OfflineItem item) {
        String displayTitle = (item.pageTitle != null && item.pageTitle.length() > 0)
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

    // 删除视频
    private void deleteVideo(OfflineItem item) {
        try {
            if (item.isCompleted && item.env != null) {
                item.env.deleteEntry();
            } else if (!item.isCompleted) {
                Intent cancelIntent = new Intent(this, VideoDownloadService.class);
                cancelIntent.setAction(VideoDownloadService.ACTION_CANCEL);
                startService(cancelIntent);
                if (item.env != null) {
                    item.env.deleteEntry();
                } else if (item.videoFile != null) {
                    item.videoFile.delete();
                }
            } else {
                if (item.videoFile != null && item.videoFile.exists()) {
                    item.videoFile.delete();
                }
            }
            if (item.coverFile != null && item.coverFile.exists()) {
                item.coverFile.delete();
            }

            SoftReference<Bitmap> ref = imageCache.remove(item.getCacheKey());
            if (ref != null) {
                Bitmap bmp = ref.get();
                if (bmp != null && !bmp.isRecycled()) {
                    bmp.recycle();
                }
            }

            Toast.makeText(this, "已删除: " + item.title, Toast.LENGTH_SHORT).show();
            refreshList();
        } catch (Exception e) {
            Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 刷新列表
    private void refreshList() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                mDownloadDir = getDownloadDir();
                final List<OfflineItem> items = scanAllItems();
                final File downloadDir = mDownloadDir;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        videoList.clear();
                        videoList.addAll(items);
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
                        startAutoRefreshIfNeeded();
                    }
                });
            }
        }).start();
    }

    // 静默刷新
    private void refreshListSilent() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mDownloadDir = getDownloadDir();
                final List<OfflineItem> items = scanAllItems();
                final File downloadDir = mDownloadDir;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        videoList.clear();
                        videoList.addAll(items);
                        adapter.notifyDataSetChanged();
                        updateStorageBar(downloadDir);
                        startAutoRefreshIfNeeded();
                    }
                });
            }
        }).start();
    }

    // 更新存储空间显示
    private void updateStorageBar(File downloadDir) {
        try {
            long totalBytes;
            long availBytes;

            try {
                String path;
                if (downloadDir.exists()) {
                    path = downloadDir.getAbsolutePath();
                } else if (PermissionUtil.hasWriteStorage(this)) {
                    path = Environment.getExternalStorageDirectory().getPath();
                } else {
                    path = getFilesDir().getPath();
                }
                StatFs stat = new StatFs(path);
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

    // 自动刷新
    private void startAutoRefreshIfNeeded() {
        boolean hasInProgress = false;
        for (int i = 0; i < videoList.size(); i++) {
            OfflineItem item = videoList.get(i);
            if (!item.isCompleted) {
                hasInProgress = true;
                break;
            }
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

    // 扫描
    private List<OfflineItem> scanAllItems() {
        List<OfflineItem> items = new ArrayList<OfflineItem>();
        File downloadDir = mDownloadDir;

        Log.d("OfflineActivity", "=== 开始扫描下载目录 ===");
        Log.d("OfflineActivity", "目录路径: " + (downloadDir != null ? downloadDir.getAbsolutePath() : "null"));

        if (downloadDir == null || !downloadDir.isDirectory()) {
            Log.w("OfflineActivity", "下载目录无效或不存在");
            return items;
        }

        File[] allFiles = downloadDir.listFiles();
        if (allFiles == null) {
            Log.w("OfflineActivity", "listFiles() 返回 null");
            return items;
        }

        Log.d("OfflineActivity", "目录下总文件数: " + allFiles.length);
        for (int i = 0; i < allFiles.length; i++) {
            File f = allFiles[i];
            Log.d("OfflineActivity", "  文件 " + i + ": " + f.getName() + " (目录:" + f.isDirectory() + ", 视频:" + isVideoFile(f) + ")");
        }

        // 1. 扫描新版
        Log.d("OfflineActivity", "--- 扫描新版 ---");
        ArrayList<VideoDownloadEntry> entries = VideoDownloadEnvironment.loadAllEntries(downloadDir);
        if (entries != null) {
            Log.d("OfflineActivity", "新版条目数: " + entries.size());
            for (int i = 0; i < entries.size(); i++) {
                VideoDownloadEntry entry = entries.get(i);
                OfflineItem item = new OfflineItem();
                item.env = entry.downloadEnv;
                item.videoFile = entry.getVideoFile();
                item.coverFile = entry.getCoverFile();
                item.danmakuFile = entry.getDanmakuFile();
                item.title = (entry.bvid != null && entry.bvid.length() > 0)
                        ? entry.bvid : "av" + entry.avid;
                item.mainTitle = entry.title;
                item.pageTitle = entry.pageTitle;
                item.bvid = entry.bvid;
                item.cid = entry.cid;
                item.qualityName = shortQualityName(entry.qualityName);
                item.isCompleted = entry.isCompleted;
                item.downloadedBytes = entry.downloadedBytes;
                item.totalBytes = entry.totalBytes;
                item.isPaused = entry.isPaused;
                item.avid = entry.avid;
                item.page = entry.page;
                if (item.videoFile != null && item.videoFile.exists()) {
                    item.size = item.videoFile.length();
                    items.add(item);
                    Log.d("OfflineActivity", "  添加新版: " + item.videoFile.getName() + " (完成)");
                } else if (!entry.isCompleted && item.videoFile != null) {
                    item.size = entry.downloadedBytes;
                    items.add(item);
                    Log.d("OfflineActivity", "  添加新版: " + item.videoFile.getName() + " (下载中)");
                } else {
                    Log.d("OfflineActivity", "  跳过新版: " + (item.videoFile != null ? item.videoFile.getName() : "null") + " (文件不存在)");
                }
            }
        } else {
            Log.d("OfflineActivity", "新版条目: null");
        }

        // 2. 扫描旧版平面文件
        Log.d("OfflineActivity", "--- 扫描旧版 ---");
        int oldFileCount = 0;
        for (int i = 0; i < allFiles.length; i++) {
            File file = allFiles[i];
            if (file.isFile() && isVideoFile(file)) {
                oldFileCount++;
                Log.d("OfflineActivity", "找到旧版视频文件: " + file.getName());

                boolean alreadyFound = false;
                for (int j = 0; j < items.size(); j++) {
                    OfflineItem existing = items.get(j);
                    if (existing.videoFile != null && existing.videoFile.equals(file)) {
                        alreadyFound = true;
                        Log.d("OfflineActivity", "  文件已在新版中: " + file.getName());
                        break;
                    }
                }
                if (!alreadyFound) {
                    Log.d("OfflineActivity", "  添加旧版文件: " + file.getName());
                    OfflineItem item = new OfflineItem();
                    item.videoFile = file;
                    String name = getFileNameWithoutExtension(file.getName());
                    item.title = name;
                    item.size = file.length();
                    item.isCompleted = true;

                    long avid = extractAvidFromFileName(name);
                    if (avid > 0) {
                        item.avid = avid;
                        int page = extractPageFromFileName(name);
                        if (page > 0) {
                            item.page = page;
                        }
                        Log.d("OfflineActivity", "    提取 avid=" + avid + ", page=" + item.page);
                    }

                    item.coverFile = getUnifiedCoverFile(item, name);
                    migrateOldCover(name, item.coverFile);

                    items.add(item);
                    Log.d("OfflineActivity", "  当前 items 总数: " + items.size());
                }
            }
        }
        Log.d("OfflineActivity", "旧版视频文件总数: " + oldFileCount);

        // 3. 确保所有视频文件都被添加
        Log.d("OfflineActivity", "--- 检查遗漏 ---");
        int beforeCheck = items.size();
        for (int i = 0; i < allFiles.length; i++) {
            File file = allFiles[i];
            if (file.isFile() && isVideoFile(file)) {
                boolean found = false;
                for (int j = 0; j < items.size(); j++) {
                    OfflineItem existing = items.get(j);
                    if (existing.videoFile != null && existing.videoFile.equals(file)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Log.d("OfflineActivity", "  补充添加遗漏文件: " + file.getName());
                    OfflineItem item = new OfflineItem();
                    item.videoFile = file;
                    String name = getFileNameWithoutExtension(file.getName());
                    item.title = name;
                    item.size = file.length();
                    item.isCompleted = true;
                    item.coverFile = getUnifiedCoverFile(item, name);
                    migrateOldCover(name, item.coverFile);
                    items.add(item);
                }
            }
        }
        Log.d("OfflineActivity", "检查前: " + beforeCheck + ", 检查后: " + items.size());

        Log.d("OfflineActivity", "=== 扫描完成，共 " + items.size() + " 个条目 ===");

        // 4. 合并多 P
        List<OfflineItem> grouped = groupCompletedItems(items);
        Log.d("OfflineActivity", "合并后: " + grouped.size() + " 个条目");
        return grouped;
    }

    // 统一获取封面文件路径
    private File getUnifiedCoverFile(OfflineItem item, String title) {
        File downloadDir = getDownloadDir();
        if (item.avid > 0) {
            File avidDir = new File(downloadDir, String.valueOf(item.avid));
            if (!avidDir.exists()) {
                avidDir.mkdirs();
            }
            return new File(avidDir, "cover.jpg");
        } else {
            File coverDir = new File(downloadDir, "_covers");
            if (!coverDir.exists()) {
                coverDir.mkdirs();
            }
            String hash = String.valueOf(Math.abs(title.hashCode()));
            return new File(coverDir, hash + ".jpg");
        }
    }

    // 迁移旧版封面
    private void migrateOldCover(String title, File targetFile) {
        if (targetFile.exists()) {
            return;
        }

        File oldCoverDir = new File(getCacheDir(), "offline_covers");
        if (oldCoverDir.exists()) {
            File oldCover = new File(oldCoverDir, title + ".jpg");
            if (oldCover.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(oldCover);
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    fis.close();
                    oldCover.delete();
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
    }

    // 合并多 P
    private List<OfflineItem> groupCompletedItems(List<OfflineItem> items) {
        List<OfflineItem> result = new ArrayList<OfflineItem>();
        Map<Long, OfflineItem> completedGroups = new HashMap<Long, OfflineItem>();
        List<OfflineItem> inProgressList = new ArrayList<OfflineItem>();

        for (int i = 0; i < items.size(); i++) {
            OfflineItem item = items.get(i);
            if (!item.isCompleted) {
                inProgressList.add(item);
            } else if (item.avid > 0) {
                OfflineItem group = completedGroups.get(item.avid);
                if (group == null) {
                    completedGroups.put(item.avid, item);
                    result.add(item);
                } else {
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
                    group.size += item.size;
                    group.pageTitle = null;
                }
            } else {
                result.add(item);
            }
        }

        result.addAll(0, inProgressList);
        return result;
    }

    // 提取 avid
    private long extractAvidFromFileName(String name) {
        Pattern p = Pattern.compile("av(\\d+)");
        Matcher m = p.matcher(name);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {}
        }
        p = Pattern.compile("\\[(\\d+)\\]");
        m = p.matcher(name);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {}
        }
        return 0;
    }

    // 提取 page
    private int extractPageFromFileName(String name) {
        Pattern p = Pattern.compile("[PpEe](\\d+)");
        Matcher m = p.matcher(name);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {}
        }
        p = Pattern.compile("第(\\d+)话");
        m = p.matcher(name);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {}
        }
        p = Pattern.compile("P(\\d+)");
        m = p.matcher(name);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {}
        }
        return 1;
    }

    private File getDownloadDir() {
        if (isSDCardAvailable() && PermissionUtil.hasWriteStorage(this)) {
            File sdDownload = new File(Environment.getExternalStorageDirectory(), "BiliClassic/Download");
            if (sdDownload.exists()) {
                return sdDownload;
            }
            if (sdDownload.mkdirs()) {
                return sdDownload;
            }
        }
        File internalDownload = new File(getFilesDir(), "Download");
        if (!internalDownload.exists()) {
            internalDownload.mkdirs();
        }
        return internalDownload;
    }

    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".flv") || name.endsWith(".mkv")
                || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".3gp");
    }

    private String getFileNameWithoutExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }

    private boolean isSDCardAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024L * 1024) {
            return String.format("%.1f KB", size / 1024.0f);
        } else if (size < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0f * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0f * 1024 * 1024));
        }
    }

    private String shortQualityName(String name) {
        if (name == null) return "";
        int spaceIdx = name.indexOf(' ');
        if (spaceIdx > 0) return name.substring(0, spaceIdx);
        return name;
    }

    // 加载封面
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

        if (item.coverFile == null || !item.coverFile.exists()) {
            coverView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            return;
        }

        Boolean isLoading = loadingMap.get(position);
        if (isLoading != null && isLoading) {
            return;
        }

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

    // Adapter
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
                holder.progressIndeterminate = (ImageView) convertView.findViewById(R.id.progress_indeterminate);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final OfflineItem item = videoList.get(position);
            if (item != null) {
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

                String stateText = item.title;
                if (item.qualityName != null && item.qualityName.length() > 0) {
                    stateText = item.qualityName + "  |  " + stateText;
                }
                holder.state.setText(stateText);

                if (holder.itemProgress != null) {
                    if (!item.isCompleted && item.totalBytes > 0) {
                        holder.itemProgress.setVisibility(View.VISIBLE);
                        holder.itemProgress.setIndeterminate(false);
                        int pct = (int) (item.downloadedBytes * 100 / item.totalBytes);
                        holder.itemProgress.setProgress(pct);
                        holder.state.setText("下载中 " + pct + "%");
                        if (holder.progressIndeterminate != null) {
                            holder.progressIndeterminate.setVisibility(View.GONE);
                        }
                    } else if (!item.isCompleted && item.totalBytes <= 0) {
                        holder.itemProgress.setVisibility(View.GONE);
                        if (holder.progressIndeterminate != null) {
                            holder.progressIndeterminate.setVisibility(View.VISIBLE);
                            final ImageView iv = holder.progressIndeterminate;
                            mainHandler.post(new Runnable() {
                                public void run() {
                                    android.graphics.drawable.AnimationDrawable anim =
                                            (android.graphics.drawable.AnimationDrawable) iv.getDrawable();
                                    if (anim != null) {
                                        anim.stop();
                                        anim.start();
                                    }
                                }
                            });
                        }
                        holder.state.setText("准备中...");
                    } else {
                        holder.itemProgress.setVisibility(View.GONE);
                        if (holder.progressIndeterminate != null) {
                            holder.progressIndeterminate.setVisibility(View.GONE);
                        }
                    }
                }

                if (holder.btnPlay != null) {
                    if (!item.isCompleted) {
                        final long itemKey = item.getKey();
                        final boolean isPaused = item.isPaused;
                        holder.btnPlay.setVisibility(View.VISIBLE);
                        holder.btnPlay.setImageResource(isPaused
                                ? android.R.drawable.ic_media_play
                                : android.R.drawable.ic_media_pause);
                        holder.btnPlay.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (item.isPaused) {
                                    item.isPaused = false;
                                    saveItemPaused(item, false);
                                    Intent intent = new Intent(OfflineActivity.this,
                                            VideoDownloadService.class);
                                    intent.setAction(VideoDownloadService.ACTION_RESUME);
                                    intent.putExtra("key", itemKey);
                                    startService(intent);
                                } else {
                                    item.isPaused = true;
                                    saveItemPaused(item, true);
                                    Intent intent = new Intent(OfflineActivity.this,
                                            VideoDownloadService.class);
                                    intent.setAction(VideoDownloadService.ACTION_PAUSE);
                                    intent.putExtra("key", itemKey);
                                    startService(intent);
                                }
                                adapter.notifyDataSetChanged();
                            }
                        });
                    } else {
                        holder.btnPlay.setVisibility(View.VISIBLE);
                        holder.btnPlay.setImageResource(R.drawable.ic_btn_av_play_chroma);
                        holder.btnPlay.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                playVideo(item);
                            }
                        });
                    }
                }
            }

            holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            if (item != null && !isScrolling) {
                loadCoverFromLocal(item, holder.cover, position);
            }

            final int pos = position;
            final OfflineItem clickItem = item;
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onOfflineItemClick(clickItem, pos);
                }
            });

            return convertView;
        }
    }

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView size;
        TextView state;
        ImageView btnPlay;
        ProgressBar itemProgress;
        ImageView progressIndeterminate;
    }

    static class OfflineItem {
        VideoDownloadEnvironment env;
        File videoFile;
        File coverFile;
        File danmakuFile;
        String title;
        String mainTitle;
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
        List<String> pages;
        List<File> pagesFile;
        int totalPageCount;
        boolean isPaused;

        long getKey() {
            return (avid << 32) | (page & 0xFFFFFFFFL);
        }

        String getCacheKey() {
            if (env != null) return env.avid + "/" + env.page;
            if (title != null) return title;
            return String.valueOf(System.identityHashCode(this));
        }
    }

    private void saveItemPaused(OfflineItem item, boolean paused) {
        if (item.env == null) return;
        try {
            VideoDownloadEntry entry = item.env.loadEntry();
            if (entry != null) {
                entry.isPaused = paused;
                item.env.saveEntry(entry);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}