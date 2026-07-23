package tv.biliclassic;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.ClipboardManager;
import android.text.SpannableString;
import android.text.style.ReplacementSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import tv.biliclassic.api.ReplyApi;
import tv.biliclassic.model.Reply;
import java.util.HashSet;
import java.util.Set;

import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.ReplyHelper;
import tv.biliclassic.util.DialogUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

public class ReplyListActivity extends BaseActivity {

    private static final String TAG = "ReplyListActivity";
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Set<String> loadingUrls = new HashSet<String>();
    private ListView lv;
    private TextView tvTitle;
    private TextView tvEmpty;
    private View rootCommentView;
    private LinearLayout mRootPictureContainer;

    private long aid;
    private String bvid;
    private long rpid;
    private String rootUserName;
    private String rootCommentMessage;
    private long rootMid;
    private long rootTime;
    private String rootAvatarUrl;
    private List<String> rootPictureList;
    private int totalReplyCount;
    private boolean rootLiked;
    private boolean rootIsTop;
    private boolean rootPictureExpanded = false;
    private int mRootLikeCount;

    private List<ReplyData> allReplies = new ArrayList<ReplyData>();
    private ReplyListAdapter adapter;

    // 分页参数
    private String pagination = "";
    private boolean isLoading = false;
    private boolean isEnd = false;
    private View footerView;
    private ProgressBar footerProgress;
    private TextView footerText;

    private String pendingImageDataJson = null;
    private static final int REQUEST_PICK_COMMENT_IMAGE = 2001;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reply_list);

        tvTitle = (TextView) findViewById(R.id.tv_reply_list_title);
        lv = (ListView) findViewById(R.id.lv_replies);
        tvEmpty = (TextView) findViewById(R.id.tv_empty);
        rootCommentView = findViewById(R.id.root_comment);

        ImageView btnBack = (ImageView) findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        aid = getIntent().getLongExtra("aid", 0);
        bvid = getIntent().getStringExtra("bvid");
        rpid = getIntent().getLongExtra("rpid", 0);
        rootUserName = getIntent().getStringExtra("root_user_name");
        rootCommentMessage = getIntent().getStringExtra("root_comment_message");
        rootMid = getIntent().getLongExtra("root_mid", 0);
        rootTime = getIntent().getLongExtra("root_time", 0);
        rootAvatarUrl = getIntent().getStringExtra("root_avatar");
        rootPictureList = (List<String>) getIntent().getSerializableExtra("root_pictures");
        rootIsTop = getIntent().getBooleanExtra("root_is_top", false);
        totalReplyCount = getIntent().getIntExtra("total_count", 0);
        Log.e("ReplyList", "onCreate aid=" + aid + " bvid=" + bvid + " rpid=" + rpid + " msg=" + (rootCommentMessage != null ? rootCommentMessage.substring(0, Math.min(20, rootCommentMessage.length())) : "null"));

        if (aid == 0 && (bvid == null || bvid.length() == 0)) {
            Toast.makeText(this, "视频参数无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (aid == 0 && bvid != null && bvid.length() > 0) {
            tvTitle.setText("正在获取信息...");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        long fetchedAid = tv.biliclassic.api.FavoriteApi.getAidByBvid(bvid);
                        if (fetchedAid != 0) {
                            aid = fetchedAid;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvTitle.setText("全部回复");
                                    initViews();
                                    loadReplies();
                                }
                            });
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ReplyListActivity.this, "无法获取视频信息", Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            });
                        }
                    } catch (final Exception e) {
                        final String errorMsg = e.getMessage();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ReplyListActivity.this, "获取视频信息失败: " + errorMsg, Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }
                }
            }).start();
            return;
        }

        tvTitle.setText("全部回复");
        initViews();
        loadReplies();
    }

    private void initViews() {
        // 根评论 - 复用 item_comment
        if (rootUserName != null && rootUserName.length() > 0 &&
                rootCommentMessage != null && rootCommentMessage.length() > 0) {
            rootCommentView.setVisibility(View.VISIBLE);

            ImageView rootAvatar = (ImageView) rootCommentView.findViewById(R.id.avatar);
            TextView rootUserNameView = (TextView) rootCommentView.findViewById(R.id.user_name);
            final TextView rootMsgView = (TextView) rootCommentView.findViewById(R.id.message);
            TextView rootTimeView = (TextView) rootCommentView.findViewById(R.id.time);
            mRootPictureContainer = (LinearLayout) rootCommentView.findViewById(R.id.picture_container);
            final TextView rootLikeCount = (TextView) rootCommentView.findViewById(R.id.like_count);
            TextView rootReplyBtn = (TextView) rootCommentView.findViewById(R.id.reply_button);
            final ImageView rootLikeIcon = (ImageView) rootCommentView.findViewById(R.id.like_icon);
            final TextView rootExpandBtn = (TextView) rootCommentView.findViewById(R.id.btn_expand);

            rootUserNameView.setText(rootUserName);
            String rootMsg = rootCommentMessage;
            if (rootIsTop) {
                if (rootMsg != null && rootMsg.startsWith("[置顶]")) {
                    rootMsg = rootMsg.substring(4);
                }
                SpannableString ss = new SpannableString("\u200B" + rootMsg);
                ss.setSpan(new BadgeSpan(getResources().getDisplayMetrics().density), 0, 1,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                rootMsgView.setText(ss);
            } else {
                rootMsgView.setText(rootMsg);
            }

            if (rootTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                rootTimeView.setText(sdf.format(new Date(rootTime * 1000)));
            } else {
                rootTimeView.setText("刚刚");
            }

            // 根评论展开/收起（隐藏再显示，不闪烁）
            if (rootExpandBtn != null) {
                rootExpandBtn.setVisibility(View.GONE);
                rootMsgView.setMaxLines(3);
                rootMsgView.setEllipsize(android.text.TextUtils.TruncateAt.END);

                // 先隐藏内容，测完再显示
                rootMsgView.setVisibility(View.INVISIBLE);
                rootMsgView.setMaxLines(Integer.MAX_VALUE);
                rootMsgView.setEllipsize(null);
                rootMsgView.requestLayout();

                rootMsgView.post(new Runnable() {
                    @Override
                    public void run() {
                        int fullLineCount = rootMsgView.getLineCount();

                        // 恢复限制
                        rootMsgView.setMaxLines(3);
                        rootMsgView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        rootMsgView.setVisibility(View.VISIBLE);

                        if (fullLineCount > 3) {
                            rootExpandBtn.setVisibility(View.VISIBLE);
                        } else {
                            rootExpandBtn.setVisibility(View.GONE);
                        }
                    }
                });

                rootExpandBtn.setOnClickListener(new View.OnClickListener() {
                    private boolean isExpanded = false;

                    @Override
                    public void onClick(View v) {
                        isExpanded = !isExpanded;
                        if (isExpanded) {
                            rootMsgView.setMaxLines(Integer.MAX_VALUE);
                            rootMsgView.setEllipsize(null);
                            rootExpandBtn.setText("收起");
                        } else {
                            rootMsgView.setMaxLines(3);
                            rootMsgView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            rootExpandBtn.setText("展开");
                        }
                    }
                });
            }

            // 点赞数
            mRootLikeCount = getIntent().getIntExtra("root_like_count", 0);
            rootLiked = getIntent().getBooleanExtra("root_liked", false);
            rootLikeCount.setText(String.valueOf(mRootLikeCount));
            if (rootLiked) {
                rootLikeCount.setTextColor(0xFFD86DA5);
                rootLikeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
            } else {
                rootLikeCount.setTextColor(0xFF999999);
                rootLikeIcon.setColorFilter((android.graphics.ColorFilter) null);
            }

            // 根评论长按复制/删除
            final long finalMid = SharedPreferencesUtil.getLong("mid", 0);
            rootCommentView.setOnTouchListener(new View.OnTouchListener() {
                private boolean mIsLongPress;
                private Runnable mLongPressRunnable;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            mIsLongPress = false;
                            v.setBackgroundColor(0x40D86DA5);
                            mLongPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    mIsLongPress = true;
                                    final String text = rootCommentMessage;
                                    if (text != null && text.length() > 0) {
                                        if (rootMid == finalMid && finalMid != 0) {
                                            AlertDialog.Builder builder = new AlertDialog.Builder(DialogUtil.wrap(ReplyListActivity.this));
                                            builder.setItems(new String[]{"复制评论", "删除评论"}, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    if (which == 0) {
                                                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                        clipboard.setText(text);
                                                        try {
                                                            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                                                            if (vibrator != null) vibrator.vibrate(50);
                                                        } catch (Exception e) { e.printStackTrace(); }
                                                        Toast.makeText(ReplyListActivity.this, "已复制评论", Toast.LENGTH_SHORT).show();
                                                    } else if (which == 1) {
                                                        deleteRootComment();
                                                    }
                                                }
                                            });
                                            builder.show();
                                        } else {
                                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                            clipboard.setText(text);
                                            try {
                                                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                                                if (vibrator != null) vibrator.vibrate(50);
                                            } catch (Exception e) { e.printStackTrace(); }
                                            Toast.makeText(ReplyListActivity.this, "已复制评论", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }
                            };
                            mainHandler.postDelayed(mLongPressRunnable, ViewConfiguration.getLongPressTimeout());
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (mLongPressRunnable != null) {
                                mainHandler.removeCallbacks(mLongPressRunnable);
                            }
                            v.setBackgroundResource(R.drawable.item_click_effect_white);
                            break;
                    }
                    return false;
                }
            });

            // 加载头像
            loadAvatar(rootAvatar, rootAvatarUrl);

            // 图片
            updateRootPictures();

            // 点赞点击
            final long mid = SharedPreferencesUtil.getLong("mid", 0);
            rootLikeIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final boolean wasLiked = rootLiked;
                    rootLiked = !wasLiked;
                    if (rootLiked) {
                        mRootLikeCount++;
                        rootLikeCount.setTextColor(0xFFD86DA5);
                        rootLikeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                    } else {
                        mRootLikeCount--;
                        rootLikeCount.setTextColor(0xFF999999);
                        rootLikeIcon.setColorFilter((android.graphics.ColorFilter) null);
                    }
                    rootLikeCount.setText(String.valueOf(mRootLikeCount));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int code;
                                if (rootLiked) {
                                    code = ReplyApi.likeComment(aid, rpid, 1);
                                } else {
                                    code = ReplyApi.unlikeComment(aid, rpid, 1);
                                }
                                if (code != 0) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            rootLiked = wasLiked;
                                            if (wasLiked) {
                                                mRootLikeCount++;
                                                rootLikeCount.setTextColor(0xFFD86DA5);
                                                rootLikeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                                            } else {
                                                mRootLikeCount--;
                                                rootLikeCount.setTextColor(0xFF999999);
                                                rootLikeIcon.setColorFilter((android.graphics.ColorFilter) null);
                                            }
                                            rootLikeCount.setText(String.valueOf(mRootLikeCount));
                                            Toast.makeText(ReplyListActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                } else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            CommentFragment.notifyRootLikeChanged(rpid, rootLiked, mRootLikeCount);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        rootLiked = wasLiked;
                                        if (wasLiked) {
                                            mRootLikeCount++;
                                            rootLikeCount.setTextColor(0xFFD86DA5);
                                            rootLikeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                                        } else {
                                            mRootLikeCount--;
                                            rootLikeCount.setTextColor(0xFF999999);
                                            rootLikeIcon.setColorFilter((android.graphics.ColorFilter) null);
                                        }
                                        rootLikeCount.setText(String.valueOf(mRootLikeCount));
                                        Toast.makeText(ReplyListActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }).start();
                }
            });

            // 回复点击
            rootReplyBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ReplyData rootReply = new ReplyData();
                    rootReply.rpid = rpid;
                    rootReply.userName = rootUserName;
                    showReplyDialog(rootReply);
                }
            });

            // 点击头像跳转
            final long finalRootMid = rootMid;
            if (finalRootMid != 0) {
                View.OnClickListener clickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(ReplyListActivity.this, UserProfileActivity.class);
                        intent.putExtra("mid", finalRootMid);
                        startActivity(intent);
                    }
                };
                rootAvatar.setOnClickListener(clickListener);
                rootUserNameView.setOnClickListener(clickListener);
            }

        } else {
            rootCommentView.setVisibility(View.GONE);
        }

        // ====== Footer ======
        footerView = getLayoutInflater().inflate(R.layout.list_footer, null);
        footerProgress = (ProgressBar) footerView.findViewById(R.id.footer_progress);
        footerText = (TextView) footerView.findViewById(R.id.footer_text);
        if (footerProgress != null) {
            footerProgress.setVisibility(View.GONE);
        }
        lv.addFooterView(footerView);
        footerView.setVisibility(View.GONE);

        adapter = new ReplyListAdapter(this, allReplies);
        adapter.setOid(aid);
        adapter.setReplyType(1);
        adapter.setMid(SharedPreferencesUtil.getLong("mid", 0));
        adapter.setOnReplyClickListener(new ReplyListAdapter.OnReplyClickListener() {
            @Override
            public void onReplyClick(ReplyData reply) {
                showReplyDialog(reply);
            }
        });
        lv.setAdapter(adapter);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= allReplies.size()) return;
                ReplyData rd = allReplies.get(position);
                if (rd.mid != 0) {
                    Intent intent = new Intent(ReplyListActivity.this, UserProfileActivity.class);
                    intent.putExtra("mid", rd.mid);
                    startActivity(intent);
                }
            }
        });

        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= allReplies.size()) return false;
                ReplyData rd = allReplies.get(position);
                showReplyDialog(rd);
                return true;
            }
        });

        // 滚动监听
        lv.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_IDLE) {
                    int lastVisible = view.getLastVisiblePosition();
                    int dataCount = adapter.getCount();
                    if (lastVisible >= dataCount - 1 && !isLoading && !isEnd && dataCount > 0) {
                        loadReplies();
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isLoading && !isEnd && totalItemCount > 0) {
                    int lastVisible = firstVisibleItem + visibleItemCount;
                    int dataCount = adapter.getCount();
                    if (lastVisible >= dataCount - 2) {
                        loadReplies();
                    }
                }
            }
        });

        lv.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
    }

    private void updateRootPictures() {
        if (rootPictureList == null || rootPictureList.size() == 0) {
            if (mRootPictureContainer != null) mRootPictureContainer.setVisibility(View.GONE);
            return;
        }
        mRootPictureContainer.removeAllViews();
        mRootPictureContainer.setVisibility(View.VISIBLE);

        int showCount = rootPictureExpanded ? Math.min(rootPictureList.size(), 9) : Math.min(rootPictureList.size(), 3);
        int imgSize = dpToPx(80);
        int margin = dpToPx(4);

        mRootPictureContainer.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < showCount; i++) {
            if (i % 3 == 0) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                mRootPictureContainer.addView(row);
            }
            LinearLayout row = (LinearLayout) mRootPictureContainer.getChildAt(
                    mRootPictureContainer.getChildCount() - 1);

            final String imgUrl = rootPictureList.get(i);
            final int clickIndex = i;
            ImageView imgView = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(imgSize, imgSize);
            lp.rightMargin = margin;
            lp.bottomMargin = (i / 3 < (showCount - 1) / 3) ? margin : 0;
            imgView.setLayoutParams(lp);
            imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imgView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
            row.addView(imgView);
            loadImage(imgView, imgUrl);
            imgView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ReplyListActivity.this, ImageViewerActivity.class);
                    intent.putStringArrayListExtra("imageList", new ArrayList<String>(rootPictureList));
                    intent.putExtra("index", clickIndex);
                    startActivity(intent);
                }
            });
        }

        if (rootPictureList.size() > 3) {
            View toggleView;
            if (!rootPictureExpanded) {
                TextView moreTv = new TextView(this);
                moreTv.setText("+" + (rootPictureList.size() - 3));
                moreTv.setTextSize(14);
                moreTv.setTextColor(0xFFFFFFFF);
                moreTv.setGravity(Gravity.CENTER);
                moreTv.setBackgroundColor(0x88000000);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(imgSize, imgSize);
                lp.leftMargin = margin;
                moreTv.setLayoutParams(lp);
                toggleView = moreTv;
            } else {
                TextView collapseTv = new TextView(this);
                collapseTv.setText("收起");
                collapseTv.setTextSize(12);
                collapseTv.setTextColor(0xFFD86DA5);
                collapseTv.setGravity(Gravity.CENTER);
                collapseTv.setPadding(0, dpToPx(4), 0, 0);
                toggleView = collapseTv;
            }
            final View fToggle = toggleView;
            fToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rootPictureExpanded = !rootPictureExpanded;
                    updateRootPictures();
                }
            });
            mRootPictureContainer.addView(fToggle);
        }
    }

    private void loadReplies() {
        if (isLoading) return;
        isLoading = true;

        final String cookies = SharedPreferencesUtil.getString("cookies", "");
        if (cookies == null || cookies.length() == 0) {
            Toast.makeText(this, "请先登录的说~", Toast.LENGTH_SHORT).show();
            isLoading = false;
            return;
        }

        tvEmpty.setVisibility(View.GONE);
        footerView.setVisibility(View.VISIBLE);
        if (footerProgress != null) {
            footerProgress.setVisibility(View.VISIBLE);
        }
        if (footerText != null) {
            footerText.setText("嘿咻…嘿咻…");
            footerText.setVisibility(View.VISIBLE);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Reply> replyList = new ArrayList<Reply>();
                    ReplyApi.ReplyListResult result = ReplyApi.getRepliesLazy(
                            aid, rpid, pagination, ReplyApi.REPLY_TYPE_VIDEO, 2, replyList);

                    int code = result.code;
                    final String nextPagination = result.nextPagination;

                    Log.d(TAG, "loadReplies: code=" + code + ", nextPagination=" + nextPagination);

                    if (code == -1) {
                        showError("请求失败");
                        return;
                    }

                    final List<ReplyData> newReplies = new ArrayList<ReplyData>();
                    for (Reply reply : replyList) {
                        ReplyData rd = new ReplyData();
                        rd.rpid = reply.rpid;
                        rd.root = reply.root;
                        rd.parent = reply.parent;
                        rd.time = reply.ctime;
                        rd.likeCount = reply.likeCount;
                        rd.liked = reply.liked;
                        if (reply.sender != null) {
                            rd.userName = reply.sender.name != null ? reply.sender.name : "用户";
                            rd.mid = reply.sender.mid;
                            rd.avatar = reply.sender.avatar;
                        } else {
                            rd.userName = "用户";
                            rd.mid = 0;
                            rd.avatar = null;
                        }
                        rd.message = reply.message != null ? reply.message : "";
                        rd.isTop = reply.isTop;
                        if (reply.pictureList != null && reply.pictureList.size() > 0) {
                            rd.pictureList = new ArrayList<String>();
                            for (Object pic : reply.pictureList) {
                                if (pic instanceof String) {
                                    rd.pictureList.add((String) pic);
                                }
                            }
                        }
                        newReplies.add(rd);
                    }

                    final int newCount = newReplies.size();
                    final boolean isEndNow = (code == 1);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            if (footerProgress != null) {
                                footerProgress.setVisibility(View.GONE);
                            }

                            if (newCount == 0 && allReplies.size() == 0) {
                                showEmpty("暂无回复");
                                return;
                            }

                            allReplies.addAll(newReplies);
                            adapter.notifyDataSetChanged();

                            int count = totalReplyCount > 0 ? totalReplyCount : allReplies.size();
                            tvTitle.setText("全部回复（" + count + "条）");

                            lv.setVisibility(View.VISIBLE);
                            tvEmpty.setVisibility(View.GONE);

                            isEnd = isEndNow;
                            pagination = nextPagination;

                            if (isEnd || newCount == 0) {
                                footerView.setVisibility(View.GONE);
                                if (isEnd && allReplies.size() > 0) {
                                    showLoadEndTip();
                                }
                            } else {
                                footerView.setVisibility(View.VISIBLE);
                                if (footerProgress != null) {
                                    footerProgress.setVisibility(View.GONE);
                                }
                                if (footerText != null) {
                                    footerText.setText("嘿咻…嘿咻…");
                                    footerText.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                    });

                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            if (footerProgress != null) {
                                footerProgress.setVisibility(View.GONE);
                            }
                            footerView.setVisibility(View.GONE);
                            tvEmpty.setVisibility(View.VISIBLE);
                            tvEmpty.setText("加载失败: " + e.getMessage());
                            Toast.makeText(ReplyListActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        if (footerProgress != null) {
            footerProgress.setVisibility(View.GONE);
        }
        if (footerText != null) {
            footerText.setText(getString(R.string.emoticon__no_more_data));
            footerText.setVisibility(View.VISIBLE);
        }
    }

    private void showError(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                isLoading = false;
                if (footerProgress != null) {
                    footerProgress.setVisibility(View.GONE);
                }
                footerView.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText(msg);
                Toast.makeText(ReplyListActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmpty(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                isLoading = false;
                if (footerProgress != null) {
                    footerProgress.setVisibility(View.GONE);
                }
                footerView.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText(msg);
            }
        });
    }

    private void loadAvatar(final ImageView imageView, String urlStr) {
        if (urlStr == null || urlStr.length() == 0) return;
        if (urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr.substring(8);
        }
        final String finalUrl = urlStr;

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(finalUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.connect();

                    InputStream is = conn.getInputStream();

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(is, null, opts);
                    is.close();

                    int targetSize = dpToPx(48);
                    int scale = 1;
                    if (opts.outWidth > targetSize || opts.outHeight > targetSize) {
                        int widthRatio = opts.outWidth / targetSize;
                        int heightRatio = opts.outHeight / targetSize;
                        scale = Math.max(widthRatio, heightRatio);
                        if (scale < 1) scale = 1;
                        if (scale > 8) scale = 8;
                    }

                    conn.disconnect();
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.connect();
                    is = conn.getInputStream();

                    opts = new BitmapFactory.Options();
                    opts.inSampleSize = scale;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;

                    final Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                    is.close();
                    conn.disconnect();

                    if (bitmap != null && !bitmap.isRecycled()) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                                addAvatarBorder(imageView);
                            }
                        });
                    }
                } catch (OutOfMemoryError e) {
                    System.gc();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (conn != null) {
                        try { conn.disconnect(); } catch (Exception e) {}
                    }
                }
            }
        }).start();
    }

    private void loadImage(final ImageView imageView, String urlStr) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return;
        if (urlStr == null || urlStr.length() == 0) return;
        if (urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr.substring(8);
        }

        Bitmap cached = GlobalImageCache.getInstance().get(urlStr);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return;
        }

        final String finalUrl = urlStr;
        synchronized (loadingUrls) {
            if (loadingUrls.contains(finalUrl)) return;
            loadingUrls.add(finalUrl);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(finalUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.connect();

                    InputStream is = conn.getInputStream();

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(is, null, opts);
                    is.close();

                    int targetSize = dpToPx(80);
                    int scale = 1;
                    if (opts.outWidth > targetSize || opts.outHeight > targetSize) {
                        int widthRatio = opts.outWidth / targetSize;
                        int heightRatio = opts.outHeight / targetSize;
                        scale = Math.max(widthRatio, heightRatio);
                        if (scale < 1) scale = 1;
                        if (scale > 8) scale = 8;
                    }

                    conn.disconnect();
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.connect();
                    is = conn.getInputStream();

                    opts = new BitmapFactory.Options();
                    opts.inSampleSize = scale;
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;

                    final Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts);
                    is.close();
                    conn.disconnect();

                    if (bitmap != null && !bitmap.isRecycled()) {
                        GlobalImageCache.getInstance().put(finalUrl, bitmap);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (OutOfMemoryError e) {
                    System.gc();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    synchronized (loadingUrls) {
                        loadingUrls.remove(finalUrl);
                    }
                    if (conn != null) {
                        try { conn.disconnect(); } catch (Exception e) {}
                    }
                }
            }
        }).start();
    }

    private void addAvatarBorder(ImageView imageView) {
        if (imageView == null) return;
        try {
            Drawable borderDrawable = getResources().getDrawable(R.drawable.image_border_overlay);
            imageView.setBackgroundDrawable(borderDrawable);
            int paddingPx = dpToPx(2);
            imageView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } catch (Exception e) {}
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
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

    private void deleteRootComment() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int code = ReplyApi.deleteComment(aid, rpid, 1);
                    if (code == 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ReplyListActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ReplyListActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ReplyListActivity.this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showReplyDialog(final ReplyData reply) {
        String hint = (reply != null && reply.userName != null && reply.userName.length() > 0)
                ? "回复 " + reply.userName + " 的评论..."
                : "输入回复内容...";

        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        final EditText input = new EditText(this);
        input.setHint(hint);
        input.setLines(3);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        if (tv.biliclassic.util.SdkHelper.getSdkInt() >= 14) {
            android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
            inputBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            inputBg.setStroke(dpToPx(2), 0xFFD0D0D0);
            inputBg.setColor(0xFFFFFFFF);
            input.setBackgroundDrawable(inputBg);
        }

        // 表情按钮
        final LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, 0, 0, dpToPx(6));

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

        final long parentRpid = reply != null ? reply.rpid : 0;

        new AlertDialog.Builder(DialogUtil.wrap(this))
                .setTitle("发送回复")
                .setView(layout)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String text = input.getText().toString().trim();
                        if (text == null || text.length() == 0) {
                            Toast.makeText(ReplyListActivity.this, "回复内容不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        sendReply(rpid, parentRpid, text);
                        d.dismiss();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                    }
                })
                .show();
    }

    private void showEmojiPicker(final EditText input) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(DialogUtil.wrap(this));
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

    private void sendReply(final long root, final long parent, final String text) {
        ReplyHelper.sendReply(this, aid, root, parent, text, new ReplyHelper.ReplyCallback() {
            @Override
            public void onSuccess(String responseJson) {
                allReplies.clear();
                adapter.notifyDataSetChanged();
                pagination = "";
                isEnd = false;
                loadReplies();
            }

            @Override
            public void onFailed(String error) {}
        });
    }

    static class ReplyData {
        String userName;
        String message;
        String avatar;
        long mid;
        long rpid;
        long root;
        long parent;
        long time;
        int likeCount;
        boolean liked;
        boolean isTop;
        List<String> pictureList;
    }

    private static class BadgeSpan extends android.text.style.ReplacementSpan {
        private int mWidth;
        private int mPaddingPx;
        private int mGapPx;
        private int mCornerPx;
        private float mStrokePx;
        private int mTextMarginPx;
        private static final String TEXT = "置顶";

        BadgeSpan(float density) {
            mPaddingPx = (int)(4 * density + 0.5f);
            mGapPx = (int)(1 * density + 0.5f);
            mCornerPx = (int)(2 * density + 0.5f);
            mStrokePx = 1 * density + 0.5f;
            mTextMarginPx = (int)(1 * density + 0.5f);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float textWidth = paint.measureText(TEXT);
            mWidth = (int)(textWidth + mPaddingPx * 2 + mStrokePx * 2 + mGapPx + mTextMarginPx);
            if (fm != null) {
                android.graphics.Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
                fm.ascent = pfm.ascent - mPaddingPx;
                fm.descent = pfm.descent + mPaddingPx;
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return mWidth;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            int origColor = paint.getColor();
            android.graphics.Paint.Style origStyle = paint.getStyle();
            float origStrokeWidth = paint.getStrokeWidth();
            boolean origAntiAlias = paint.isAntiAlias();

            android.graphics.Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            float half = mStrokePx / 2f;
            float rectTop = y + fm.ascent - mPaddingPx + half;
            float rectBottom = y + fm.descent + mPaddingPx - half;

            float textWidth = paint.measureText(TEXT);
            float rectWidth = textWidth + mPaddingPx * 2;

            float offsetX = mGapPx;

            android.graphics.RectF rect = new android.graphics.RectF(
                    x + offsetX,
                    rectTop,
                    x + offsetX + rectWidth,
                    rectBottom
            );

            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(mStrokePx);
            paint.setColor(0xFFD86DA5);
            paint.setAntiAlias(true);
            canvas.drawRoundRect(rect, mCornerPx, mCornerPx, paint);

            paint.setColor(0xFFD86DA5);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            float centerY = (rectTop + rectBottom) / 2 - (fm.ascent + fm.descent) / 2;
            canvas.drawText(TEXT, x + offsetX + mPaddingPx, centerY, paint);

            paint.setColor(origColor);
            paint.setStyle(origStyle);
            paint.setStrokeWidth(origStrokeWidth);
            paint.setAntiAlias(origAntiAlias);
        }
    }
}