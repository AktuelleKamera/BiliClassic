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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tv.biliclassic.util.GlobalImageCache;
import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.UpdateUtil;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private static final String AVATAR_FILE_NAME = "avatar_cache.jpg";

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

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private long currentMid = 0;
    private int currentCoinValue = 0;
    private boolean isVip = false;

    // 当前版本信息
    private int currentVersionCode = -1;
    private String currentVersionName = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.content_profile, container, false);

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
                        Toast.makeText(getActivity(), "获取用户信息失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getActivity(), "请先登录的说~", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getActivity(), "请先登录的说~", Toast.LENGTH_SHORT).show();
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
        updateLoginStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLoginStatus();
    }

    // 检查更新（使用 UpdateUtil）
    private void checkForUpdate() {
        Toast.makeText(getActivity(), "正在检查更新...", Toast.LENGTH_SHORT).show();

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
                        if (isAdded() && tvUserId != null) tvUserId.setText("用户名");
                    }
                });
            }
            mainHandler.post(new Runnable() {
                public void run() {
                    if (isAdded() && tvUid != null) tvUid.setText("UID: " + mid);
                }
            });

            tvCoin.setText("加载中……");

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

            tvUserId.setText("未登录");
            tvUid.setText("");
            tvCoin.setText("请登录以使用完整功能");
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
                        tvCoin.setText("请重新登录");
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
                                    tvCoin.setText("请重新登录");
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
        new AlertDialog.Builder(getActivity())
                .setTitle("真的要离开了吗…？")
                .setMessage("呜…你确定要退出登录吗？\n退出后就不能愉快地看番了哦 (；′⌒`)")
                .setPositiveButton("留下来", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getActivity(), "嗯嗯！留下来陪我们一起看番吧！(＾▽＾)", Toast.LENGTH_SHORT).show();
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
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
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