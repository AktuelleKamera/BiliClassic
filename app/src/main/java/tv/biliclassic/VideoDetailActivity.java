package tv.biliclassic;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import java.util.List;

import tv.biliclassic.download.VideoDownloadService;
import tv.biliclassic.download.VideoDownloadEnvironment;

public class VideoDetailActivity extends BaseActivity {

    private ViewPager viewPager;
    private long aid;
    private String bvid;
    private VideoDetailFragment videoDetailFragment;
    private boolean fragmentReady = false;
    private TextView tvAvid;
    private Handler cleanupHandler = new Handler();
    private boolean isCleaned = false;
    private int currentPagePosition = 0;

    // 下载质量选择相关
    private String mPendingVideoUrl;
    private String mPendingTitle;
    private String mPendingPageTitle;
    private long mPendingAid;
    private long mPendingCid;
    private int mPendingPage;
    private String mPendingCoverUrl;
    private String mPendingUpName;
    private String[] mPendingQnNames;
    private int[] mPendingQnValues;
    private String mPendingBvid;
    private String mPendingDescription;
    private String mPendingTags;
    private boolean mOfflineMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_detail);

        mOfflineMode = getIntent().getBooleanExtra("offline_mode", false);

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
            if (mOfflineMode) {
                btnDownload.setVisibility(View.GONE);  // 离线模式隐藏下载按钮
            } else {
                btnDownload.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDownloadChoiceDialog();
                    }
                });
            }
        }

        viewPager = (ViewPager) findViewById(R.id.viewpager);
        viewPager.setAdapter(new VideoDetailPagerAdapter(getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(mOfflineMode ? 1 : 3);

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
            viewPager.setCurrentItem(currentPagePosition, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupHandler.removeCallbacksAndMessages(null);
        videoDetailFragment = null;
        fragmentReady = false;
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

    // ========== 下载相关（新实现） ==========

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

        // 构建自定义对话框：CheckBox选P + Radio选择画质
        final String[] qualityNames = {"360P 流畅", "480P 清晰", "720P 高清"};
        final int[] qualityValues = {16, 32, 64};
        final boolean[] pageChecked = new boolean[pages.size()];
        pageChecked[0] = true; // 默认选中第一P

        String[] pageNames = new String[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            String t = pages.get(i).title;
            if (t == null || t.length() == 0) t = "P" + (i + 1);
            pageNames[i] = (i + 1) + ". " + t;
        }

        final int[] selectedQuality = {SettingsActivity.getVideoQuality()};
        // 找默认画质在列表中的索引
        int defaultQnIdx = 0;
        for (int i = 0; i < qualityValues.length; i++) {
            if (qualityValues[i] == selectedQuality[0]) { defaultQnIdx = i; break; }
        }
        final int[] qualityIdx = {defaultQnIdx};

        // 用单层列表: 先显示P列表(多选), 再显示画质列表(单选)
        final int pageCount = pages.size();
        final int totalItems = pageCount + 1 + 3; // P + 分隔标题 + 画质
        String[] items = new String[totalItems];
        final boolean[] checkedItems = new boolean[totalItems];
        for (int i = 0; i < pageCount; i++) {
            items[i] = pageNames[i];
            checkedItems[i] = pageChecked[i];
        }
        items[pageCount] = "———— 选择画质 ————";
        for (int i = 0; i < 3; i++) {
            items[pageCount + 1 + i] = qualityNames[i];
            checkedItems[pageCount + 1 + i] = (i == qualityIdx[0]);
        }

        new AlertDialog.Builder(this)
            .setTitle("选择分P和画质")
            .setMultiChoiceItems(items, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    if (which < pageCount) {
                        pageChecked[which] = isChecked;
                    } else if (which > pageCount) {
                        // 画质选择：取消其他画质项
                        int qIdx = which - pageCount - 1;
                        if (isChecked) {
                            for (int i = 0; i < 3; i++) {
                                int pos = pageCount + 1 + i;
                                if (pos != which) {
                                    checkedItems[pos] = false;
                                    ((AlertDialog) dialog).getListView().setItemChecked(pos, false);
                                } else {
                                    qualityIdx[0] = qIdx;
                                }
                            }
                        }
                    }
                }
            })
            .setPositiveButton("下载", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    for (int i = 0; i < pageCount; i++) {
                        if (pageChecked[i]) {
                            videoDetailFragment.prepareDownload(pages.get(i), qualityValues[qualityIdx[0]],
                                    qualityNames[qualityIdx[0]]);
                        }
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 由 VideoDetailFragment 调用，传递已解析的下载信息
     * 显示画质选择对话框，用户确认后启动下载服务
     */
    /**
     * 直接启动下载服务（画质已在弹窗中选择）
     */
    public void startDownloadDirect(String videoUrl, String title, String pageTitle,
                                     long aid, long cid, int page,
                                     int quality, String qualityName,
                                     String coverUrl, String upName, String bvid,
                                     String description, String tags) {
        // 检查是否已存在
        VideoDownloadEnvironment env = new VideoDownloadEnvironment(
                getDownloadDir(), aid, page);
        if (env.getVideoFile().exists()) {
            Toast.makeText(this, "已存在: " + pageTitle, Toast.LENGTH_SHORT).show();
            return;
        }

        VideoDownloadService.startDownload(
                this, aid, bvid, title, pageTitle, cid, page,
                quality, qualityName, coverUrl, upName, videoUrl,
                description, tags);
        Toast.makeText(this, "已加入: " + pageTitle, Toast.LENGTH_SHORT).show();
    }

    // ========== /下载相关 ==========

    private File getDownloadDir() {
        if (isSDCardAvailable()) {
            File sdDownload = new File(Environment.getExternalStorageDirectory(), "BiliClassic/Download");
            if (!sdDownload.exists()) sdDownload.mkdirs();
            return sdDownload;
        }
        File internalDownload = new File(getFilesDir(), "Download");
        if (!internalDownload.exists()) internalDownload.mkdirs();
        return internalDownload;
    }

    private boolean isSDCardAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    // ========== 其他原有方法 ==========

    public void setVideoDetailFragment(VideoDetailFragment fragment) {
        this.videoDetailFragment = fragment;
        this.fragmentReady = true;
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
                if (mOfflineMode) {
                    fragmentArgs.putBoolean("offline_mode", true);
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
            return mOfflineMode ? 1 : 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }
}
