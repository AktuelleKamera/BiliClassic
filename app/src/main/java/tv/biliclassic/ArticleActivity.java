package tv.biliclassic;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tv.biliclassic.api.ArticleApi;
import tv.biliclassic.model.ArticleInfo;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.StringUtil;

/**
 * 专栏详情：参考 BiliTerminal 的 ArticleApi/ArticleContentAdapter，
 * 拉取 x/article/view 后用原生的文本/图片块渲染正文（不走网页）。
 */
public class ArticleActivity extends BaseActivity {

    private static final Pattern IMG_PATTERN = Pattern.compile(
            "<img[^>]*?src\\s*=\\s*[\"']?([^\"'\\s>]+)[\"']?[^>]*>",
            Pattern.CASE_INSENSITIVE);

    private long cvid;
    private LinearLayout mContent;
    private ScrollView mScroll;
    private View mLoading;
    private final List<String> mImageUrls = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article);
        initRoundTitleBar();

        cvid = getIntent().getLongExtra("cvid", 0);
        if (cvid <= 0) {
            String extra = getIntent().getStringExtra("cvid");
            if (extra != null) {
                try {
                    cvid = Long.parseLong(extra.trim());
                } catch (Exception e) {
                    cvid = 0;
                }
            }
        }

        mContent = (LinearLayout) findViewById(R.id.article_content);
        mScroll = (ScrollView) findViewById(R.id.article_scroll);
        mLoading = findViewById(R.id.loading_container);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        String title = getIntent().getStringExtra("title");
        TextView titleText = (TextView) findViewById(R.id.title_text);
        if (titleText != null && title != null && title.length() > 0) {
            titleText.setText(title);
        }

        if (cvid <= 0) {
            finish();
            return;
        }
        loadArticle();
    }

    private void loadArticle() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArticleInfo info = ArticleApi.getArticle(cvid);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (info == null) {
                            if (mLoading != null) mLoading.setVisibility(View.GONE);
                            android.widget.Toast.makeText(ArticleActivity.this,
                                    "专栏加载失败", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        render(info);
                    }
                });
            }
        }).start();
    }

    private void render(ArticleInfo info) {
        if (mLoading != null) mLoading.setVisibility(View.GONE);
        if (mScroll != null) mScroll.setVisibility(View.VISIBLE);
        if (mContent == null) return;
        mContent.removeAllViews();
        mImageUrls.clear();

        TextView titleText = (TextView) findViewById(R.id.title_text);
        if (titleText != null && info.title != null && info.title.length() > 0) {
            titleText.setText(info.title);
        }

        int contentWidthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density) - 24;

        // 头图
        if (info.banner != null && info.banner.length() > 0) {
            ImageView cover = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            cover.setLayoutParams(lp);
            cover.setAdjustViewBounds(true);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ImageLoader.bind(cover, normalizeUrl(info.banner),
                    R.drawable.bili_default_image_tv_with_bg, contentWidthDp, contentWidthDp);
            mContent.addView(cover);
        }

        // 标题
        TextView title = new TextView(this);
        title.setText(info.title != null ? info.title : "");
        title.setTextSize(20);
        title.setTextColor(0xFF333333);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = dp(6);
        title.setLayoutParams(titleLp);
        mContent.addView(title);

        // 作者 / 时间 / 阅读
        StringBuilder meta = new StringBuilder();
        if (info.authorName != null && info.authorName.length() > 0) {
            meta.append(info.authorName);
        }
        if (info.ctime > 0) {
            if (meta.length() > 0) meta.append(" · ");
            try {
                meta.append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(info.ctime * 1000L)));
            } catch (Exception e) {
                // ignore
            }
        }
        if (info.view > 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(StringUtil.toWan(info.view)).append("阅读");
        }
        if (meta.length() > 0) {
            TextView metaTv = new TextView(this);
            metaTv.setText(meta.toString());
            metaTv.setTextSize(12);
            metaTv.setTextColor(0xFF999999);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            metaLp.bottomMargin = dp(12);
            metaTv.setLayoutParams(metaLp);
            mContent.addView(metaTv);
        }

        renderContent(info.content);
    }

    /** 把正文 HTML 按 <img> 切成"文本块 / 图片块"交替渲染 */
    private void renderContent(String html) {
        if (html == null || html.length() == 0) return;
        Matcher m = IMG_PATTERN.matcher(html);
        int last = 0;
        while (m.find()) {
            addTextBlock(html.substring(last, m.start()));
            addImageBlock(m.group(1));
            last = m.end();
        }
        addTextBlock(html.substring(last));
    }

    private void addTextBlock(String html) {
        if (html == null || html.length() == 0) return;
        String text;
        try {
            String pre = html.replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</p>", "\n\n")
                    .replaceAll("(?i)<p[^>]*>", "");
            text = Html.fromHtml(pre).toString();
        } catch (Throwable t) {
            text = html.replaceAll("<[^>]+>", "");
        }
        text = text.replace('\u00a0', ' ').trim();
        if (text.length() == 0) return;

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(0xFF333333);
        tv.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        mContent.addView(tv);
    }

    private void addImageBlock(String src) {
        String url = normalizeUrl(src);
        if (url.length() == 0) return;

        final int index = mImageUrls.size();
        mImageUrls.add(url);

        int contentWidthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density) - 24;

        ImageView iv = new ImageView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(8);
        iv.setLayoutParams(lp);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ImageLoader.bind(iv, url, R.drawable.bili_default_image_tv_with_bg, contentWidthDp, contentWidthDp);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mImageUrls.isEmpty()) return;
                Intent intent = new Intent(ArticleActivity.this, ImageViewerActivity.class);
                intent.putStringArrayListExtra("imageList", new ArrayList<String>(mImageUrls));
                intent.putExtra("index", index);
                startActivity(intent);
            }
        });
        mContent.addView(iv);
    }

    private String normalizeUrl(String url) {
        if (url == null || url.length() == 0) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://")) return "https://" + url.substring(7);
        if (!url.startsWith("https://")) return "https://" + url;
        return url;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
