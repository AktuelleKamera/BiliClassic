package tv.biliclassic;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import tv.biliclassic.api.ReplyApi;
import tv.biliclassic.util.SharedPreferencesUtil;

public class ReplyListAdapter extends BaseAdapter {

    private Context context;
    private List<ReplyListActivity.ReplyData> list;
    private OnReplyClickListener replyClickListener;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private List<String> cacheKeys = new ArrayList<String>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();
    private boolean isScrolling = false;
    private static final int MAX_CACHE_SIZE = 80;

    private long mOid;
    private int mReplyType = 1;
    private long mMid;

    public void setOid(long oid) { mOid = oid; }
    public void setReplyType(int type) { mReplyType = type; }
    public void setMid(long mid) { mMid = mid; }

    public interface OnReplyClickListener {
        void onReplyClick(ReplyListActivity.ReplyData reply);
    }

    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.replyClickListener = listener;
    }

    public ReplyListAdapter(Context context, List<ReplyListActivity.ReplyData> list) {
        this.context = context;
        this.list = list;
        initExecutor();
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
            holder.time = (TextView) convertView.findViewById(R.id.time);
            holder.likeIcon = (ImageView) convertView.findViewById(R.id.like_icon);
            holder.likeCount = (TextView) convertView.findViewById(R.id.like_count);
            holder.replyButton = (TextView) convertView.findViewById(R.id.reply_button);
            holder.pictureContainer = (LinearLayout) convertView.findViewById(R.id.picture_container);
            convertView.setTag(holder);
            convertView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    final ViewHolder h = (ViewHolder) v.getTag();
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            v.setBackgroundColor(0x40D86DA5);
                            h.copyText = ((TextView) h.message).getText().toString();
                            h.longPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    final long itemMid = h.mid;
                                    final String text = h.copyText;
                                    if (itemMid == mMid && mMid != 0) {
                                        final long oid = h.oid;
                                        final long rpid = h.rpid;
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setItems(new String[]{"复制评论", "删除评论"}, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                if (which == 0) {
                                                    copyToClipboard(text);
                                                } else if (which == 1) {
                                                    deleteComment(oid, rpid);
                                                }
                                            }
                                        });
                                        builder.show();
                                    } else {
                                        copyToClipboard(text);
                                    }
                                }
                            };
                            mainHandler.postDelayed(h.longPressRunnable, ViewConfiguration.getLongPressTimeout());
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (h.longPressRunnable != null) {
                                mainHandler.removeCallbacks(h.longPressRunnable);
                            }
                            v.setBackgroundResource(R.drawable.item_click_effect_white);
                            break;
                    }
                    return false;
                }
            });
        } else {
            holder = (ViewHolder) convertView.getTag();
            convertView.setBackgroundResource(R.drawable.item_click_effect_white);
        }

        final ReplyListActivity.ReplyData rd = list.get(position);
        holder.copyText = rd.message;
        holder.userName.setText(rd.userName != null ? rd.userName : "用户");
        holder.message.setText(rd.message != null ? rd.message : "");
        holder.mid = rd.mid;
        holder.oid = mOid;
        holder.rpid = rd.rpid;

        if (rd.time > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
            holder.time.setText(sdf.format(new Date(rd.time * 1000)));
        } else {
            holder.time.setText("刚刚");
        }

        // 显示点赞数
        if (holder.likeCount != null) {
            holder.likeCount.setVisibility(View.VISIBLE);
            holder.likeCount.setText(String.valueOf(rd.likeCount));
        }
        final ViewHolder h2 = holder;
        if (h2.likeIcon != null) {
            if (rd.liked) {
                h2.likeCount.setTextColor(0xFFD86DA5);
                h2.likeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
            } else {
                h2.likeCount.setTextColor(0xFF999999);
                h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
            }
            h2.likeIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final boolean wasLiked = rd.liked;
                    rd.liked = !wasLiked;
                    if (rd.liked) {
                        rd.likeCount++;
                        h2.likeCount.setTextColor(0xFFD86DA5);
                        h2.likeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                    } else {
                        rd.likeCount--;
                        h2.likeCount.setTextColor(0xFF999999);
                        h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                    }
                    h2.likeCount.setText(String.valueOf(rd.likeCount));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int code;
                                if (rd.liked) {
                                    code = ReplyApi.likeComment(mOid, rd.rpid, mReplyType);
                                } else {
                                    code = ReplyApi.unlikeComment(mOid, rd.rpid, mReplyType);
                                }
                                if (code != 0) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            rd.liked = wasLiked;
                                            if (wasLiked) {
                                                rd.likeCount++;
                                                h2.likeCount.setTextColor(0xFFD86DA5);
                                                h2.likeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                                            } else {
                                                rd.likeCount--;
                                                h2.likeCount.setTextColor(0xFF999999);
                                                h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                                            }
                                            h2.likeCount.setText(String.valueOf(rd.likeCount));
                                            Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        rd.liked = wasLiked;
                                        if (wasLiked) {
                                            rd.likeCount++;
                                            h2.likeCount.setTextColor(0xFFD86DA5);
                                            h2.likeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                                        } else {
                                            rd.likeCount--;
                                            h2.likeCount.setTextColor(0xFF999999);
                                            h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                                        }
                                        h2.likeCount.setText(String.valueOf(rd.likeCount));
                                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }).start();
                }
            });
        }

        //显示回复按钮
        if (holder.replyButton != null) {
            holder.replyButton.setVisibility(View.VISIBLE);
            holder.replyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (replyClickListener != null) {
                        replyClickListener.onReplyClick(rd);
                    }
                }
            });
        }

        // 显示图片
        if (holder.pictureContainer != null) {
            if (rd.pictureList != null && rd.pictureList.size() > 0) {
                holder.pictureContainer.removeAllViews();
                holder.pictureContainer.setVisibility(View.VISIBLE);

                int maxShow = Math.min(rd.pictureList.size(), 3);
                for (int i = 0; i < maxShow; i++) {
                    final String imgUrl = rd.pictureList.get(i);
                    ImageView imgView = new ImageView(context);
                    int size = dpToPx(80);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.rightMargin = dpToPx(4);
                    imgView.setLayoutParams(lp);
                    imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
                    holder.pictureContainer.addView(imgView);
                    loadReplyImage(imgView, imgUrl);
                }

                if (rd.pictureList.size() > 3) {
                    TextView moreTv = new TextView(context);
                    moreTv.setText("+" + (rd.pictureList.size() - 3));
                    moreTv.setTextSize(14);
                    moreTv.setTextColor(0xFFFFFFFF);
                    moreTv.setGravity(Gravity.CENTER);
                    moreTv.setBackgroundColor(0x88000000);
                    int size = dpToPx(80);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.rightMargin = dpToPx(4);
                    moreTv.setLayoutParams(lp);
                    holder.pictureContainer.addView(moreTv);
                }
            } else {
                holder.pictureContainer.setVisibility(View.GONE);
            }
        }

        holder.avatar.setImageResource(R.drawable.bili_default_avatar);
        addAvatarBorder(holder.avatar);

        // 加载头像
        if (rd.avatar != null && rd.avatar.length() > 0) {
            String avatarUrl = rd.avatar;
            if (avatarUrl.startsWith("https://")) {
                avatarUrl = "http://" + avatarUrl.substring(8);
            }
            final String finalAvatarUrl = avatarUrl;
            final ImageView avatarView = holder.avatar;
            final int currentPos = position;
            avatarView.setTag(finalAvatarUrl);

            SoftReference<Bitmap> softRef = imageCache.get(finalAvatarUrl);
            if (softRef != null) {
                Bitmap cachedBitmap = softRef.get();
                if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                    avatarView.setImageBitmap(cachedBitmap);
                    addAvatarBorder(avatarView);
                    return convertView;
                } else {
                    imageCache.remove(finalAvatarUrl);
                    cacheKeys.remove(finalAvatarUrl);
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
                    final Bitmap bitmap = downloadImage(finalAvatarUrl);
                    loadingMap.remove(currentPos);

                    if (bitmap != null && !bitmap.isRecycled()) {
                        addToCache(finalAvatarUrl, bitmap);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Object tag = avatarView.getTag();
                                if (tag != null && tag.equals(finalAvatarUrl)) {
                                    avatarView.setImageBitmap(bitmap);
                                    addAvatarBorder(avatarView);
                                }
                            }
                        });
                    }
                }
            });
        }

        final long mid = rd.mid;
        if (mid != 0) {
            View.OnClickListener clickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, UserProfileActivity.class);
                    intent.putExtra("mid", mid);
                    context.startActivity(intent);
                }
            };
            holder.avatar.setOnClickListener(clickListener);
            holder.userName.setOnClickListener(clickListener);
        }

        return convertView;
    }

    private void loadReplyImage(final ImageView imageView, String urlStr) {
        if (urlStr == null || urlStr.length() == 0) return;
        final String finalUrl = urlStr;

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(finalUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
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
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
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

    private void addToCache(String key, Bitmap bitmap) {
        if (cacheKeys.size() >= MAX_CACHE_SIZE) {
            String oldestKey = cacheKeys.remove(0);
            SoftReference<Bitmap> oldRef = imageCache.remove(oldestKey);
            if (oldRef != null) {
                Bitmap old = oldRef.get();
                if (old != null && !old.isRecycled()) {
                    old.recycle();
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
        } catch (Exception e) {}
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

            int targetSize = dpToPx(48);
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

            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            return bitmap;
        } catch (OutOfMemoryError e) {
            System.gc();
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception e) {}
            }
        }
    }

    private void copyToClipboard(String text) {
        if (text != null && text.length() > 0) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(text);
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) vibrator.vibrate(50);
            } catch (Exception e) { e.printStackTrace(); }
            Toast.makeText(context, "已复制评论", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteComment(final long oid, final long rpid) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int code = ReplyApi.deleteComment(oid, rpid, mReplyType);
                    if (code == 0) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                int pos = -1;
                                for (int i = 0; i < list.size(); i++) {
                                    if (list.get(i).rpid == rpid) {
                                        pos = i;
                                        break;
                                    }
                                }
                                if (pos >= 0) {
                                    list.remove(pos);
                                    notifyDataSetChanged();
                                }
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    public void updateData(List<ReplyListActivity.ReplyData> newList) {
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
        cacheKeys.clear();
        loadingMap.clear();
    }

    static class ViewHolder {
        ImageView avatar;
        TextView userName;
        TextView message;
        TextView time;
        ImageView likeIcon;
        TextView likeCount;
        TextView replyButton;
        LinearLayout pictureContainer;
        String copyText;
        Runnable longPressRunnable;
        long mid;
        long oid;
        long rpid;
    }
}