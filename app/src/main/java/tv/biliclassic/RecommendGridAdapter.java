package tv.biliclassic;

import tv.biliclassic.util.DeviceUtil;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import tv.biliclassic.adapter.BaseObservableAdapter;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.NetWorkUtil;

/**
 * 推荐/分区列表的行式适配器（配合 ListView 使用，实现虚拟化）。
 * 每行 = numColumns 个视频卡片；ListView 只构建可见行，滚动回收。
 */
public class RecommendGridAdapter extends BaseObservableAdapter<VideoCard> {

    private static final String TAG = "RecommendAdapter";
    private int numColumns = 2;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 滚动中暂缓应用新图，避免每张图到达都触发整屏软件重绘；
    // 仅在主线程访问（mainHandler.post 与 setScrolling 都在主线程）

    public RecommendGridAdapter(Context context, List<VideoCard> list) {
        super(context, list);
    }

    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
        notifyDataSetChanged();
    }

    public int getNumColumns() {
        return numColumns;
    }

    @Override
    public int getCount() {
        if (list == null || list.size() == 0) return 0;
        return (list.size() + numColumns - 1) / numColumns;
    }

    @Override
    public Object getItem(int position) {
        int start = position * numColumns;
        if (list != null && start < list.size()) {
            return list.get(start);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        if (convertView instanceof LinearLayout) {
            row = (LinearLayout) convertView;
        } else {
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(DeviceUtil.dpToPx(4), 0, DeviceUtil.dpToPx(4), 0);
        }

        // 列数变化时重建行内 cell
        if (row.getChildCount() != numColumns) {
            row.removeAllViews();
            for (int i = 0; i < numColumns; i++) {
                View cell = LayoutInflater.from(context).inflate(R.layout.item_recommend, row, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i < numColumns - 1) {
                    lp.rightMargin = DeviceUtil.dpToPx(8);
                }
                cell.setLayoutParams(lp);
                row.addView(cell);
            }
        }

        int cellWidth = computeCellWidth();
        int start = position * numColumns;
        for (int i = 0; i < numColumns; i++) {
            View cell = row.getChildAt(i);
            int index = start + i;
            if (index < list.size()) {
                cell.setVisibility(View.VISIBLE);
                bindCell(cell, list.get(index), cellWidth);
                // 方向键选中高亮：直接切换 background drawable，不依赖 selector 状态
                // 触摸滑动时隐藏高亮（mHideHighlight），避免光标与手指位置混淆
                boolean isSelected = index == selectedPosition && !mHideHighlight;
                // 夜间模式：磁贴本体换灰色（文字保持深色不变），白天白色
                int itemBgRes = isSelected
                        ? R.drawable.recommend_item_selected
                        : (tv.biliclassic.metro.MetroTheme.isNight()
                                ? R.drawable.item_click_effect_grey
                                : R.drawable.item_click_effect_white);
                // 背景没变就不重设：setBackgroundResource 会新建 drawable 并触发
                // requestLayout/invalidate，软件渲染下每次 getView 都设会放大掉帧
                CellHolder ch = (CellHolder) cell.getTag();
                if (ch == null) {
                    cell.setBackgroundResource(itemBgRes);
                } else if (ch.itemBgRes != itemBgRes) {
                    ch.itemBgRes = itemBgRes;
                    cell.setBackgroundResource(itemBgRes);
                }
            } else {
                cell.setVisibility(View.INVISIBLE);
            }
        }
        return row;
    }

    private int computeCellWidth() {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int padding = DeviceUtil.dpToPx(4) * 2;
        int spacing = DeviceUtil.dpToPx(8);
        return (screenWidth - padding - (numColumns - 1) * spacing) / numColumns;
    }

    private void bindCell(final View cell, final VideoCard item, int cellWidth) {
        CellHolder h = (CellHolder) cell.getTag();
        if (h == null) {
            h = new CellHolder();
            h.coverContainer = (FrameLayout) cell.findViewById(R.id.cover_container);
            h.cover = (ImageView) cell.findViewById(R.id.cover);
            h.title = (TextView) cell.findViewById(R.id.title);
            h.view = (TextView) cell.findViewById(R.id.view);
            h.danmaku = (TextView) cell.findViewById(R.id.danmaku);
            cell.setTag(h);
        }

        int coverHeight = cellWidth * 9 / 16;
        if (coverHeight > 0) {
            ViewGroup.LayoutParams p = h.coverContainer.getLayoutParams();
            if (p.height != coverHeight) {
                p.height = coverHeight;
                h.coverContainer.setLayoutParams(p);
            }
        }

        // 夜间模式：封面衬底换深灰（与磁贴本体一致）、标题白字；白天白衬底深色字。状态变化才重设
        boolean night = tv.biliclassic.metro.MetroTheme.isNight();
        if (h.nightBgApplied != night) {
            h.nightBgApplied = night;
            if (night) {
                h.coverContainer.setBackgroundColor(0xFF484848);
            } else {
                h.coverContainer.setBackgroundResource(R.drawable.bili_thumb_boarder);
            }
        }
        h.title.setTextColor(night ? 0xFFF2F2F2 : 0xFF333333);

        // 文本没变就不重设：滚动复用行时避免每次 setText 都触发 invalidate/重排
        String title = item.title != null ? item.title : "";
        if (h.titleText == null || !h.titleText.equals(title)) {
            h.titleText = title;
            h.title.setText(title);
        }
        String viewStr = item.view != null ? item.view : "0";
        if (h.viewText == null || !h.viewText.equals(viewStr)) {
            h.viewText = viewStr;
            h.view.setText(viewStr);
        }
        String danmakuStr = item.danmaku > 0 ? String.valueOf(item.danmaku) : "0";
        if (h.danmakuText == null || !h.danmakuText.equals(danmakuStr)) {
            h.danmakuText = danmakuStr;
            h.danmaku.setText(danmakuStr);
        }
        // 点击：直接绑定当前视频
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (item == null) return;
                Intent intent = new Intent(context, VideoDetailActivity.class);
                boolean hasId = false;
                if (item.aid != 0) {
                    intent.putExtra("aid", item.aid);
                    hasId = true;
                }
                // BV号 也一并传（有就传），否则只传 aid 时详情页只有 av号、拿不到 BV号
                if (item.bvid != null && item.bvid.length() > 0) {
                    intent.putExtra("bvid", item.bvid);
                    hasId = true;
                }
                if (!hasId) {
                    Toast.makeText(context, "无法获取视频信息", Toast.LENGTH_SHORT).show();
                    return;
                }
                context.startActivity(intent);
            }
        });

        final boolean hasCover = item.cover != null && item.cover.length() > 0
                && !SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false);
        if (hasCover) {
            float density = context.getResources().getDisplayMetrics().density;
            ImageLoader.bind(h.cover, item.cover, R.drawable.bili_default_image_tv_with_bg,
                    Math.round(cellWidth / density), Math.round(coverHeight / density));
        } else {
            h.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);
        }
    }

    /**
     * 设置方向键选中的视频卡索引，触发高亮更新（不触发时直接返回）。
     */
    public void setSelectedPosition(int position) {
        if (selectedPosition == position) {
            return;
        }
        selectedPosition = position;
        notifyDataSetChanged();
    }

    public void clearCache() {
    }

    static class CellHolder {
        FrameLayout coverContainer;
        ImageView cover;
        TextView title;
        TextView view;
        TextView danmaku;
        String currentCoverUrl;
        String titleText;
        String viewText;
        String danmakuText;
        boolean nightBgApplied; // 封面衬底当前是否已应用夜间灰色
        int itemBgRes;          // 磁贴本体当前背景资源（0=未设）
    }
}
