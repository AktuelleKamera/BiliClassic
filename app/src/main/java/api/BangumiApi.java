package tv.biliclassic.api;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tv.biliclassic.model.Bangumi;
import tv.biliclassic.model.VideoCard;
import tv.biliclassic.util.NetWorkUtil;
import tv.biliclassic.util.SharedPreferencesUtil;
import tv.biliclassic.util.StringUtil;

public class BangumiApi {

    private static final String TAG = "BangumiApi";

    // 获取追番列表
    public static int getFollowingList(int page, List<VideoCard> cardList) throws JSONException, IOException {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid == 0) {
            Log.w(TAG, "未登录，无法获取追番列表");
            return -1;
        }

        String url = "https://api.bilibili.com/x/space/bangumi/follow/list?type=1&follow_status=0&pn=" + page
                + "&ps=15&vmid=" + mid;

        JSONObject all = NetWorkUtil.getJson(url);
        if (all.getInt("code") != 0) {
            throw new JSONException(all.getString("message"));
        }

        JSONObject data = all.getJSONObject("data");
        if (!data.has("list") || data.isNull("list")) {
            return 1;
        }

        JSONArray list = data.getJSONArray("list");
        if (list.length() == 0) {
            return 1;
        }

        for (int i = 0; i < list.length(); i++) {
            JSONObject bangumi = list.getJSONObject(i);
            VideoCard card = new VideoCard();
            card.type = "media_bangumi";
            card.aid = bangumi.getLong("media_id");
            card.title = bangumi.getString("title");
            card.cover = bangumi.getString("cover");
            card.view = StringUtil.toWan(bangumi.getJSONObject("stat").optInt("view"));
            cardList.add(card);
        }
        return 0;
    }

    /**
     * 通过 epid 获取完整番剧信息（直接使用 /pgc/view/web/season 接口，一次请求获取所有数据）
     */
    public static Bangumi getBangumiByEpid(long epid) {
        try {
            String url = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epid;
            JSONObject all = NetWorkUtil.getJson(url);
            Log.d(TAG, "getBangumiByEpid 响应: " + all.toString());

            if (all.getInt("code") != 0) {
                Log.e(TAG, "getBangumiByEpid 返回错误: " + all.optString("message"));
                return null;
            }
            JSONObject result = all.getJSONObject("result");

            Bangumi bangumi = new Bangumi();
            bangumi.info = parseInfoFromResult(result);
            bangumi.sectionList = parseSectionsFromResult(result);
            return bangumi;
        } catch (Exception e) {
            Log.e(TAG, "获取番剧信息失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从 result 解析基本信息
     */
    private static Bangumi.Info parseInfoFromResult(JSONObject result) throws JSONException {
        Bangumi.Info info = new Bangumi.Info();
        info.season_id = result.getLong("season_id");
        info.media_id = result.optLong("media_id", 0);
        info.title = result.optString("series_title", result.optString("title", ""));
        info.cover = result.optString("cover", "");
        info.cover_horizontal = result.optString("horizontal_cover", "");
        info.evaluate = result.optString("evaluate", "");
        info.season = result.optString("season_title", "");
        info.type_name = result.optString("type_name", "番剧");
        info.type = result.optInt("type", 0);

        // 更新状态（最新一集）
        info.indexShow = "敬请期待";
        if (result.has("episodes") && !result.isNull("episodes")) {
            JSONArray episodes = result.getJSONArray("episodes");
            if (episodes.length() > 0) {
                JSONObject firstEp = episodes.getJSONObject(0);
                String showTitle = firstEp.optString("show_title", "");
                if (showTitle != null && showTitle.length() > 0) {
                    info.indexShow = showTitle;
                } else {
                    info.indexShow = firstEp.optString("long_title", "更新中");
                }
            }
        }

        // 评分
        if (result.has("rating") && !result.isNull("rating")) {
            JSONObject rating = result.getJSONObject("rating");
            info.score = (float) rating.optDouble("score", 0.0);
            info.count = rating.optInt("count", 0);
        } else {
            info.score = 0.0f;
            info.count = 0;
        }

        // 地区
        JSONArray areas = result.optJSONArray("areas");
        StringBuilder sb = new StringBuilder();
        if (areas != null) {
            for (int i = 0; i < areas.length(); i++) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                JSONObject area = areas.optJSONObject(i);
                if (area != null) {
                    sb.append(area.optString("name", ""));
                }
            }
        }
        info.area_name = sb.toString();

        Log.d(TAG, "type_name: " + info.type_name + ", season: " + info.season + ", title: " + info.title);
        return info;
    }

    /**
     * 从 result 解析分集列表
     */
    private static ArrayList<Bangumi.Section> parseSectionsFromResult(JSONObject result) throws JSONException {
        ArrayList<Bangumi.Section> sectionList = new ArrayList<Bangumi.Section>();

        // 如果 episodes 存在，直接解析
        if (result.has("episodes") && !result.isNull("episodes")) {
            JSONArray episodes = result.getJSONArray("episodes");
            if (episodes.length() > 0) {
                Bangumi.Section section = new Bangumi.Section();
                section.id = 0;
                section.title = "正片";
                section.type = 0;
                section.episodeList = new ArrayList<Bangumi.Episode>();

                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject ep = episodes.getJSONObject(i);
                    Bangumi.Episode episode = new Bangumi.Episode();
                    episode.id = ep.getLong("id");
                    episode.aid = ep.getLong("aid");
                    episode.cid = ep.getLong("cid");
                    episode.cover = ep.optString("cover", "");
                    episode.badge = ep.optString("badge", "");
                    episode.title = ep.optString("title", "");
                    episode.titleLong = ep.optString("long_title", "");
                    episode.episodeIndex = ep.optInt("episode", i + 1);
                    if (episode.episodeIndex == 0) {
                        episode.episodeIndex = i + 1;
                    }
                    // 如果有 show_title 则使用
                    String showTitle = ep.optString("show_title", "");
                    if (showTitle != null && showTitle.length() > 0) {
                        episode.titleLong = showTitle;
                    }
                    section.episodeList.add(episode);
                }

                sectionList.add(section);
            }
        }

        // 如果有其他章节（番外、SP等）在 section 字段中
        JSONArray otherSections = result.optJSONArray("section");
        if (otherSections != null) {
            for (int i = 0; i < otherSections.length(); i++) {
                JSONObject sectionJson = otherSections.getJSONObject(i);
                String sectionTitle = sectionJson.optString("title", "其他");
                JSONArray sectionEpisodes = sectionJson.optJSONArray("episodes");
                if (sectionEpisodes != null && sectionEpisodes.length() > 0) {
                    Bangumi.Section section = new Bangumi.Section();
                    section.id = sectionJson.getLong("id");
                    section.title = sectionTitle;
                    section.type = sectionJson.getInt("type");
                    section.episodeList = new ArrayList<Bangumi.Episode>();

                    for (int j = 0; j < sectionEpisodes.length(); j++) {
                        JSONObject ep = sectionEpisodes.getJSONObject(j);
                        Bangumi.Episode episode = new Bangumi.Episode();
                        episode.id = ep.getLong("id");
                        episode.aid = ep.getLong("aid");
                        episode.cid = ep.getLong("cid");
                        episode.cover = ep.optString("cover", "");
                        episode.badge = ep.optString("badge", "");
                        episode.title = ep.optString("title", "");
                        episode.titleLong = ep.optString("long_title", "");
                        episode.episodeIndex = ep.optInt("episode", j + 1);
                        if (episode.episodeIndex == 0) {
                            episode.episodeIndex = j + 1;
                        }
                        String showTitle = ep.optString("show_title", "");
                        if (showTitle != null && showTitle.length() > 0) {
                            episode.titleLong = showTitle;
                        }
                        section.episodeList.add(episode);
                    }

                    sectionList.add(section);
                }
            }
        }

        return sectionList;
    }

    /**
     * 获取番剧详情（兼容旧接口，通过 seasonId）
     */
    public static Bangumi getBangumi(long seasonId) {
        try {
            Bangumi bangumi = new Bangumi();
            bangumi.info = getInfo(seasonId);
            if (bangumi.info == null) {
                return null;
            }
            Log.d(TAG, "seasonId: " + seasonId);
            Log.d(TAG, "bangumi: " + (bangumi != null ? "not null" : "null"));
            bangumi.sectionList = getSections(bangumi.info.season_id);
            return bangumi;
        } catch (Exception e) {
            Log.e(TAG, "获取番剧信息失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过 epid 获取番剧信息（包含 season_id 和 media_id）
     */
    public static class SeasonInfo {
        public long seasonId;
        public long mediaId;
    }

    public static SeasonInfo getSeasonInfoFromEpid(long epid) {
        try {
            String url = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epid;
            JSONObject all = NetWorkUtil.getJson(url);
            Log.d(TAG, "getSeasonInfoFromEpid 响应: " + all.toString());

            if (all.getInt("code") != 0) {
                Log.e(TAG, "getSeasonInfoFromEpid 返回错误: " + all.optString("message"));
                return null;
            }
            JSONObject result = all.getJSONObject("result");
            SeasonInfo info = new SeasonInfo();
            info.seasonId = result.optLong("season_id", 0);
            if (info.seasonId == 0) {
                info.seasonId = result.optLong("seasonId", 0);
            }
            info.mediaId = result.optLong("media_id", 0);
            if (info.mediaId == 0) {
                info.mediaId = result.optLong("mediaId", 0);
            }
            Log.d(TAG, "seasonId: " + info.seasonId + ", mediaId: " + info.mediaId);
            return info;
        } catch (Exception e) {
            Log.e(TAG, "getSeasonInfoFromEpid 异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 通过 epid 获取 season_id
     */
    public static long getSeasonIdFromEpid(long epid) {
        SeasonInfo info = getSeasonInfoFromEpid(epid);
        return info != null ? info.seasonId : 0L;
    }

    /**
     * 通过 epid 获取 media_id
     */
    public static long getMediaIdFromEpid(long epid) {
        SeasonInfo info = getSeasonInfoFromEpid(epid);
        return info != null ? info.mediaId : 0L;
    }

    /**
     * 获取番剧基本信息（封面、评分、标题等）
     */
    public static Bangumi.Info getInfo(long seasonId) throws IOException, JSONException {
        String url = "https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId;
        JSONObject all = NetWorkUtil.getJson(url);

        int code = all.getInt("code");
        if (code != 0) {
            if (code == -404) {
                throw new JSONException("番剧不存在或已下架");
            }
            throw new JSONException("错误码：" + code);
        }

        JSONObject result = all.getJSONObject("result");

        Bangumi.Info info = new Bangumi.Info();
        info.media_id = result.optLong("media_id", 0);
        info.season_id = result.optLong("season_id", 0);
        info.title = result.optString("title", "");
        info.cover = result.optString("cover", "");
        info.cover_horizontal = result.optString("horizontal_cover", "");
        info.type = result.optInt("type", 0);
        info.type_name = result.optString("type_name", "");

        if (info.type_name == null || info.type_name.length() == 0) {
            JSONArray styles = result.optJSONArray("styles");
            if (styles != null && styles.length() > 0) {
                info.type_name = styles.optString(0, "番剧");
            } else {
                info.type_name = "番剧";
            }
        }

        info.evaluate = result.optString("evaluate", "");

        JSONObject newEp = result.optJSONObject("new_ep");
        if (newEp != null) {
            String desc = newEp.optString("desc", "");
            String indexShow = newEp.optString("index_show", "");
            if (desc != null && desc.length() > 0) {
                info.indexShow = desc;
            } else {
                info.indexShow = indexShow;
            }
        } else {
            info.indexShow = "敬请期待";
        }

        JSONArray seasons = result.optJSONArray("seasons");
        if (seasons != null && seasons.length() > 0) {
            for (int i = 0; i < seasons.length(); i++) {
                JSONObject season = seasons.getJSONObject(i);
                long sid = season.optLong("season_id", 0);
                if (sid == info.season_id) {
                    String seasonTitle = season.optString("season_title", "");
                    if (seasonTitle != null && seasonTitle.length() > 0) {
                        info.season = seasonTitle;
                    }
                    break;
                }
            }
        }
        if (info.season == null || info.season.length() == 0) {
            info.season = info.title;
        }

        JSONObject rating = result.optJSONObject("rating");
        if (rating != null) {
            info.count = rating.optInt("count", 0);
            info.score = (float) rating.optDouble("score", 0.0);
        } else {
            info.count = 0;
            info.score = 0.0f;
        }

        JSONArray areas = result.optJSONArray("areas");
        StringBuilder sb = new StringBuilder();
        if (areas != null) {
            for (int i = 0; i < areas.length(); i++) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                JSONObject area = areas.optJSONObject(i);
                if (area != null) {
                    sb.append(area.optString("name", ""));
                }
            }
        }
        info.area_name = sb.toString();

        Log.d(TAG, "type_name: " + info.type_name);
        Log.d(TAG, "season: " + info.season);

        return info;
    }

    /**
     * 获取番剧分集列表
     */
    public static ArrayList<Bangumi.Section> getSections(long seasonId) throws IOException, JSONException {
        String url = "https://api.bilibili.com/pgc/web/season/section?season_id=" + seasonId;

        String jsonStr = NetWorkUtil.get(url);
        if (jsonStr == null || jsonStr.length() == 0) {
            throw new IOException("网络返回为空");
        }

        JSONObject all = new JSONObject(jsonStr);
        int code = all.optInt("code", -1);

        Log.d(TAG, "getSections code: " + code);

        if (code != 0) {
            String msg = all.optString("message", "未知错误");
            throw new JSONException("错误码：" + code + ", 消息：" + msg);
        }

        JSONObject result = all.getJSONObject("result");
        ArrayList<Bangumi.Section> sectionList = new ArrayList<Bangumi.Section>();

        JSONObject mainSection = result.optJSONObject("main_section");
        if (mainSection != null) {
            Bangumi.Section section = analyzeSection(mainSection);
            if (section.title == null || section.title.length() == 0) {
                section.title = "正片";
            }
            sectionList.add(section);
        }

        JSONArray otherSections = result.optJSONArray("section");
        if (otherSections != null) {
            for (int i = 0; i < otherSections.length(); i++) {
                JSONObject sectionJson = otherSections.getJSONObject(i);
                Bangumi.Section section = analyzeSection(sectionJson);
                if (section.episodeList != null && !section.episodeList.isEmpty()) {
                    if (section.title == null || section.title.length() == 0) {
                        section.title = "其他";
                    }
                    sectionList.add(section);
                }
            }
        }

        return sectionList;
    }

    public static Bangumi.Section analyzeSection(JSONObject json) throws JSONException {
        Bangumi.Section section = new Bangumi.Section();
        section.id = json.getLong("id");
        section.title = json.optString("title", "未知章节");
        section.type = json.getInt("type");

        JSONArray episodes = json.getJSONArray("episodes");
        ArrayList<Bangumi.Episode> episodeList = new ArrayList<Bangumi.Episode>();
        for (int i = 0; i < episodes.length(); i++) {
            episodeList.add(analyzeEpisode(episodes.getJSONObject(i)));
        }
        section.episodeList = episodeList;

        return section;
    }

    public static Bangumi.Episode analyzeEpisode(JSONObject json) throws JSONException {
        Bangumi.Episode episode = new Bangumi.Episode();
        episode.id = json.getLong("id");
        episode.aid = json.getLong("aid");
        episode.cid = json.getLong("cid");
        episode.cover = json.optString("cover", "");
        episode.badge = json.optString("badge", "");
        episode.title = json.optString("title", "");
        episode.titleLong = json.optString("long_title", "");

        try {
            episode.episodeIndex = Integer.parseInt(episode.title);
        } catch (NumberFormatException e) {
            episode.episodeIndex = 0;
        }

        if (episode.episodeIndex == 0) {
            episode.episodeIndex = json.optInt("index", 0);
        }
        if (episode.episodeIndex == 0) {
            episode.episodeIndex = json.optInt("episode", 0);
        }

        return episode;
    }
}