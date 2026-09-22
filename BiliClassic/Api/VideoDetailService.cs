using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class VideoPart
    {
        public string Cid = "";
        public string Page = "";
        public string Title = "";
        public string Duration = "";

        public string DisplayTitle
        {
            get
            {
                if (string.IsNullOrEmpty(Page))
                {
                    return Title ?? "";
                }
                return "P" + Page + "  " + (Title ?? "");
            }
        }

        public string DurationText
        {
            get { return FormatDuration(Duration); }
        }

        public string IndexText
        {
            get { return "P" + Page; }
        }

        public string PartTitle
        {
            get { return Title ?? ""; }
        }

        public static string FormatDuration(string raw)
        {
            int seconds;
            if (!int.TryParse(raw, out seconds) || seconds <= 0)
            {
                return "";
            }
            if (seconds >= 3600)
            {
                return (seconds / 3600).ToString()
                    + ":" + ((seconds % 3600) / 60).ToString("D2")
                    + ":" + (seconds % 60).ToString("D2");
            }
            return (seconds / 60).ToString() + ":" + (seconds % 60).ToString("D2");
        }
    }

    public sealed class VideoDetail
    {
        public string Bvid = "";

        public string Aid = "";

        public string Title = "";
        public string Pic = "";
        public string Author = "";

        public string AuthorMid = "";
        public string Desc = "";
        public string View = "";
        public string Danmaku = "";
        public string Like = "";
        public string Coin = "";
        public string Favorite = "";
        public string Reply = "";
        public string PubDate = "";
        public string Duration = "";

        public List<VideoPart> Parts = new List<VideoPart>();

        public string Error = "";

        public bool IsMultiPart
        {
            get { return Parts.Count > 1; }
        }
    }

    public static class VideoDetailService
    {
        public const string Endpoint = "https://api.bilibili.com/x/web-interface/view";

        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");
        private static readonly Regex AidRegex = new Regex(@"""aid"":\s*(\d+)");
        private static readonly Regex TitleRegex = new Regex(@"""title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex PicRegex = new Regex(@"""pic"":\s*""([^""]+)""");
        private static readonly Regex DescRegex = new Regex(@"""desc"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex DurationRegex = new Regex(@"""duration"":\s*(\d+)");
        private static readonly Regex PubDateRegex = new Regex(@"""pubdate"":\s*(\d+)");
        private static readonly Regex NameRegex = new Regex(@"""name"":\s*""([^""]*)""");
        private static readonly Regex MidRegex = new Regex(@"""mid"":\s*(\d+)");
        private static readonly Regex ViewRegex = new Regex(@"""view"":\s*(\d+)");
        private static readonly Regex DanmakuRegex = new Regex(@"""danmaku"":\s*(\d+)");
        private static readonly Regex LikeRegex = new Regex(@"""like"":\s*(\d+)");
        private static readonly Regex CoinRegex = new Regex(@"""coin"":\s*(\d+)");
        private static readonly Regex FavoriteRegex = new Regex(@"""favorite"":\s*(\d+)");
        private static readonly Regex ReplyRegex = new Regex(@"""reply"":\s*(\d+)");
        private static readonly Regex CidRegex = new Regex(@"""cid"":\s*(\d+)");
        private static readonly Regex PageRegex = new Regex(@"""page"":\s*(\d+)");
        private static readonly Regex PartRegex = new Regex(@"""part"":\s*""((?:[^""\\]|\\.)*)""");

        public static void Fetch(string bvid, Action<VideoDetail> onDone)
        {
            VideoDetail detail = new VideoDetail();
            detail.Bvid = bvid ?? "";

            if (string.IsNullOrEmpty(bvid))
            {
                detail.Error = "缺少 BV 号";
                onDone(detail);
                return;
            }

            string url = Endpoint + "?bvid=" + bvid;

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                try
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        detail.Error = (http == null || string.IsNullOrEmpty(http.Error))
                            ? "详情请求无响应"
                            : "详情请求失败: " + http.Error;
                    }
                    else
                    {
                        Parse(http.Body, detail);
                    }
                }
                catch (Exception ex)
                {
                    detail.Error = "解析详情失败: " + ex.Message;
                }
                onDone(detail);
            });
        }

        private static void Parse(string body, VideoDetail detail)
        {
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = JsonText.ReadString(body, "message");
                detail.Error = "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
                return;
            }

            detail.Aid = FirstGroup(AidRegex, body);
            detail.Title = JsonText.StripHtml(JsonText.Unescape(FirstGroup(TitleRegex, body)));
            detail.Desc = JsonText.StripHtml(JsonText.Unescape(FirstGroup(DescRegex, body)));
            detail.Duration = FirstGroup(DurationRegex, body);
            detail.PubDate = FirstGroup(PubDateRegex, body);

            detail.Pic = FirstGroup(PicRegex, body);
            if (detail.Pic.StartsWith("//", StringComparison.Ordinal))
            {
                detail.Pic = "https:" + detail.Pic;
            }

            int ownerIndex = body.IndexOf("\"owner\":{", StringComparison.Ordinal);
            if (ownerIndex >= 0)
            {
                string owner = Slice(body, ownerIndex, 400);
                detail.Author = FirstGroup(NameRegex, owner);
                detail.AuthorMid = FirstGroup(MidRegex, owner);
            }

            int statIndex = body.IndexOf("\"stat\":{", StringComparison.Ordinal);
            if (statIndex >= 0)
            {
                string stat = Slice(body, statIndex, 600);
                detail.View = FirstGroup(ViewRegex, stat);
                detail.Danmaku = FirstGroup(DanmakuRegex, stat);
                detail.Like = FirstGroup(LikeRegex, stat);
                detail.Coin = FirstGroup(CoinRegex, stat);
                detail.Favorite = FirstGroup(FavoriteRegex, stat);
                detail.Reply = FirstGroup(ReplyRegex, stat);
            }

            ParseParts(body, detail);
        }

        private static void ParseParts(string body, VideoDetail detail)
        {
            int start = body.IndexOf("\"pages\":[", StringComparison.Ordinal);
            if (start < 0)
            {
                return;
            }

            int end = body.IndexOf("],", start, StringComparison.Ordinal);
            if (end < 0)
            {
                end = body.IndexOf("]", start, StringComparison.Ordinal);
            }
            string region = end > start ? body.Substring(start, end - start) : body.Substring(start);

            MatchCollection cids = CidRegex.Matches(region);
            foreach (Match m in cids)
            {
                string cid = m.Groups[1].Value;
                if (cid.Length == 0)
                {
                    continue;
                }

                int next = region.IndexOf("\"cid\":", m.Index + 6, StringComparison.Ordinal);
                int limit = next > m.Index ? next : region.Length;
                if (limit > m.Index + 600)
                {
                    limit = m.Index + 600;
                }
                string after = region.Substring(m.Index, limit - m.Index);

                VideoPart part = new VideoPart();
                part.Cid = cid;
                part.Page = FirstGroup(PageRegex, after);
                if (part.Page.Length == 0)
                {
                    part.Page = (detail.Parts.Count + 1).ToString();
                }
                part.Title = JsonText.StripHtml(JsonText.Unescape(FirstGroup(PartRegex, after)));
                part.Duration = FirstGroup(DurationRegex, after);
                detail.Parts.Add(part);
            }
        }

        public static string FormatPubDate(string raw)
        {
            long seconds;
            if (!long.TryParse(raw, out seconds) || seconds <= 0)
            {
                return "";
            }
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return epoch.AddSeconds(seconds).ToLocalTime()
                .ToString("yyyy-MM-dd HH:mm", System.Globalization.CultureInfo.InvariantCulture);
        }

        private static string Slice(string text, int start, int maxLength)
        {
            if (start < 0 || start >= text.Length)
            {
                return "";
            }
            int length = Math.Min(maxLength, text.Length - start);
            return text.Substring(start, length);
        }

        private static string FirstGroup(Regex regex, string text)
        {
            Match match = regex.Match(text);
            return match.Success ? match.Groups[1].Value : "";
        }
    }
}
