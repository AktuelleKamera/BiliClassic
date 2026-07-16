package tv.biliclassic;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.ClipboardManager;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
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

public class CommentAdapter extends BaseAdapter {

    private Context context;
    private List<CommentFragment.CommentItem> list;
    private long mAid;
    private String mBvid;
    private CommentFragment mFragment;
    private ExecutorService executor;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();
    private List<String> cacheKeys = new ArrayList<String>();
    private Map<Integer, Boolean> loadingMap = new HashMap<Integer, Boolean>();

    private long mMid;
    private int mReplyType = 1;

    public void setMid(long mid) { mMid = mid; }
    public void setReplyType(int type) { mReplyType = type; }
    public void setBvid(String bvid) { mBvid = bvid; }

    private boolean isScrolling = false;
    private static final int MAX_CACHE_SIZE = 80;
    private float mDensity;

    public interface OnUserClickListener {
        void onUserClick(long mid, String userName);
    }
    private OnUserClickListener userClickListener;

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.userClickListener = listener;
    }

    public interface OnReplyClickListener {
        void onReplyClick(CommentFragment.CommentItem comment, CommentFragment.ReplyItem reply);
    }

    private OnReplyClickListener replyClickListener;

    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.replyClickListener = listener;
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

    public CommentAdapter(Context context, List<CommentFragment.CommentItem> list, long aid, CommentFragment fragment) {
        this.context = context;
        this.list = list;
        this.mAid = aid;
        this.mFragment = fragment;
        mDensity = context.getResources().getDisplayMetrics().density;
        initExecutor();
    }

    public void setScrolling(boolean scrolling) {
        this.isScrolling = scrolling;
        if (!scrolling) {
            notifyDataSetChanged();
        }
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
    public View getView(final int position, View convertView, final ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
            holder = new ViewHolder();
            holder.avatar = (ImageView) convertView.findViewById(R.id.avatar);
            holder.userNameView = (TextView) convertView.findViewById(R.id.user_name);
            holder.message = (TextView) convertView.findViewById(R.id.message);
            holder.likeIcon = (ImageView) convertView.findViewById(R.id.like_icon);
            holder.likeCount = (TextView) convertView.findViewById(R.id.like_count);
            holder.replyButton = (TextView) convertView.findViewById(R.id.reply_button);
            holder.time = (TextView) convertView.findViewById(R.id.time);
            holder.repliesContainer = (LinearLayout) convertView.findViewById(R.id.replies_container);
            holder.repliesText = (TextView) convertView.findViewById(R.id.replies_text);
            holder.repliesMore = (TextView) convertView.findViewById(R.id.replies_more);
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
                            h.downRpid = h.rpid;
                            Log.e("CommentClick", "DOWN rpid=" + h.rpid + " downRpid=" + h.downRpid);
                            h.longPressFired = false;
                            h.longPressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    h.longPressFired = true;
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

        final CommentFragment.CommentItem item = list.get(position);
        holder.copyText = item.message;
        holder.userNameView.setText(item.userName);
        String msgText = item.message != null ? item.message : "";
        if (item.isTop && msgText.startsWith("[置顶]")) {
            msgText = msgText.substring(4);
        }
        if (item.isTop) {
            SpannableString ss = new SpannableString("\u200B" + msgText);
            ss.setSpan(new BadgeSpan(mDensity), 0, 1,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.message.setText(ss);
            holder.message.setMovementMethod(null);
        } else {
            holder.message.setText(msgText);
        }
        holder.mid = item.mid;
        holder.oid = mAid;
        holder.rpid = item.rpid;
        Log.e("CommentClick", "bind pos=" + position + " rpid=" + item.rpid + " msg=" + (item.message != null ? item.message.substring(0, Math.min(20, item.message.length())) : "null"));

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ViewHolder h = (ViewHolder) v.getTag();
                long clickedRpid = h.downRpid;
                Log.e("CommentClick", "click downRpid=" + clickedRpid);
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).rpid == clickedRpid) {
                        CommentFragment.CommentItem ci = list.get(i);
                        Intent intent = new Intent(v.getContext(), ReplyListActivity.class);
                        intent.putExtra("aid", mAid);
                        if (mBvid != null) intent.putExtra("bvid", mBvid);
                        intent.putExtra("rpid", ci.rpid);
                        if (ci.userName != null) intent.putExtra("root_user_name", ci.userName);
                        if (ci.message != null) intent.putExtra("root_comment_message", ci.message);
                        intent.putExtra("root_mid", ci.mid);
                        intent.putExtra("root_time", ci.time);
                        if (ci.userAvatar != null) intent.putExtra("root_avatar", ci.userAvatar);
                        if (ci.pictureList != null && ci.pictureList.size() > 0) {
                            intent.putExtra("root_pictures", new ArrayList<String>(ci.pictureList));
                        }
                        intent.putExtra("total_count", ci.replyCount);
                        intent.putExtra("root_like_count", ci.likeCount);
                        intent.putExtra("root_liked", ci.liked);
                        v.getContext().startActivity(intent);
                        break;
                    }
                }
            }
        });

        holder.likeCount.setText(String.valueOf(item.likeCount));
        final ViewHolder h2 = holder;
        if (h2.likeIcon != null) {
            if (item.liked) {
                h2.likeCount.setTextColor(0xFFD86DA5);
                h2.likeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
            } else {
                h2.likeCount.setTextColor(0xFF999999);
                h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
            }
            h2.likeIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final int pos = list.indexOf(item);
                    if (pos < 0) return;
                    final boolean wasLiked = item.liked;
                    item.liked = !wasLiked;
                    if (item.liked) {
                        item.likeCount++;
                        h2.likeCount.setTextColor(0xFFD86DA5);
                        h2.likeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                    } else {
                        item.likeCount--;
                        h2.likeCount.setTextColor(0xFF999999);
                        h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                    }
                    h2.likeCount.setText(String.valueOf(item.likeCount));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int code;
                                if (item.liked) {
                                    code = ReplyApi.likeComment(mAid, item.rpid, mReplyType);
                                } else {
                                    code = ReplyApi.unlikeComment(mAid, item.rpid, mReplyType);
                                }
                                if (code != 0) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            item.liked = wasLiked;
                                            if (wasLiked) {
                                                item.likeCount++;
                                                h2.likeCount.setTextColor(0xFFD86DA5);
                                                h2.likeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                                            } else {
                                                item.likeCount--;
                                                h2.likeCount.setTextColor(0xFF999999);
                                                h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                                            }
                                            h2.likeCount.setText(String.valueOf(item.likeCount));
                                            Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        item.liked = wasLiked;
                                        if (wasLiked) {
                                            item.likeCount++;
                                            h2.likeCount.setTextColor(0xFFD86DA5);
                                            h2.likeIcon.setColorFilter(0xFFD86DA5, android.graphics.PorterDuff.Mode.SRC_ATOP);
                                        } else {
                                            item.likeCount--;
                                            h2.likeCount.setTextColor(0xFF999999);
                                            h2.likeIcon.setColorFilter((android.graphics.ColorFilter) null);
                                        }
                                        h2.likeCount.setText(String.valueOf(item.likeCount));
                                        Toast.makeText(context, "网络错误", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }).start();
                }
            });
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        holder.time.setText(sdf.format(new Date(item.time * 1000)));
        holder.avatar.setImageResource(R.drawable.bili_default_avatar);
        addAvatarBorder(holder.avatar);

        // 显示图片
        if (holder.pictureContainer != null) {
            if (item.pictureList != null && item.pictureList.size() > 0) {
                holder.pictureContainer.removeAllViews();
                holder.pictureContainer.setVisibility(View.VISIBLE);

                // 限制最多显示3张
                int maxShow = Math.min(item.pictureList.size(), 3);
                for (int i = 0; i < maxShow; i++) {
                    final String imgUrl = item.pictureList.get(i);
                    final int clickIndex = i;
                    ImageView imgView = new ImageView(context);
                    int size = dpToPx(80);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.rightMargin = dpToPx(4);
                    imgView.setLayoutParams(lp);
                    imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgView.setImageResource(R.drawable.bili_default_image_tv_with_bg);
                    holder.pictureContainer.addView(imgView);
                    loadCommentImage(imgView, imgUrl);
                    imgView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(context, ImageViewerActivity.class);
                            intent.putStringArrayListExtra("imageList", new ArrayList<String>(item.pictureList));
                            intent.putExtra("index", clickIndex);
                            context.startActivity(intent);
                        }
                    });
                }

                if (item.pictureList.size() > 3) {
                    TextView moreTv = new TextView(context);
                    moreTv.setText("+" + (item.pictureList.size() - 3));
                    moreTv.setTextSize(14);
                    moreTv.setTextColor(0xFFFFFFFF);
                    moreTv.setGravity(Gravity.CENTER);
                    moreTv.setBackgroundColor(0x88000000);
                    int size = dpToPx(80);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.rightMargin = dpToPx(4);
                    moreTv.setLayoutParams(lp);
                    holder.pictureContainer.addView(moreTv);
                    moreTv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(context, ImageViewerActivity.class);
                            intent.putStringArrayListExtra("imageList", new ArrayList<String>(item.pictureList));
                            intent.putExtra("index", 3);
                            context.startActivity(intent);
                        }
                    });
                }
            } else {
                holder.pictureContainer.setVisibility(View.GONE);
            }
        }

        List<CommentFragment.ReplyItem> replies = item.replies;
        int totalReplyCount = item.replyCount;

        if (replies != null && replies.size() > 0) {
            holder.repliesContainer.setVisibility(View.VISIBLE);
            holder.repliesText.setVisibility(View.VISIBLE);

            int showCount = Math.min(replies.size(), 2);
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            for (int i = 0; i < showCount; i++) {
                CommentFragment.ReplyItem ri = replies.get(i);
                if (i > 0) ssb.append("\n");
                int start = ssb.length();
                String uname = ri.userName != null ? ri.userName : "";
                ssb.append(uname);
                ssb.append(": ");
                final long mid = ri.mid;
                final CommentFragment.ReplyItem finalRi = ri;
                ssb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        if (mid != 0) {
                            Intent intent = new Intent(context, UserProfileActivity.class);
                            intent.putExtra("mid", mid);
                            context.startActivity(intent);
                        } else {
                            Toast.makeText(context, "无法获取用户信息", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void updateDrawState(TextPaint ds) {
                        ds.setUnderlineText(false);
                    }
                }, start, start + uname.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int messageStart = ssb.length();
                String replyText = ri.message != null ? ri.message : "";
                ssb.append(replyText);
                if (replyClickListener != null && replyText.length() > 0) {
                    ssb.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            replyClickListener.onReplyClick(item, finalRi);
                        }
                        @Override
                        public void updateDrawState(TextPaint ds) {
                            ds.setUnderlineText(false);
                        }
                    }, messageStart, messageStart + replyText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            holder.repliesText.setText(ssb);
            holder.repliesText.setMovementMethod(LinkMovementMethod.getInstance());

            if (totalReplyCount > 2) {
                holder.repliesMore.setVisibility(View.VISIBLE);
                holder.repliesMore.setText("共" + totalReplyCount + "条回复，点击查看");
                final CommentFragment.CommentItem finalItem = item;
                final int finalTotalReplyCount = totalReplyCount;
                holder.repliesMore.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mFragment != null) {
                            mFragment.showAllReplies(finalItem);
                        } else {
                            openReplyList(finalItem, finalTotalReplyCount);
                        }
                    }
                });
            } else {
                holder.repliesMore.setVisibility(View.GONE);
            }
        } else if (totalReplyCount > 0) {
            holder.repliesContainer.setVisibility(View.VISIBLE);
            holder.repliesText.setVisibility(View.GONE);
            holder.repliesMore.setVisibility(View.VISIBLE);
            holder.repliesMore.setText("共" + totalReplyCount + "条回复，点击查看");
            final CommentFragment.CommentItem finalItem = item;
            final int finalTotalReplyCount = totalReplyCount;
            holder.repliesMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mFragment != null) {
                        mFragment.showAllReplies(finalItem);
                    } else {
                        openReplyList(finalItem, finalTotalReplyCount);
                    }
                }
            });
        } else {
            holder.repliesContainer.setVisibility(View.GONE);
            holder.repliesText.setVisibility(View.GONE);
            if (holder.repliesMore != null) holder.repliesMore.setVisibility(View.GONE);
        }

        if (holder.replyButton != null) {
            holder.replyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (replyClickListener != null) {
                        replyClickListener.onReplyClick(item, null);
                    }
                }
            });
        }

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
        holder.userNameView.setOnClickListener(userClickListener);

        if (item.userAvatar != null && item.userAvatar.length() > 0) {
            String avatarUrl = item.userAvatar;
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

            if (isScrolling) {
                return convertView;
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

        return convertView;
    }

    private void loadCommentImage(final ImageView imageView, String urlStr) {
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

                    //计算采样率
                    int targetSize = dpToPx(80);
                    int scale = 1;
                    if (opts.outWidth > targetSize || opts.outHeight > targetSize) {
                        int widthRatio = opts.outWidth / targetSize;
                        int heightRatio = opts.outHeight / targetSize;
                        scale = Math.max(widthRatio, heightRatio);
                        if (scale < 1) scale = 1;
                        if (scale > 8) scale = 8;
                    }

                    // 重新连接并解码
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

    private void openReplyList(CommentFragment.CommentItem item, int totalCount) {
        Intent intent = new Intent(context, ReplyListActivity.class);
        intent.putExtra("aid", mAid);
        if (mBvid != null) intent.putExtra("bvid", mBvid);
        intent.putExtra("rpid", item.rpid);
        if (item.userName != null) intent.putExtra("root_user_name", item.userName);
        if (item.message != null) intent.putExtra("root_comment_message", item.message);
        intent.putExtra("root_mid", item.mid);
        intent.putExtra("root_time", item.time);
        if (item.userAvatar != null) intent.putExtra("root_avatar", item.userAvatar);
        if (item.pictureList != null && item.pictureList.size() > 0) {
            intent.putExtra("root_pictures", new ArrayList<String>(item.pictureList));
        }
        intent.putExtra("total_count", totalCount);
        intent.putExtra("root_like_count", item.likeCount);
        intent.putExtra("root_liked", item.liked);
        intent.putExtra("root_is_top", item.isTop);
        context.startActivity(intent);
    }

    static class ViewHolder {
        ImageView avatar;
        TextView userNameView;
        TextView message;
        ImageView likeIcon;
        TextView likeCount;
        TextView replyButton;
        TextView time;
        LinearLayout repliesContainer;
        TextView repliesText;
        TextView repliesMore;
        LinearLayout pictureContainer;
        String copyText;
        Runnable longPressRunnable;
        boolean longPressFired;
        long mid;
        long oid;
        long rpid;
        long downRpid;
    }

    private static class BadgeSpan extends android.text.style.ReplacementSpan {
        private int mWidth;
        private int mPaddingPx;
        private int mGapPx;
        private int mCornerPx;
        private float mStrokePx;
        private int mTextMarginPx;  // 标签和文字之间的间距
        private static final String TEXT = "置顶";

        BadgeSpan(float density) {
            mPaddingPx = (int)(4 * density + 0.5f);
            mGapPx = (int)(1 * density + 0.5f);
            mCornerPx = (int)(2 * density + 0.5f);
            mStrokePx = 1 * density + 0.5f;
            mTextMarginPx = (int)(1 * density + 0.5f);  // 标签右边和评论文字的间距
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            float textWidth = paint.measureText(TEXT);
            // 总宽度 = 标签宽度 + 左边距 + 标签和文字间距
            mWidth = (int)(textWidth + mPaddingPx * 2 + mStrokePx * 2 + mGapPx + mTextMarginPx);
            if (fm != null) {
                android.graphics.Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
                fm.ascent = pfm.ascent - mPaddingPx;
                fm.descent = pfm.descent + mPaddingPx;
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return mWidth;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            int origColor = paint.getColor();
            android.graphics.Paint.Style origStyle = paint.getStyle();
            float origStrokeWidth = paint.getStrokeWidth();
            boolean origAntiAlias = paint.isAntiAlias();

            android.graphics.Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            float half = mStrokePx / 2f;
            float rectTop = y + fm.ascent - mPaddingPx + half;
            float rectBottom = y + fm.descent + mPaddingPx - half;

            float textWidth = paint.measureText(TEXT);
            float rectWidth = textWidth + mPaddingPx * 2;

            // mGapPx 作为左边距偏移（只在 draw 里用）
            float offsetX = mGapPx;

            android.graphics.RectF rect = new android.graphics.RectF(
                    x + offsetX,
                    rectTop,
                    x + offsetX + rectWidth,
                    rectBottom
            );

            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(mStrokePx);
            paint.setColor(0xFFD86DA5);
            paint.setAntiAlias(true);
            canvas.drawRoundRect(rect, mCornerPx, mCornerPx, paint);

            paint.setColor(0xFFD86DA5);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            float centerY = (rectTop + rectBottom) / 2 - (fm.ascent + fm.descent) / 2;
            canvas.drawText(TEXT, x + offsetX + mPaddingPx, centerY, paint);

            paint.setColor(origColor);
            paint.setStyle(origStyle);
            paint.setStrokeWidth(origStrokeWidth);
            paint.setAntiAlias(origAntiAlias);
        }
    }
}