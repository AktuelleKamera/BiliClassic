package tv.biliclassic;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import tv.biliclassic.util.SharedPreferencesUtil;

public class CommentAdapter extends BaseAdapter {

    private Context context;
    private List<CommentFragment.CommentItem> list;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private List<String> cacheKeys = new ArrayList<String>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();

    private boolean isScrolling = false;
    private static final int MAX_CACHE_SIZE = 20;

    // 点击监听接口
    public interface OnUserClickListener {
        void onUserClick(long mid, String userName);
    }
    private OnUserClickListener userClickListener;

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.userClickListener = listener;
    }

    private boolean isLowMemoryDevice() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return maxMemory < 16384;
    }

    private int getConfiguredThreadCount() {
        int savedThreads = SharedPreferencesUtil.getInt(SharedPreferencesUtil.IMAGE_LOAD_THREADS, 0);
        if (savedThreads > 0) {
            return savedThreads;
        }
        return isLowMemoryDevice() ? 1 : 2;
    }

    private void initExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        int threadCount = getConfiguredThreadCount();
        if (threadCount <= 1) {
            executor = Executors.newSingleThreadExecutor();
        } else {
            executor = new ThreadPoolExecutor(threadCount, threadCount, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>());
        }
    }

    public CommentAdapter(Context context, List<CommentFragment.CommentItem> list) {
        this.context = context;
        this.list = list;
        initExecutor();
    }

    public void setScrolling(boolean scrolling) {
        this.isScrolling = scrolling;
    }

    public void reloadExecutor() {
        initExecutor();
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
            convertView = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
            holder = new ViewHolder();
            holder.avatar = (ImageView) convertView.findViewById(R.id.avatar);
            holder.userName = (TextView) convertView.findViewById(R.id.user_name);
            holder.message = (TextView) convertView.findViewById(R.id.message);
            holder.likeCount = (TextView) convertView.findViewById(R.id.like_count);
            holder.time = (TextView) convertView.findViewById(R.id.time);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final CommentFragment.CommentItem item = list.get(position);
        holder.userName.setText(item.userName);
        holder.message.setText(item.message);
        holder.likeCount.setText(String.valueOf(item.likeCount));

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        holder.time.setText(sdf.format(new Date(item.time * 1000)));
        holder.avatar.setImageResource(R.drawable.bili_default_avatar);
        addAvatarBorder(holder.avatar);

        // ========== 点击头像或用户名进入用户主页 ==========
        final long mid = item.mid;
        final String userName = item.userName;

        View.OnClickListener userClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mid != 0) {
                    Intent intent = new Intent(context, UserProfileActivity.class);
                    intent.putExtra("mid", mid);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "无法获取用户信息", Toast.LENGTH_SHORT).show();
                }
            }
        };

        holder.avatar.setOnClickListener(userClickListener);
        holder.userName.setOnClickListener(userClickListener);

        if (isScrolling) {
            return convertView;
        }

        if (item.userAvatar != null && item.userAvatar.length() > 0) {
            final String avatarUrl = item.userAvatar;
            final ImageView avatarView = holder.avatar;
            final int currentPos = position;
            avatarView.setTag(avatarUrl);

            SoftReference<Bitmap> softBitmap = imageCache.get(avatarUrl);
            if (softBitmap != null) {
                Bitmap cachedBitmap = softBitmap.get();
                if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                    avatarView.setImageBitmap(cachedBitmap);
                    addAvatarBorder(avatarView);
                    return convertView;
                } else {
                    imageCache.remove(avatarUrl);
                    cacheKeys.remove(avatarUrl);
                }
            }

            Boolean isLoading = loadingMap.get(currentPos);
            if (isLoading != null && isLoading) {
                return convertView;
            }

            loadingMap.put(currentPos, true);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = downloadImage(avatarUrl);
                    loadingMap.remove(currentPos);

                    if (bitmap != null && !bitmap.isRecycled()) {
                        addToCache(avatarUrl, bitmap);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Object tag = avatarView.getTag();
                                if (tag != null && tag.equals(avatarUrl)) {
                                    avatarView.setImageBitmap(bitmap);
                                    addAvatarBorder(avatarView);
                                }
                            }
                        });
                    }
                }
            });
        }

        return convertView;
    }

    private void addToCache(String key, Bitmap bitmap) {
        if (cacheKeys.size() >= MAX_CACHE_SIZE) {
            String oldestKey = cacheKeys.remove(0);
            SoftReference<Bitmap> oldRef = imageCache.remove(oldestKey);
            if (oldRef != null) {
                Bitmap oldBmp = oldRef.get();
                if (oldBmp != null && !oldBmp.isRecycled()) {
                    oldBmp.recycle();
                }
            }
        }
        imageCache.put(key, new SoftReference<Bitmap>(bitmap));
        cacheKeys.add(key);
    }

    private void addAvatarBorder(ImageView imageView) {
        if (imageView == null) return;
        try {
            Drawable borderDrawable = context.getResources().getDrawable(R.drawable.image_border_overlay);
            imageView.setBackgroundDrawable(borderDrawable);
            int paddingPx = dpToPx(2);
            imageView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
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
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            int targetSize = 48;
            int scale = 1;
            if (options.outWidth > targetSize || options.outHeight > targetSize) {
                int widthRatio = options.outWidth / targetSize;
                int heightRatio = options.outHeight / targetSize;
                scale = Math.max(widthRatio, heightRatio);
                if (scale < 1) scale = 1;
                if (scale > 8) scale = 8;
            }

            conn.disconnect();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();
            is = conn.getInputStream();

            options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inPurgeable = true;
            options.inInputShareable = true;

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

    public void updateData(List<CommentFragment.CommentItem> newList) {
        this.list = newList;
        loadingMap.clear();
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
        cacheKeys.clear();
        loadingMap.clear();
    }

    static class ViewHolder {
        ImageView avatar;
        TextView userName;
        TextView message;
        TextView likeCount;
        TextView time;
    }
}