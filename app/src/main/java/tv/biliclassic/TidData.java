package tv.biliclassic;

import java.util.HashMap;
import java.util.Map;

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
