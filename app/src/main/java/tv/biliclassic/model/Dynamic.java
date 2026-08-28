package tv.biliclassic.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条动态（含转发的原动态摘要信息）
 */
public class Dynamic {
    public long dynamicId;
    public String type = "";
    public long mid;
    public String uname = "";
    public String avatar = "";
    public String pubTime = "";
    public String content = "";

    // 图片（DRAW / OPUS）
    public List<String> pics = new ArrayList<String>();

    // 卡片：视频/合集/番剧/专栏/直播 共用（title+cover），具体跳转看下面字段
    public VideoCard videoCard;
    public String cardLabel = "";
    public long articleId;
    public long roomId;
    public long epid;

    public int likeCount;
    public boolean liked;
    public boolean canDelete;

    // 评论锚点（basic.comment_id_str / comment_type，17=动态评论），供详情页评论区使用
    public long commentId;
    public int commentType;

    // 转发的原动态
    public Dynamic forward;
}
