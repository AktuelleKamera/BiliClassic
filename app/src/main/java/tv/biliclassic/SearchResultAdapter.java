package tv.biliclassic;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.StringUtil;

public class SearchResultAdapter extends BaseAdapter {

    // 结果行第二个统计字段的显示模式（随排序方式切换，模仿 1.8.4）
    public static final int STAT_PLAY = 0;
    public static final int STAT_DANMAKU = 1;
    public static final int STAT_FAVORITE = 2;
    public static final int STAT_REVIEW = 3;
    public static final int STAT_PUBDATE = 4;

    private Context context;
    private List<SearchActivity.SearchResultItem> list;
    private volatile boolean mScrolling = false;

    // 键盘光标选中的项，-1 表示无选中
    private int selectedPosition = -1;

    // 触摸滑动中是否隐藏光标高亮（滑动时隐藏，再次按键时恢复）
    private boolean mHideHighlight = false;

    // 当前统计字段显示模式
    private int statMode = STAT_PLAY;

    public void setStatMode(int mode) {
        this.statMode = mode;
        notifyDataSetChanged();
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    /**
     * 触摸滑动时隐藏/显示光标高亮（值不变时跳过重绘）。
     */
    public void setHideHighlight(boolean hide) {
        if (this.mHideHighlight == hide) {
            return;
        }
        this.mHideHighlight = hide;
        notifyDataSetChanged();
    }

    public SearchResultAdapter(Context context, List<SearchActivity.SearchResultItem> list) {
        this.context = context;
        this.list = list;
    }

    /** 滚动状态变化时由 ListView 的 OnScrollListener 调用 */
    public void setScrolling(boolean scrolling) {
        ImageLoader.setScrolling(scrolling);
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final SearchActivity.SearchResultItem item = list.get(position);

        // UP主结果：复用关注列表的 item_following 布局
        if (item != null && item.isUser) {
            return getUserView(position, convertView, parent, item);
        }
        // 番剧结果：复用视频搜索行布局
        if (item != null && item.isBangumi) {
            return getBangumiView(position, convertView, parent, item);
        }
        // 生放送结果：复用生放送条目布局
        if (item != null && item.isLive) {
            return getLiveView(position, convertView, parent, item);
        }
        // 专栏结果：复用视频搜索行布局
        if (item != null && item.isArticle) {
            return getArticleView(position, convertView, parent, item);
        }

        ViewHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof ViewHolder)) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_search_result, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.authorGroup = convertView.findViewById(R.id.author_group);
            holder.authorLabel = (TextView) convertView.findViewById(R.id.author_label);
            holder.author = (TextView) convertView.findViewById(R.id.author);
            holder.statName = (TextView) convertView.findViewById(R.id.stat_name);
            holder.statValue = (TextView) convertView.findViewById(R.id.stat_value);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        applyHighlight(convertView, position);

        final int currentPos = position;

        holder.title.setText(item.title);
        holder.authorLabel.setText(R.string.search_field_author);
        holder.author.setText(item.author != null ? item.author : "");

        // 发布日期模式下隐藏 UP 主组，只显示发布日期（与 1.8.4 一致）
        if (statMode == STAT_PUBDATE) {
            holder.authorGroup.setVisibility(View.GONE);
        } else {
            holder.authorGroup.setVisibility(View.VISIBLE);
        }

        String label;
        String value;
        switch (statMode) {
            case STAT_DANMAKU:
                label = "弹幕:";
                value = StringUtil.toWan(item.danmaku);
                break;
            case STAT_FAVORITE:
                label = "收藏:";
                value = StringUtil.toWan(item.favorites);
                break;
            case STAT_REVIEW:
                label = "评论:";
                value = StringUtil.toWan(item.review);
                break;
            case STAT_PUBDATE:
                label = "发布:";
                value = formatDate(item.pubdate);
                break;
            case STAT_PLAY:
            default:
                label = "播放:";
                value = StringUtil.toWan(item.play);
                break;
        }
        holder.statName.setText(label);
        holder.statValue.setText(value);

        ImageLoader.bind(holder.cover, item.cover, R.drawable.bili_default_image_tv_with_bg, 88, 66);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof SearchActivity) {
                    ((SearchActivity) context).onSearchResultClick(item, currentPos);
                }
            }
        });

        return convertView;
    }

    /** UP主结果行：复用 item_following 布局（隐藏取消关注按钮） */
    private View getUserView(final int position, View convertView, ViewGroup parent,
                             final SearchActivity.SearchResultItem item) {
        UserHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof UserHolder)) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_following, parent, false);
            holder = new UserHolder();
            holder.avatar = (ImageView) convertView.findViewById(R.id.iv_avatar);
            holder.name = (TextView) convertView.findViewById(R.id.tv_name);
            holder.sign = (TextView) convertView.findViewById(R.id.tv_sign);
            holder.btnUnfollow = convertView.findViewById(R.id.btn_unfollow);
            convertView.setTag(holder);
        } else {
            holder = (UserHolder) convertView.getTag();
        }

        applyHighlight(convertView, position);

        holder.name.setText(item.userName != null ? item.userName : "");
        holder.sign.setText(item.userSign != null && item.userSign.length() > 0 ? item.userSign : "");
        holder.btnUnfollow.setVisibility(View.GONE);
        ImageLoader.bind(holder.avatar, item.userAvatar, R.drawable.bili_default_avatar, 44, 44);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof SearchActivity) {
                    ((SearchActivity) context).onSearchResultClick(item, position);
                }
            }
        });

        return convertView;
    }

    /** 番剧结果行：封面 + 标题 + 地区 + 更新状态 */
    private View getBangumiView(final int position, View convertView, ViewGroup parent,
                                final SearchActivity.SearchResultItem item) {
        ViewHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof ViewHolder)) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_search_result, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.authorGroup = convertView.findViewById(R.id.author_group);
            holder.authorLabel = (TextView) convertView.findViewById(R.id.author_label);
            holder.author = (TextView) convertView.findViewById(R.id.author);
            holder.statName = (TextView) convertView.findViewById(R.id.stat_name);
            holder.statValue = (TextView) convertView.findViewById(R.id.stat_value);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        applyHighlight(convertView, position);

        holder.title.setText(item.bangumiTitle != null ? item.bangumiTitle : "");
        holder.authorGroup.setVisibility(View.VISIBLE);
        holder.authorLabel.setText("地区:");
        holder.author.setText(item.bangumiArea != null ? item.bangumiArea : "");
        holder.statName.setText("更新:");
        holder.statValue.setText(item.bangumiIndexShow != null ? item.bangumiIndexShow : "");

        ImageLoader.bind(holder.cover, item.bangumiCover, R.drawable.bili_default_image_tv_with_bg, 88, 66);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof SearchActivity) {
                    ((SearchActivity) context).onSearchResultClick(item, position);
                }
            }
        });

        return convertView;
    }

    /** 专栏结果行：封面 + 标题 + 分区 + 阅读数 */
    private View getArticleView(final int position, View convertView, ViewGroup parent,
                                final SearchActivity.SearchResultItem item) {
        ViewHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof ViewHolder)) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_search_result, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.authorGroup = convertView.findViewById(R.id.author_group);
            holder.authorLabel = (TextView) convertView.findViewById(R.id.author_label);
            holder.author = (TextView) convertView.findViewById(R.id.author);
            holder.statName = (TextView) convertView.findViewById(R.id.stat_name);
            holder.statValue = (TextView) convertView.findViewById(R.id.stat_value);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        applyHighlight(convertView, position);

        holder.title.setText(item.articleTitle != null ? item.articleTitle : "");
        holder.authorGroup.setVisibility(View.VISIBLE);
        holder.authorLabel.setText("分区:");
        holder.author.setText(item.articleCategory != null ? item.articleCategory : "");
        holder.statName.setText("阅读:");
        holder.statValue.setText(StringUtil.toWan(item.articleView));

        ImageLoader.bind(holder.cover, item.articleCover, R.drawable.bili_default_image_tv_with_bg, 88, 66);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof SearchActivity) {
                    ((SearchActivity) context).onSearchResultClick(item, position);
                }
            }
        });

        return convertView;
    }

    /** 生放送结果行：复用 LiveRoomAdapter 的 item_live_room 布局 */
    private View getLiveView(final int position, View convertView, ViewGroup parent,
                             final SearchActivity.SearchResultItem item) {
        LiveHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof LiveHolder)) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_live_room, parent, false);
            holder = new LiveHolder();
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.uname = (TextView) convertView.findViewById(R.id.uname);
            holder.info = (TextView) convertView.findViewById(R.id.info);
            convertView.setTag(holder);
        } else {
            holder = (LiveHolder) convertView.getTag();
        }

        applyHighlight(convertView, position);

        tv.biliclassic.model.LiveRoom room = item.liveRoom;
        holder.title.setText(room.title != null ? room.title : "");
        holder.uname.setText(room.uname != null && room.uname.length() > 0 ? room.uname : "未知主播");

        StringBuilder info = new StringBuilder();
        if (room.online > 0) {
            info.append(StringUtil.toWan(room.online)).append("人气");
        }
        if (room.area_name != null && room.area_name.length() > 0) {
            if (info.length() > 0) info.append(" · ");
            info.append(room.area_name);
        }
        if (room.live_status == 1) {
            if (info.length() > 0) info.append(" · ");
            info.append("直播中");
        }
        holder.info.setText(info.toString());

        ImageLoader.bind(holder.cover, room.pickCover(), R.drawable.bili_default_image_tv_with_bg, 96, 66);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof SearchActivity) {
                    ((SearchActivity) context).onSearchResultClick(item, position);
                }
            }
        });

        return convertView;
    }

    private void applyHighlight(View convertView, int position) {
        if (position == selectedPosition && !mHideHighlight) {
            convertView.setBackgroundColor(0x66D86DA5);
        } else {
            try {
                convertView.setBackgroundDrawable(
                        convertView.getResources().getDrawable(R.drawable.item_click_effect_white));
            } catch (Exception e) {
                convertView.setBackgroundColor(0xFFFFFFFF);
            }
        }
    }

    private String formatDate(long pubdate) {
        if (pubdate <= 0) {
            return "";
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").format(new Date(pubdate * 1000L));
        } catch (Exception e) {
            return "";
        }
    }

    public void updateData(List<SearchActivity.SearchResultItem> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    public void clearCache() {
        ImageLoader.clearCache();
    }

    static class ViewHolder {
        TextView title;
        View authorGroup;
        TextView authorLabel;
        TextView author;
        TextView statName;
        TextView statValue;
        ImageView cover;
    }

    static class UserHolder {
        ImageView avatar;
        TextView name;
        TextView sign;
        View btnUnfollow;
    }

    static class LiveHolder {
        ImageView cover;
        TextView title;
        TextView uname;
        TextView info;
    }
}
