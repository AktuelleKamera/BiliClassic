package tv.biliclassic;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import tv.biliclassic.api.VideoInfoApi;
import tv.biliclassic.model.VideoInfo;

public class VideoDetailActivity extends BaseActivity {

    private ViewPager viewPager;
    private long aid;
    private String bvid;
    private ProgressDialog downloadDialog;
    private VideoDetailFragment videoDetailFragment;
    private boolean fragmentReady = false;
    private TextView tvAvid;
    private Handler cleanupHandler = new Handler();
    private boolean isCleaned = false;
    private int currentPagePosition = 0;  // 保存当前页面位置

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_detail);

        PagerTabStrip tabStrip = (PagerTabStrip) findViewById(R.id.pager_tab_strip);
        if (tabStrip != null) {
            tabStrip.setTabIndicatorColor(0xFFFCA3C5);
            tabStrip.setBackgroundColor(0xFFD86DA5);
            tabStrip.setTextColor(0xFFFFFFFF);
        }

        Intent intent = getIntent();
        Uri data = intent.getData();

        aid = 0L;
        bvid = null;

        if (data != null) {
            parseExternalUri(data);
        } else {
            aid = intent.getLongExtra("aid", 0L);
            bvid = intent.getStringExtra("bvid");
        }

        if (aid == 0L && (bvid == null || bvid.length() == 0)) {
            Toast.makeText(this, "视频参数无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvAvid = (TextView) findViewById(R.id.tv_avid);
        updateAvidDisplay();

        tvAvid.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                copyAvidToClipboard();
                return true;
            }
        });

        ImageView btnShare = (ImageView) findViewById(R.id.btn_share);
        if (btnShare != null) {
            btnShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shareVideo();
                }
            });
        }

        ImageView btnDownload = (ImageView) findViewById(R.id.btn_download);
        if (btnDownload != null) {
            btnDownload.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDownloadChoiceDialog();
                }
            });
        }

        viewPager = (ViewPager) findViewById(R.id.viewpager);
        viewPager.setAdapter(new VideoDetailPagerAdapter(getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(3);

        // 监听页面切换，保存当前位置
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                currentPagePosition = position;
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (downloadDialog != null && downloadDialog.isShowing()) {
            downloadDialog.dismiss();
            downloadDialog = null;
        }

        // 保存当前页面位置
        if (viewPager != null) {
            currentPagePosition = viewPager.getCurrentItem();
        }

        isCleaned = false;
        cleanupHandler.removeCallbacksAndMessages(null);
        cleanupHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && viewPager != null && !isCleaned) {
                    viewPager.setAdapter(null);
                    isCleaned = true;
                }
            }
        }, 300);
    }

    @Override
    protected void onResume() {
        super.onResume();

        cleanupHandler.removeCallbacksAndMessages(null);
        isCleaned = false;

        if (viewPager != null && viewPager.getAdapter() == null) {
            viewPager.setAdapter(new VideoDetailPagerAdapter(getSupportFragmentManager()));
            // 恢复到之前的位置
            viewPager.setCurrentItem(currentPagePosition, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupHandler.removeCallbacksAndMessages(null);
        if (downloadDialog != null) {
            if (downloadDialog.isShowing()) {
                downloadDialog.dismiss();
            }
            downloadDialog = null;
        }
        videoDetailFragment = null;
        fragmentReady = false;
        if (viewPager != null) {
            try {
                viewPager.setAdapter(null);
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    private void updateAvidDisplay() {
        if (aid != 0L) {
            tvAvid.setText("av" + aid);
        } else if (bvid != null && bvid.length() > 0) {
            tvAvid.setText(bvid);
        } else {
            tvAvid.setText("参数错误");
        }
    }

    private void copyAvidToClipboard() {
        String copyText = "";
        if (aid != 0L) {
            copyText = "av" + aid;
        } else if (bvid != null && bvid.length() > 0) {
            copyText = bvid;
        }

        if (copyText == null || copyText.length() == 0) {
            Toast.makeText(this, "无法复制", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setText(copyText);
                Toast.makeText(this, "已复制: " + copyText, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareVideo() {
        String shareText = "";
        String shareUrl = "";

        if (aid != 0L) {
            shareText = "av" + aid;
            shareUrl = "https://www.bilibili.com/video/av" + aid;
        } else if (bvid != null && bvid.length() > 0) {
            shareText = bvid;
            shareUrl = "https://www.bilibili.com/video/" + bvid;
        } else {
            Toast.makeText(this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalShareText = shareText;
        final String finalShareUrl = shareUrl;

        final String[] shareOptions = {"复制链接", "分享到...", "取消"};

        new AlertDialog.Builder(this)
                .setTitle("分享视频")
                .setItems(shareOptions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            copyToClipboard(finalShareUrl);
                            Toast.makeText(VideoDetailActivity.this, "链接已复制", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_TEXT, finalShareText + "\n" + finalShareUrl);
                            startActivity(Intent.createChooser(shareIntent, "分享到"));
                        }
                    }
                })
                .show();
    }

    private void copyToClipboard(String text) {
        try {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setText(text);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showDownloadChoiceDialog() {
        if (!fragmentReady || videoDetailFragment == null) {
            Toast.makeText(this, "请等待页面加载完成", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<VideoDetailFragment.VideoPage> pages = videoDetailFragment.getVideoPages();
        if (pages == null || pages.size() == 0) {
            Toast.makeText(this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pages.size() == 1) {
            VideoDetailFragment.VideoPage page = pages.get(0);
            videoDetailFragment.startDownload(page);
        } else {
            final String[] pageNames = new String[pages.size()];
            for (int i = 0; i < pages.size(); i++) {
                VideoDetailFragment.VideoPage page = pages.get(i);
                String title = page.title;
                if (title == null || title.length() == 0) {
                    title = "P" + (i + 1);
                }
                pageNames[i] = (i + 1) + ". " + title;
            }

            new AlertDialog.Builder(this)
                    .setTitle("选择要下载的分P")
                    .setItems(pageNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            VideoDetailFragment.VideoPage selectedPage = pages.get(which);
                            videoDetailFragment.startDownload(selectedPage);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    public void setVideoDetailFragment(VideoDetailFragment fragment) {
        this.videoDetailFragment = fragment;
        this.fragmentReady = true;
    }

    public void startDownloadWithUrl(String videoUrl, String title, long aid, long cid, int pageIndex) {
        downloadVideo(videoUrl, title, aid, cid);
    }

    private void downloadVideo(final String videoUrl, final String displayTitle, final long aid, final long cid) {
        File downloadDir;
        if (isSDCardAvailable()) {
            downloadDir = new File(Environment.getExternalStorageDirectory(), "BiliClassic/Download");
        } else {
            downloadDir = new File(getFilesDir(), "Download");
        }

        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        String safeDisplayTitle = (displayTitle == null || displayTitle.length() == 0) ? "video" : displayTitle;
        String fileName = safeDisplayTitle.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_") + ".mp4";
        final File outputFile = new File(downloadDir, fileName);

        if (outputFile.exists()) {
            Toast.makeText(this, "文件已存在: " + outputFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return;
        }

        startDownload(videoUrl, outputFile, safeDisplayTitle, aid);
    }

    private void startDownload(final String videoUrl, final File outputFile, final String displayTitle, final long aid) {
        downloadDialog = new ProgressDialog(this);
        downloadDialog.setTitle("正在下载");
        downloadDialog.setMessage(displayTitle);
        downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadDialog.setCancelable(true);
        downloadDialog.setMax(100);
        downloadDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadFile(videoUrl, outputFile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (downloadDialog != null && downloadDialog.isShowing()) {
                                downloadDialog.dismiss();
                            }
                            Toast.makeText(VideoDetailActivity.this, "下载完成: " + outputFile.getName(), Toast.LENGTH_LONG).show();
                            downloadCoverForVideo(aid, outputFile.getName());
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (downloadDialog != null && downloadDialog.isShowing()) {
                                downloadDialog.dismiss();
                            }
                            Toast.makeText(VideoDetailActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void downloadCoverForVideo(final long aid, final String videoFileName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    VideoInfo videoInfo = VideoInfoApi.getVideoInfo(aid);
                    if (videoInfo == null) {
                        return;
                    }

                    String coverFileName = videoFileName;
                    if (coverFileName.endsWith(".mp4")) {
                        coverFileName = coverFileName.substring(0, coverFileName.length() - 4);
                    }

                    if (videoInfo.cover != null && videoInfo.cover.length() > 0) {
                        String coverUrl = videoInfo.cover;
                        if (coverUrl.startsWith("https://")) {
                            coverUrl = "http://" + coverUrl.substring(8);
                        }

                        Bitmap bitmap = downloadImage(coverUrl);
                        if (bitmap != null && !bitmap.isRecycled()) {
                            File coverDir = new File(getCacheDir(), "offline_covers");
                            if (!coverDir.exists()) {
                                coverDir.mkdirs();
                            }
                            File coverFile = new File(coverDir, coverFileName + ".jpg");
                            saveCoverToFile(bitmap, coverFile);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Bitmap downloadImage(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            InputStream is = conn.getInputStream();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            return bitmap;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void saveCoverToFile(Bitmap bitmap, File coverFile) {
        try {
            FileOutputStream fos = new FileOutputStream(coverFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void downloadFile(String videoUrl, File outputFile) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(videoUrl).openConnection();
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.connect();

        int contentLength = conn.getContentLength();
        InputStream is = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(outputFile);
        byte[] buffer = new byte[8192];
        int len;
        long total = 0L;
        int lastProgress = 0;

        while ((len = is.read(buffer)) != -1) {
            fos.write(buffer, 0, len);
            total += len;

            if (contentLength > 0) {
                final int percent = (int) (total * 100 / contentLength);
                if (percent > lastProgress) {
                    lastProgress = percent;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (downloadDialog != null) {
                                downloadDialog.setProgress(percent);
                            }
                        }
                    });
                }
            }
        }
        fos.close();
        is.close();
        conn.disconnect();
    }

    private boolean isSDCardAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void parseExternalUri(Uri data) {
        String scheme = data.getScheme();
        String host = data.getHost();
        String path = data.getPath();

        if ("bilibili".equals(scheme) && "video".equals(host)) {
            List segments = data.getPathSegments();
            if (segments != null && segments.size() > 0) {
                String videoId = (String) segments.get(0);
                if (videoId.startsWith("BV")) {
                    bvid = videoId;
                } else if (videoId.startsWith("av")) {
                    try {
                        aid = Long.parseLong(videoId.substring(2));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if ("https".equals(scheme) && "www.bilibili.com".equals(host) && path != null && path.startsWith("/video/")) {
            String videoId = path.substring(7);
            if (videoId.startsWith("BV")) {
                bvid = videoId;
            }
        }

        if ("https".equals(scheme) && "b23.tv".equals(host) && path != null) {
            String videoId = path.substring(1);
            if (videoId.startsWith("BV")) {
                bvid = videoId;
            }
        }
    }

    private class VideoDetailPagerAdapter extends FragmentPagerAdapter {
        private String[] titles = {"视频详情", "相关视频", "评论"};

        public VideoDetailPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                VideoDetailFragment fragment = new VideoDetailFragment();
                Bundle fragmentArgs = new Bundle();
                fragmentArgs.putLong("aid", aid);
                if (bvid != null) {
                    fragmentArgs.putString("bvid", bvid);
                }
                fragment.setArguments(fragmentArgs);
                return fragment;
            } else if (position == 1) {
                RelatedVideosFragment fragment = new RelatedVideosFragment();
                Bundle relatedArgs = new Bundle();
                relatedArgs.putLong("aid", aid);
                if (bvid != null) {
                    relatedArgs.putString("bvid", bvid);
                }
                fragment.setArguments(relatedArgs);
                return fragment;
            } else {
                CommentFragment fragment = new CommentFragment();
                Bundle commentArgs = new Bundle();
                commentArgs.putLong("aid", aid);
                if (bvid != null) {
                    commentArgs.putString("bvid", bvid);
                }
                fragment.setArguments(commentArgs);
                return fragment;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }
}