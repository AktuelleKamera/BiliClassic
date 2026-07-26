package tv.biliclassic.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * 应用更新检查工具类
 * 支持多分支版本管理 (0.3.x, 0.4.x, 0.5.x, 1.x ...)
 * 支持 enabled 字段控制分支是否开放
 * 兼容 Android 1.6 (API 4) 及以上
 */
public class UpdateUtil {

    public interface UpdateCallback {
        void onCheckStart();
        void onCheckComplete(boolean hasUpdate, String message);
        void onCheckFailed(String error);
    }

    public static void checkUpdate(final Context context,
                                   final int currentVersionCode,
                                   final String currentVersionName,
                                   final UpdateCallback callback) {
        if (callback != null) {
            callback.onCheckStart();
        }

        new Thread(new Runnable() {
            public void run() {
                boolean success = false;
                String versionJson = null;

                try {
                    versionJson = fetchVersionJson("http://www.biliclassic.cn/api/version.json");
                    if (versionJson != null) success = true;
                } catch (Exception e) {}

                if (!success) {
                    try {
                        versionJson = fetchVersionJson("http://7891vip.top/biliclassic/update.php");
                        if (versionJson != null) success = true;
                    } catch (Exception e) {}
                }

                final String finalJson = versionJson;
                final boolean finalSuccess = success;

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        if (!finalSuccess || finalJson == null) {
                            if (callback != null) {
                                callback.onCheckFailed("检查更新失败");
                            } else {
                                Toast.makeText(context, "检查更新失败", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        handleResult(context, finalJson, currentVersionCode,
                                currentVersionName, callback);
                    }
                });
            }
        }).start();
    }

    private static String fetchVersionJson(String urlString) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "BiliClassic");

            if (conn.getResponseCode() != 200) return null;

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void handleResult(Context context,
                                     String versionJson,
                                     int currentCode,
                                     String currentName,
                                     UpdateCallback callback) {
        try {
            JSONObject json = new JSONObject(versionJson);
            JSONObject versions = json.optJSONObject("versions");
            int minSdk = json.optInt("min_sdk", 0);

            if (minSdk > 0 && android.os.Build.VERSION.SDK_INT < minSdk) {
                String msg = "新版本需要更高系统版本";
                if (callback != null) {
                    callback.onCheckComplete(false, msg);
                } else {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (versions == null) {
                showNoUpdate(context, currentName, callback);
                return;
            }

            String currentMajor = parseMajorVersion(currentName);

            List branches = getBranches(versions);
            if (branches.size() == 0) {
                showNoUpdate(context, currentName, callback);
                return;
            }

            int currentIdx = findBranchIndex(branches, currentMajor);
            if (currentIdx < 0) {
                BranchInfo first = (BranchInfo) branches.get(0);
                if (first.versionCode > currentCode) {
                    String msg = "当前: " + currentName + "\n最新: " + first.latest + "\n\n" + first.changelog;
                    showUpdateDialog(context, first.latest, msg, first.downloadUrl, first.forceUpdate);
                    if (callback != null) callback.onCheckComplete(true, "发现新版本: " + first.latest);
                } else {
                    showNoUpdate(context, currentName, callback);
                }
                return;
            }

            BranchInfo currentBranch = (BranchInfo) branches.get(currentIdx);
            boolean hasCurrentUpdate = (currentBranch.versionCode > currentCode);

            List higherBranches = new ArrayList();
            for (int i = currentIdx + 1; i < branches.size(); i++) {
                BranchInfo bi = (BranchInfo) branches.get(i);
                if (bi.versionCode > currentCode && bi.enabled) {
                    higherBranches.add(bi);
                }
            }

            // 情况1: 当前分支有更新，且有更高分支 → 三按钮
            if (hasCurrentUpdate && higherBranches.size() > 0) {
                showMultiUpdateDialog(context, currentName, currentBranch, higherBranches, callback);
                return;
            }

            // 情况2: 当前分支有更新，无更高分支 → 两按钮
            if (hasCurrentUpdate) {
                String msg = "当前: " + currentName + "\n最新: " + currentBranch.latest + "\n\n" + currentBranch.changelog;
                showUpdateDialog(context, currentBranch.latest, msg,
                        currentBranch.downloadUrl, currentBranch.forceUpdate);
                if (callback != null) callback.onCheckComplete(true, "发现新版本: " + currentBranch.latest);
                return;
            }

            // 情况3: 当前分支无更新，但有更高分支 → 直接跳到最新版
            if (higherBranches.size() > 0) {
                BranchInfo latest = (BranchInfo) higherBranches.get(higherBranches.size() - 1);
                String msg = "当前: " + currentName + "\n最新: " + latest.latest + "\n\n" +
                        "跨越了 " + higherBranches.size() + " 个大版本\n\n" + latest.changelog;
                showUpdateDialog(context, latest.latest, msg, latest.downloadUrl, latest.forceUpdate);
                if (callback != null) callback.onCheckComplete(true, "发现新版本: " + latest.latest);
                return;
            }

            // 情况4: 没有更新
            showNoUpdate(context, currentName, callback);

        } catch (Exception e) {
            String msg = "解析更新信息失败: " + e.getMessage();
            if (callback != null) {
                callback.onCheckFailed(msg);
            } else {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static String parseMajorVersion(String version) {
        if (version == null || version.length() == 0) return "";
        String[] parts = version.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return version;
    }

    private static List getBranches(JSONObject versions) {
        List result = new ArrayList();
        try {
            Iterator keys = versions.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                JSONObject branch = versions.getJSONObject(key);
                BranchInfo info = new BranchInfo();
                info.major = key;
                info.latest = branch.optString("latest", "");
                info.versionCode = branch.optInt("version_code", 0);
                info.downloadUrl = branch.optString("download_url", "");
                info.forceUpdate = branch.optBoolean("force_update", false);
                info.enabled = branch.optBoolean("enabled", true);
                info.changelog = getChangelog(branch);
                result.add(info);
            }
        } catch (Exception e) {}
        Collections.sort(result, new Comparator() {
            public int compare(Object a, Object b) {
                return compareMajor(((BranchInfo) a).major, ((BranchInfo) b).major);
            }
        });
        return result;
    }

    private static int compareMajor(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = 0, nb = 0;
            try {
                if (i < pa.length) na = Integer.parseInt(pa[i]);
            } catch (NumberFormatException e) {}
            try {
                if (i < pb.length) nb = Integer.parseInt(pb[i]);
            } catch (NumberFormatException e) {}
            if (na != nb) return na - nb;
        }
        return 0;
    }

    private static int findBranchIndex(List branches, String major) {
        for (int i = 0; i < branches.size(); i++) {
            BranchInfo bi = (BranchInfo) branches.get(i);
            if (bi.major.equals(major)) {
                return i;
            }
        }
        return -1;
    }

    private static String getChangelog(JSONObject json) {
        try {
            JSONArray arr = json.optJSONArray("changelog");
            if (arr != null && arr.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    String line = arr.getString(i);
                    if (line != null && line.length() > 0) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append("  ").append(line);
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {}
        return json.optString("changelog", "");
    }

    private static void showNoUpdate(Context context, String currentName, UpdateCallback callback) {
        String msg = "已是最新版本 (" + currentName + ")";
        if (callback != null) {
            callback.onCheckComplete(false, msg);
        } else {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        }
    }

    // 两按钮：立即更新 / 稍后
    private static void showUpdateDialog(final Context context,
                                         String versionName,
                                         String message,
                                         final String downloadUrl,
                                         boolean forceUpdate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("发现新版本: " + versionName);
        builder.setMessage(message);

        builder.setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                openDownload(context, downloadUrl);
            }
        });

        builder.setNegativeButton("稍后", null);

        builder.setCancelable(!forceUpdate);
        builder.show();
    }

    // 三按钮：升级到当前分支 / 升级到最新版 / 稍后
    private static void showMultiUpdateDialog(final Context context,
                                              String currentName,
                                              final BranchInfo currentBranch,
                                              final List higherBranches,
                                              final UpdateCallback callback) {
        // 取最后一个（最高版本）
        final BranchInfo latest = (BranchInfo) higherBranches.get(higherBranches.size() - 1);

        StringBuilder msg = new StringBuilder();
        msg.append("当前版本: ").append(currentName).append("\n\n");

        msg.append("版本 ").append(currentBranch.latest).append(" (推荐更新)\n");
        msg.append(currentBranch.changelog).append("\n\n");

        msg.append("版本 ").append(latest.latest).append(" (最新版)\n");
        msg.append(latest.changelog);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("发现新版本");
        builder.setMessage(msg.toString());

        // 按钮1：升级到当前分支（左）
        builder.setPositiveButton("升级到 " + currentBranch.latest,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        openDownload(context, currentBranch.downloadUrl);
                        if (callback != null) {
                            callback.onCheckComplete(true, "开始下载: " + currentBranch.latest);
                        }
                    }
                });

        // 按钮2：升级到最新版（中）
        builder.setNeutralButton("升级到 " + latest.latest,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        openDownload(context, latest.downloadUrl);
                        if (callback != null) {
                            callback.onCheckComplete(true, "开始下载: " + latest.latest);
                        }
                    }
                });

        // 按钮3：稍后（右）
        builder.setNegativeButton("稍后", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        builder.setCancelable(true);
        builder.show();
    }

    private static void openDownload(Context context, String url) {
        if (url != null && url.length() > 0) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } else {
            Toast.makeText(context, "下载地址无效", Toast.LENGTH_SHORT).show();
        }
    }

    private static class BranchInfo {
        String major;
        String latest;
        int versionCode;
        String downloadUrl;
        boolean forceUpdate;
        boolean enabled;
        String changelog;
    }
}