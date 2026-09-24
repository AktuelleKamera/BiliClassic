using System;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public static class InteractionService
    {
        private const string ApiRoot = "https://api.bilibili.com";

        public static void Like(string aid, int likeState, Action<bool, string> onDone)
        {
            if (!CheckLogin(aid, onDone))
            {
                return;
            }

            string form = "aid=" + aid + "&like=" + likeState + "&csrf=" + BiliSession.Csrf;
            Post(ApiRoot + "/x/web-interface/archive/like", form, Http.Referer, onDone);
        }

        public static void Coin(string aid, int multiply, Action<bool, string> onDone)
        {
            if (!CheckLogin(aid, onDone))
            {
                return;
            }

            string form = "aid=" + aid
                + "&multiply=" + multiply
                + "&select_like=0"
                + "&cross_domain=true"
                + "&from_spmid=333.788.0.0"
                + "&spmid=333.788.0.0"
                + "&statistics=%7B%22appId%22%3A100%2C%22platform%22%3A5%7D"
                + "&eab_x=2"
                + "&ramval=0"
                + "&source=web_normal"
                + "&csrf=" + BiliSession.Csrf;
            Post(ApiRoot + "/x/web-interface/coin/add", form,
                "https://www.bilibili.com/video/av" + aid, onDone);
        }

        public static void DeleteHistory(string aid, Action<bool, string> onDone)
        {
            if (string.IsNullOrEmpty(aid))
            {
                onDone(false, "缺少视频ID");
                return;
            }

            string form = "kid=" + aid + "&csrf=" + BiliSession.Csrf;
            Post(ApiRoot + "/x/v2/history/delete", form, Http.Referer, onDone);
        }

        public static void DeleteFolder(string fid, Action<bool, string> onDone)
        {
            if (string.IsNullOrEmpty(fid) || fid == "0")
            {
                onDone(false, "缺少收藏夹ID");
                return;
            }

            string form = "media_ids=" + fid + "&csrf=" + BiliSession.Csrf;
            Post(ApiRoot + "/x/v3/fav/folder/del", form,
                "https://space.bilibili.com/", onDone);
        }

        public static void DeleteFavorite(string aid, string bvid, string fid,
                                          Action<bool, string> onDone)
        {
            if (!CheckLogin(aid, onDone))
            {
                return;
            }
            if (string.IsNullOrEmpty(fid) || fid == "0")
            {
                onDone(false, "缺少收藏夹ID");
                return;
            }

            string form = "resources=" + aid + ":2&media_id=" + fid + "&csrf=" + BiliSession.Csrf;
            Post(ApiRoot + "/x/v3/fav/resource/batch-del", form,
                "https://space.bilibili.com/", onDone);
        }

        private static bool CheckLogin(string aid, Action<bool, string> onDone)
        {
            if (!BiliSession.IsLoggedIn || string.IsNullOrEmpty(BiliSession.Csrf))
            {
                onDone(false, "请先登录");
                return false;
            }
            if (string.IsNullOrEmpty(aid) || aid == "0")
            {
                onDone(false, "缺少视频ID");
                return false;
            }
            return true;
        }

        private static void Post(string url, string form, string referer, Action<bool, string> onDone)
        {
            Http.PostForm(url, form, referer, delegate(HttpResult http)
            {
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(false, string.IsNullOrEmpty(http.Error) ? "请求失败" : http.Error);
                    return;
                }

                Match code = Regex.Match(http.Body, @"""code"":\s*(-?\d+)");
                string value = code.Success ? code.Groups[1].Value : "?";

                if (value == "0")
                {
                    onDone(true, "");
                    return;
                }

                string message = JsonText.ReadString(http.Body, "message");
                onDone(false, "操作失败 code=" + value + (message.Length > 0 ? " " + message : ""));
            });
        }
    }
}
