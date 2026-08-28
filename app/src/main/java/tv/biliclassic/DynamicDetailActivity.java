package tv.biliclassic;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import tv.biliclassic.model.Dynamic;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.api.DynamicApi;
import tv.biliclassic.util.ImageLoader;

/**
 * 动态详情页：原生渲染（参考哔哩终端），替代原来打开 t.bilibili.com 网页
 * （移动版网页会弹"下载APP"遮罩，老设备 WebView 也打不开）。
 */
public class DynamicDetailActivity extends BaseActivity {

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

    private Dynamic mDynamic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_detail);

        setTitle("动态详情");

        avatar = (ImageView) findViewById(R.id.dd_avatar);
        name = (TextView) findViewById(R.id.dd_name);
        time = (TextView) findViewById(R.id.dd_time);
        content = (TextView) findViewById(R.id.dd_content);
        picsBox = (LinearLayout) findViewById(R.id.dd_pics);
        cardBox = findViewById(R.id.dd_card);
        cardCover = (ImageView) findViewById(R.id.dd_card_cover);
        cardTitle = (TextView) findViewById(R.id.dd_card_title);
        cardLabel = (TextView) findViewById(R.id.dd_card_label);
        forwardBox = findViewById(R.id.dd_forward_box);
        forwardContent = (TextView) findViewById(R.id.dd_forward_content);
        like = (TextView) findViewById(R.id.dd_like);

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
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            return isDestroyed();
        }
        return false;
    }

    private void render(Dynamic d) {
        ImageLoader.bind(avatar, d.avatar, R.drawable.bili_default_image_tv_with_bg, 0, 0);
        name.setText(d.uname != null ? d.uname : "");
        time.setText(d.pubTime != null ? d.pubTime : "");

        setText(content, d.content);

        picsBox.removeAllViews();
        if (d.pics != null && d.pics.size() > 0) {
            picsBox.setVisibility(View.VISIBLE);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            for (int i = 0; i < d.pics.size() && i < 9; i++) {
                final ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, screenWidth * 3 / 4);
                if (i > 0) lp.topMargin = dp(6);
                iv.setLayoutParams(lp);
                iv.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        openImages();
                    }
                });
                ImageLoader.bind(iv, d.pics.get(i), R.drawable.bili_default_image_tv_with_bg, 0, 0);
                picsBox.addView(iv);
            }
        } else {
            picsBox.setVisibility(View.GONE);
        }

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
            openWeb("https://www.bilibili.com/read/cv" + d.articleId, "专栏文章");
        } else if (d.roomId != 0) {
            openWeb("https://live.bilibili.com/" + d.roomId, "直播间");
        } else if (d.epid != 0) {
            openWeb("https://www.bilibili.com/bangumi/play/ep" + d.epid, "番剧");
        }
    }

    private void openImages() {
        if (mDynamic == null || mDynamic.pics == null || mDynamic.pics.size() == 0) return;
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putStringArrayListExtra("imageList", new java.util.ArrayList<String>(mDynamic.pics));
        intent.putExtra("index", 0);
        startActivity(intent);
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
}
