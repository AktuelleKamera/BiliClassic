package tv.biliclassic.model;

import java.io.Serializable;

public class VideoCard implements Serializable {
    public String title;
    public String upName;
    public String view;
    public String cover;
    public String type = "video";
    /** 番剧 season_id（type 为 bangumi 时有效，用于跳转番剧详情） */
    public long seasonId = 0;
    public long aid;
    public String bvid;
    public long cid = 0;
    public int danmaku = 0;

    public VideoCard(String title, String upName, String view, String cover, long aid, String bvid, String type) {
        this.title = title;
        this.upName = upName;
        this.view = view;
        this.cover = cover;
        this.aid = aid;
        this.bvid = bvid;
        this.type = type;
    }

    public VideoCard(String title, String upName, String view, String cover, long aid, String bvid, long cid) {
        this.title = title;
        this.upName = upName;
        this.view = view;
        this.cover = cover;
        this.aid = aid;
        this.bvid = bvid;
        this.cid = cid;
    }

    public VideoCard(String title, String upName, String view, String cover, long aid, String bvid) {
        this.title = title;
        this.upName = upName;
        this.view = view;
        this.cover = cover;
        this.aid = aid;
        this.bvid = bvid;
    }

    public VideoCard(String title, String upName, String view, String cover, long aid, String bvid, int danmaku) {
        this.title = title;
        this.upName = upName;
        this.view = view;
        this.cover = cover;
        this.aid = aid;
        this.bvid = bvid;
        this.danmaku = danmaku;
    }

    public VideoCard() {
    }
}