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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.util.SharedPreferencesUtil;

public class OfflineActivity extends BaseActivity {

    private ListView listView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private OfflineAdapter adapter;
    private List<OfflineItem> videoList = new ArrayList<OfflineItem>();

    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();

    private boolean isScrolling = false;

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
                OfflineItem item = videoList.get(position);
                if (item != null && item.file != null && item.file.exists()) {
                    playVideo(item.file);
                } else {
                    Toast.makeText(OfflineActivity.this, "视频文件不存在", Toast.LENGTH_SHORT).show();
                    refreshList();
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final OfflineItem item = videoList.get(position);
                if (item != null && item.file != null && item.file.exists()) {
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

    private void playVideo(File videoFile) {
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

    private void showDeleteDialog(final OfflineItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除视频")
                .setMessage("确定要删除 \"" + item.title + "\" 吗？")
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
            if (item.file.delete()) {
                if (item.coverFile != null && item.coverFile.exists()) {
                    item.coverFile.delete();
                }
                SoftReference<Bitmap> ref = imageCache.remove(item.title);
                if (ref != null) {
                    Bitmap bmp = ref.get();
                    if (bmp != null && !bmp.isRecycled()) {
                        bmp.recycle();
                    }
                }
                Toast.makeText(this, "已删除: " + item.title, Toast.LENGTH_SHORT).show();
                refreshList();
            } else {
                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshList() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<OfflineItem> items = scanVideoFiles();
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
                    }
                });
            }
        }).start();
    }

    private List<OfflineItem> scanVideoFiles() {
        List<OfflineItem> items = new ArrayList<OfflineItem>();

        File downloadDir = getDownloadDir();
        if (downloadDir != null && downloadDir.exists()) {
            scanDirectory(downloadDir, items);
        }

        return items;
    }

    private File getDownloadDir() {
        if (isSDCardAvailable()) {
            File sdDownload = new File(Environment.getExternalStorageDirectory(), "BiliClassic/Download");
            if (sdDownload.exists() || sdDownload.mkdirs()) {
                return sdDownload;
            }
        }
        File internalDownload = new File(getFilesDir(), "Download");
        if (!internalDownload.exists()) {
            internalDownload.mkdirs();
        }
        return internalDownload;
    }

    private void scanDirectory(File dir, List<OfflineItem> items) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, items);
            } else if (file.isFile() && isVideoFile(file)) {
                OfflineItem item = new OfflineItem();
                item.file = file;
                String fileName = file.getName();
                String title = getFileNameWithoutExtension(fileName);
                item.title = title;
                item.size = file.length();
                File coverDir = new File(getCacheDir(), "offline_covers");
                if (!coverDir.exists()) {
                    coverDir.mkdirs();
                }
                item.coverFile = new File(coverDir, title + ".jpg");
                items.add(item);
            }
        }
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
        } else if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        } else if (size < 1024 * 1024 * 1024) {
            return (size / 1024 / 1024) + " MB";
        } else {
            return (size / 1024 / 1024 / 1024) + " GB";
        }
    }

    private void loadCoverFromLocal(final OfflineItem item, final ImageView coverView, final int position) {
        if (item == null || coverView == null) return;
        if (isScrolling) return;

        SoftReference<Bitmap> softBitmap = imageCache.get(item.title);
        if (softBitmap != null) {
            Bitmap cachedBitmap = softBitmap.get();
            if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                coverView.setImageBitmap(cachedBitmap);
                return;
            } else {
                imageCache.remove(item.title);
            }
        }

        if (item.coverFile != null && item.coverFile.exists()) {
            Boolean isLoading = loadingMap.get(position);
            if (isLoading != null && isLoading) {
                return;
            }

            loadingMap.put(position, true);
            final ImageView imageView = coverView;
            final String title = item.title;

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    final Bitmap bitmap = BitmapFactory.decodeFile(item.coverFile.getAbsolutePath(), options);

                    loadingMap.remove(position);

                    if (bitmap != null && !bitmap.isRecycled()) {
                        imageCache.put(title, new SoftReference<Bitmap>(bitmap));
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            OfflineItem item = videoList.get(position);
            if (item != null) {
                holder.title.setText(item.title);
                holder.size.setText(formatFileSize(item.size));
            }

            holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);

            if (item != null && !isScrolling) {
                loadCoverFromLocal(item, holder.cover, position);
            }

            return convertView;
        }
    }

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView size;
    }

    static class OfflineItem {
        File file;
        File coverFile;
        String title;
        long size;
    }
}