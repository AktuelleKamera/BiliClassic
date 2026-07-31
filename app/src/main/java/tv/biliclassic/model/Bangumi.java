package tv.biliclassic.model;

import java.io.Serializable;
import java.util.ArrayList;

import tv.biliclassic.model.PlayerData;
import tv.biliclassic.util.SharedPreferencesUtil;

public class Bangumi {
    public Info info;
    public ArrayList<Section> sectionList;

    public static class Info {
        public long media_id;
        public long season_id;
        public int type;
        public int count;
        public float score;
        public String title;
        public String cover;
        public String cover_horizontal;
        public String type_name;
        public String area_name;
        public String indexShow;
        public String evaluate;
        public String season;
    }

    public static class Section {
        public long id;
        public int type;
        public String title;
        public ArrayList<Episode> episodeList;

        public Section() {
        }
    }

    public static class Episode implements Serializable {
        public long id;
        public long aid;
        public long cid;
        public String title;
        public String titleLong;
        public String cover;
        public String badge;
        public boolean isDivider = false;  // 是否为分割标题
        public int episodeIndex = 0;  // 实际集数

        public Episode() {
        }

        public PlayerData toPlayerData() {
            PlayerData data = new PlayerData(PlayerData.TYPE_BANGUMI);
            data.aid = aid;
            data.cid = cid;
            data.title = title;
            data.mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            return data;
        }
    }
}