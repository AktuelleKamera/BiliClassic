package tv.biliclassic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TidData {

    public static final int TID_BANGUMI = 13;
    public static final int TID_BANGUMI_TWO = 33;
    public static final int TID_BANGUMI_THREE = 34;

    public static final int TID_PART = 11;
    public static final int TID_PART_TWOELEMENT = 32;
    public static final int TID_PART_COORDINATE = 15;

    public static final int TID_DOUGA = 1;
    public static final int TID_DOUGA_MAD = 24;
    public static final int TID_DOUGA_MMD = 25;
    public static final int TID_DOUGA_KICHIKU = 26;
    public static final int TID_DOUGA_ELSE = 27;

    public static final int TID_ENT = 5;
    public static final int TID_ENT_LIFE = 21;
    public static final int TID_ENT_DANCE = 20;
    public static final int TID_ENT_KICHIKU = 22;
    public static final int TID_ENT_TELVISN = 23;

    public static final int TID_MUSIC = 3;
    public static final int TID_MUSIC_VIDEO = 28;
    public static final int TID_MUSIC_COORDINATE = 29;
    public static final int TID_MUSIC_VOCALOID = 30;
    public static final int TID_MUSIC_COVER = 31;

    public static final int TID_GAME = 4;
    public static final int TID_GAME_VIDEO = 17;
    public static final int TID_GAME_CTARY = 18;
    public static final int TID_GAME_MUGEN = 19;

    public static final int TID_TECH = 36;
    public static final int TID_TECH_POP_SCIENCE = 37;
    public static final int TID_TECH_GEO = 38;
    public static final int TID_TECH_FUTURE = 39;
    public static final int TID_TECH_WILD = 40;

    private static final Map<Integer, String> TID_NAME_MAP = new HashMap<Integer, String>();
    private static final Map<Integer, int[]> TID_GROUP_MAP = new HashMap<Integer, int[]>();
    /** 分区在现行体系下已无内容时，回退搜索用的关键词 */
    private static final Map<Integer, String> TID_SEARCH_KEYWORD_MAP = new HashMap<Integer, String>();
    /** 这些分区直接走搜索（region 列表内容过时或为空） */
    private static final Set<Integer> SEARCH_ONLY_TIDS = new HashSet<Integer>();
    /** 分区搜索的投稿时间范围 [begin, end]（秒），用于「旧番」等只取老视频 */
    private static final Map<Integer, long[]> TID_SEARCH_TIME_RANGE = new HashMap<Integer, long[]>();
    /** 分区搜索的排序方式（null=综合，pubdate=按时间，click/dm/stow）；默认按时间 */
    private static final Map<Integer, String> TID_SEARCH_ORDER_MAP = new HashMap<Integer, String>();

    static {
        TID_GROUP_MAP.put(TID_DOUGA, new int[]{TID_DOUGA_MAD, TID_DOUGA_MMD, TID_DOUGA_KICHIKU, TID_DOUGA_ELSE});
        TID_GROUP_MAP.put(TID_MUSIC, new int[]{TID_MUSIC_VIDEO, TID_MUSIC_COORDINATE, TID_MUSIC_VOCALOID, TID_MUSIC_COVER});
        TID_GROUP_MAP.put(TID_GAME, new int[]{TID_GAME_VIDEO, TID_GAME_CTARY, TID_GAME_MUGEN});
        TID_GROUP_MAP.put(TID_ENT, new int[]{TID_ENT_LIFE, TID_ENT_DANCE, TID_ENT_KICHIKU, TID_ENT_TELVISN});
        TID_GROUP_MAP.put(TID_PART, new int[]{TID_PART_TWOELEMENT, TID_PART_COORDINATE});
        TID_GROUP_MAP.put(TID_BANGUMI, new int[]{TID_BANGUMI_TWO, TID_BANGUMI_THREE});
        TID_GROUP_MAP.put(TID_TECH, new int[]{TID_TECH_POP_SCIENCE, TID_TECH_GEO, TID_TECH_FUTURE, TID_TECH_WILD});

        TID_NAME_MAP.put(TID_DOUGA, "动画");
        TID_NAME_MAP.put(TID_DOUGA_MAD, "MAD");
        TID_NAME_MAP.put(TID_DOUGA_MMD, "MMD");
        TID_NAME_MAP.put(TID_DOUGA_KICHIKU, "鬼畜");
        TID_NAME_MAP.put(TID_DOUGA_ELSE, "其他");

        TID_NAME_MAP.put(TID_MUSIC, "音乐");
        TID_NAME_MAP.put(TID_MUSIC_VIDEO, "视频");
        TID_NAME_MAP.put(TID_MUSIC_COORDINATE, "演奏");
        TID_NAME_MAP.put(TID_MUSIC_VOCALOID, "VOCALOID");
        TID_NAME_MAP.put(TID_MUSIC_COVER, "翻唱");

        TID_NAME_MAP.put(TID_GAME, "游戏");
        TID_NAME_MAP.put(TID_GAME_VIDEO, "视频");
        TID_NAME_MAP.put(TID_GAME_CTARY, "解说");
        TID_NAME_MAP.put(TID_GAME_MUGEN, "MUGEN");

        TID_NAME_MAP.put(TID_ENT, "娱乐");
        TID_NAME_MAP.put(TID_ENT_LIFE, "生活");
        TID_NAME_MAP.put(TID_ENT_DANCE, "舞蹈");
        TID_NAME_MAP.put(TID_ENT_KICHIKU, "鬼畜");
        TID_NAME_MAP.put(TID_ENT_TELVISN, "电视");

        TID_NAME_MAP.put(TID_PART, "连载"); // 电视剧
        TID_NAME_MAP.put(TID_PART_TWOELEMENT, "连载动画");
        TID_NAME_MAP.put(TID_PART_COORDINATE, "完结动画");

        TID_NAME_MAP.put(TID_BANGUMI, "番剧");
        TID_NAME_MAP.put(TID_BANGUMI_TWO, "新番");
        TID_NAME_MAP.put(TID_BANGUMI_THREE, "旧番");

        TID_NAME_MAP.put(TID_TECH, "科技");
        TID_NAME_MAP.put(TID_TECH_POP_SCIENCE, "科普");
        TID_NAME_MAP.put(TID_TECH_GEO, "地理");
        TID_NAME_MAP.put(TID_TECH_FUTURE, "未来");
        TID_NAME_MAP.put(TID_TECH_WILD, "自然");

        // 分区无内容时回退搜索的关键词（可按需调整）
        TID_SEARCH_KEYWORD_MAP.put(TID_DOUGA_MAD, "MAD·AMV");
        TID_SEARCH_KEYWORD_MAP.put(TID_DOUGA_MMD, "MMD");
        TID_SEARCH_KEYWORD_MAP.put(TID_DOUGA_KICHIKU, "鬼畜");
        TID_SEARCH_KEYWORD_MAP.put(TID_DOUGA_ELSE, "动画短片");
        TID_SEARCH_KEYWORD_MAP.put(TID_MUSIC_VIDEO, "音乐视频");
        TID_SEARCH_KEYWORD_MAP.put(TID_MUSIC_COORDINATE, "乐器演奏");
        TID_SEARCH_KEYWORD_MAP.put(TID_MUSIC_VOCALOID, "VOCALOID");
        TID_SEARCH_KEYWORD_MAP.put(TID_MUSIC_COVER, "翻唱");
        TID_SEARCH_KEYWORD_MAP.put(TID_GAME_VIDEO, "游戏视频");
        TID_SEARCH_KEYWORD_MAP.put(TID_GAME_CTARY, "游戏解说");
        TID_SEARCH_KEYWORD_MAP.put(TID_GAME_MUGEN, "MUGEN");
        TID_SEARCH_KEYWORD_MAP.put(TID_ENT_LIFE, "生活");
        TID_SEARCH_KEYWORD_MAP.put(TID_ENT_DANCE, "舞蹈");
        TID_SEARCH_KEYWORD_MAP.put(TID_ENT_KICHIKU, "鬼畜");
        TID_SEARCH_KEYWORD_MAP.put(TID_ENT_TELVISN, "电视剧");
        TID_SEARCH_KEYWORD_MAP.put(TID_PART_TWOELEMENT, "连载动画");
        TID_SEARCH_KEYWORD_MAP.put(TID_PART_COORDINATE, "完结动画");
        TID_SEARCH_KEYWORD_MAP.put(TID_BANGUMI_TWO, "新番");
        TID_SEARCH_KEYWORD_MAP.put(TID_BANGUMI_THREE, "番剧");
        TID_SEARCH_KEYWORD_MAP.put(TID_TECH_POP_SCIENCE, "科普");
        TID_SEARCH_KEYWORD_MAP.put(TID_TECH_GEO, "地理科普");
        TID_SEARCH_KEYWORD_MAP.put(TID_TECH_FUTURE, "未来科技");
        TID_SEARCH_KEYWORD_MAP.put(TID_TECH_WILD, "自然纪录片");

        // 新番/旧番的 region 列表内容过时或为空，直接走搜索（按时间排序）
        SEARCH_ONLY_TIDS.add(TID_BANGUMI_TWO);
        SEARCH_ONLY_TIDS.add(TID_BANGUMI_THREE);

        // 旧番：只要 2015 年以前的投稿。注意 B 站时间过滤必须 begin+end 同时给，
        // 且 begin 不能为 0，否则过滤会被忽略（1245945600 = 2009-06-26，B站建站日）
        TID_SEARCH_TIME_RANGE.put(TID_BANGUMI_THREE, new long[]{1245945600L, 1441123199L});
        // 旧番用综合排序：按时间(pubdate)会把东方/MUGEN/MMD 等杂物排到前面
        TID_SEARCH_ORDER_MAP.put(TID_BANGUMI_THREE, "");
    }

    /** 分区搜索的投稿起始时间（秒），0 表示不限 */
    public static long getSearchBeginTime(int tid) {
        long[] range = TID_SEARCH_TIME_RANGE.get(tid);
        return range != null ? range[0] : 0L;
    }

    /** 分区搜索的投稿截止时间（秒），0 表示不限 */
    public static long getSearchEndTime(int tid) {
        long[] range = TID_SEARCH_TIME_RANGE.get(tid);
        return range != null ? range[1] : 0L;
    }

    /** 分区搜索的排序方式，默认按时间（pubdate）；返回空串表示综合排序 */
    public static String getSearchOrder(int tid) {
        String order = TID_SEARCH_ORDER_MAP.get(tid);
        return order != null ? order : "pubdate";
    }

    /** 该分区是否直接走搜索 */
    public static boolean isSearchOnly(int tid) {
        return SEARCH_ONLY_TIDS.contains(tid);
    }

    /** 分区无内容时回退搜索的关键词（无配置则用分区名） */
    public static String getSearchKeyword(int tid) {
        String keyword = TID_SEARCH_KEYWORD_MAP.get(tid);
        return keyword != null ? keyword : getNameByTid(tid);
    }

    public static int[] getTidGroup(int tid) {
        int[] group = TID_GROUP_MAP.get(tid);
        return group != null ? group : new int[0];
    }

    public static String getNameByTid(int tid) {
        String name = TID_NAME_MAP.get(tid);
        return name != null ? name : "未知";
    }

    public static int[] getMainCategories() {
        return new int[]{TID_BANGUMI, TID_PART, TID_DOUGA, TID_ENT, TID_MUSIC, TID_GAME, TID_TECH};
    }
}
