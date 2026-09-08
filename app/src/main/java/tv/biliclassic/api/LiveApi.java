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
 *
 * 安卓2也要看B站！
 */
package tv.biliclassic.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import tv.biliclassic.model.LivePlayInfo;
import tv.biliclassic.model.LiveRoom;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;

/**
 * 生放送（直播）API 测试实现。
 * 参考 BiliTerminal 的 LiveApi，改为 org.json 解析以适配本项目。
 */
public class LiveApi {
    private static final String TAG = "LiveApi";

    // 常用清晰度（qn 值与 B 站一致）；实际可用清晰度以 g_qn_desc 为准
    public static final LinkedHashMap<String, Integer> QualityMap = new LinkedHashMap<String, Integer>();
    static {
        QualityMap.put("流畅", Integer.valueOf(80));
        QualityMap.put("高清", Integer.valueOf(150));
        QualityMap.put("超清", Integer.valueOf(250));
        QualityMap.put("蓝光", Integer.valueOf(400));
        QualityMap.put("原画", Integer.valueOf(10000));
    }

    // 本进程内是否已检查过真实 buvid3
    private static boolean sBuvidEnsured = false;
    /** 确保请求前已获取服务器签发的 buvid3（内部合并进 Cookie 存储） */
    private static void ensureBuvid() {
        if (!sBuvidEnsured) {
            sBuvidEnsured = true;
            String stored = SharedPreferencesUtil.getString("cookies", "");
            if (NetWorkUtil.getInfoFromCookie("buvid3", stored).length() == 0) {
                NetWorkUtil.fetchBuvid3();
            }
        }
    }


    /**
     * 带风控重试的 GET：返回 -352/-412 时补齐真实设备 Cookie 再试一次
     */
    private static JSONObject getJsonWithRiskRetry(String url) throws IOException, JSONException {
        ensureBuvid();
        JSONObject result = NetWorkUtil.getJson(url);
        int code = result == null ? -1 : result.optInt("code", -1);
        if (code == -352 || code == -412) {
            Log.w(TAG, "风控 code=" + code + "，重新获取 buvid 后重试");
            NetWorkUtil.fetchBuvid3();
            result = NetWorkUtil.getJson(url);
        }
        return result;
    }

    /**
     * 关注的生放送（正在直播的关注房间，需登录 Cookie，与 B 站"关注"页一致）
     *
     * @param page 页码（从 1 开始）
     * @param out  输出列表
     * @return 0 成功；-1 接口返回失败（含未登录）
     */
    public static int getFollowedRooms(int page, List<LiveRoom> out) throws IOException, JSONException {
        String url = "https://api.live.bilibili.com/xlive/web-ucenter/v1/xfetter/GetWebList";
        url += new NetWorkUtil.FormData().setUrlParam(true)
                .put("page", page)
                .put("page_size", 10);

        JSONObject result = getJsonWithRiskRetry(url);
        int code = result == null ? -1 : result.optInt("code", -1);
        if (code != 0) {
            Log.w(TAG, "getFollowedRooms code=" + code + " msg=" + result.optString("message"));
            return -1;
        }

        JSONObject data = result.optJSONObject("data");
        if (data == null) return -1;
        JSONArray rooms = data.optJSONArray("rooms");
        if (rooms == null) rooms = data.optJSONArray("list");
        if (rooms == null) return 0;

        for (int i = 0; i < rooms.length(); i++) {
            JSONObject item = rooms.optJSONObject(i);
            if (item == null) continue;
            LiveRoom room = analyzeRoom(item);
            if (room.realRoomId() == 0) continue;
            // 接口若返回未开播房间则过滤；字段缺失时不过滤（避免整页被清空）
            if (item.has("live_status") && room.live_status != 1) continue;
            out.add(room);
        }
        return 0;
    }

    /**
     * 房间信息（标题/简介/状态等）
     */
    public static LiveRoom getRoomInfo(long roomId) throws IOException, JSONException {
        String url = "https://api.live.bilibili.com/room/v1/Room/get_info";
        url += new NetWorkUtil.FormData().setUrlParam(true).put("room_id", roomId);

        JSONObject result = getJsonWithRiskRetry(url);
        if (result == null || result.optInt("code", -1) != 0) return null;
        JSONObject data = result.optJSONObject("data");
        if (data == null) return null;

        LiveRoom room = analyzeRoom(data);
        if (room.roomid <= 0) room.roomid = roomId;
        return room;
    }

    /**
     * 播放信息（流地址/清晰度/线路）
     *
     * @param qn 期望清晰度；0 表示使用服务器默认
     */
    public static LivePlayInfo getRoomPlayInfo(long roomId, int qn) throws IOException, JSONException {
        String url = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo";
        url += new NetWorkUtil.FormData().setUrlParam(true)
                .put("room_id", roomId)
                .put("qn", qn)
                .put("protocol", "0,1")
                .put("format", "0,1,2")
                .put("codec", "0,1,2")
                .put("platform", "web")
                .put("ptype", 8)
                .put("dolby", 5)
                .put("panorama", 1);

        JSONObject result = getJsonWithRiskRetry(url);
        int code = result == null ? -1 : result.optInt("code", -1);
        if (code != 0) {
            Log.w(TAG, "getRoomPlayInfo code=" + code + " msg=" + (result == null ? "" : result.optString("message")));
            return null;
        }
        JSONObject data = result.optJSONObject("data");
        if (data == null) return null;
        return analyzePlayInfo(data);
    }

    /**
     * 从 JSON 填充 LiveRoom（推荐列表条目与 get_info 结构字段名基本一致）
     */
    public static LiveRoom analyzeRoom(JSONObject json) {
        LiveRoom room = new LiveRoom();
        if (json == null) return room;
        room.roomid = json.optLong("roomid", json.optLong("room_id", 0));
        room.short_id = json.optLong("short_id", 0);
        room.uid = json.optLong("uid", 0);
        room.title = json.optString("title", "");
        room.uname = json.optString("uname", "");
        room.face = json.optString("face", "");
        room.cover = json.optString("cover", "");
        room.user_cover = json.optString("user_cover", "");
        room.system_cover = json.optString("system_cover", "");
        room.keyframe = json.optString("keyframe", "");
        room.tags = json.optString("tags", "");
        room.description = json.optString("description", "");
        room.online = json.optInt("online", 0);
        room.attention = json.optInt("attention", 0);
        room.area_name = json.optString("area_name", "");
        if (room.area_name.length() == 0) room.area_name = json.optString("area_v2_name", "");
        room.area_parent_name = json.optString("area_parent_name", "");
        if (room.area_parent_name.length() == 0) {
            room.area_parent_name = json.optString("area_v2_parent_name", "");
        }
        if (room.area_parent_name.length() == 0) {
            room.area_parent_name = json.optString("parent_name", "");
        }
        room.live_status = json.optInt("live_status", 0);
        room.liveTime = json.optString("live_time", "");

        if (room.uname.length() == 0) room.uname = json.optString("nickname", "");

        // 联合观看数（watched_show.text_small，如 "12 人看过"）
        JSONObject watched = json.optJSONObject("watched_show");
        if (watched != null) {
            room.watched_text = watched.optString("text_small", "");
        }

        if (room.roomid <= 0 && room.short_id > 0) room.roomid = room.short_id;
        return room;
    }

    /**
     * 从 getRoomPlayInfo 的 data 填充 LivePlayInfo
     */
    public static LivePlayInfo analyzePlayInfo(JSONObject data) {
        LivePlayInfo info = new LivePlayInfo();
        if (data == null) return info;
        info.roomid = data.optLong("room_id", 0);
        info.short_id = data.optLong("short_id", 0);
        info.uid = data.optLong("uid", 0);
        info.live_status = data.optInt("live_status", 0);
        info.live_time = data.optLong("live_time", 0);

        JSONObject playurlInfo = data.optJSONObject("playurl_info");
        if (playurlInfo == null) return info;
        JSONObject playurl = playurlInfo.optJSONObject("playurl");
        if (playurl == null) return info;

        LivePlayInfo.PlayUrl pu = new LivePlayInfo.PlayUrl();
        pu.cid = playurl.optLong("cid", 0);
        pu.dolby_qn = playurl.optInt("dolby_qn", 0);

        JSONArray qnDesc = playurl.optJSONArray("g_qn_desc");
        if (qnDesc != null) {
            pu.g_qn_desc = new ArrayList<LivePlayInfo.QnDesc>();
            for (int i = 0; i < qnDesc.length(); i++) {
                JSONObject q = qnDesc.optJSONObject(i);
                if (q == null) continue;
                LivePlayInfo.QnDesc d = new LivePlayInfo.QnDesc();
                d.qn = q.optInt("qn", 0);
                d.desc = q.optString("desc", "");
                pu.g_qn_desc.add(d);
            }
        }

        JSONArray streams = playurl.optJSONArray("stream");
        if (streams != null) {
            pu.stream = new ArrayList<LivePlayInfo.ProtocolInfo>();
            for (int i = 0; i < streams.length(); i++) {
                JSONObject s = streams.optJSONObject(i);
                if (s == null) continue;
                LivePlayInfo.ProtocolInfo pi = new LivePlayInfo.ProtocolInfo();
                pi.protocol_name = s.optString("protocol_name", "");

                JSONArray formats = s.optJSONArray("format");
                if (formats != null) {
                    pi.format = new ArrayList<LivePlayInfo.Format>();
                    for (int j = 0; j < formats.length(); j++) {
                        JSONObject f = formats.optJSONObject(j);
                        if (f == null) continue;
                        LivePlayInfo.Format fi = new LivePlayInfo.Format();
                        fi.format_name = f.optString("format_name", "");
                        fi.master_url = f.optString("master_url", "");

                        JSONArray codecs = f.optJSONArray("codec");
                        if (codecs != null) {
                            fi.codec = new ArrayList<LivePlayInfo.Codec>();
                            for (int k = 0; k < codecs.length(); k++) {
                                JSONObject c = codecs.optJSONObject(k);
                                if (c == null) continue;
                                LivePlayInfo.Codec ci = new LivePlayInfo.Codec();
                                ci.codec_name = c.optString("codec_name", "");
                                ci.current_qn = c.optInt("current_qn", 0);
                                ci.base_url = c.optString("base_url", "");
                                ci.hdr_qn = c.optInt("hdr_qn", 0);
                                ci.dolby_type = c.optInt("dolby_type", 0);
                                ci.attr_name = c.optString("attr_name", "");

                                JSONArray acceptQn = c.optJSONArray("accept_qn");
                                if (acceptQn != null) {
                                    ci.accept_qn = new ArrayList<Integer>();
                                    for (int m = 0; m < acceptQn.length(); m++) {
                                        ci.accept_qn.add(Integer.valueOf(acceptQn.optInt(m, 0)));
                                    }
                                }

                                JSONArray urlInfos = c.optJSONArray("url_info");
                                if (urlInfos != null) {
                                    ci.url_info = new ArrayList<LivePlayInfo.UrlInfo>();
                                    for (int n = 0; n < urlInfos.length(); n++) {
                                        JSONObject u = urlInfos.optJSONObject(n);
                                        if (u == null) continue;
                                        LivePlayInfo.UrlInfo ui = new LivePlayInfo.UrlInfo();
                                        ui.host = u.optString("host", "");
                                        ui.extra = u.optString("extra", "");
                                        ui.stream_ttl = u.optInt("stream_ttl", 0);
                                        ci.url_info.add(ui);
                                    }
                                }
                                fi.codec.add(ci);
                            }
                        }
                        pi.format.add(fi);
                    }
                }
                pu.stream.add(pi);
            }
        }

        info.playUrl = pu;
        return info;
    }

    /**
     * 拼接播放地址：CDN host + base_url + extra
     */
    public static String buildPlayUrl(LivePlayInfo info, int streamIndex, int formatIndex,
                                      int codecIndex, int urlIndex) {
        try {
            LivePlayInfo.ProtocolInfo p = info.playUrl.stream.get(streamIndex);
            LivePlayInfo.Format f = p.format.get(formatIndex);
            LivePlayInfo.Codec c = f.codec.get(codecIndex);
            LivePlayInfo.UrlInfo u = c.url_info.get(urlIndex);
            return u.host + c.base_url + u.extra;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 选一个播放兼容性最好的流：优先 FLV（http_stream + flv，老设备 IJK 支持最好），
     * 找不到时返回第一个可用流。返回 {stream, format, codec} 下标，null 表示无可用流。
     */
    public static int[] pickPreferredStream(LivePlayInfo info) {
        if (info == null || info.playUrl == null || info.playUrl.stream == null) return null;
        int[] first = null;
        for (int s = 0; s < info.playUrl.stream.size(); s++) {
            LivePlayInfo.ProtocolInfo p = info.playUrl.stream.get(s);
            if (p == null || p.format == null) continue;
            for (int f = 0; f < p.format.size(); f++) {
                LivePlayInfo.Format fi = p.format.get(f);
                if (fi == null || fi.codec == null || fi.codec.isEmpty()) continue;
                for (int c = 0; c < fi.codec.size(); c++) {
                    LivePlayInfo.Codec ci = fi.codec.get(c);
                    if (ci == null || ci.url_info == null || ci.url_info.isEmpty()) continue;
                    if (first == null) {
                        first = new int[]{s, f, c};
                    }
                    if ("http_stream".equals(p.protocol_name) && "flv".equals(fi.format_name)) {
                        return new int[]{s, f, c};
                    }
                }
            }
        }
        return first;
    }
}
