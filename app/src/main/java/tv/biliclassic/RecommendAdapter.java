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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.biliclassic.model.VideoCard;

public class RecommendAdapter extends BaseAdapter {

    private Context context;
    private List<VideoCard> list;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();

    public RecommendAdapter(Context context, List<VideoCard> list) {
        this.context = context;
        this.list = list;
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
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_recommend, parent, false);
            holder = new ViewHolder();
            holder.cover = (ImageView) convertView.findViewById(R.id.cover);
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.view = (TextView) convertView.findViewById(R.id.view);
            holder.danmaku = (TextView) convertView.findViewById(R.id.danmaku);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        VideoCard item = list.get(position);
        if (item != null) {
            holder.title.setText(item.title);

            // 播放量
            String viewText = (item.view != null && item.view.length() > 0) ? item.view : "0";
            holder.view.setText(viewText);

            // 弹幕量
            String danmakuText = (item.danmaku > 0) ? String.valueOf(item.danmaku) : "0";
            holder.danmaku.setText(danmakuText);
        }

        // 设置占位图
        holder.cover.setImageResource(R.drawable.bili_default_image_tv_with_bg);

        if (item != null && item.cover != null && item.cover.length() > 0) {
            String coverUrl = item.cover;
            if (coverUrl.startsWith("https://")) {
                coverUrl = "http://" + coverUrl.substring(8);
            }
            final String finalUrl = coverUrl;
            final ImageView coverView = holder.cover;
            coverView.setTag(finalUrl);

            // 检查缓存
            SoftReference<Bitmap> softBitmap = imageCache.get(finalUrl);
            if (softBitmap != null) {
                Bitmap cached = softBitmap.get();
                if (cached != null && !cached.isRecycled()) {
                    coverView.setImageBitmap(cached);
                    return convertView;
                } else {
                    imageCache.remove(finalUrl);
                }
            }

            // 异步加载
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = downloadImage(finalUrl);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        imageCache.put(finalUrl, new SoftReference<Bitmap>(bitmap));
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Object tag = coverView.getTag();
                                if (tag != null && tag.equals(finalUrl)) {
                                    coverView.setImageBitmap(bitmap);
                                }
                            }
                        });
                    }
                }
            });
        }

        return convertView;
    }

    private Bitmap downloadImage(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            InputStream is = conn.getInputStream();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            return bitmap;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void updateData(List<VideoCard> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    public void clearCache() {
        for (SoftReference<Bitmap> ref : imageCache.values()) {
            Bitmap bmp = ref.get();
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        imageCache.clear();
    }

    static class ViewHolder {
        ImageView cover;
        TextView title;
        TextView view;
        TextView danmaku;
    }
}