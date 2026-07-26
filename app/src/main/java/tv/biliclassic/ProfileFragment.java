package tv.biliclassic;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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

import tv.biliclassic.util.MsgUtil;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

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

        // ========== 检查更新按钮 ==========
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

    // ========== 检查更新 ==========

    private void checkForUpdate() {
        Toast.makeText(getActivity(), "正在检查更新...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                String versionJson = null;

                // 1. 优先从主站获取
                try {
                    java.net.URL url = new java.net.URL("http://www.biliclassic.cn/api/version.json");
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "BiliClassic");

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        java.io.InputStream is = conn.getInputStream();
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(is, "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        is.close();
                        versionJson = sb.toString();
                        success = true;
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    // 主站失败
                }

                // 2. 备用站
                if (!success) {
                    try {
                        java.net.URL url = new java.net.URL(
                                "http://7891vip.top/biliclassic/update.php");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(8000);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", "BiliClassic");

                        int responseCode = conn.getResponseCode();
                        if (responseCode == 200) {
                            java.io.InputStream is = conn.getInputStream();
                            java.io.BufferedReader reader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(is, "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();
                            is.close();
                            versionJson = sb.toString();
                            success = true;
                        }
                        conn.disconnect();
                    } catch (Exception e) {
                        // 备用站失败
                    }
                }

                final String finalVersionJson = versionJson;
                final boolean finalSuccess = success;

                if (finalSuccess && finalVersionJson != null && finalVersionJson.length() > 0) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleUpdateCheckResult(finalVersionJson);
                        }
                    });
                } else {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getActivity(), "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void handleUpdateCheckResult(String versionJson) {
        try {
            JSONObject json = new JSONObject(versionJson);

            int latestVersionCode = json.optInt("version_code", 0);
            if (latestVersionCode == 0) {
                latestVersionCode = json.optInt("versionCode", 0);
            }
            String latestVersionName = json.optString("version", "");
            String downloadUrl = json.optString("download_url", "");
            boolean forceUpdate = json.optBoolean("force_update", false);
            int minSdk = json.optInt("min_sdk", 0);

            // 解析 changelog
            String changelog = "";
            try {
                org.json.JSONArray changelogArray = json.optJSONArray("changelog");
                if (changelogArray != null && changelogArray.length() > 0) {
                    StringBuilder logBuilder = new StringBuilder();
                    for (int i = 0; i < changelogArray.length(); i++) {
                        logBuilder.append("• ").append(changelogArray.getString(i));
                        if (i < changelogArray.length() - 1) {
                            logBuilder.append("\n");
                        }
                    }
                    changelog = logBuilder.toString();
                }
            } catch (Exception e) {
                changelog = json.optString("changelog", "");
            }

            // 检查是否有更新
            boolean hasUpdate = false;
            if (latestVersionCode > 0) {
                hasUpdate = (latestVersionCode > currentVersionCode);
            } else {
                hasUpdate = compareVersions(currentVersionName, latestVersionName);
            }

            // SDK 版本检查
            int sdkVersion = android.os.Build.VERSION.SDK_INT;
            if (minSdk > 0 && sdkVersion < minSdk) {
                Toast.makeText(getActivity(), "新版本需要 Android " + minSdk + " 及以上系统", Toast.LENGTH_LONG).show();
                return;
            }

            if (hasUpdate) {
                String detailInfo = "当前: " + currentVersionName + "\n" +
                        "最新: " + latestVersionName + "\n\n";
                showUpdateDialog(latestVersionName, detailInfo + changelog, downloadUrl, forceUpdate);
            } else {
                Toast.makeText(getActivity(), "已是最新版本 (" + currentVersionName + ")", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(getActivity(), "解析更新信息失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private boolean compareVersions(String current, String latest) {
        if (current == null || latest == null || current.length() == 0 || latest.length() == 0) {
            return false;
        }

        current = current.trim();
        latest = latest.trim();

        if (current.equals(latest)) {
            return false;
        }

        String[] currentParts = splitVersion(current);
        String[] latestParts = splitVersion(latest);

        String currentBase = currentParts[0];
        String latestBase = latestParts[0];
        String currentSuffix = currentParts[1];
        String latestSuffix = latestParts[1];

        int cmp = compareVersionNumbers(currentBase, latestBase);
        if (cmp != 0) {
            return cmp < 0;
        }

        return compareSuffix(currentSuffix, latestSuffix) < 0;
    }

    private String[] splitVersion(String version) {
        String base = version;
        String suffix = "";

        int rIndex = version.indexOf("-r");
        if (rIndex > 0) {
            base = version.substring(0, rIndex);
            suffix = version.substring(rIndex + 1);
        } else {
            int fixIndex = version.indexOf("-fix");
            if (fixIndex > 0) {
                base = version.substring(0, fixIndex);
                suffix = version.substring(fixIndex + 1);
            }
        }

        return new String[]{base, suffix};
    }

    private int compareVersionNumbers(String v1, String v2) {
        if (v1.indexOf('.') >= 0 || v2.indexOf('.') >= 0) {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");

            int len = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < len; i++) {
                int num1 = 0;
                int num2 = 0;
                try {
                    if (i < parts1.length) num1 = Integer.parseInt(parts1[i]);
                    if (i < parts2.length) num2 = Integer.parseInt(parts2[i]);
                } catch (NumberFormatException e) {
                    String s1 = (i < parts1.length) ? parts1[i] : "";
                    String s2 = (i < parts2.length) ? parts2[i] : "";
                    int cmp = s1.compareTo(s2);
                    if (cmp != 0) return cmp;
                    continue;
                }
                if (num1 != num2) {
                    return num1 - num2;
                }
            }
            return 0;
        }

        try {
            int n1 = Integer.parseInt(v1);
            int n2 = Integer.parseInt(v2);
            return n1 - n2;
        } catch (NumberFormatException e) {
            return v1.compareTo(v2);
        }
    }

    private int compareSuffix(String currentSuffix, String latestSuffix) {
        if (currentSuffix != null && currentSuffix.length() > 0 &&
                latestSuffix != null && latestSuffix.length() > 0) {

            if (currentSuffix.startsWith("r") && latestSuffix.startsWith("r")) {
                try {
                    int n1 = Integer.parseInt(currentSuffix.substring(1));
                    int n2 = Integer.parseInt(latestSuffix.substring(1));
                    return n1 - n2;
                } catch (NumberFormatException e) {
                    return currentSuffix.compareTo(latestSuffix);
                }
            }

            if (currentSuffix.startsWith("r") && latestSuffix.startsWith("fix")) {
                return -1;
            }
            if (currentSuffix.startsWith("fix") && latestSuffix.startsWith("r")) {
                return 1;
            }

            return currentSuffix.compareTo(latestSuffix);
        }

        if (currentSuffix != null && currentSuffix.length() > 0) {
            return 1;
        }

        if (latestSuffix != null && latestSuffix.length() > 0) {
            return -1;
        }

        return 0;
    }

    private void showUpdateDialog(String versionName, String changelog, final String downloadUrl, boolean forceUpdate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("发现新版本: " + versionName);
        builder.setMessage(changelog);

        builder.setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (downloadUrl != null && downloadUrl.length() > 0) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(downloadUrl));
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), "下载地址无效", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (!forceUpdate) {
            builder.setNegativeButton("稍后", null);
        }

        builder.setCancelable(!forceUpdate);
        builder.show();
    }

    // ========== 原 ProfileFragment 方法 ==========

    private boolean isLoggedIn() {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String cookies = SharedPreferencesUtil.getString("cookies", "");
        return mid != 0 && cookies != null && cookies.length() > 0;
    }

    private void updateLoginStatus() {
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String uname = SharedPreferencesUtil.getString("uname", "");
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
                tvUserId.setText(uname);
            } else {
                tvUserId.setText("用户名");
            }
            tvUid.setText("UID: " + mid);

            tvCoin.setText("加载中...");

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
                        SharedPreferencesUtil.putString("uname", uname);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (isAdded() && tvUserId != null) {
                                    tvUserId.setText(uname);
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

    private void loadAvatarFromFileOrNetwork(long mid) {
        final File avatarFile = getAvatarFile();
        final long savedMid = SharedPreferencesUtil.getLong("avatar_mid", 0);

        ivAvatar.setImageResource(R.drawable.bili_default_avatar);

        if (avatarFile.exists() && savedMid == mid) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bmp = BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
                    if (bmp != null && !bmp.isRecycled()) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (isAdded() && ivAvatar != null) {
                                    ivAvatar.setImageBitmap(bmp);
                                    Log.d(TAG, "从本地文件加载头像成功");
                                }
                            }
                        });
                        return;
                    }
                }
            });
        }

        downloadAvatar(mid);
    }

    private void downloadAvatar(final long mid) {
        currentMid = mid;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.bilibili.com/x/space/acc/info?mid=" + mid;
                    String response = NetWorkUtil.get(url);
                    JSONObject json = new JSONObject(response);
                    if (json.optInt("code") != 0) {
                        Log.w(TAG, "获取头像失败: code=" + json.optInt("code"));
                        return;
                    }

                    JSONObject data = json.getJSONObject("data");
                    String faceUrl = data.optString("face");
                    if (faceUrl == null || faceUrl.length() == 0) {
                        Log.w(TAG, "头像 URL 为空");
                        return;
                    }

                    if (faceUrl.startsWith("https://")) {
                        faceUrl = "http://" + faceUrl.substring(8);
                    }

                    final Bitmap bmp = downloadBitmap(faceUrl);
                    if (bmp != null && !bmp.isRecycled()) {
                        saveAvatarToFile(bmp, mid);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (isAdded() && ivAvatar != null && !bmp.isRecycled()) {
                                    ivAvatar.setImageBitmap(bmp);
                                    Log.d(TAG, "头像下载并保存成功");
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "downloadAvatar error: " + e.getMessage());
                }
            }
        });
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