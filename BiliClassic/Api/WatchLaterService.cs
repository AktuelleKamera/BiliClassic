using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public static class WatchLaterService
    {
        private const string ApiRoot = "https://api.bilibili.com";

        private const string ListUrl = ApiRoot + "/x/v2/history/toview/web";

        public static void Fetch(Action<List<VideoItem>, string> onDone)
        {
            Http.GetText(ListUrl, Http.Referer, delegate(HttpResult http)
            {
                List<VideoItem> items = new List<VideoItem>();
                string error = Parse(http, items);
                onDone(items, error);
            });
        }

        public static void Add(string aid, Action<bool, string> onDone)
        {
            Post("/x/v2/history/toview/add", aid, onDone);
        }

        public static void Delete(string aid, Action<bool, string> onDone)
        {
            Post("/x/v2/history/toview/del", aid, onDone);
        }

        private static void Post(string path, string aid, Action<bool, string> onDone)
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

            string form = "aid=" + aid + "&csrf=" + csrf;
            Http.PostForm(ApiRoot + path, form, Http.Referer, delegate(HttpResult http)
            {
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(false, string.IsNullOrEmpty(http.Error) ? "请求失败" : http.Error);
                    return;
                }

                Match code = Regex.Match(http.Body, @"""code"":\s*(-?\d+)");
                if (code.Success && code.Groups[1].Value == "0")
                {
                    onDone(true, "");
                    return;
                }

                string message = JsonText.ReadString(http.Body, "message");
                onDone(false, "失败 code=" + (code.Success ? code.Groups[1].Value : "?")
                    + (message.Length > 0 ? " " + message : ""));
            });
        }

        private static string Parse(HttpResult http, List<VideoItem> items)
        {
            if (http == null || string.IsNullOrEmpty(http.Body))
            {
                return string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error;
            }

            string body = http.Body;
            Match code = Regex.Match(JsonText.Head(body, 200), @"""code"":\s*(-?\d+)");
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = JsonText.ReadString(body, "message");
                return "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
            }

            MatchCollection anchors = Regex.Matches(body, @"""bvid"":\s*""([^""]+)""");
            int cursor = 0;
            for (int i = 0; i < anchors.Count; i++)
            {
                int end = anchors[i].Index;
                string region = body.Substring(cursor, end - cursor);
                cursor = end;

                VideoItem item = new VideoItem();
                item.Bvid = anchors[i].Groups[1].Value;
                item.Aid = LastNumber(region, "aid").ToString();
                item.Title = JsonText.StripHtml(LastString(region, "title"));
                item.Pic = Normalize(LastString(region, "pic"));
                item.Author = LastString(region, "name");

                long view = LastNumber(region, "view");
                if (view > 0)
                {
                    item.View = view.ToString();
                }

                if (item.Bvid.Length == 0 && item.Title.Length == 0)
                {
                    continue;
                }
                items.Add(item);
            }

            return "";
        }

        private static string LastString(string json, string field)
        {
            MatchCollection matches = Regex.Matches(json,
                @"""" + field + @""":\s*""((?:[^""\\]|\\.)*)""");
            return matches.Count > 0
                ? JsonText.Unescape(matches[matches.Count - 1].Groups[1].Value)
                : "";
        }

        private static long LastNumber(string json, string field)
        {
            MatchCollection matches = Regex.Matches(json, @"""" + field + @""":\s*(-?\d+)");
            long value;
            return matches.Count > 0
                && long.TryParse(matches[matches.Count - 1].Groups[1].Value, out value)
                ? value
                : 0;
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
