/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 详情请参阅 <https://www.gnu.org/licenses/>
 */
package tv.biliclassic.api;

import org.json.JSONObject;

import tv.biliclassic.model.ArticleInfo;
import tv.biliclassic.util.NetWorkUtil;

/**
 * 专栏（文章）API。参考 BiliTerminal 的 ArticleApi，改为 org.json 解析。
 */
public class ArticleApi {

    /**
     * 获取专栏完整信息（标题/头图/作者/统计/正文 HTML）
     * 接口：https://api.bilibili.com/x/article/view?id=cvid
     */
    public static ArticleInfo getArticle(long id) {
        try {
            String url = "https://api.bilibili.com/x/article/view?id=" + id
                    + "&gaia_source=main_web&web_location=333.976";
            JSONObject result = NetWorkUtil.getJson(ConfInfoApi.signWBI(url));
            if (result == null || !result.has("data") || result.isNull("data")) {
                return null;
            }
            JSONObject data = result.getJSONObject("data");

            ArticleInfo a = new ArticleInfo();
            a.id = id;
            a.title = data.optString("title", "");
            a.summary = data.optString("summary", "");
            a.banner = data.optString("banner_url", "");
            a.ctime = data.optLong("ctime", 0);

            JSONObject author = data.optJSONObject("author");
            if (author != null) {
                a.authorMid = author.optLong("mid", 0);
                a.authorName = author.optString("name", "");
                a.authorFace = author.optString("face", "");
            }

            JSONObject stats = data.optJSONObject("stats");
            if (stats != null) {
                a.view = stats.optInt("view", 0);
                a.favorite = stats.optInt("favorite", 0);
                a.like = stats.optInt("like", 0);
                a.reply = stats.optInt("reply", 0);
                a.coin = stats.optInt("coin", 0);
            }

            a.wordCount = data.optInt("words", 0);
            a.keywords = data.optString("keywords", "");
            a.content = data.optString("content", "");
            return a;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
