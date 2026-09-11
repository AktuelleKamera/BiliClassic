package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import tv.biliclassic.TidData;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.StringUtil;

/**
 * 分区视频列表。
 *
 * App 端分区信息流 /x/v2/region/dynamic/list 支持游标（ctime=cbottom）翻页，
 * 覆盖全部一级/二级分区；接口单页固定 10 条，这里内部拼 2 页凑成 20 条，
 * 与原调用方「每页 20 条」的分页步长保持一致。
 *
 * 部分老分区（解说、地理、未来、自然、旧番、完结动画 等）在现行分区体系中
 * 已无内容，这类空分区自动回退到 B 站搜索对应标签（关键词见 TidData.getSearchKeyword）。
 */
public class PartitionApi {

    private static final String TAG = "PartitionApi";

    private static final String REGION_LIST_URL = "https://app.bilibili.com/x/v2/region/dynamic/list";
    /** B 站公开的 Android 端 appkey/appsec（见 bilibili-API-collect） */
    private static final String APP_KEY = "1d8b6e7d45233436";
    private static final String APP_SEC = "560c52ccd288fed045859ed18bffd033";

    /** App 接口单页固定 10 条 */
    private static final int ITEMS_PER_FETCH = 10;
    /** 对外仍按 20 条/页的步长（调用方以 size < 20 判断是否到底） */
    private static final int TARGET_PAGE_SIZE = 20;
    /** 单次调用最多拉取的游标页数（保险丝，防死循环） */
    private static final int MAX_FETCH_PER_CALL = 4;

    /** 各分区最近一次翻页游标（cbottom），page=1 时重置 */
    private static final Map<Integer, Long> sCursors = new ConcurrentHashMap<Integer, Long>();
    /** 记录哪些分区已确认有内容（避免翻页到底后误触发搜索） */
    private static final Set<Integer> sHasContent =
            java.util.Collections.synchronizedSet(new HashSet<Integer>());

    private static ArrayList<String> buildHeaders() {
        ArrayList<String> headers = new ArrayList<String>();
        headers.add("User-Agent");
        headers.add("Mozilla/5.0 BiliDroid/6.18.0 (bbcall@gmail.com) os/android");
        return headers;
    }

    /**
     * 获取分区视频列表。
     *
     * @param rid  分区 tid
     * @param page 页码（从 1 开始；page=1 重置游标，page>1 续传游标）
     */
    public static void getRegionVideos(List<VideoCard> videoCardList, int rid, int page)
            throws IOException, JSONException {
        // region 列表内容过时或为空的分区，直接走搜索（按时间排序）
        if (TidData.isSearchOnly(rid)) {
            getBySearch(videoCardList, rid, page);
            return;
        }

        if (page <= 1) {
            sCursors.remove(rid);
        }
        Long cursor = sCursors.get(rid);
        boolean hasContent = sHasContent.contains(rid);

        HashSet<Long> seen = new HashSet<Long>();
        int fetches = 0;
        while (videoCardList.size() < TARGET_PAGE_SIZE && fetches < MAX_FETCH_PER_CALL) {
            JSONObject data = fetchRegionPage(rid, cursor);
            if (data == null) {
                break;
            }
            JSONArray items = data.optJSONArray("new");
            if (items == null || items.length() == 0) {
                // 已到底或该分区无内容
                break;
            }
            int before = videoCardList.size();
            parseItems(items, videoCardList, seen);
            if (videoCardList.size() > before) {
                hasContent = true;
                sHasContent.add(rid);
            }

            long cbottom = data.optLong("cbottom", 0);
            if (cbottom > 0) {
                sCursors.put(rid, cbottom);
                cursor = cbottom;
            }
            fetches++;
            if (videoCardList.size() == before) {
                // 本页没有新增，避免重复
                break;
            }
        }

        // 分区在现行体系中已无内容（且从未拿到过数据）→ 回退搜索对应标签
        if (videoCardList.isEmpty() && !hasContent) {
            getBySearch(videoCardList, rid, page);
        }
    }

    /** 请求一个游标页（cursor 为 null 时是第一页：pull=1），返回 data 对象 */
    private static JSONObject fetchRegionPage(int rid, Long cursor) throws IOException, JSONException {
        TreeMap<String, String> params = new TreeMap<String, String>();
        params.put("appkey", APP_KEY);
        params.put("build", "6180000");
        params.put("mobi_app", "android");
        params.put("platform", "android");
        params.put("pull", cursor == null ? "1" : "0");
        params.put("rid", String.valueOf(rid));
        if (cursor != null) {
            params.put("ctime", String.valueOf(cursor));
        }
        params.put("ts", String.valueOf(System.currentTimeMillis() / 1000));

        // 排序后的 query 用于签名和请求（必须完全一致）
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        String sign = md5LowerCase(query.toString() + APP_SEC);
        String url = REGION_LIST_URL + "?" + query + "&sign=" + sign;
        android.util.Log.d(TAG, "请求URL: " + REGION_LIST_URL + " rid=" + rid + " cursor=" + cursor);

        JSONObject result = NetWorkUtil.getJson(url, buildHeaders());
        if (result == null) {
            return null;
        }
        int code = result.optInt("code", -1);
        if (code != 0) {
            android.util.Log.e(TAG, "region/dynamic/list 返回错误: code=" + code
                    + ", message=" + result.optString("message", ""));
            return null;
        }
        return result.optJSONObject("data");
    }

    /** 解析 data.new[]：param=aid、name=UP主、play=播放、danmaku=弹幕 */
    private static void parseItems(JSONArray items, List<VideoCard> videoCardList, HashSet<Long> seen) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            // 只收普通视频卡片，跳过广告/番剧 season 等其他类型
            String gotoType = item.optString("goto", "");
            if (!"av".equals(gotoType)) {
                continue;
            }
            long aid;
            try {
                aid = Long.parseLong(item.optString("param", "0"));
            } catch (NumberFormatException e) {
                aid = 0;
            }
            if (aid == 0 || seen.contains(aid)) {
                continue;
            }
            seen.add(aid);

            String cover = item.optString("cover", "").replace("http://", "https://");
            String title = item.optString("title", "无标题");
            String upName = item.optString("name", "");
            int view = item.optInt("play", 0);
            int danmaku = item.optInt("danmaku", 0);

            videoCardList.add(new VideoCard(title, upName, StringUtil.toWan(view),
                    cover, aid, "", danmaku));
        }
    }

    /** 空分区：用分区对应标签走 B 站搜索 */
    private static void getBySearch(List<VideoCard> videoCardList, int rid, int page)
            throws IOException, JSONException {
        String keyword = TidData.getSearchKeyword(rid);
        String order = TidData.getSearchOrder(rid);
        long beginS = TidData.getSearchBeginTime(rid);
        long endS = TidData.getSearchEndTime(rid);
        android.util.Log.d(TAG, "分区 " + rid + " 搜索: " + keyword + " page=" + page
                + " 时间范围=" + beginS + "~" + endS + " 排序=" + order);

        JSONObject result = SearchApi.search(keyword, page, order, beginS, endS, 42);
        if (result == null || result.optInt("code", -1) != 0) {
            android.util.Log.e(TAG, "搜索失败: keyword=" + keyword);
            return;
        }
        JSONObject data = result.optJSONObject("data");
        if (data == null) {
            return;
        }
        JSONArray arr = data.optJSONArray("result");
        if (arr == null) {
            return;
        }
        videoCardList.addAll(SearchApi.parseVideoCardsForPartition(arr));
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** App 端签名：md5(排序后query + appsec)，小写 */
    private static String md5LowerCase(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            android.util.Log.e(TAG, "MD5 签名失败: " + e.getMessage());
            return "";
        }
    }
}
