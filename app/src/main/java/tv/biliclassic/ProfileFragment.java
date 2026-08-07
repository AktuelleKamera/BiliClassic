package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.support.v4.app.Fragment;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.KeyBindingUtil;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.PermissionUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateUtil;
import tv.biliclassic.util.DialogUtil;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private static final String AVATAR_FILE_NAME = "avatar_cache.jpg";

    // list 选项图标 Drawable 静态缓存（2.x 上每次 inflate 都重新解码资源图，缓存后只解码一次）
    private static final Map<Integer, android.graphics.drawable.Drawable> sIconCache =
            new HashMap<Integer, android.graphics.drawable.Drawable>();

    private android.graphics.drawable.Drawable getCachedIcon(int resId) {
        android.graphics.drawable.Drawable d = sIconCache.get(resId);
        if (d == null) {
            try {
                d = getResources().getDrawable(resId);
                sIconCache.put(resId, d);
            } catch (Throwable t) {
                d = null;
            }
        }
        return d;
    }

    private void applyCachedIcon(View item, int resId) {
        if (item instanceof ViewGroup) {
            View child = ((ViewGroup) item).getChildAt(0);
            if (child instanceof ImageView) {
                android.graphics.drawable.Drawable d = getCachedIcon(resId);
                if (d != null) {
                    ((ImageView) child).setImageDrawable(d);
                }
            }
        }
    }

    // inflate 失败（Android 2.x 外部堆不足）时为 true：跳过控件初始化，仅显示空白页
    private boolean mInflateFailed = false;

    private TextView tvUserId;
    private TextView tvUid;
    private TextView tvCoin;
    private TextView tvVipBadge;
    private Button btnLogout;
    private Button btnSwitchAccount;
    private Button btnLogin;
    private ImageView ivAvatar;
    private GlobalImageCache imageCache = GlobalImageCache.getInstance();

    private View itemFavorites;
    private View itemHistory;
    private View itemOffline;
    private View itemSettings;
    private View itemRefresh;

    // 遥控器按键导航：可聚焦条目集合 + 当前选中下标
    private final ArrayList<View> mKeyNavItems = new ArrayList<View>();
    private int mKeyNavIndex = -1;
    // 高亮覆盖前的原始背景（仅按键导航实际高亮过才记录，避免改动普通界面外观）
    private final java.util.Map<View, android.graphics.drawable.Drawable> mNavOriginalBg =
            new HashMap<View, android.graphics.drawable.Drawable>();

    private ExecutorService executor = createImageExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 统一走 SdkHelper：优先用户设置，未设置再按设备内存给默认值，不写死
    private static ExecutorService createImageExecutor() {
        int threads = tv.biliclassic.util.SdkHelper.getImageLoadThreads();
        if (threads <= 1) {
            return Executors.newSingleThreadExecutor();
        }
        return Executors.newFixedThreadPool(threads);
    }

    private long currentMid = 0;
    private int currentCoinValue = 0;
    private boolean isVip = false;

    // 当前版本信息
    private int currentVersionCode = -1;
    private String currentVersionName = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Android 2.x 外部堆小，inflate 布局可能 OOM，失败清缓存重试，仍失败返回空布局不崩溃
        // 不显式 System.gc()
        try {
            GlobalImageCache.getInstance().releaseMemory();
        } catch (Throwable t) {
        }

        View view = null;
        for (int attempt = 0; attempt < 2 && view == null; attempt++) {
            try {
                view = inflater.inflate(R.layout.content_profile, container, false);
            } catch (OutOfMemoryError e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (android.view.InflateException e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            } catch (Throwable e) {
                GlobalImageCache.getInstance().releaseMemory();
                System.gc();
            }
        }
        if (view == null) {
            // inflate 失败（外部堆不足）：置标记并返回空布局，跳过所有控件初始化，避免 NPE
            mInflateFailed = true;
            view = new LinearLayout(getActivity());
            view.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return view;
        }
        mInflateFailed = false;

        // 绘制缓存（仅 32MB+ 堆设备）：滑页转场命中缓存，避免每帧重绘全部内容
        if (tv.biliclassic.util.SdkHelper.isHighMemoryDevice()) {
            view.setDrawingCacheEnabled(true);
            view.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_AUTO);
        }

        // 获取当前版本信息
        try {
            currentVersionCode = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionCode;
            currentVersionName = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
        } catch (Exception e) {
            currentVersionCode = 0;
            currentVersionName = "0.0.0";
        }

        tvUserId = (TextView) view.findViewById(R.id.tv_user_id);
        tvUid = (TextView) view.findViewById(R.id.tv_uid);
        tvCoin = (TextView) view.findViewById(R.id.tv_coin);
        tvVipBadge = (TextView) view.findViewById(R.id.tv_vip_badge);
        btnLogout = (Button) view.findViewById(R.id.btn_logout);
        btnSwitchAccount = (Button) view.findViewById(R.id.btn_switch_account);
        btnLogin = (Button) view.findViewById(R.id.btn_login);
        ivAvatar = (ImageView) view.findViewById(R.id.iv_avatar);

        itemFavorites = view.findViewById(R.id.item_favorites);
        itemHistory = view.findViewById(R.id.item_history);
        itemOffline = view.findViewById(R.id.item_offline);
        itemSettings = view.findViewById(R.id.item_settings);
        itemRefresh = view.findViewById(R.id.item_refresh);

        // list 选项图标用静态缓存 Drawable（避免每次重建重新解码资源图）
        applyCachedIcon(itemRefresh, R.drawable.ic_action_refresh);
        applyCachedIcon(itemFavorites, R.drawable.ic_action_collections_collection);
        applyCachedIcon(itemHistory, R.drawable.ic_action_device_access_data_usage);
        applyCachedIcon(itemOffline, R.drawable.ic_action_download_manager);
        applyCachedIcon(itemSettings, R.drawable.ic_action_settings);

        // 点击头像或名字进入个人主页
        View.OnClickListener profileClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLoggedIn()) {
                    long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
                    if (mid != 0) {
                        Intent intent = new Intent(getActivity(), UserProfileActivity.class);
                        intent.putExtra("mid", mid);
                        startActivity(intent);
                    } else {
                        Toast.makeText(getActivity(), getActivity().getString(R.string.profilefragment_toast_83b7), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.profilefragment_toast_8bf7), Toast.LENGTH_SHORT).show();
                }
            }
        };

        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(profileClickListener);
        }
        if (tvUserId != null) {
            tvUserId.setOnClickListener(profileClickListener);
        }

        // 检查更新按钮
        if (itemRefresh != null) {
            itemRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkForUpdate();
                }
            });
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.putExtra("login", true);
                startActivity(intent);
            }
        });

        btnSwitchAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.putExtra("login", true);
                startActivity(intent);
            }
        });

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogoutDialog();
            }
        });

        itemFavorites.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isLoggedIn()) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.profilefragment_toast_8bf7), Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(getActivity(), FavoriteFolderListActivity.class);
                startActivity(intent);
            }
        });

        itemHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), HistoryActivity.class);
                startActivity(intent);
            }
        });

        itemOffline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), OfflineActivity.class);
                startActivity(intent);
            }
        });

        if (itemSettings != null) {
            itemSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getActivity(), SettingsActivity.class);
                    startActivity(intent);
                }
            });
        }

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mInflateFailed) return;
        updateLoginStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mInflateFailed) return;
        updateLoginStatus();
        // 回到页面时重建按键导航（登录态切换会改变可见条目）。
        // 不在此处应用高亮：避免普通（触摸）界面被无端改变外观。
        buildKeyNavItems();
        if (mKeyNavIndex < 0 && mKeyNavItems.size() > 0) {
            mKeyNavIndex = 0;
        }
    }

    /**
     * 收集个人中心所有可见可交互条目（登录按钮/头像+切号+退出/功能列表），
     * 供遥控器方向键/确认键导航。条目可见性随登录状态变化，故每次重建。
     */
    private void buildKeyNavItems() {
        // 先恢复所有被按键导航高亮覆盖过的条目背景（如登录按钮选中后切 Tab 再回来，
        // 之前的高亮色会残留；这里在重建列表时统一恢复原始背景）。
        if (mNavOriginalBg.size() > 0) {
            for (java.util.Map.Entry<View, android.graphics.drawable.Drawable> e
                    : mNavOriginalBg.entrySet()) {
                View v = e.getKey();
                if (v != null && e.getValue() != null) {
                    v.setBackgroundDrawable(e.getValue());
                }
            }
            mNavOriginalBg.clear();
        }
        // 按钮类确定性恢复布局背景色（不依赖保存的 drawable）
        if (btnLogin != null) btnLogin.setBackgroundColor(0xFFD86DA5);
        if (btnSwitchAccount != null) btnSwitchAccount.setBackgroundColor(0xFFDDDDDD);
        if (btnLogout != null) btnLogout.setBackgroundColor(0xFFDDDDDD);
        // 功能列表项确定性恢复原 selector 背景，确保高亮色不残留
        restoreFeatureItemBg(itemRefresh);
        restoreFeatureItemBg(itemFavorites);
        restoreFeatureItemBg(itemHistory);
        restoreFeatureItemBg(itemOffline);
        restoreFeatureItemBg(itemSettings);
        mKeyNavItems.clear();
        if (mInflateFailed || getView() == null) return;
        View loginContainer = getView().findViewById(R.id.login_container);
        if (loginContainer != null && loginContainer.getVisibility() == View.VISIBLE) {
            if (btnLogin != null && btnLogin.getVisibility() == View.VISIBLE) {
                mKeyNavItems.add(btnLogin);
            }
        }
        View userCard = getView().findViewById(R.id.user_card);
        if (userCard != null && userCard.getVisibility() == View.VISIBLE) {
            if (ivAvatar != null && ivAvatar.getVisibility() == View.VISIBLE) {
                mKeyNavItems.add(ivAvatar);
            }
            if (btnSwitchAccount != null && btnSwitchAccount.getVisibility() == View.VISIBLE) {
                mKeyNavItems.add(btnSwitchAccount);
            }
            if (btnLogout != null && btnLogout.getVisibility() == View.VISIBLE) {
                mKeyNavItems.add(btnLogout);
            }
        }
        addNavItem(itemRefresh);
        addNavItem(itemFavorites);
        addNavItem(itemHistory);
        addNavItem(itemOffline);
        addNavItem(itemSettings);
    }

    private void addNavItem(View v) {
        if (v != null && v.getVisibility() == View.VISIBLE) {
            mKeyNavItems.add(v);
        }
    }

    /** 功能列表项恢复原始点击效果背景（透明 selector），清除可能的残留高亮色。 */
    private void restoreFeatureItemBg(View v) {
        if (v == null) return;
        try {
            v.setBackgroundDrawable(v.getResources().getDrawable(R.drawable.item_click_effect));
        } catch (Exception e) {
            v.setBackgroundColor(0xFFFFFFFF);
        }
    }

    /**
     * 刷新按键导航高亮：选中条目叠粉色背景，其余恢复原始背景。
     * 仅按键导航激活时调用（首次方向键/确认键按下后），
     * 且只在第一次覆盖前保存原始背景，之后始终恢复原样，不影响触摸界面。
     */
    private void applyKeyNavHighlight() {
        for (int i = 0; i < mKeyNavItems.size(); i++) {
            View v = mKeyNavItems.get(i);
            if (v == null) {
                continue;
            }
            if (i == mKeyNavIndex) {
                // 选中：登录按钮本身是粉色 #D86DA5，选中时用深粉强调；其他条目用浅粉半透明
                if (v.getId() == R.id.btn_login) {
                    v.setBackgroundColor(0xFFC06090);
                } else {
                    v.setBackgroundColor(0x66D86DA5);
                }
            } else {
                // 未选中：确定性恢复布局背景色（不依赖 getBackground 保存，
                // 避免 Android 2.x Button 背景为 null、或已保存高亮色导致无法恢复）
                if (v.getId() == R.id.btn_login) {
                    v.setBackgroundColor(0xFFD86DA5);
                } else if (v.getId() == R.id.btn_switch_account
                        || v.getId() == R.id.btn_logout) {
                    v.setBackgroundColor(0xFFDDDDDD);
                } else {
                    // 功能列表项：直接恢复原始 item_click_effect 背景
                    restoreFeatureItemBg(v);
                }
            }
        }
    }

    /**
     * 移动按键导航光标（方向：-1 上，+1 下）并滚动到可见。
     */
    private void moveKeyNav(int direction) {
        if (mKeyNavItems.size() == 0) {
            return;
        }
        int next = mKeyNavIndex + direction;
        if (next < 0) {
            next = 0;
        } else if (next >= mKeyNavItems.size()) {
            next = mKeyNavItems.size() - 1;
        }
        if (next != mKeyNavIndex) {
            mKeyNavIndex = next;
            applyKeyNavHighlight();
            scrollKeyNavToVisible(mKeyNavItems.get(mKeyNavIndex));
        }
    }

    /** 滚动 ScrollView 让选中条目完整可见（用绝对位置，条目可能嵌套多层）。 */
    private void scrollKeyNavToVisible(View item) {
        View root = getView();
        if (!(root instanceof android.widget.ScrollView) || item == null) {
            return;
        }
        android.widget.ScrollView scrollView = (android.widget.ScrollView) root;
        // 从条目向上累加各层 getTop()，得到相对 ScrollView 内容的绝对位置
        int top = 0;
        View p = item;
        while (p != null && p != scrollView) {
            top += p.getTop();
            p = (View) p.getParent();
        }
        int bottom = top + item.getHeight();
        int scrollY = scrollView.getScrollY();
        int height = scrollView.getHeight();
        if (top < scrollY) {
            scrollView.smoothScrollTo(0, Math.max(0, top));
        } else if (bottom > scrollY + height) {
            scrollView.smoothScrollTo(0, bottom - height);
        }
    }

    /**
     * 供 MainActivity.dispatchKeyEvent 调用：
     * 方向键上下移动光标，确认键触发选中条目点击。
     */
    public boolean handleRemoteKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int action = KeyBindingUtil.classify(event.getKeyCode());
        if (action != KeyBindingUtil.ACTION_UP
                && action != KeyBindingUtil.ACTION_DOWN
                && action != KeyBindingUtil.ACTION_CONFIRM) {
            return false;
        }
        if (mKeyNavItems.size() == 0) {
            buildKeyNavItems();
        }
        if (mKeyNavItems.size() == 0) {
            return false;
        }
        if (mKeyNavIndex < 0 || mKeyNavIndex >= mKeyNavItems.size()) {
            mKeyNavIndex = 0;
        }
        if (event.getRepeatCount() == 0) {
            if (action == KeyBindingUtil.ACTION_UP) {
                moveKeyNav(-1);
            } else if (action == KeyBindingUtil.ACTION_DOWN) {
                moveKeyNav(1);
            } else if (action == KeyBindingUtil.ACTION_CONFIRM) {
                // 首次确认时先应用高亮（若此前从未按键），保证选中态可见
                applyKeyNavHighlight();
                View v = mKeyNavItems.get(mKeyNavIndex);
                if (v != null) {
                    v.performClick();
                }
                return true;
            }
        }
        return true;
    }

    // 检查更新（使用 UpdateUtil）
    private void checkForUpdate() {
        Toast.makeText(getActivity(), getActivity().getString(R.string.profilefragment_toast_6b63), Toast.LENGTH_SHORT).show();

        UpdateUtil.checkUpdate(getActivity(), currentVersionCode, currentVersionName,
                new UpdateUtil.UpdateCallback() {
                    @Override
                    public void onCheckStart() {
                        // UI 已经在调用前设置了
                    }

                    @Override
                    public void onCheckComplete(boolean hasUpdate, String message) {
                        if (!hasUpdate) {
                            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCheckFailed(String error) {
                        Toast.makeText(getActivity(), error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 原 ProfileFragment 方法
    private boolean isLoggedIn() {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        return mid != 0 && cookies != null && cookies.length() > 0;
    }

    private void updateLoginStatus() {
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        final String uname = SharedPreferencesUtil.getString("uname", "");
        String cookies = SharedPreferencesUtil.getString("cookies", "");

        View userCard = getView() != null ? getView().findViewById(R.id.user_card) : null;
        View loginContainer = getView() != null ? getView().findViewById(R.id.login_container) : null;

        boolean isLoggedIn = (mid != 0 && cookies != null && cookies.length() > 0);

        if (isLoggedIn) {
            if (userCard != null) {
                userCard.setVisibility(View.VISIBLE);
            }
            if (loginContainer != null) {
                loginContainer.setVisibility(View.GONE);
            }

            if (uname != null && uname.length() > 0) {
                mainHandler.post(new Runnable() {
                    public void run() {
                        if (isAdded() && tvUserId != null) tvUserId.setText(uname);
                    }
                });
            } else {
                mainHandler.post(new Runnable() {
                    public void run() {
                        if (isAdded() && tvUserId != null) tvUserId.setText(getString(R.string.profilefragment_settext_7528));
                    }
                });
            }
            mainHandler.post(new Runnable() {
                public void run() {
                    if (isAdded() && tvUid != null) tvUid.setText("UID: " + mid);
                }
            });

            tvCoin.setText(getString(R.string.profilefragment_settext_52a0));

            loadAvatarFromFileOrNetwork(mid);

            if (uname == null || uname.length() == 0) {
                fetchUserName(mid);
            }

            fetchCoinAndVip();

        } else {
            if (userCard != null) {
                userCard.setVisibility(View.GONE);
            }
            if (loginContainer != null) {
                loginContainer.setVisibility(View.VISIBLE);
            }

            tvUserId.setText(getString(R.string.profilefragment_settext_672a));
            tvUid.setText("");
            tvCoin.setText(getString(R.string.profilefragment_settext_8bf7));
            tvVipBadge.setVisibility(View.GONE);
            ivAvatar.setImageResource(R.drawable.bili_default_avatar);
            currentMid = 0;
            currentCoinValue = 0;
            isVip = false;
        }
    }

    private void fetchUserName(final long mid) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String response = NetWorkUtil.get("https://api.bilibili.com/x/web-interface/nav");
                    JSONObject json = new JSONObject(response);
                    if (json.getInt("code") == 0) {
                        JSONObject data = json.getJSONObject("data");
                        final String uname = data.getString("uname");
                        final String face = data.optString("face");
                        SharedPreferencesUtil.putString("uname", uname);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (isAdded() && tvUserId != null) {
                                    tvUserId.setText(uname);
                                }
                                if (face != null && face.length() > 0) {
                                    loadAvatarUrl(face);
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "获取用户名失败: " + e.getMessage());
                }
            }
        });
    }

    private void loadAvatarUrl(final String urlStr) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return;
        if (ivAvatar == null || getActivity() == null) return;
        final String key = urlStr;
        ivAvatar.setTag(key);
        Bitmap cached = imageCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            ivAvatar.setImageBitmap(cached);
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String dlUrl = key;
                if (dlUrl.startsWith("https://")) {
                    dlUrl = "http://" + dlUrl.substring(8);
                }
                final Bitmap bmp = downloadBitmap(dlUrl);
                if (bmp != null && !bmp.isRecycled()) {
                    imageCache.put(key, bmp);
                    SharedPreferencesUtil.putString("avatar_url", key);
                    saveAvatarToFile(bmp, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isAdded() && ivAvatar != null) {
                                Object tag = ivAvatar.getTag();
                                if (tag != null && tag.equals(key)) {
                                    ivAvatar.setImageBitmap(bmp);
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private void fetchCoinAndVip() {
        final String cookies = SharedPreferencesUtil.getString("cookies", "");

        if (cookies == null || cookies.length() == 0) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isAdded() && tvCoin != null) {
                        tvCoin.setText(getString(R.string.profilefragment_settext_8bf7_1));
                    }
                }
            });
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ArrayList<String> headers = new ArrayList<String>();
                    headers.add("User-Agent");
                    headers.add(NetWorkUtil.USER_AGENT_WEB);
                    headers.add("Referer");
                    headers.add("https://www.bilibili.com/");
                    headers.add("Cookie");
                    headers.add(cookies);

                    String response = NetWorkUtil.get("https://api.bilibili.com/x/web-interface/nav", headers);

                    if (response == null || response.length() == 0) {
                        return;
                    }

                    JSONObject json = new JSONObject(response);
                    int code = json.optInt("code", -1);

                    if (code == 0) {
                        JSONObject data = json.getJSONObject("data");
                        if (data != null) {
                            int coin = data.optInt("money", 0);
                            currentCoinValue = coin;

                            JSONObject vipObj = data.optJSONObject("vip");
                            if (vipObj != null) {
                                int vipType = vipObj.optInt("type", 0);
                                int vipStatus = vipObj.optInt("status", 0);
                                isVip = (vipType > 0 && vipStatus == 1);
                            } else {
                                int vipStatus = data.optInt("vip_status", 0);
                                isVip = (vipStatus == 1);
                            }

                            final int finalCoin = coin;
                            final boolean finalVip = isVip;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (isAdded()) {
                                        tvCoin.setText(finalCoin + " 硬币");
                                        if (finalVip) {
                                            tvVipBadge.setVisibility(View.VISIBLE);
                                        } else {
                                            tvVipBadge.setVisibility(View.GONE);
                                        }
                                    }
                                }
                            });
                        }
                    } else if (code == -101) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (isAdded() && tvCoin != null) {
                                    tvCoin.setText(getString(R.string.profilefragment_settext_8bf7_1));
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "fetchCoinAndVip: " + e.getMessage());
                }
            }
        });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(DialogUtil.wrap(getActivity()))
                .setTitle(getString(R.string.profilefragment_settitle_771f))
                .setMessage(getString(R.string.profilefragment_setmessage_545c))
                .setPositiveButton("留下来", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getActivity(), getActivity().getString(R.string.profilefragment_toast_55ef), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("狠心离开", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doLogout();
                        dialog.dismiss();
                    }
                })
                .setCancelable(true)
                .show();
    }

    private File getAvatarFile() {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                && PermissionUtil.hasWriteStorage(getActivity())) {
            try {
                File externalCache = new File(Environment.getExternalStorageDirectory(), "BiliClassic/avatar_cache");
                if (!externalCache.exists()) {
                    externalCache.mkdirs();
                }
                return new File(externalCache, AVATAR_FILE_NAME);
            } catch (Exception e) {
                Log.e(TAG, "创建外部缓存目录失败: " + e.getMessage());
            }
        }
        return new File(getActivity().getCacheDir(), AVATAR_FILE_NAME);
    }

    private void loadAvatarFromFileOrNetwork(final long mid) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return;
        if (ivAvatar == null) return;
        final File avatarFile = getAvatarFile();
        final long savedMid = SharedPreferencesUtil.getLong("avatar_mid", 0);
        final String savedUrl = SharedPreferencesUtil.getString("avatar_url", "");

        // 文件缓存命中：直接用，不走网络
        if (avatarFile.exists() && savedMid == mid && savedUrl.length() > 0) {
            ivAvatar.setTag(savedUrl);
            // 先检查 GlobalImageCache
            Bitmap cached = imageCache.get(savedUrl);
            if (cached != null && !cached.isRecycled()) {
                ivAvatar.setImageBitmap(cached);
                return;
            }
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bmp = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                    if (bmp != null && !bmp.isRecycled()) {
                        imageCache.put(savedUrl, bmp);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (isAdded() && ivAvatar != null) {
                                    Object tag = ivAvatar.getTag();
                                    if (tag != null && tag.equals(savedUrl)) {
                                        ivAvatar.setImageBitmap(bmp);
                                    }
                                }
                            }
                        });
                    }
                }
            });
            return;
        }

        // 无缓存：走网络
        fetchUserName(mid);
    }

    private void saveAvatarToFile(Bitmap bitmap, long mid) {
        try {
            File avatarFile = getAvatarFile();
            FileOutputStream fos = new FileOutputStream(avatarFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
            fos.close();
            SharedPreferencesUtil.putLong("avatar_mid", mid);
            Log.d(TAG, "头像已保存到: " + avatarFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "保存头像失败: " + e.getMessage());
        }
    }

    private void clearAvatarCache() {
        try {
            File avatarFile = getAvatarFile();
            if (avatarFile.exists()) {
                avatarFile.delete();
                Log.d(TAG, "已删除本地头像缓存");
            }
            SharedPreferencesUtil.removeValue("avatar_mid");
            SharedPreferencesUtil.removeValue("avatar_url");
        } catch (Exception e) {
            Log.e(TAG, "清除头像缓存失败: " + e.getMessage());
        }
    }

    private Bitmap downloadBitmap(String urlStr) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NO_IMAGE_MODE, false)) return null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", NetWorkUtil.USER_AGENT_WEB);
            conn.connect();

            InputStream is = conn.getInputStream();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "downloadBitmap error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void doLogout() {
        SharedPreferencesUtil.removeValue("cookies");
        SharedPreferencesUtil.removeValue("mid");
        SharedPreferencesUtil.removeValue("csrf");
        SharedPreferencesUtil.removeValue("refresh_token");
        SharedPreferencesUtil.removeValue("uname");

        NetWorkUtil.setCookieString("");
        NetWorkUtil.refreshHeaders();
        SharedPreferencesUtil.putString("cookies", "");

        clearAvatarCache();
        currentMid = 0;
        currentCoinValue = 0;
        isVip = false;

        updateLoginStatus();
        MsgUtil.showMsg(getActivity(), "已退出登录…随时欢迎回来哦(´；ω；`)");
    }
}