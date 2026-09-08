/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年8月28日
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

import tv.biliclassic.model.Dynamic;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.DmImgParamUtil;
import tv.biliclassic.util.NetWorkUtil;

/**
 * 动态相关接口：获取关注动态列表、发布文字动态、点赞、删除
 */
public class DynamicApi {

    private static final String FEATURES =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,"
                    + "decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete";

    /**
     * 获取关注的动态列表（feed/all，需登录 Cookie）。
     *
     * @param out    输出解析后的动态列表（追加）
     * @param offset 上一次返回的翻页偏移；首次传 null 或空串
     * @return 下一次请求的 offset；空串表示没有更多了
     */
    public static String getFeedList(List<Dynamic> out, String offset) throws IOException, JSONException {
        StringBuffer url = new StringBuffer(
                "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?type=all&features=");
        url.append(FEATURES);
        if (offset != null && offset.length() > 0) {
            url.append("&offset=").append(offset);
        }
        try {
            String signed = ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url.toString()));
            JSONObject resp = NetWorkUtil.getJson(signed);
            if (resp.optInt("code", -1) != 0) {
                throw new JSONException(
                        resp.optString("message", "获取动态列表失败(code=" + resp.optInt("code", -1) + ")"));
            }
            JSONObject data = resp.optJSONObject("data");
            if (data == null) throw new JSONException("获取动态列表失败: data为空");

            String nextOffset = data.optBoolean("has_more", false)
                    ? data.optString("offset", "") : "";
            JSONArray items = data.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    try {
                        Dynamic d = analyzeDynamic(item);
                        if (d != null && d.dynamicId != 0
                                && !"DYNAMIC_TYPE_NONE".equals(d.type)) {
                            out.add(d);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return nextOffset;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof org.json.JSONException) throw (org.json.JSONException) e;
            IOException io = new IOException(e.getMessage() == null ? e.toString() : e.getMessage());
            io.initCause(e);
            throw io;
        }
    }

    /**
     * 获取单条动态详情（web-dynamic v1 detail 接口）。
     */
    public static Dynamic getDynamicDetail(long dynamicId) throws IOException, JSONException {
        JSONObject resp = NetWorkUtil.getJson(
                "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?id=" + dynamicId);
        if (resp.optInt("code", -1) != 0) {
            throw new JSONException(resp.optString("message", "获取动态详情失败"));
        }
        JSONObject data = resp.optJSONObject("data");
        JSONObject item = data != null ? data.optJSONObject("item") : null;
        if (item == null || item.length() == 0) {
            throw new JSONException("动态不存在或已被删除");
        }
        return analyzeDynamic(item);
    }

    /**
     * 发布纯文字动态。
     *
     * @return 0 表示成功，其他为B站错误码；-1 表示请求或解析失败
     */
    public static int publishText(String content) throws IOException {
        try {
            String csrf = NetWorkUtil.getCsrf();
            String arg = "dynamic_id=0&type=4&rid=0&content="
                    + URLEncoder.encode(content, "UTF-8")
                    + "&csrf=" + csrf;
            String response = NetWorkUtil.post(
                    "https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/create",
                    arg, NetWorkUtil.webHeaders);
            JSONObject result = new JSONObject(response);
            return result.optInt("code", -1);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 点赞/取消赞动态。
     *
     * @return 0 表示成功，其他为错误码
     */
    public static int likeDynamic(long dyid, boolean like) throws IOException {
        try {
            String csrf = NetWorkUtil.getCsrf();
            String arg = "dynamic_id=" + dyid + "&up=" + (like ? "1" : "2")
                    + "&csrf_token=" + csrf;
            String response = NetWorkUtil.post(
                    "https://api.vc.bilibili.com/dynamic_like/v1/dynamic_like/thumb",
                    arg, NetWorkUtil.webHeaders);
            return new JSONObject(response).optInt("code", -1);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 删除动态（仅自己的动态）。
     *
     * @return 0 表示成功，其他为错误码
     */
    public static int deleteDynamic(long dyid) throws IOException {
        try {
            String csrf = NetWorkUtil.getCsrf();
            String arg = "dynamic_id=" + dyid + "&csrf_token=" + csrf;
            String response = NetWorkUtil.post(
                    "https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/rm_dynamic",
                    arg, NetWorkUtil.webHeaders);
            return new JSONObject(response).optInt("code", -1);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            return -1;
        }
    }

    // 解析

    /**
     * 解析单条动态 JSON（web-dynamic v1 结构）。
     */
    public static Dynamic analyzeDynamic(JSONObject json) {
        Dynamic d = new Dynamic();
        if (json == null) return d;
        try {
            d.dynamicId = Long.parseLong(json.optString("id_str", "0"));
        } catch (NumberFormatException ignored) {
        }
        d.type = json.optString("type", "");

        JSONObject basic = json.optJSONObject("basic");
        if (basic != null) {
            try {
                d.commentId = Long.parseLong(basic.optString("comment_id_str", "0"));
            } catch (NumberFormatException ignored) {
            }
            d.commentType = basic.optInt("comment_type", 17);
        }
        if (d.commentId == 0) d.commentId = d.dynamicId;

        JSONObject modules = json.optJSONObject("modules");
        if (modules == null) return d;

        JSONObject author = modules.optJSONObject("module_author");
        if (author != null) {
            d.mid = author.optLong("mid", 0);
            d.uname = author.optString("name", "");
            d.avatar = author.optString("face", "");
            d.pubTime = author.optString("pub_time", "");
        }

        if ("DYNAMIC_TYPE_NONE".equals(d.type)) {
            d.content = "[动态不存在]";
            return d;
        }

        JSONObject mdyn = modules.optJSONObject("module_dynamic");
        if (mdyn != null) {
            JSONObject desc = mdyn.optJSONObject("desc");
            d.content = desc != null
                    ? analyzeRichText(desc.optJSONArray("rich_text_nodes")) : "";
            JSONObject major = mdyn.optJSONObject("major");
            if (major != null) analyzeMajor(d, major);
        }

        JSONObject stat = modules.optJSONObject("module_stat");
        if (stat != null) {
            JSONObject like = stat.optJSONObject("like");
            if (like != null) {
                d.likeCount = like.optInt("count", 0);
                d.liked = optBool(like, "status");
            }
        }

        JSONObject more = modules.optJSONObject("module_more");
        if (more != null) {
            JSONArray tpi = more.optJSONArray("three_point_items");
            if (tpi != null) {
                for (int i = 0; i < tpi.length(); i++) {
                    JSONObject it = tpi.optJSONObject(i);
                    if (it != null && "THREE_POINT_DELETE".equals(it.optString("type", ""))) {
                        d.canDelete = true;
                        break;
                    }
                }
            }
        }

        JSONObject orig = json.optJSONObject("orig");
        if (orig != null && orig.length() > 0) {
            d.forward = analyzeDynamic(orig);
        }
        return d;
    }

    private static void analyzeMajor(Dynamic d, JSONObject major) {
        String majorType = major.optString("type", "");

        if ("MAJOR_TYPE_ARCHIVE".equals(majorType)) {
            JSONObject a = major.optJSONObject("archive");
            if (a != null) {
                d.videoCard = analyzeVideoCard(a);
                d.cardLabel = "投稿视频";
            }
        } else if ("MAJOR_TYPE_UGC_SEASON".equals(majorType)) {
            JSONObject u = major.optJSONObject("ugc_season");
            if (u != null) {
                d.videoCard = analyzeVideoCard(u);
                d.cardLabel = "视频合集";
            }
        } else if ("MAJOR_TYPE_PGC".equals(majorType)) {
            JSONObject p = major.optJSONObject("pgc");
            if (p != null) {
                d.epid = p.optLong("epid", 0);
                d.videoCard = new VideoCard(p.optString("title", ""), "",
                        statPlay(p), p.optString("cover", ""), 0, "");
                d.cardLabel = "番剧";
            }
        } else if ("MAJOR_TYPE_ARTICLE".equals(majorType)) {
            JSONObject art = major.optJSONObject("article");
            if (art != null) {
                d.articleId = art.optLong("id", 0);
                JSONArray covers = art.optJSONArray("covers");
                String cover = (covers != null && covers.length() > 0)
                        ? covers.optString(0, "") : "";
                d.videoCard = new VideoCard(art.optString("title", ""), "", "", cover, 0, "");
                d.cardLabel = "专栏文章";
            }
        } else if ("MAJOR_TYPE_DRAW".equals(majorType)) {
            JSONObject draw = major.optJSONObject("draw");
            JSONArray items = draw != null ? draw.optJSONArray("items") : null;
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it != null) {
                        String src = it.optString("src", "");
                        if (src.length() > 0) d.pics.add(src);
                    }
                }
            }
        } else if ("MAJOR_TYPE_OPUS".equals(majorType)) {
            JSONObject opus = major.optJSONObject("opus");
            if (opus != null) {
                JSONArray pics = opus.optJSONArray("pics");
                if (pics != null) {
                    for (int i = 0; i < pics.length(); i++) {
                        JSONObject p = pics.optJSONObject(i);
                        if (p != null) {
                            String u = p.optString("url", "");
                            if (u.length() > 0) d.pics.add(u);
                        }
                    }
                }
                JSONObject summary = opus.optJSONObject("summary");
                if (summary != null) {
                    String s = analyzeRichText(summary.optJSONArray("rich_text_nodes"));
                    if (s.length() > 0) {
                        d.content = d.content.length() > 0 ? d.content + "\n" + s : s;
                    }
                }
            }
        } else if ("MAJOR_TYPE_LIVE_RCMD".equals(majorType)) {
            JSONObject lrc = major.optJSONObject("live_rcmd");
            JSONObject info = null;
            if (lrc != null) {
                try {
                    JSONObject content = new JSONObject(lrc.optString("content", "{}"));
                    info = content.optJSONObject("live_play_info");
                } catch (Exception ignored) {
                }
            }
            applyLive(d, info);
        } else if ("MAJOR_TYPE_LIVE".equals(majorType)) {
            applyLive(d, major.optJSONObject("live"));
        } else if (majorType.length() > 0) {
            d.content += "\n[暂时无法显示此动态的附加内容]";
        }
    }

    private static void applyLive(Dynamic d, JSONObject info) {
        if (info == null) return;
        d.roomId = info.optLong("room_id", info.optLong("id", 0));
        d.videoCard = new VideoCard(info.optString("title", ""), "",
                "", info.optString("cover", ""), 0, "");
        d.cardLabel = "直播间";
    }

    private static VideoCard analyzeVideoCard(JSONObject json) {
        return new VideoCard(json.optString("title", ""), "",
                statPlay(json), json.optString("cover", ""),
                json.optLong("aid", 0), json.optString("bvid", ""));
    }

    private static String statPlay(JSONObject json) {
        JSONObject stat = json.optJSONObject("stat");
        return stat != null ? stat.optString("play", "") : "";
    }

    private static String analyzeRichText(JSONArray nodes) {
        if (nodes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null) sb.append(node.optString("text", ""));
        }
        return sb.toString();
    }

    /** 兼容 boolean / 0-1 数字 / 字符串 的取值 */
    private static boolean optBool(JSONObject o, String key) {
        if (o == null || !o.has(key)) return false;
        Object v = o.opt(key);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) {
            return "1".equals(v) || "true".equalsIgnoreCase((String) v);
        }
        return false;
    }
}
