using System;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class FavFolderItem
    {
        public long Fid { get; set; }
        public string Title { get; set; }

        public string Display
        {
            get { return Title + "（" + Count + "个视频）"; }
        }

        public int Count { get; set; }
    }

    public static class FavoriteService
    {
        private const string ApiRoot = "https://api.bilibili.com";
        private const int TypeVideo = 2;

        public static void Add(string aid, string bvid, long fid, Action<bool, string> onDone)
        {
            string csrf = BiliSession.Csrf;
            if (string.IsNullOrEmpty(csrf))
            {
                onDone(false, "请先登录");
                return;
            }
            if (string.IsNullOrEmpty(aid))
            {
                onDone(false, "缺少aid");
                return;
            }
            if (fid <= 0)
            {
                onDone(false, "收藏夹无效");
                return;
            }

            string suffix = MidSuffix();
            if (suffix.Length == 0)
            {
                onDone(false, "登录信息不完整");
                return;
            }

            string addFid = fid.ToString() + suffix;
            string form = "rid=" + aid
                + "&type=" + TypeVideo
                + "&add_media_ids=" + addFid
                + "&del_media_ids="
                + "&csrf=" + csrf;

            Http.PostForm(ApiRoot + "/x/v3/fav/resource/deal", form, Http.Referer,
                delegate(HttpResult http)
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
                    if (value == "11201")
                    {
                        onDone(true, "这个视频已经在这个收藏夹里了");
                        return;
                    }

                    string message = JsonText.ReadString(http.Body, "message");
                    onDone(false, "收藏失败 code=" + value
                        + (message.Length > 0 ? " " + message : ""));
                });
        }

        private static string MidSuffix()
        {
            string mid = BiliSession.Mid;
            if (string.IsNullOrEmpty(mid) || mid.Length < 2)
            {
                return "";
            }
            return mid.Substring(mid.Length - 2);
        }
    }
}
