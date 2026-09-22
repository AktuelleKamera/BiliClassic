using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class RelatedResult
    {
        public List<VideoItem> Items = new List<VideoItem>();

        public string Error = "";
    }

    public static class RelatedService
    {
        public const string Endpoint = "https://api.bilibili.com/x/web-interface/archive/related";

        private const int MaxWindow = 2000;

        private static readonly Regex PicRegex = new Regex(@"""pic"":\s*""([^""]+)""");
        private static readonly Regex BvidRegex = new Regex(@"""bvid"":\s*""([^""]+)""");
        private static readonly Regex TitleRegex = new Regex(@"""title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex NameRegex = new Regex(@"""name"":\s*""([^""]*)""");
        private static readonly Regex ViewRegex = new Regex(@"""view"":\s*(\d+)");
        private static readonly Regex DanmakuRegex = new Regex(@"""danmaku"":\s*(\d+)");
        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");

        public static void Fetch(string bvid, Action<RelatedResult> onDone)
        {
            RelatedResult result = new RelatedResult();

            if (string.IsNullOrEmpty(bvid))
            {
                result.Error = "缺少 BV 号";
                onDone(result);
                return;
            }

            string url = Endpoint + "?bvid=" + bvid;

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                try
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        result.Error = (http == null || string.IsNullOrEmpty(http.Error))
                            ? "相关推荐请求无响应"
                            : "相关推荐请求失败: " + http.Error;
                    }
                    else
                    {
                        Parse(http.Body, result);
                    }
                }
                catch (Exception ex)
                {
                    result.Error = "解析相关推荐失败: " + ex.Message;
                }
                onDone(result);
            });
        }

        private static void Parse(string body, RelatedResult result)
        {
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = JsonText.ReadString(body, "message");
                result.Error = "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
                return;
            }

            MatchCollection pics = PicRegex.Matches(body);
            for (int i = 0; i < pics.Count; i++)
            {
                Match pic = pics[i];

                int next = (i + 1 < pics.Count) ? pics[i + 1].Index : body.Length;
                int limit = next > pic.Index ? next : body.Length;
                if (limit > pic.Index + MaxWindow)
                {
                    limit = pic.Index + MaxWindow;
                }
                string window = body.Substring(pic.Index, limit - pic.Index);

                string bvid = FirstGroup(BvidRegex, window);
                if (bvid.Length == 0)
                {
                    continue;
                }

                VideoItem item = new VideoItem();
                item.Bvid = bvid;
                item.Pic = pic.Groups[1].Value;
                if (item.Pic.StartsWith("//", StringComparison.Ordinal))
                {
                    item.Pic = "https:" + item.Pic;
                }
                item.Title = JsonText.StripHtml(JsonText.Unescape(FirstGroup(TitleRegex, window)));
                item.Author = FirstGroup(NameRegex, window);
                item.View = FirstGroup(ViewRegex, window);
                item.Danmaku = FirstGroup(DanmakuRegex, window);
                result.Items.Add(item);
            }
        }

        private static string FirstGroup(Regex regex, string text)
        {
            Match match = regex.Match(text);
            return match.Success ? match.Groups[1].Value : "";
        }
    }
}
