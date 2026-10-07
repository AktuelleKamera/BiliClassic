package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.api.DynamicApi;
import tv.biliclassic.api.ReplyApi;
import tv.biliclassic.model.Dynamic;
import tv.biliclassic.model.Reply;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.DialogUtil;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.ReplyHelper;
import tv.biliclassic.util.SdkHelper;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 动态详情页：原生渲染动态内容 + 评论区（复用 CommentAdapter / ReplyApi，type=17）
 */
public class DynamicDetailActivity extends BaseActivity {

    private ListView list;
    private tv.biliclassic.widget.LoadingBarView loading;
    private View footer;
    private tv.biliclassic.widget.LoadingBarView footerBar;

    private View header;
    private ImageView avatar;
    private TextView name;
    private TextView time;
    private TextView content;
    private LinearLayout picsBox;
    private View cardBox;
    private ImageView cardCover;
    private TextView cardTitle;
    private TextView cardLabel;
    private View forwardBox;
    private TextView forwardContent;
    private TextView like;
    private TextView writeComment;

    private CommentAdapter adapter;
    private final List<CommentFragment.CommentItem> replies = new ArrayList<CommentFragment.CommentItem>();

    private Dynamic mDynamic;
    private String mCursor = "";
    private boolean mLoading = false;
    private boolean mEnd = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_detail);
        initRoundTitleBar();

        View back = findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    finish();
                }
            });
        }
        TextView titleText = (TextView) findViewById(R.id.title_text);
        if (titleText != null) {
            titleText.setText("动态详情");
        }

        list = (ListView) findViewById(R.id.dd_list);
        loading = (tv.biliclassic.widget.LoadingBarView) findViewById(R.id.dd_loading);

        header = LayoutInflater.from(this).inflate(R.layout.dd_header, list, false);
        avatar = (ImageView) header.findViewById(R.id.dd_avatar);
        addAvatarBorder(avatar);
        name = (TextView) header.findViewById(R.id.dd_name);
        time = (TextView) header.findViewById(R.id.dd_time);
        content = (TextView) header.findViewById(R.id.dd_content);
        picsBox = (LinearLayout) header.findViewById(R.id.dd_pics);
        cardBox = header.findViewById(R.id.dd_card);
        cardCover = (ImageView) header.findViewById(R.id.dd_card_cover);
        cardTitle = (TextView) header.findViewById(R.id.dd_card_title);
        cardLabel = (TextView) header.findViewById(R.id.dd_card_label);
        forwardBox = header.findViewById(R.id.dd_forward_box);
        forwardContent = (TextView) header.findViewById(R.id.dd_forward_content);
        like = (TextView) header.findViewById(R.id.dd_like);
        writeComment = (TextView) header.findViewById(R.id.dd_write_comment);

        list.addHeaderView(header);

        footer = LayoutInflater.from(this).inflate(R.layout.list_footer, list, false);
        footerBar = (tv.biliclassic.widget.LoadingBarView) footer;
        list.addFooterView(footer);
        footerBar.hide();

        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (totalItemCount == 0) return;
                if (firstVisibleItem + visibleItemCount >= totalItemCount) {
                    if (!mEnd && !mLoading) loadReplies();
                }
            }
        });

        if (writeComment != null) {
            writeComment.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showReplyDialog(null);
                }
            });
        }

        final long id = getIntent().getLongExtra("id", 0);
        if (id == 0) {
            Toast.makeText(this, "动态不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                try {
                    final Dynamic d = DynamicApi.getDynamicDetail(id);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (isFinishing() || isDestroyedCompat()) return;
                            mDynamic = d;
                            render(d);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (isFinishing() || isDestroyedCompat()) return;
                            Toast.makeText(DynamicDetailActivity.this,
                                    "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                }
            }
        }).start();
    }

    private boolean isDestroyedCompat() {
        if (SdkHelper.getSdkInt() >= 17) {
            try {
                return ((Boolean) android.app.Activity.class
                        .getMethod("isDestroyed").invoke(this)).booleanValue();
            } catch (Throwable t) {
            }
        }
        return false;
    }

    private void render(Dynamic d) {
        loading.hide();

        // 夜间：头部/卡片/转发/正文显式套色（列表项/头部动态创建，UiSkin 覆盖不到）
        final boolean night = tv.biliclassic.metro.MetroTheme.isNight();
        View headerRow = header.findViewById(R.id.dd_header_row);
        if (headerRow != null) {
            tv.biliclassic.util.UiSkin.setBgResourceKeepPadding(headerRow, night
                    ? R.drawable.item_click_effect_grey : R.drawable.item_click_effect_white);
        }
        if (cardBox != null) tv.biliclassic.util.UiSkin.setBgColorKeepPadding(cardBox, night ? 0xFF2A2A2A : 0xFFE8E8E8);
        if (forwardBox != null) tv.biliclassic.util.UiSkin.setBgColorKeepPadding(forwardBox, night ? 0xFF2A2A2A : 0xFFE8E8E8);
        if (cardTitle != null) cardTitle.setTextColor(night ? 0xFFE6E6E6 : 0xFF333333);
        if (cardLabel != null) cardLabel.setTextColor(night ? 0xFFB0B0B0 : 0xFF999999);
        if (forwardContent != null) forwardContent.setTextColor(night ? 0xFFB0B0B0 : 0xFF666666);
        name.setTextColor(night ? 0xFFE6E6E6 : 0xFF333333);
        time.setTextColor(night ? 0xFFB0B0B0 : 0xFF999999);
        content.setTextColor(night ? 0xFFB0B0B0 : 0xFF555555);

        ImageLoader.bind(avatar, d.avatar, R.drawable.bili_default_image_tv_with_bg, 48, 48);
        name.setText(d.uname != null ? d.uname : "");
        time.setText(d.pubTime != null ? d.pubTime : "");

        View.OnClickListener userClick = new View.OnClickListener() {
            public void onClick(View v) {
                openProfile(mDynamic != null ? mDynamic.mid : 0);
            }
        };
        avatar.setOnClickListener(userClick);
        name.setOnClickListener(userClick);

        setText(content, d.content);

        renderPics(d);

        applyCard(cardBox, cardTitle, cardLabel, d);

        if (d.forward != null && d.forward.dynamicId != 0) {
            forwardBox.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            sb.append("@").append(d.forward.uname != null ? d.forward.uname : "").append("：");
            if (d.forward.content != null && d.forward.content.length() > 0) {
                sb.append(d.forward.content);
            }
            forwardContent.setText(sb.toString());
            forwardBox.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        Intent it = new Intent(DynamicDetailActivity.this, DynamicDetailActivity.class);
                        it.putExtra("id", mDynamic.forward.dynamicId);
                        startActivity(it);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } else {
            forwardBox.setVisibility(View.GONE);
            forwardBox.setOnClickListener(null);
        }

        like.setText(d.likeCount > 0 ? d.likeCount + " 人觉得很赞" : "");

        adapter = new CommentAdapter(this, replies, d.commentId, null);
        adapter.setMid(SharedPreferencesUtil.getLong("mid", 0));
        adapter.setReplyType(d.commentType != 0 ? d.commentType : ReplyApi.REPLY_TYPE_DYNAMIC);
        adapter.setOnReplyClickListener(new CommentAdapter.OnReplyClickListener() {
            public void onReplyClick(CommentFragment.CommentItem comment, CommentFragment.ReplyItem reply) {
                showReplyDialog(comment);
            }
        });
        list.setAdapter(adapter);

        loadReplies();
    }

    private void renderPics(Dynamic d) {
        picsBox.removeAllViews();
        if (d.pics == null || d.pics.size() == 0) {
            picsBox.setVisibility(View.GONE);
            return;
        }
        picsBox.setVisibility(View.VISIBLE);
        int gap = dp(4);
        int available = getResources().getDisplayMetrics().widthPixels - dp(20);
        int count = Math.min(d.pics.size(), 9);
        int cols = (count == 1 || count == 4) ? 1 : 3;
        if (count == 4) cols = 2;
        int cell = (available - gap * (cols - 1)) / cols;
        int cellDp = Math.max(1, (int) (cell / getResources().getDisplayMetrics().density));
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = gap;
                row.setLayoutParams(rlp);
                picsBox.addView(row);
            }
            final int index = i;
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cell, cell);
            if (i % cols != 0) lp.leftMargin = gap;
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    openImages(index);
                }
            });
            ImageLoader.bind(iv, d.pics.get(i), R.drawable.bili_default_image_tv_with_bg, cellDp, cellDp);
            row.addView(iv);
        }
    }

    private void loadReplies() {
        if (mDynamic == null || mLoading || mEnd) return;
        mLoading = true;
        footerBar.showLoading();

        final long oid = mDynamic.commentId;
        final int type = mDynamic.commentType != 0 ? mDynamic.commentType : ReplyApi.REPLY_TYPE_DYNAMIC;
        final String cursor = mCursor;
        new Thread(new Runnable() {
            public void run() {
                List<Reply> got = new ArrayList<Reply>();
                final boolean ok;
                final boolean end;
                final String next;
                try {
                    ReplyApi.ReplyListResult res = ReplyApi.getRepliesLazy(oid, 0, cursor, type, 2, got);
                    ok = res.code != -1;
                    end = res.code == 1 || res.nextPagination == null || res.nextPagination.length() == 0;
                    next = res.nextPagination;
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (isFinishing() || isDestroyedCompat()) return;
                            mLoading = false;
                            footerBar.showStatus("加载失败，点击重试");
                        }
                    });
                    return;
                }
                final List<CommentFragment.CommentItem> mapped = new ArrayList<CommentFragment.CommentItem>();
                for (Reply r : got) mapped.add(toItem(r));
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing() || isDestroyedCompat()) return;
                        if (!ok) {
                            mLoading = false;
                            footerBar.showStatus("加载失败，点击重试");
                            return;
                        }
                        replies.addAll(mapped);
                        if (adapter != null) adapter.notifyDataSetChanged();
                        mCursor = next != null ? next : "";
                        mEnd = end;
                        mLoading = false;
                        updateFooter();
                    }
                });
            }
        }).start();
    }

    private void reloadReplies() {
        replies.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        mCursor = "";
        mEnd = false;
        mLoading = false;
        loadReplies();
    }

    private void updateFooter() {
        if (replies.size() == 0) {
            footerBar.showStatus("还没有评论，快来抢沙发吧~");
        } else if (mEnd) {
            footerBar.showStatus(getString(R.string.emoticon__no_more_data));
        } else {
            footerBar.hide();
        }
    }

    private CommentFragment.CommentItem toItem(Reply r) {
        CommentFragment.CommentItem ci = new CommentFragment.CommentItem();
        ci.rpid = r.rpid;
        ci.mid = r.sender != null ? r.sender.mid : 0;
        ci.userName = r.sender != null ? r.sender.name : "";
        ci.userAvatar = r.sender != null ? r.sender.avatar : "";
        ci.message = r.message;
        ci.likeCount = r.likeCount;
        ci.liked = r.liked;
        ci.isTop = r.isTop;
        ci.time = r.ctime;
        ci.replyCount = r.childCount;
        ci.pictureList = new ArrayList<String>(r.pictureList);
        ci.replies = new ArrayList<CommentFragment.ReplyItem>();
        if (r.childMsgList != null) {
            for (Reply c : r.childMsgList) {
                CommentFragment.ReplyItem ri = new CommentFragment.ReplyItem();
                ri.userName = c.sender != null ? c.sender.name : "";
                ri.mid = c.sender != null ? c.sender.mid : 0;
                ri.message = c.message;
                ri.rpid = c.rpid;
                ri.root = c.root;
                ri.parent = c.parent;
                ci.replies.add(ri);
            }
        }
        return ci;
    }

    private void showReplyDialog(final CommentFragment.CommentItem replyTo) {
        if (mDynamic == null) return;
        final EditText input = new EditText(this);
        int pad = dp(12);
        input.setPadding(pad, pad, pad, pad);
        input.setHint(replyTo == null ? "发一条友善的评论" : "回复 " + replyTo.userName);
        new AlertDialog.Builder(SdkHelper.dialogContext(DialogUtil.wrap(this)))
                .setTitle(replyTo == null ? "发表评论" : "回复")
                .setView(input)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText().toString().trim();
                        if (text.length() == 0) return;
                        long root = replyTo == null ? 0 : replyTo.rpid;
                        long parent = replyTo == null ? 0 : replyTo.rpid;
                        int type = mDynamic.commentType != 0 ? mDynamic.commentType : ReplyApi.REPLY_TYPE_DYNAMIC;
                        ReplyHelper.sendReply(DynamicDetailActivity.this, mDynamic.commentId,
                                root, parent, text, type, new ReplyHelper.ReplyCallback() {
                                    public void onSuccess(String responseJson) {
                                        reloadReplies();
                                    }

                                    public void onFailed(String error) {
                                    }
                                });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyCard(View box, final TextView title, final TextView label, final Dynamic d) {
        VideoCard vc = d.videoCard;
        boolean hasCard = vc != null && vc.title != null && vc.title.length() > 0
                && (d.videoCard.aid != 0 || (vc.bvid != null && vc.bvid.length() > 0)
                || d.articleId != 0 || d.roomId != 0 || d.epid != 0);
        if (!hasCard) {
            box.setVisibility(View.GONE);
            box.setOnClickListener(null);
            return;
        }
        box.setVisibility(View.VISIBLE);
        title.setText(vc.title);
        label.setText(safeLabel(d));
        ImageLoader.bind(cardCover, vc.cover, R.drawable.bili_default_image_tv_with_bg, 96, 60);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openCard(mDynamic);
            }
        });
    }

    private String safeLabel(Dynamic d) {
        return d.cardLabel != null && d.cardLabel.length() > 0 ? d.cardLabel : "动态";
    }

    private void openCard(Dynamic d) {
        if (d == null) return;
        if (d.videoCard != null
                && (d.videoCard.aid != 0 || (d.videoCard.bvid != null && d.videoCard.bvid.length() > 0))) {
            Intent intent = new Intent(this, VideoDetailActivity.class);
            if (d.videoCard.aid != 0) {
                intent.putExtra("aid", d.videoCard.aid);
            } else {
                intent.putExtra("bvid", d.videoCard.bvid);
            }
            startActivity(intent);
            return;
        }
        if (d.articleId != 0) {
            openArticle(d.articleId, d.videoCard != null ? d.videoCard.title : "专栏文章");
        } else if (d.roomId != 0) {
            Intent live = new Intent(this, LiveInfoActivity.class);
            live.putExtra("room_id", d.roomId);
            startActivity(live);
        } else if (d.epid != 0) {
            openWeb("https://www.bilibili.com/bangumi/play/ep" + d.epid, "番剧");
        }
    }

    private void openImages(int index) {
        if (mDynamic == null || mDynamic.pics == null || mDynamic.pics.size() == 0) return;
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putStringArrayListExtra("imageList", new ArrayList<String>(mDynamic.pics));
        intent.putExtra("index", index);
        startActivity(intent);
    }

    private void openProfile(long mid) {
        if (mid == 0) return;
        try {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("mid", mid);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void openArticle(long cvid, String title) {
        if (cvid == 0) return;
        try {
            Intent intent = new Intent(this, ArticleActivity.class);
            intent.putExtra("cvid", cvid);
            intent.putExtra("title", title);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void openWeb(String url, String title) {
        try {
            Intent intent = new Intent(this, WebViewActivity.class);
            intent.putExtra("url", url);
            intent.putExtra("title", title);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void setText(TextView tv, String text) {
        if (text != null && text.length() > 0) {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 评论同款头像描边框 */
    private void addAvatarBorder(ImageView imageView) {
        if (imageView == null) return;
        try {
            android.graphics.drawable.Drawable borderDrawable =
                    getResources().getDrawable(R.drawable.image_border_overlay);
            imageView.setBackgroundDrawable(borderDrawable);
            int paddingPx = dp(2);
            imageView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } catch (Exception e) {
        }
    }
}
