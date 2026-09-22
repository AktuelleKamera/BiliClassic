using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public static class RankingService
    {
        private const string Endpoint =
            "https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all";

        private static readonly Regex BvidRegex = new Regex(@"""bvid"":\s*""([^""]+)""");
        private static readonly Regex PicRegex = new Regex(@"""pic"":\s*""([^""]+)""");
        private static readonly Regex TitleRegex =
            new Regex(@"""title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex NameRegex = new Regex(@"""name"":\s*""([^""]*)""");
        private static readonly Regex ViewRegex = new Regex(@"""view"":\s*(\d+)");
        private static readonly Regex DanmakuRegex = new Regex(@"""danmaku"":\s*(\d+)");

        public static void Fetch(Action<List<VideoItem>, string> onDone)
        {
            Http.GetText(Endpoint, Http.Referer, delegate(HttpResult http)
            {
                List<VideoItem> items = new List<VideoItem>();

                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(items, string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error);
                    return;
                }

                Match code = Regex.Match(http.Body, @"""code"":\s*(-?\d+)");
                if (code.Success && code.Groups[1].Value != "0")
                {
                    string message = JsonText.ReadString(http.Body, "message");
                    onDone(items, "接口返回 code=" + code.Groups[1].Value
                        + (message.Length > 0 ? " " + message : ""));
                    return;
                }

                MatchCollection anchors = BvidRegex.Matches(http.Body);
                for (int i = 0; i < anchors.Count; i++)
                {
                    int start = anchors[i].Index;
                    int end = (i + 1 < anchors.Count) ? anchors[i + 1].Index : http.Body.Length;
                    int window = Math.Min(end - start, 1500);
                    string region = http.Body.Substring(start, window);

                    VideoItem item = new VideoItem();
                    item.Bvid = anchors[i].Groups[1].Value;
                    item.Title = JsonText.StripHtml(
                        JsonText.Unescape(FirstGroup(TitleRegex, region)));
                    item.Pic = Normalize(FirstGroup(PicRegex, region));
                    item.Author = FirstGroup(NameRegex, region);
                    item.View = FirstGroup(ViewRegex, region);
                    item.Danmaku = FirstGroup(DanmakuRegex, region);

                    if (item.Bvid.Length > 0 && (item.Title.Length > 0 || item.Pic.Length > 0))
                    {
                        items.Add(item);
                    }
                }

                onDone(items, items.Count == 0 ? "没有解析到排行榜数据" : "");
            });
        }

        private static string FirstGroup(Regex regex, string text)
        {
            Match match = regex.Match(text);
            return match.Success ? match.Groups[1].Value : "";
        }

        private static string Normalize(string url)
        {
            if (url != null && url.StartsWith("//", StringComparison.Ordinal))
            {
                return "https:" + url;
            }
            return url ?? "";
        }
    }
}
