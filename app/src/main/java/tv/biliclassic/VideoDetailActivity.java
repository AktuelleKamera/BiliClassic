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
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
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

    // 下载选择相关
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

    // 下载对话框数据
    private List<VideoDetailFragment.VideoPage> mPages;
    private boolean[] mPageChecked;
    private int mSelectedQuality = 64;
    private String mSelectedQualityName = "720P 高清";
    private AlertDialog mDownloadDialog;

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

        // 下载/删除按钮
        ImageView btnDownload = (ImageView) findViewById(R.id.btn_download);
        if (btnDownload != null) {
            if (mOfflineMode) {
                // 离线模式：显示删除按钮
                btnDownload.setImageResource(R.drawable.ic_action_delete);
                btnDownload.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDeleteConfirmDialog();
                    }
                });
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
        if (mDownloadDialog != null && mDownloadDialog.isShowing()) {
            mDownloadDialog.dismiss();
            mDownloadDialog = null;
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

    // 显示删除确认对话框（离线模式用）
    private void showDeleteConfirmDialog() {
        String title = "";
        if (videoDetailFragment != null && videoDetailFragment.videoInfo != null) {
            title = videoDetailFragment.videoInfo.title;
        }
        if (title == null || title.length() == 0) {
            if (aid != 0L) {
                title = "av" + aid;
            } else if (bvid != null && bvid.length() > 0) {
                title = bvid;
            } else {
                title = "该视频";
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("删除离线视频")
                .setMessage("确定要删除 \"" + title + "\" 的离线缓存吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteOfflineVideo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 删除离线视频
    private void deleteOfflineVideo() {
        File downloadDir = getDownloadDir();
        File avidDir = new File(downloadDir, String.valueOf(aid));

        if (avidDir.exists() && avidDir.isDirectory()) {
            boolean deleted = deleteRecursive(avidDir);
            if (deleted) {
                Toast.makeText(this, "已删除离线缓存", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "未找到离线缓存", Toast.LENGTH_SHORT).show();
        }
    }

    // 递归删除文件/文件夹
    private boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursive(children[i]);
                }
            }
        }
        return file.delete();
    }

    // 下载相关

    private void showDownloadChoiceDialog() {
        if (!fragmentReady || videoDetailFragment == null) {
            Toast.makeText(this, "请等待页面加载完成", Toast.LENGTH_SHORT).show();
            return;
        }

        mPages = videoDetailFragment.getVideoPages();
        if (mPages == null || mPages.size() == 0) {
            Toast.makeText(this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
            return;
        }

        mPageChecked = new boolean[mPages.size()];
        mPageChecked[0] = true;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download_choice, null);

        ListView listView = (ListView) dialogView.findViewById(R.id.list_view);
        final RadioGroup qualityGroup = (RadioGroup) dialogView.findViewById(R.id.quality_group);

        int defaultQuality = SettingsActivity.getVideoQuality();
        if (defaultQuality == 16) {
            qualityGroup.check(R.id.quality_360);
        } else if (defaultQuality == 32) {
            qualityGroup.check(R.id.quality_480);
        } else {
            qualityGroup.check(R.id.quality_720);
        }

        final PageListAdapter adapter = new PageListAdapter();
        listView.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择分P和画质");
        builder.setView(dialogView);
        builder.setPositiveButton("下载", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int checkedId = qualityGroup.getCheckedRadioButtonId();
                if (checkedId == R.id.quality_360) {
                    mSelectedQuality = 16;
                    mSelectedQualityName = "360P 流畅";
                } else if (checkedId == R.id.quality_480) {
                    mSelectedQuality = 32;
                    mSelectedQualityName = "480P 清晰";
                } else {
                    mSelectedQuality = 64;
                    mSelectedQualityName = "720P 高清";
                }

                for (int i = 0; i < mPages.size(); i++) {
                    if (mPageChecked[i]) {
                        videoDetailFragment.prepareDownload(mPages.get(i), mSelectedQuality, mSelectedQualityName);
                    }
                }
                mDownloadDialog = null;
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mDownloadDialog = null;
            }
        });

        mDownloadDialog = builder.show();
    }

    // 分P列表适配器（带CheckBox）
    private class PageListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mPages.size();
        }

        @Override
        public Object getItem(int position) {
            return mPages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_download_page, parent, false);
                holder = new ViewHolder();
                holder.checkBox = (CheckBox) convertView.findViewById(R.id.checkbox);
                holder.title = (TextView) convertView.findViewById(R.id.title);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            VideoDetailFragment.VideoPage page = mPages.get(position);
            String title = page.title;
            if (title == null || title.length() == 0) {
                title = "P" + (position + 1);
            }
            holder.title.setText((position + 1) + ". " + title);
            holder.checkBox.setChecked(mPageChecked[position]);
            holder.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPageChecked[position] = ((CheckBox) v).isChecked();
                }
            });

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPageChecked[position] = !mPageChecked[position];
                    notifyDataSetChanged();
                }
            });

            return convertView;
        }
    }

    static class ViewHolder {
        CheckBox checkBox;
        TextView title;
    }

    public void startDownloadDirect(String videoUrl, String title, String pageTitle,
                                    long aid, long cid, int page,
                                    int quality, String qualityName,
                                    String coverUrl, String upName, String bvid,
                                    String description, String tags) {
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

    public void setVideoDetailFragment(Fragment fragment) {
        this.videoDetailFragment = (VideoDetailFragment) fragment;
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