/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 详情请参阅 <https://www.gnu.org/licenses/>
 */
package tv.biliclassic.model;

import java.io.Serializable;

/**
 * 专栏（文章）详情。字段与 B 站 x/article/view 接口一致，参考 BiliTerminal 的 ArticleInfo。
 */
public class ArticleInfo implements Serializable {
    public long id;
    public String title;
    public String summary;   // 摘要
    public String banner;    // 头图
    public long authorMid;
    public String authorName;
    public String authorFace;
    public long ctime;
    public int view;
    public int favorite;
    public int like;
    public int reply;
    public int coin;
    public int wordCount;    // 字数
    public String keywords;
    public String content;   // 正文（HTML）
}
