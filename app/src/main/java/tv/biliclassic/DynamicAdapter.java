package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import tv.biliclassic.model.Dynamic;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 动态列表适配器：头像/图片/封面异步加载（GlobalImageCache），
 * 支持点赞、删除、查看大图、跳转视频/专栏/直播/用户主页。
 */
public class DynamicAdapter extends BaseAdapter {

    public interface Listener {
        void onLike(Dynamic d);

        void onDelete(Dynamic d);
    }

    private static final int MAX_PICS_SHOWN = 3;

    private final Context context;
    private final List<Dynamic> list;
    private final Listener listener;

    private int picHeight = 0;

    public DynamicAdapter(Context context, List<Dynamic> list, Listener listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        return list != null && position >= 0 && position < list.size() ? list.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_dynamic, parent, false);
            Holder h = new Holder();
            h.avatar = (ImageView) view.findViewById(R.id.dyn_avatar);
            h.name = (TextView) view.findViewById(R.id.dyn_name);
            h.time = (TextView) view.findViewById(R.id.dyn_time);
            h.content = (TextView) view.findViewById(R.id.dyn_content);
            h.picsRow = (LinearLayout) view.findViewById(R.id.dyn_pics_row);
            h.pics = new ImageView[MAX_PICS_SHOWN];
            h.pics[0] = (ImageView) view.findViewById(R.id.dyn_pic1);
            h.pics[1] = (ImageView) view.findViewById(R.id.dyn_pic2);
            h.pics[2] = (ImageView) view.findViewById(R.id.dyn_pic3);
            h.picsMore = (TextView) view.findViewById(R.id.dyn_pics_more);
            h.cardBox = (LinearLayout) view.findViewById(R.id.dyn_card_box);
            h.cardCover = (ImageView) view.findViewById(R.id.dyn_card_cover);
            h.cardTitle = (TextView) view.findViewById(R.id.dyn_card_title);
            h.cardInfo = (TextView) view.findViewById(R.id.dyn_card_info);
            h.forwardBox = (LinearLayout) view.findViewById(R.id.dyn_forward_box);
            h.forwardName = (TextView) view.findViewById(R.id.dyn_forward_name);
            h.forwardContent = (TextView) view.findViewById(R.id.dyn_forward_content);
            h.forwardTitle = (TextView) view.findViewById(R.id.dyn_forward_title);
            h.like = (TextView) view.findViewById(R.id.dyn_like);
            h.delete = (TextView) view.findViewById(R.id.dyn_delete);
            view.setTag(h);
        }
        bind(view, (Holder) view.getTag(), (Dynamic) getItem(position));
        return view;
    }

    private void bind(final View root, final Holder h, final Dynamic d) {
        if (d == null) return;

        h.name.setText(d.uname == null || d.uname.length() == 0 ? "哔哩哔哩用户" : d.uname);
        h.time.setText(d.pubTime == null ? "" : d.pubTime);
        loadBitmap(h.avatar, d.avatar, dpToPx(42), dpToPx(42), R.drawable.bili_default_avatar);

        setTextAndVisibility(h.content, d.content);

        // 图片行（最多显示3张，其余提示）
        boolean noImage = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false);
        int picCount = d.pics != null ? d.pics.size() : 0;
        if (picCount > 0 && !noImage) {
            ensurePicHeight();
            h.picsRow.setVisibility(View.VISIBLE);
            for (int i = 0; i < MAX_PICS_SHOWN; i++) {
                final ImageView iv = h.pics[i];
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
                if (lp.height != picHeight) {
                    lp.height = picHeight;
                    iv.setLayoutParams(lp);
                }
                if (i < picCount) {
                    iv.setVisibility(View.VISIBLE);
                    loadBitmap(iv, d.pics.get(i), picHeight, picHeight,
                            R.drawable.bili_default_image_tv_with_bg);
                    final int index = i;
                    iv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            openImages(d, index);
                        }
                    });
                } else {
                    iv.setVisibility(View.GONE);
                }
            }
            if (picCount > MAX_PICS_SHOWN) {
                h.picsMore.setVisibility(View.VISIBLE);
                h.picsMore.setText("+" + (picCount - MAX_PICS_SHOWN)
                        + "张，点击图片可查看全部");
            } else {
                h.picsMore.setVisibility(View.GONE);
            }
        } else {
            h.picsRow.setVisibility(View.GONE);
            h.picsMore.setVisibility(View.GONE);
        }

        // 卡片（视频/合集/番剧/专栏/直播）
        if (d.videoCard != null) {
            h.cardBox.setVisibility(View.VISIBLE);
            h.cardBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openCard(d);
                }
            });
            h.cardTitle.setText(d.videoCard.title == null ? "" : d.videoCard.title);
            String info = d.cardLabel == null ? "" : d.cardLabel;
            if (d.videoCard.view != null && d.videoCard.view.length() > 0
                    && !"0".equals(d.videoCard.view)) {
                info += " · " + d.videoCard.view + "播放";
            }
            h.cardInfo.setText(info);
            loadBitmap(h.cardCover, d.videoCard.cover, dpToPx(96), dpToPx(60),
                    R.drawable.bili_default_image_tv_with_bg);
        } else {
            h.cardBox.setVisibility(View.GONE);
            h.cardBox.setOnClickListener(null);
        }

        // 转发摘要
        if (d.forward != null) {
            h.forwardBox.setVisibility(View.VISIBLE);
            h.forwardName.setText("@"
                    + (d.forward.uname == null ? "" : d.forward.uname));
            String fc = d.forward.content;
            if (fc != null && fc.length() > 0) {
                h.forwardContent.setVisibility(View.VISIBLE);
                h.forwardContent.setText(fc);
            } else {
                h.forwardContent.setVisibility(View.GONE);
            }
            boolean hasFwdTitle = d.forward.videoCard != null
                    && d.forward.videoCard.title != null
                    && d.forward.videoCard.title.length() > 0;
            if (hasFwdTitle) {
                h.forwardTitle.setVisibility(View.VISIBLE);
                h.forwardTitle.setText("[" + safeLabel(d.forward) + "] "
                        + d.forward.videoCard.title);
            } else if (d.forward.pics != null && d.forward.pics.size() > 0) {
                h.forwardTitle.setVisibility(View.VISIBLE);
                h.forwardTitle.setText("[图片x" + d.forward.pics.size() + "]");
            } else {
                h.forwardTitle.setVisibility(View.GONE);
            }
            h.forwardBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openCard(d.forward);
                }
            });
        } else {
            h.forwardBox.setVisibility(View.GONE);
            h.forwardBox.setOnClickListener(null);
        }

        // 操作行
        h.like.setText(d.liked ? "已赞 " + d.likeCount
                : (d.likeCount > 0 ? "赞 " + d.likeCount : "赞"));
        h.like.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onLike(d);
            }
        });
        if (d.canDelete) {
            h.delete.setVisibility(View.VISIBLE);
            h.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onDelete(d);
                }
            });
        } else {
            h.delete.setVisibility(View.GONE);
            h.delete.setOnClickListener(null);
        }

        // 整条点击：意义不明的网页查看
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDetail(d.dynamicId);
            }
        });
    }

    private String safeLabel(Dynamic d) {
        return d.cardLabel != null && d.cardLabel.length() > 0 ? d.cardLabel : "动态";
    }

    private void setTextAndVisibility(TextView tv, String text) {
        if (text != null && text.length() > 0) {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void openImages(Dynamic d, int index) {
        if (context == null || d.pics == null || d.pics.size() == 0) return;
        Intent intent = new Intent(context, ImageViewerActivity.class);
        intent.putStringArrayListExtra("imageList", new java.util.ArrayList<String>(d.pics));
        intent.putExtra("index", index);
        context.startActivity(intent);
    }

    private void openCard(Dynamic d) {
        if (context == null || d == null) return;
        if (d.videoCard != null) {
            if (d.videoCard.aid != 0 || (d.videoCard.bvid != null && d.videoCard.bvid.length() > 0)) {
                Intent intent = new Intent(context, VideoDetailActivity.class);
                if (d.videoCard.aid != 0) {
                    intent.putExtra("aid", d.videoCard.aid);
                } else {
                    intent.putExtra("bvid", d.videoCard.bvid);
                }
                context.startActivity(intent);
                return;
            }
        }
        if (d.articleId != 0) {
            openWeb("https://www.bilibili.com/read/cv" + d.articleId, "专栏文章");
            return;
        }
        if (d.roomId != 0) {
            openWeb("https://live.bilibili.com/" + d.roomId, "直播间");
            return;
        }
        if (d.epid != 0) {
            openWeb("https://www.bilibili.com/bangumi/play/ep" + d.epid, "番剧");
            return;
        }
        if (d.dynamicId != 0) {
            openDetail(d.dynamicId);
        }
    }

    /** 点击动态：只开原生详情页（网页版老设备看不了，不回退网页） */
    private void openDetail(long dynamicId) {
        if (dynamicId == 0 || context == null) return;
        try {
            Intent intent = new Intent(context, DynamicDetailActivity.class);
            intent.putExtra("id", dynamicId);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void openWeb(String url, String title) {
        try {
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.putExtra("url", url);
            intent.putExtra("title", title);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void ensurePicHeight() {
        if (picHeight <= 0) {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int rowWidth = screenWidth - dpToPx(52) - dpToPx(20);
            picHeight = Math.max(dpToPx(70), rowWidth / MAX_PICS_SHOWN);
        }
    }

    /** 异步加载图片：内存缓存命中直接显示，否则交给 ImageLoader 统一下载/解码 */
    private void loadBitmap(final ImageView iv, String rawUrl, final int w, final int h,
                            final int defaultRes) {
        if (iv == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        ImageLoader.bind(iv, rawUrl, defaultRes, Math.round(w / density), Math.round(h / density));
    }

    public void clearCache() {
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    static class Holder {
        ImageView avatar;
        TextView name;
        TextView time;
        TextView content;
        LinearLayout picsRow;
        ImageView[] pics;
        TextView picsMore;
        LinearLayout cardBox;
        ImageView cardCover;
        TextView cardTitle;
        TextView cardInfo;
        LinearLayout forwardBox;
        TextView forwardName;
        TextView forwardContent;
        TextView forwardTitle;
        TextView like;
        TextView delete;
    }
}
