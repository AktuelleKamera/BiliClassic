package tv.biliclassic;

import android.app.AlertDialog;
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
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import tv.biliclassic.api.BangumiApi;
import tv.biliclassic.api.FavoriteApi;
import tv.biliclassic.api.InteractionApi;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.ReplyHelper;
import tv.biliclassic.api.ReplyApi;
import tv.biliclassic.api.VideoInfoApi;
import tv.biliclassic.download.VideoDownloadService;
import tv.biliclassic.download.VideoDownloadEnvironment;
import tv.biliclassic.util.BroadcastConstants;
import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.model.VideoInfo;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

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

    // 番剧相关
    private boolean isBangumi = false;
    private long epid = -1;
    private long mBangumiMediaId = 0;
    private boolean fromBangumi = false;
    private boolean mOfflineMode;

    // 下载对话框数据
    private List<VideoDetailFragment.VideoPage> mPages;
    private boolean[] mPageChecked;
    private int mSelectedQuality = 64;
    private String mSelectedQualityName = "720P 高清";
    private AlertDialog mDownloadDialog;

    // 收藏相关
    private boolean mIsFavorited = false;
    private ImageView btnFavorite;
    private boolean mIsFavoriteLoading = false;
    private boolean mIsFavoriteUpdating = false;
    private boolean mIsDeleteDialogShowing = false;
    private boolean mIsPausingForTransient = false;

    public void setPausingForTransient(boolean pausing) {
        mIsPausingForTransient = pausing;
    }

    // 评论相关
    private ImageView btnComment;
    private ArrayList<String> pendingImageDataList = new ArrayList<String>();
    private static final int MAX_COMMENT_IMAGES = 9;
    private TextView dialogImageBtn = null;
    private static final int REQUEST_PICK_COMMENT_IMAGE = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_detail);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mOfflineMode = getIntent().getBooleanExtra("offline_mode", false);
        fromBangumi = getIntent().getBooleanExtra("from_bangumi", false);

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

        // 从番剧分集点击进入（两个 Tab：视频详情 + 评论，显示 AV 号）
        long bangumiMediaId = intent.getLongExtra("bangumi_media_id", 0);
        if (bangumiMediaId > 0) {
            isBangumi = false;
            mBangumiMediaId = bangumiMediaId;

            viewPager = (ViewPager) findViewById(R.id.viewpager);
            viewPager.setAdapter(new TwoTabPagerAdapter(getSupportFragmentManager()));
            viewPager.setOffscreenPageLimit(1);
            viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    currentPagePosition = position;
                }
            });

            // 初始化底部按钮
            initBottomButtons();

            return;
        }

        // 外部点击番剧进入（番剧详情页 + 评论，显示番剧名称）
        if (!mOfflineMode && (aid != 0 || (bvid != null && bvid.length() > 0))) {
            if (fromBangumi) {
                isBangumi = true;

                // 直接显示番剧名称，不调用 updateAvidDisplay()
                String bangumiTitle = intent.getStringExtra("bangumi_title");
                if (bangumiTitle != null && bangumiTitle.length() > 0) {
                    tvAvid.setText(bangumiTitle);
                } else {
                    tvAvid.setText("番剧详情");
                }

                fetchBangumiInfoFromAid(aid);
                return;
            }
            checkAndSetup();
            return;
        }

        // 普通视频
        updateAvidDisplay();
        initNormalVideo();

        // 初始化底部按钮
        initBottomButtons();
    }

    private void fetchBangumiInfoFromAid(final long aid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    VideoInfo info = VideoInfoApi.getVideoInfo(aid);
                    if (info != null && info.epid > 0) {
                        epid = info.epid;
                        BangumiApi.SeasonInfo seasonInfo = BangumiApi.getSeasonInfoFromEpid(epid);
                        Log.d("Bangumi", "aid=" + aid + ", epid=" + epid +
                                ", seasonId=" + (seasonInfo != null ? seasonInfo.seasonId : 0) +
                                ", mediaId=" + (seasonInfo != null ? seasonInfo.mediaId : 0));

                        if (seasonInfo != null && seasonInfo.seasonId > 0) {
                            mBangumiMediaId = seasonInfo.seasonId;  // 存储 season_id
                            final tv.biliclassic.model.Bangumi bangumi = BangumiApi.getBangumi(seasonInfo.seasonId);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (bangumi != null && bangumi.info != null) {
                                        tvAvid.setText(bangumi.info.title);
                                        initBangumiView();
                                        initBottomButtons();
                                        return;
                                    }
                                    isBangumi = false;
                                    updateAvidDisplay();
                                    initNormalVideo();
                                    initBottomButtons();
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    isBangumi = false;
                                    updateAvidDisplay();
                                    initNormalVideo();
                                    initBottomButtons();
                                }
                            });
                        }
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isBangumi = false;
                                updateAvidDisplay();
                                initNormalVideo();
                                initBottomButtons();
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isBangumi = false;
                            updateAvidDisplay();
                            initNormalVideo();
                            initBottomButtons();
                        }
                    });
                }
            }
        }).start();
    }

    private void checkAndSetup() {
        final long finalAid = aid;
        final String finalBvid = bvid;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    VideoInfo info;
                    if (finalAid != 0) {
                        info = VideoInfoApi.getVideoInfo(finalAid);
                    } else {
                        info = VideoInfoApi.getVideoInfo(finalBvid);
                    }

                    if (info == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //Toast.makeText(VideoDetailActivity.this, "视频已失效，可查看评论区", Toast.LENGTH_LONG).show();
                                isBangumi = false;
                                updateAvidDisplay();
                                initNormalVideo();
                                initBottomButtons();
                            }
                        });
                        return;
                    }

                    final VideoInfo finalInfo = info;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (finalInfo.epid > 0) {
                                // 是番剧
                                isBangumi = true;
                                tvAvid.setText(finalInfo.title != null ? finalInfo.title : "番剧");
                                fetchBangumiInfoFromAid(finalAid);
                            } else {
                                // 普通视频更新标题
                                isBangumi = false;
                                updateAvidDisplay();
                                initNormalVideo();
                                initBottomButtons();
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(VideoDetailActivity.this, "视频信息加载失败", Toast.LENGTH_SHORT).show();
                            isBangumi = false;
                            updateAvidDisplay();
                            initNormalVideo();
                            initBottomButtons();
                        }
                    });
                }
            }
        }).start();
    }

    // 番剧视图（只有两个 Tab：番剧详情 + 评论）
    private void initBangumiView() {
        PagerTabStrip tabStrip = (PagerTabStrip) findViewById(R.id.pager_tab_strip);
        if (tabStrip != null) {
            tabStrip.setTabIndicatorColor(0xFFFCA3C5);
            tabStrip.setBackgroundColor(0xFFD86DA5);
            tabStrip.setTextColor(0xFFFFFFFF);
        }

        viewPager = (ViewPager) findViewById(R.id.viewpager);
        viewPager.setAdapter(new BangumiPagerAdapter(getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(1);

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

    // 普通视频视图（三个 Tab）
    private void initNormalVideo() {
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

    // 初始化底部四个shit按钮
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showInteractionMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initBottomButtons() {
        // 分享按钮
        ImageView btnShare = (ImageView) findViewById(R.id.btn_share);
        if (btnShare != null) {
            btnShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shareVideo();
                }
            });
        }

        // 收藏按钮
        btnFavorite = (ImageView) findViewById(R.id.btn_favorite);
        if (btnFavorite != null) {
            btnFavorite.setImageResource(R.drawable.ic_action_rating_important);
            if (mOfflineMode) {
                btnFavorite.setVisibility(View.GONE);
            } else {
                btnFavorite.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mIsFavoriteLoading || mIsFavoriteUpdating) {
                            return;
                        }
                        final long finalAid = getCorrectAid();
                        if (finalAid == 0L) {
                            Toast.makeText(VideoDetailActivity.this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        long mid = SharedPreferencesUtil.getLong("mid", 0);
                        String cookies = SharedPreferencesUtil.getString("cookies", "");
                        if (mid == 0 || cookies == null || cookies.length() == 0) {
                            Toast.makeText(VideoDetailActivity.this, "登录以后才能收藏喵～(*_*)", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showFavoriteDialog();
                    }
                });
                btnFavorite.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showInteractionMenu();
                        return true;
                    }
                });
                checkFavoriteState();
            }
        }

        // 评论按钮
        btnComment = (ImageView) findViewById(R.id.btn_comment);
        if (btnComment != null) {
            if (mOfflineMode) {
                btnComment.setVisibility(View.GONE);
            } else {
                btnComment.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        long mid = SharedPreferencesUtil.getLong("mid", 0);
                        String cookies = SharedPreferencesUtil.getString("cookies", "");
                        if (mid == 0 || cookies == null || cookies.length() == 0) {
                            Toast.makeText(VideoDetailActivity.this, "请先登录的说~", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showSendCommentDialog();
                    }
                });
            }
        }

        // 下载/删除按钮
        ImageView btnDownload = (ImageView) findViewById(R.id.btn_download);
        if (btnDownload != null) {
            if (mOfflineMode) {
                btnDownload.setImageResource(R.drawable.ic_action_delete);
                btnDownload.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mIsDeleteDialogShowing) return;
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (viewPager != null) {
            currentPagePosition = viewPager.getCurrentItem();
        }
        if (mIsPausingForTransient) {
            mIsPausingForTransient = false;
            return;
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
        mIsPausingForTransient = false;
        cleanupHandler.removeCallbacksAndMessages(null);
        isCleaned = false;
        if (viewPager != null && viewPager.getAdapter() == null) {
            if (isBangumi) {
                viewPager.setAdapter(new BangumiPagerAdapter(getSupportFragmentManager()));
            } else {
                if (mBangumiMediaId > 0) {
                    viewPager.setAdapter(new TwoTabPagerAdapter(getSupportFragmentManager()));
                } else {
                    viewPager.setAdapter(new VideoDetailPagerAdapter(getSupportFragmentManager()));
                }
            }
            viewPager.setCurrentItem(currentPagePosition, false);
        }
        if (!mOfflineMode && btnFavorite != null) {
            checkFavoriteState();
        }
        mIsDeleteDialogShowing = false;
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

    private void showSendCommentDialog() {
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), 0, dpToPx(16), 0);

        final EditText input = new EditText(this);
        input.setHint("输入评论内容...");
        input.setLines(3);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 14) {
            android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
            inputBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            inputBg.setStroke(dpToPx(2), 0xFFD0D0D0);
            inputBg.setColor(0xFFFFFFFF);
            input.setBackgroundDrawable(inputBg);
        }

        // 顶部按钮行：图片 + 表情
        final LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 0, 0, dpToPx(6));

        final TextView imageBtn = new TextView(this);
        imageBtn.setText("添加图片");
        imageBtn.setTextSize(13);
        imageBtn.setTextColor(0xFFD86DA5);
        imageBtn.setPadding(0, 0, dpToPx(12), 0);
        imageBtn.setBackgroundDrawable(getResources().getDrawable(R.drawable.item_click_effect));
        imageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pendingImageDataList.size() >= MAX_COMMENT_IMAGES) {
                    Toast.makeText(VideoDetailActivity.this, "最多上传" + MAX_COMMENT_IMAGES + "张图片", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickIntent.setType("image/*");
                startActivityForResult(pickIntent, REQUEST_PICK_COMMENT_IMAGE);
            }
        });
        btnRow.addView(imageBtn);
        dialogImageBtn = imageBtn;
        if (pendingImageDataList.size() > 0) {
            imageBtn.setText("图片(" + pendingImageDataList.size() + ")");
        }

        final TextView emojiBtn = new TextView(this);
        emojiBtn.setText("表情");
        emojiBtn.setTextSize(13);
        emojiBtn.setTextColor(0xFFD86DA5);
        emojiBtn.setBackgroundDrawable(getResources().getDrawable(R.drawable.item_click_effect));
        emojiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEmojiPicker(input);
            }
        });
        btnRow.addView(emojiBtn);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.addView(btnRow, lp);
        layout.addView(input, lp);

        final TextView clearText = new TextView(this);
        clearText.setText("清空");
        clearText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        clearText.setPadding(0, 8, 0, 0);
        clearText.setTextSize(14);
        clearText.setTextColor(0xFF666666);
        clearText.setBackgroundDrawable(getResources().getDrawable(R.drawable.item_click_effect));
        clearText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
                input.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
        layout.addView(clearText, lp);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("发送评论")
                .setView(layout)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        pendingImageDataList.clear();
                        d.dismiss();
                    }
                })
                .create();

        dialog.show();
        System.gc();
        android.view.Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text == null || text.length() == 0) {
                    Toast.makeText(VideoDetailActivity.this, "评论内容不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                if (pendingImageDataList.size() > 0) {
                    String picturesJson = buildPicturesJson();
                    pendingImageDataList.clear();
                    sendCommentWithPicture(text, picturesJson);
                } else {
                    sendComment(text);
                }
            }
        });
    }

    private void sendComment(final String text) {
        Toast.makeText(this, "正在发送评论...", Toast.LENGTH_SHORT).show();

        final long finalAid = getCorrectAid();

        ReplyHelper.sendReply(this, finalAid, 0, 0, text, new ReplyHelper.ReplyCallback() {
            @Override
            public void onSuccess(String responseJson) {
                Toast.makeText(VideoDetailActivity.this, "评论发送成功", Toast.LENGTH_SHORT).show();
                CommentFragment.CommentItem newItem = CommentFragment.parseCommentFromResponse(responseJson);
                Fragment fragment = getSupportFragmentManager().findFragmentByTag(
                        "android:switcher:" + R.id.viewpager + ":" + (isBangumi ? 1 : 2));
                if (fragment instanceof CommentFragment) {
                    ((CommentFragment) fragment).insertNewComment(newItem);
                } else {
                    refreshComments();
                }
            }

            @Override
            public void onFailed(String error) {
                // 错误已在 ReplyHelper 中 Toast 显示
            }
        });
    }

    private void sendCommentWithPicture(final String text, final String picturesJson) {
        Toast.makeText(this, "正在发送评论...", Toast.LENGTH_SHORT).show();
        final long finalAid = getCorrectAid();
        ReplyHelper.sendReplyWithPictures(this, finalAid, 0, 0, text, picturesJson,
                new ReplyHelper.ReplyCallback() {
            @Override
            public void onSuccess(String responseJson) {
                Toast.makeText(VideoDetailActivity.this, "评论发送成功", Toast.LENGTH_SHORT).show();
                CommentFragment.CommentItem newItem = CommentFragment.parseCommentFromResponse(responseJson);
                Fragment fragment = getSupportFragmentManager().findFragmentByTag(
                        "android:switcher:" + R.id.viewpager + ":" + (isBangumi ? 1 : 2));
                if (fragment instanceof CommentFragment) {
                    ((CommentFragment) fragment).insertNewComment(newItem);
                } else {
                    refreshComments();
                }
            }
            @Override
            public void onFailed(String error) {}
        });
    }

    private long getCorrectAid() {
        if (aid != 0L) {
            return aid;
        }
        if (videoDetailFragment != null && videoDetailFragment.videoInfo != null) {
            return videoDetailFragment.videoInfo.aid;
        }
        if (bvid != null && bvid.length() > 0) {
            try {
                return FavoriteApi.getAidByBvid(bvid);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0L;
    }

    private static final String[] EMOJIS = {
        "( ゜- ゜)つロ", "_(:з」∠)_", "（⌒▽⌒）", "（￣▽￣）", "⌓‿⌓",
        "(=・ω・=)", "(*°▽°*)", "八(*°▽°*)♪", "✿ヽ(°▽°)ノ✿", "(¦3【▓▓】",
        "눈_눈", "(ಡωಡ)", "_(≧∇≦」∠)_", "━━━∑(ﾟ□ﾟ*川", "━(｀・ω・´)",
        "(￣3￣)", "✧(≖ ◡ ≖✿)", "(･∀･)", "(〜￣△￣)〜", "→_→",
        "(°∀°)ﾉ", "╮(￣▽￣)╭", "( ´_ゝ｀)", "←_←", "(;¬_¬)",
        "(ﾟДﾟ≡ﾟдﾟ)!?", "( ´･･)ﾉ", "(._.`)", "Σ(ﾟдﾟ;)", "Σ( ￣□￣||)",
        "<(´；ω；`)", "（/TДT)/", "(^・ω・^)", "(｡･ω･｡)", "(●￣(ｴ)￣●)",
        "ε=ε=(ノ≧∇≦)ノ", "(´･_･`)", "(-_-#)", "（￣へ￣）", "(￣ε(#￣)",
        "Σ(╯°口°)╯(┴—┴", "ヽ(`Д´)ﾉ", "(\"▔□▔)/", "(º﹃º )", "(๑>؂<๑）",
        "｡ﾟ(ﾟ´Д｀)ﾟ｡", "(∂ω∂)", "(┯_┯)", "(・ω< )★", "( ๑ˊ•̥▵•)੭₎₎",
        "¥ㄟ(´･ᴗ･`)ノ¥", "Σ_(꒪ཀ꒪」∠)_", "٩(๛ ˘ ³˘)۶❤", "(๑‾᷅^‾᷅๑)"
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQUEST_PICK_COMMENT_IMAGE && resultCode == RESULT_OK && data != null) {
            final android.net.Uri imageUri = data.getData();
            if (imageUri != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            java.io.InputStream is = getContentResolver().openInputStream(imageUri);
                            if (is == null) return;
                            byte[] imageBytes = NetWorkUtil.readStream(is);
                            is.close();

                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inJustDecodeBounds = true;
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);

                            int scale = 1;
                            if (opts.outWidth > 1920 || opts.outHeight > 1080) {
                                scale = Math.max(opts.outWidth / 1920, opts.outHeight / 1080);
                            }

                            opts = new BitmapFactory.Options();
                            opts.inSampleSize = scale;
                            opts.inPreferredConfig = Bitmap.Config.RGB_565;
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);

                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                            byte[] compressed = baos.toByteArray();
                            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();

                            final long finalAid = getCorrectAid();
                            final String fileName = "comment_" + System.currentTimeMillis() + ".jpg";
                            final String resultJson = ReplyApi.uploadReplyImage(finalAid, compressed, fileName);

                            if (resultJson != null && pendingImageDataList.size() < MAX_COMMENT_IMAGES) {
                                pendingImageDataList.add(resultJson);
                                final int imgCount = pendingImageDataList.size();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (dialogImageBtn != null) {
                                            dialogImageBtn.setText("图片(" + imgCount + ")");
                                        }
                                        Toast.makeText(VideoDetailActivity.this, "图片已上传 (" + imgCount + ")", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(VideoDetailActivity.this, "图片上传失败", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(VideoDetailActivity.this, "图片上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showEmojiPicker(final EditText input) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择表情");

        final android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dpToPx(8), 0, dpToPx(8));

        for (int i = 0; i < EMOJIS.length; i++) {
            final String emoji = EMOJIS[i];
            final TextView tv = new TextView(this);
            tv.setText(emoji);
            tv.setTextSize(16);
            tv.setTextColor(0xFF333333);
            tv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
            tv.setClickable(true);
            android.graphics.drawable.GradientDrawable emojiNormal = new android.graphics.drawable.GradientDrawable();
            emojiNormal.setColor(0xFFF0F0F0);
            android.graphics.drawable.GradientDrawable emojiPressed = new android.graphics.drawable.GradientDrawable();
            emojiPressed.setColor(0x40D86DA5);
            android.graphics.drawable.StateListDrawable emojiBg = new android.graphics.drawable.StateListDrawable();
            emojiBg.addState(new int[]{android.R.attr.state_pressed}, emojiPressed);
            emojiBg.addState(new int[]{}, emojiNormal);
            tv.setBackgroundDrawable(emojiBg);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int remaining = 1000 - input.length();
                    if (remaining <= 0) return;
                    String toInsert = emoji.length() <= remaining ? emoji : emoji.substring(0, remaining);
                    int pos = input.getSelectionStart();
                    if (pos < 0) pos = input.length();
                    input.getText().insert(pos, toInsert);
                }
            });
            list.addView(tv);

            if (i < EMOJIS.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFDDDDDD);
                list.addView(divider);
            }
        }

        scroll.addView(list, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        builder.setView(scroll);
        builder.setPositiveButton("关闭", null);
        builder.show();
    }

    private String buildPicturesJson() {
        try {
            JSONArray pics = new JSONArray();
            for (int i = 0; i < pendingImageDataList.size(); i++) {
                JSONObject imgData = new JSONObject(pendingImageDataList.get(i));
                JSONObject pic = new JSONObject();
                pic.put("img_src", imgData.optString("image_url", ""));
                pic.put("img_width", imgData.optInt("image_width", 0));
                pic.put("img_height", imgData.optInt("image_height", 0));
                pic.put("img_size", imgData.optDouble("img_size", 0));
                pics.put(pic);
            }
            return pics.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private String extractCsrfFromCookie(String cookie) {
        if (cookie == null || cookie.length() == 0) {
            return null;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("bili_jct=([a-f0-9]+)");
        java.util.regex.Matcher m = p.matcher(cookie);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private void refreshComments() {
        if (viewPager != null) {
            int commentPosition = isBangumi ? 1 : 2;
            viewPager.setCurrentItem(commentPosition);
        }
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(
                "android:switcher:" + R.id.viewpager + ":" + (isBangumi ? 1 : 2));
        if (fragment instanceof CommentFragment) {
            ((CommentFragment) fragment).refreshComments();
        }
    }

    private void checkFavoriteState() {
        final long finalAid = getCorrectAid();
        if (finalAid == 0L) return;
        long mid = SharedPreferencesUtil.getLong("mid", 0);
        if (mid == 0) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ArrayList folderList = new ArrayList();
                    ArrayList fidList = new ArrayList();
                    ArrayList stateList = new ArrayList();

                    FavoriteApi.getFavoriteState(finalAid, folderList, fidList, stateList);

                    boolean favorited = false;
                    for (int i = 0; i < stateList.size(); i++) {
                        Boolean state = (Boolean) stateList.get(i);
                        if (state != null && state.booleanValue()) {
                            favorited = true;
                            break;
                        }
                    }

                    final boolean finalFavorited = favorited;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsFavorited = finalFavorited;
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void showInteractionMenu() {
        final long finalAid = getCorrectAid();
        if (finalAid == 0L) {
            Toast.makeText(VideoDetailActivity.this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
            return;
        }
        long mid = SharedPreferencesUtil.getLong("mid", 0);
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (mid == 0 || cookies == null || cookies.length() == 0) {
            Toast.makeText(VideoDetailActivity.this, "登录以后才能操作喵～(*_*)", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = {"点赞", "投币", "收藏", "三连"};
        new AlertDialog.Builder(VideoDetailActivity.this)
                .setTitle("互动操作")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        final int code = InteractionApi.like(finalAid, 1);
                                        VideoDetailActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (code == 0) {
                                                    Toast.makeText(VideoDetailActivity.this, "点赞成功", Toast.LENGTH_SHORT).show();
                                                } else if (code == 65006) {
                                                    Toast.makeText(VideoDetailActivity.this, "重复点赞", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(VideoDetailActivity.this, "点赞失败", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        });
                                    } catch (final Exception e) {
                                        VideoDetailActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(VideoDetailActivity.this, "点赞失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                }
                            }).start();
                        } else if (which == 1) {
                            final String[] coinItems = {"1枚硬币", "2枚硬币"};
                            new AlertDialog.Builder(VideoDetailActivity.this)
                                    .setTitle("投币")
                                    .setItems(coinItems, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface coinDialog, int coinWhich) {
                                            final int multiply = coinWhich == 1 ? 2 : 1;
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        final int code = InteractionApi.coin(finalAid, multiply);
                                                        VideoDetailActivity.this.runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                if (code == 0) {
                                                                    Toast.makeText(VideoDetailActivity.this, "投币" + multiply + "枚成功", Toast.LENGTH_SHORT).show();
                                                                } else if (code == -401) {
                                                                    Toast.makeText(VideoDetailActivity.this, "需要验证码，请到网页端投币", Toast.LENGTH_SHORT).show();
                                                                } else {
                                                                    Toast.makeText(VideoDetailActivity.this, "投币失败", Toast.LENGTH_SHORT).show();
                                                                }
                                                            }
                                                        });
                                                    } catch (final Exception e) {
                                                        VideoDetailActivity.this.runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                Toast.makeText(VideoDetailActivity.this, "投币失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                            }
                                                        });
                                                    }
                                                }
                                            }).start();
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        } else if (which == 2) {
                            showFavoriteDialog();
                        } else if (which == 3) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        final int code = InteractionApi.triple(finalAid);
                                        VideoDetailActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (code == 0) {
                                                    Toast.makeText(VideoDetailActivity.this, "三连成功", Toast.LENGTH_SHORT).show();
                                                } else if (code == -401) {
                                                    Toast.makeText(VideoDetailActivity.this, "需要验证码，请到网页端三连", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(VideoDetailActivity.this, "三连失败", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        });
                                    } catch (final Exception e) {
                                        VideoDetailActivity.this.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(VideoDetailActivity.this, "三连失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                }
                            }).start();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showFavoriteDialog() {
        if (mIsFavoriteUpdating) {
            return;
        }

        final long finalAid = getCorrectAid();
        if (finalAid == 0L) {
            if (!isFinishing()) {
                Toast.makeText(this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final long mid = SharedPreferencesUtil.getLong("mid", 0);
        if (mid == 0) {
            if (!isFinishing()) {
                Toast.makeText(this, "请先登录的说~", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        mIsFavoriteLoading = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ArrayList folders = FavoriteApi.getFavoriteFoldersFast(mid);

                    final ArrayList stateNames = new ArrayList();
                    final ArrayList stateFids = new ArrayList();
                    final ArrayList stateList = new ArrayList();
                    FavoriteApi.getFavoriteState(finalAid, stateNames, stateFids, stateList);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            mIsFavoriteLoading = false;
                            if (folders == null || folders.size() == 0) {
                                Toast.makeText(VideoDetailActivity.this, "暂无收藏夹，请先在网页端创建", Toast.LENGTH_LONG).show();
                                return;
                            }

                            final String[] folderNames = new String[folders.size()];
                            final long[] folderIds = new long[folders.size()];
                            final boolean[] favStates = new boolean[folders.size()];
                            for (int i = 0; i < folders.size(); i++) {
                                FavoriteFolder folder = (FavoriteFolder) folders.get(i);
                                folderNames[i] = folder.name + " (" + folder.videoCount + "个视频)";
                                folderIds[i] = folder.fid;
                                favStates[i] = false;
                                for (int j = 0; j < stateFids.size(); j++) {
                                    if (((Long) stateFids.get(j)).longValue() == folder.fid) {
                                        favStates[i] = ((Boolean) stateList.get(j)).booleanValue();
                                        break;
                                    }
                                }
                            }

                            ArrayAdapter adapter = new ArrayAdapter<String>(
                                    VideoDetailActivity.this,
                                    android.R.layout.simple_list_item_1,
                                    folderNames) {
                                @Override
                                public View getView(int position, View convertView, ViewGroup parent) {
                                    View view = super.getView(position, convertView, parent);
                                    if (view instanceof TextView) {
                                        TextView tv = (TextView) view;
                                        if (favStates[position]) {
                                            tv.setTextColor(0xFFD86DA5);
                                        } else {
                                            tv.setTextColor(0xFF000000);
                                        }
                                    }
                                    return view;
                                }
                            };

                            new AlertDialog.Builder(VideoDetailActivity.this)
                                    .setTitle("选择收藏夹")
                                    .setAdapter(adapter, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            final long fid = folderIds[which];
                                            if (favStates[which]) {
                                                new AlertDialog.Builder(VideoDetailActivity.this)
                                                        .setTitle("删除收藏")
                                                        .setMessage("是否从该收藏夹中删除？")
                                                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface delDialog, int delWhich) {
                                                                removeFromFolder(finalAid, fid);
                                                            }
                                                        })
                                                        .setNegativeButton("取消", null)
                                                        .show();
                                            } else {
                                                addToFavorite(finalAid, fid);
                                            }
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            mIsFavoriteLoading = false;
                            Toast.makeText(VideoDetailActivity.this, "加载收藏夹失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void addToFavorite(final long finalAid, final long fid) {
        if (finalAid == 0L || mIsFavoriteUpdating) return;

        mIsFavoriteUpdating = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = FavoriteApi.addFavorite(finalAid, bvid, fid);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            mIsFavoriteUpdating = false;
                            if (code == 0) {
                                mIsFavorited = true;
                                Toast.makeText(VideoDetailActivity.this, "收藏好了喵～(=w=)", Toast.LENGTH_SHORT).show();
                                sendBroadcast(new Intent(BroadcastConstants.ACTION_FAVORITE_CHANGED));
                            } else if (code == 11201) {
                                mIsFavorited = true;
                                Toast.makeText(VideoDetailActivity.this, "已收藏过该视频", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(VideoDetailActivity.this, "收藏失败喵: " + code, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            mIsFavoriteUpdating = false;
                            Toast.makeText(VideoDetailActivity.this, "收藏失败喵: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void removeFromFolder(final long targetAid, final long targetFid) {
        if (targetAid == 0L || mIsFavoriteUpdating) return;

        mIsFavoriteUpdating = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int code = FavoriteApi.deleteFavorite(targetAid, bvid, targetFid);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            mIsFavoriteUpdating = false;
                            if (code == 0) {
                                mIsFavorited = false;
                                Toast.makeText(VideoDetailActivity.this, "已从收藏夹删除", Toast.LENGTH_SHORT).show();
                                sendBroadcast(new Intent(BroadcastConstants.ACTION_FAVORITE_CHANGED));
                            } else {
                                Toast.makeText(VideoDetailActivity.this, "删除收藏失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            mIsFavoriteUpdating = false;
                            Toast.makeText(VideoDetailActivity.this, "删除收藏失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showDeleteConfirmDialog() {
        mIsDeleteDialogShowing = true;
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
                        mIsDeleteDialogShowing = false;
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIsDeleteDialogShowing = false;
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mIsDeleteDialogShowing = false;
                    }
                })
                .show();
    }

    private void deleteOfflineVideo() {
        final long finalAid = getCorrectAid();
        if (finalAid == 0L) {
            Toast.makeText(this, "无法获取视频ID", Toast.LENGTH_SHORT).show();
            return;
        }

        File downloadDir = getDownloadDir();
        File avidDir = new File(downloadDir, String.valueOf(finalAid));

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

    private void showDownloadChoiceDialog() {
        if (isBangumi) {
            Toast.makeText(this, "番剧页面暂不支持离线下载，敬请谅解~", Toast.LENGTH_SHORT).show();
            return;
        }

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
        } else if (defaultQuality == 80) {
            qualityGroup.check(R.id.quality_1080);
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
                } else if (checkedId == R.id.quality_720) {
                    mSelectedQuality = 64;
                    mSelectedQualityName = "720P 高清";
                } else {
                    mSelectedQuality = 80;
                    mSelectedQualityName = "1080P 超清";
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

    // 下载权限待参数（权限请求成功后重试用）
    private String mPendingVideoUrl;
    private String mPendingTitle;
    private String mPendingPageTitle;
    private long mPendingAid;
    private long mPendingCid;
    private int mPendingPage;
    private int mPendingQuality;
    private String mPendingQualityName;
    private String mPendingCoverUrl;
    private String mPendingUpName;
    private String mPendingBvid;
    private String mPendingDescription;
    private String mPendingTags;

    public void startDownloadDirect(String videoUrl, String title, String pageTitle,
                                    long aid, long cid, int page,
                                    int quality, String qualityName,
                                    String coverUrl, String upName, String bvid,
                                    String description, String tags) {
        if (!PermissionUtil.hasWriteStorage(this)) {
            mPendingVideoUrl = videoUrl;
            mPendingTitle = title;
            mPendingPageTitle = pageTitle;
            mPendingAid = aid;
            mPendingCid = cid;
            mPendingPage = page;
            mPendingQuality = quality;
            mPendingQualityName = qualityName;
            mPendingCoverUrl = coverUrl;
            mPendingUpName = upName;
            mPendingBvid = bvid;
            mPendingDescription = description;
            mPendingTags = tags;
            setPausingForTransient(true);
            runWithStoragePermission(new Runnable() {
                @Override
                public void run() {
                    startDownloadDirect(mPendingVideoUrl, mPendingTitle, mPendingPageTitle,
                            mPendingAid, mPendingCid, mPendingPage,
                            mPendingQuality, mPendingQualityName,
                            mPendingCoverUrl, mPendingUpName, mPendingBvid,
                            mPendingDescription, mPendingTags);
                }
            });
            return;
        }
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
        if (isSDCardAvailable() && PermissionUtil.hasWriteStorage(this)) {
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

    // 两个 Tab 的适配器（视频详情 + 评论）
    private class TwoTabPagerAdapter extends FragmentPagerAdapter {
        private String[] titles = {"视频详情", "评论"};

        public TwoTabPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                VideoDetailFragment fragment = new VideoDetailFragment();
                Bundle args = new Bundle();
                args.putLong("aid", aid);
                if (bvid != null) {
                    args.putString("bvid", bvid);
                }
                fragment.setArguments(args);
                return fragment;
            } else {
                CommentFragment fragment = new CommentFragment();
                Bundle args = new Bundle();
                args.putLong("aid", aid);
                if (bvid != null) {
                    args.putString("bvid", bvid);
                }
                fragment.setArguments(args);
                return fragment;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }

    // 普通视频 ViewPager 适配器（三个 Tab）
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

    // 番剧 ViewPager 适配器（只有两个 Tab）
    private class BangumiPagerAdapter extends FragmentPagerAdapter {
        private String[] titles = {"番剧", "评论"};

        public BangumiPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                return BangumiDetailFragment.newInstance(mBangumiMediaId);
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
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }
}