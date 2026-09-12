package tv.biliclassic.metro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import tv.biliclassic.FavoriteFolderListActivity;
import tv.biliclassic.FollowingListActivity;
import tv.biliclassic.HistoryActivity;
import tv.biliclassic.OfflineActivity;
import tv.biliclassic.R;
import tv.biliclassic.SettingsActivity;
import tv.biliclassic.api.LoginApi;
import tv.biliclassic.api.UserInfoApi;
import tv.biliclassic.model.UserInfo;
import tv.biliclassic.util.ImageLoader;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.NetWorkUtil;

/**
 * Metro 个人中心页（作为 MetroHome 详情页的 Fragment，配合转门翻入/翻出）。
 * 大字、透明 item、无顶栏；未登录时中间显示登录二维码。
 */
public class MetroProfileFragment extends Fragment implements MetroTurnPage {

    private long mid;

    private LinearLayout loginView;
    private ImageView qrImage;
    private TextView qrStatus;

    private LinearLayout userView;
    private ImageView ivAvatar;
    private TextView tvUserName;
    private TextView tvUserStat;
    private TextView tvUserSign;

    private boolean mNeedRefresh = false;
    private Timer timer;
    private boolean mIsDestroyed = false;
    private final android.os.Handler mHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private java.util.concurrent.ExecutorService mImageExecutor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.metro_profile, container, false);

        // 视图被销毁（onDestroyView）后会重新挂载，必须复位，否则异步回调里 if(mIsDestroyed) 直接返回 → 空白/加载不出
        mIsDestroyed = false;

        int loadThreads = tv.biliclassic.util.SdkHelper.getImageLoadThreads();
        if (loadThreads <= 1) {
            mImageExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        } else {
            mImageExecutor = java.util.concurrent.Executors.newFixedThreadPool(loadThreads);
        }

        loginView = (LinearLayout) root.findViewById(R.id.login_view);
        qrImage = (ImageView) root.findViewById(R.id.qr_image);
        qrStatus = (TextView) root.findViewById(R.id.qr_status);
        userView = (LinearLayout) root.findViewById(R.id.user_view);
        ivAvatar = (ImageView) root.findViewById(R.id.iv_avatar);
        tvUserName = (TextView) root.findViewById(R.id.tv_user_name);
        tvUserStat = (TextView) root.findViewById(R.id.tv_user_stat);
        tvUserSign = (TextView) root.findViewById(R.id.tv_user_sign);

        // 夜间模式：页面底色置黑，灰色文字换白色
        if (MetroTheme.isNight()) {
            root.setBackgroundColor(0xFF000000);
            tvUserStat.setTextColor(MetroTheme.grey());
            tvUserSign.setTextColor(MetroTheme.secondary());
        }

        bindNavRow(root, R.id.row_following, FollowingListActivity.class);
        bindNavRow(root, R.id.row_favorites, FavoriteFolderListActivity.class);
        bindNavRow(root, R.id.row_history, HistoryActivity.class);
        bindNavRow(root, R.id.row_offline, OfflineActivity.class);
        bindNavRow(root, R.id.row_settings, SettingsActivity.class);

        qrImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mNeedRefresh) {
                    mNeedRefresh = false;
                    refreshQr();
                }
            }
        });

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                checkLogin();
            }
        });

        return root;
    }

    private void bindNavRow(View root, int id, final Class<?> target) {
        View v = root.findViewById(id);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() != null) {
                        startActivity(new Intent(getActivity(), target));
                    }
                }
            });
            v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(hasFocus ? 0xFFFFFFFF : 0xFFD86DA5);
                        v.setBackgroundDrawable(hasFocus ? new android.graphics.drawable.GradientDrawable(
                                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                                new int[]{0xFFF2C6DE, 0xFFD86DA5}) : null);
                    }
                }
            });
        }
    }

    private void checkLogin() {
        mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid <= 0) {
            showLoginView();
        } else {
            showUserView();
        }
    }

    private void showLoginView() {
        if (userView != null) userView.setVisibility(View.GONE);
        if (loginView != null) loginView.setVisibility(View.VISIBLE);
        mNeedRefresh = false;
        refreshQr();
    }

    private void showUserView() {
        if (loginView != null) loginView.setVisibility(View.GONE);
        if (userView != null) userView.setVisibility(View.VISIBLE);
        cancelTimer();
        loadUser();
    }

    // ===== MetroTurnPage：转门翻入/翻出的内容错峰动画 =====

    @Override
    public void animateTurnIn() {
        if (getView() != null) stagger(true);
    }

    @Override
    public void animateTurnOut() {
        if (getView() != null) stagger(false);
    }

    private void stagger(boolean in) {
        if (userView == null || loginView == null) return;
        ViewGroup g = userView.getVisibility() == View.VISIBLE ? userView : loginView;
        if (g == null) return;
        int count = g.getChildCount();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int travel = Math.max(screenWidth / 4, 120);
        for (int i = 0; i < count; i++) {
            final View ch = g.getChildAt(i);
            final Animation a;
            // SDK_INT 字段是 API 4+，低版本取不到，统一用 SdkHelper
            if (tv.biliclassic.util.SdkHelper.getSdkInt() < 16) {
                a = new android.view.animation.AlphaAnimation(in ? 0f : 1f, in ? 1f : 0f);
                a.setDuration(280);
                a.setStartOffset(i * 70);
            } else {
                a = new TranslateAnimation(in ? travel : 0, in ? 0 : travel, 0, 0);
                a.setDuration(300);
                a.setStartOffset(i * 70);
                a.setInterpolator(new DecelerateInterpolator());
                if (!in) a.setFillAfter(true);
            }
            ch.startAnimation(a);
        }
    }

    // ===== 扫码登录 =====

    private void refreshQr() {
        if (qrImage == null) return;
        qrImage.setEnabled(false);
        setQrStatus("正在获取二维码…");
        // 内存吃紧（2.x native 像素堆）时先释放全局缓存引用，再生成，最大化可用空间
        if (tv.biliclassic.util.GlobalImageCache.isMemoryLow()) {
            tv.biliclassic.util.GlobalImageCache.getInstance().freeAllUnreferenced();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bmp = LoginApi.getLoginQR(getActivity());
                    if (mIsDestroyed) return;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mIsDestroyed || qrImage == null) return;
                            // 回收旧的二维码位图（像素在 native 堆，2.x 上不主动释放会堆积撑爆）
                            android.graphics.drawable.Drawable old = qrImage.getDrawable();
                            if (old instanceof android.graphics.drawable.BitmapDrawable) {
                                Bitmap ob = ((android.graphics.drawable.BitmapDrawable) old).getBitmap();
                                if (ob != null && !ob.isRecycled()) ob.recycle();
                            }
                            if (bmp != null) {
                                qrImage.setImageBitmap(bmp);
                                qrImage.setEnabled(true);
                                mNeedRefresh = true;
                                setQrStatus("请使用B站APP扫码登录\n点击二维码可刷新");
                                startDetect();
                            } else {
                                qrImage.setEnabled(true);
                                mNeedRefresh = true;
                                setQrStatus("生成二维码失败，点击重试");
                            }
                        }
                    });
                } catch (final Exception e) {
                    if (mIsDestroyed) return;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mIsDestroyed) return;
                            qrImage.setEnabled(true);
                            mNeedRefresh = true;
                            setQrStatus("获取二维码失败，点击重试");
                        }
                    });
                }
            }
        }).start();
    }

    private void startDetect() {
        cancelTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                pollLogin();
            }
        }, 2000, 2000);
    }

    private void pollLogin() {
        final String response;
        try {
            response = LoginApi.getLoginState();
            if (response == null || response.length() == 0) return;
        } catch (Exception e) {
            return;
        }
        int state = LoginApi.parseLoginState(response);
        if (state == 2) {
            cancelTimer();
            LoginApi.saveLoginInfo(response);
            final String crossUrl = parseCrossUrl(response);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // 只有登录成功响应里的 crossDomain 链接（含 DedeUserID 参数）才用于写 Cookie；
                    // 未扫码时 data.url 是二维码跳转链接，写进去会污染 Cookie
                    if (crossUrl != null && crossUrl.length() > 0
                            && crossUrl.contains("DedeUserID")) {
                        saveCookiesFromUrl(crossUrl);
                        try { NetWorkUtil.get(crossUrl); } catch (Exception ignored) {}
                    }
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mIsDestroyed) return;
                            setQrStatus("登录成功");
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!mIsDestroyed) checkLogin();
                                }
                            }, 300);
                        }
                    });
                }
            }).start();
        } else if (state == 0) {
            setQrStatus("请使用B站APP扫码登录");
        } else if (state == 1) {
            setQrStatus("已扫描，请在手机上点击确认登录");
        } else if (state == -1) {
            cancelTimer();
            mNeedRefresh = true;
            setQrStatus("二维码已过期，点击刷新");
        }
    }

    private String parseCrossUrl(String response) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(response);
            org.json.JSONObject data = json.getJSONObject("data");
            return data.optString("url", "");
        } catch (Exception e) {
            return "";
        }
    }

    private void saveCookiesFromUrl(String url) {
        Map<String, String> params = extractParams(url);
        if (params.isEmpty()) return;
        String dedeUserID = params.get("DedeUserID");
        String biliJct = params.get("bili_jct");
        if (dedeUserID != null && dedeUserID.length() > 0) {
            try { SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, Long.parseLong(dedeUserID)); }
            catch (NumberFormatException ignored) {}
        }
        if (biliJct != null && biliJct.length() > 0) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.csrf, biliJct);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            if ("gourl".equals(k) || "go_url".equals(k) || "url".equals(k)) continue;
            String v = e.getValue();
            if (v == null || v.length() == 0) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(k).append("=").append(v);
        }
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, sb.toString());
        NetWorkUtil.setCookieString(sb.toString());
        NetWorkUtil.refreshHeaders();
    }

    private Map<String, String> extractParams(String url) {
        Map<String, String> map = new java.util.HashMap<String, String>();
        try {
            String query = url;
            int q = query.indexOf('?');
            if (q < 0 || q + 1 >= query.length()) return map;
            query = query.substring(q + 1);
            String[] parts = query.split("&");
            for (String pair : parts) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = pair.substring(0, eq);
                    String val = pair.substring(eq + 1);
                    try { val = java.net.URLDecoder.decode(val, "UTF-8"); } catch (Exception ignored) {}
                    map.put(key, val);
                }
            }
        } catch (Exception e) {
        }
        return map;
    }

    private void setQrStatus(final String text) {
        if (mIsDestroyed) return;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mIsDestroyed || qrStatus == null) return;
                qrStatus.setText(text);
            }
        });
    }

    private void loadUser() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final UserInfo info = UserInfoApi.getUserInfo(mid);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mIsDestroyed || getView() == null) return;
                            bindUser(info);
                        }
                    });
                } catch (Exception e) {
                }
            }
        }).start();
    }

    private void bindUser(UserInfo info) {
        if (info == null) return;
        tvUserName.setText(info.name);
        tvUserStat.setText("粉丝 " + info.fans + " · 关注 " + info.following + " · LV" + info.level);
        tvUserSign.setText(info.sign != null && info.sign.length() > 0 ? info.sign : "这个人很懒，什么都没写");
        loadAvatar(ivAvatar, info.avatar);
    }

    /** 头像加载：先查 GlobalImageCache，未命中则抛到图片线程池下载（带 Accept-Encoding: identity）+ 缓存并回主线程设置。 */
    private void loadAvatar(final ImageView iv, final String url) {
        if (iv == null) return;
        if (url == null || url.length() == 0) {
            iv.setImageResource(R.drawable.bili_default_avatar);
            return;
        }
        final String finalUrl = url;
        iv.setTag(finalUrl);
        Bitmap cached = tv.biliclassic.util.GlobalImageCache.getInstance().get(finalUrl);
        if (cached != null && !cached.isRecycled()) {
            iv.setImageBitmap(cached);
            return;
        }
        if (mImageExecutor == null || mImageExecutor.isShutdown()) return;
        mImageExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = downloadAvatar(finalUrl);
                if (bmp == null || bmp.isRecycled()) return;
                tv.biliclassic.util.GlobalImageCache.getInstance().put(finalUrl, bmp);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mIsDestroyed || iv.getTag() == null || !finalUrl.equals(iv.getTag())) return;
                        iv.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private Bitmap downloadAvatar(String urlStr) {
        android.content.Context ctx = getActivity();
        if (ctx == null) return null;
        return ImageLoader.fetchBitmap(ctx, urlStr, 84, 84);
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public void onDestroyView() {
        mIsDestroyed = true;
        cancelTimer();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mIsDestroyed = true;
        cancelTimer();
        if (mImageExecutor != null) {
            mImageExecutor.shutdownNow();
            mImageExecutor = null;
        }
        super.onDestroy();
    }
}
