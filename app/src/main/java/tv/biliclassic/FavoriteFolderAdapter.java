package tv.biliclassic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.model.FavoriteFolder;
import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.SharedPreferencesUtil;

public class FavoriteFolderAdapter extends BaseAdapter {

    private Context context;
    private List<FavoriteFolder> list;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mScrolling = false;

    public FavoriteFolderAdapter(Context context, List<FavoriteFolder> list) {
        this.context = context;
        this.list = list;
        if (this.list == null) {
            this.list = new ArrayList<FavoriteFolder>();
        }
    }

    /** 滚动状态变化时由 ListView 的 OnScrollListener 调用 */
    public void setScrolling(boolean scrolling) {
        ImageLoader.setScrolling(scrolling);
    }

    // ===== 遥控器方向键选中的条目（-1 = 未选中），用于整行高亮 =====
    private int selectedPosition = -1;
    private boolean mHideHighlight = false;

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public void setHideHighlight(boolean hide) {
        if (this.mHideHighlight == hide) {
            return;
        }
        this.mHideHighlight = hide;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        if (list == null || position < 0 || position >= list.size()) {
            return null;
        }
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (list == null || position < 0 || position >= list.size()) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_favorite_folder, parent, false);
            }
            return convertView;
        }

        final FavoriteFolder item = list.get(position);
        if (item == null) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_favorite_folder, parent, false);
            }
            return convertView;
        }

        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_favorite_folder, parent, false);
            holder = new ViewHolder();
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.count = (TextView) convertView.findViewById(R.id.count);
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // 遥控器光标高亮（选中：半透明粉色；未选中：恢复原点击效果背景）
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

        holder.name.setText(item.name != null ? item.name : "");
        holder.count.setText((item.videoCount >= 0 ? item.videoCount : 0) + "个视频");

        ImageLoader.bind(holder.cover, item.cover, R.drawable.bili_default_image_tv_with_bg, 86, 56);

        // ====== 点击：直接使用本次 getView 绑定的 item（避免 convertView 复用 + fid 相同导致跳错） ======
        final long clickedFid = item.fid;
        final String clickedName = item.name;
        final int pos = position;

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (context instanceof FavoriteFolderListActivity) {
                    // 优先用本次绑定的 item；若因 convertView 复用了旧监听再按 fid
                    FavoriteFolder target = item;
                    if (target == null || target.fid != clickedFid) {
                        target = null;
                        for (FavoriteFolder f : list) {
                            if (f.fid == clickedFid) {
                                target = f;
                                break;
                            }
                        }
                    }
                    System.out.println("Adapter点击: clickedFid=" + clickedFid + ", target=" + (target != null ? target.name : "null"));
                    if (target != null) {
                        ((FavoriteFolderListActivity) context).onFolderClick(target, pos);
                    } else {
                        // 如果找不到（理论上不会），用保存的名称和 fid 构造临时对象
                        FavoriteFolder fallback = new FavoriteFolder();
                        fallback.fid = clickedFid;
                        fallback.name = clickedName;
                        ((FavoriteFolderListActivity) context).onFolderClick(fallback, pos);
                    }
                }
            }
        });

        return convertView;
    }

    public void updateData(List<FavoriteFolder> newList) {
        if (newList == null) {
            this.list.clear();
            notifyDataSetChanged();
            return;
        }
        this.list.clear();
        this.list.addAll(newList);
        notifyDataSetChanged();
    }

    public void clearCache() {
        ImageLoader.clearCache();
    }

    static class ViewHolder {
        TextView name;
        TextView count;
        ImageView cover;
    }
}