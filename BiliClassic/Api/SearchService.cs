using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class SearchResult
    {
        public List<VideoItem> Items = new List<VideoItem>();

        public bool FullPage;

        public string Error = "";
    }

    public static class SearchService
    {
        public const string Endpoint = "https://api.bilibili.com/x/web-interface/wbi/search/type";

        public const string LegacyEndpoint = "https://api.bilibili.com/x/web-interface/search/type";

        public const int PageSize = 20;

        private static readonly Regex BvidRegex = new Regex(@"""bvid"":\s*""([^""]+)""");
        private static readonly Regex TitleRegex = new Regex(@"""title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex AuthorRegex = new Regex(@"""author"":\s*""([^""]*)""");
        private static readonly Regex PicRegex = new Regex(@"""pic"":\s*""([^""]+)""");
        private static readonly Regex PlayRegex = new Regex(@"""play"":\s*(\d+)");
        private static readonly Regex DanmakuRegex = new Regex(@"""video_review"":\s*(\d+)");
        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");
        private static readonly Regex MessageRegex = new Regex(@"""message"":\s*""([^""]*)""");

        private static readonly Regex VoucherRegex = new Regex(@"""v_voucher""");

        public const long SiteLaunchSeconds = 1245945600L;

        public static void Search(string keyword, int page, Action<SearchResult> onDone)
        {
            Search(keyword, page, null, 0, 0, onDone);
        }

        public static void Search(string keyword, int page, string order,
                                  long pubtimeBeginS, long pubtimeEndS,
                                  Action<SearchResult> onDone)
        {
            SearchResult result = new SearchResult();

            if (string.IsNullOrEmpty(keyword))
            {
                result.Error = "请输入关键词";
                onDone(result);
                return;
            }

            WbiSigner.EnsureReady(delegate(string keyError)
            {
                DoSearch(keyword, page, order, pubtimeBeginS, pubtimeEndS, result, onDone,
                    string.IsNullOrEmpty(keyError));
            });
        }

        private static void DoSearch(string keyword, int page, string order,
                                     long pubtimeBeginS, long pubtimeEndS,
                                     SearchResult result, Action<SearchResult> onDone, bool useWbi)
        {
            string url;
            if (useWbi)
            {
                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters["search_type"] = "video";
                parameters["keyword"] = keyword;
                parameters["page"] = page.ToString();
                if (!string.IsNullOrEmpty(order))
                {
                    parameters["order"] = order;
                }
                if (pubtimeEndS > 0)
                {
                    parameters["pubtime_begin_s"] =
                        (pubtimeBeginS > 0 ? pubtimeBeginS : SiteLaunchSeconds).ToString();
                    parameters["pubtime_end_s"] = pubtimeEndS.ToString();
                }
                else if (pubtimeBeginS > 0)
                {
                    parameters["pubtime_begin_s"] = pubtimeBeginS.ToString();
                }
                url = WbiSigner.Shared.SignUrl(Endpoint, parameters);
            }
            else
            {
                url = LegacyEndpoint
                    + "?search_type=video"
                    + "&keyword=" + Uri.EscapeDataString(keyword)
                    + "&page=" + page.ToString()
                    + "&pagesize=" + PageSize.ToString();
                if (!string.IsNullOrEmpty(order))
                {
                    url += "&order=" + order;
                }
                if (pubtimeEndS > 0)
                {
                    url += "&pubtime_begin_s="
                        + (pubtimeBeginS > 0 ? pubtimeBeginS : SiteLaunchSeconds).ToString()
                        + "&pubtime_end_s=" + pubtimeEndS.ToString();
                }
                else if (pubtimeBeginS > 0)
                {
                    url += "&pubtime_begin_s=" + pubtimeBeginS.ToString();
                }
            }

            Http.GetText(url, "https://search.bilibili.com/", delegate(HttpResult http)
            {
                try
                {
                    result.Items.Clear();
                    result.FullPage = false;
                    result.Error = "";

                    if (string.IsNullOrEmpty(http.Body))
                    {
                        result.Error = string.IsNullOrEmpty(http.Error)
                            ? "搜索请求无响应"
                            : "搜索请求失败: " + http.Error;
                    }
                    else
                    {
                        Parse(http.Body, result);

                        if (useWbi && result.Items.Count == 0 && VoucherRegex.IsMatch(http.Body))
                        {
                            DoSearch(keyword, page, order, pubtimeBeginS, pubtimeEndS, result, onDone, false);
                            return;
                        }
                    }
                }
                catch (Exception ex)
                {
                    result.Error = "搜索失败: " + ex.Message;
                }
                onDone(result);
            });
        }

        private static void Parse(string body, SearchResult result)
        {
            MatchCollection bvidMatches = BvidRegex.Matches(body);
            int rawCount = 0;
            List<string> seen = new List<string>();

            foreach (Match m in bvidMatches)
            {
                if (rawCount >= PageSize)
                {
                    break;
                }
                rawCount++;

                string bvid = m.Groups[1].Value;
                if (bvid.Length == 0 || seen.Contains(bvid))
                {
                    continue;
                }
                seen.Add(bvid);

                int forward = Math.Min(600, body.Length - m.Index);
                string after = body.Substring(m.Index, forward);

                string title = "";
                Match titleMatch = TitleRegex.Match(after);
                if (titleMatch.Success)
                {
                    title = JsonText.StripHtml(JsonText.Unescape(titleMatch.Groups[1].Value));
                }

                int start = m.Index - 200;
                if (start < 0)
                {
                    start = 0;
                }
                string before = body.Substring(start, m.Index - start);
                string author = "";
                MatchCollection authorMatches = AuthorRegex.Matches(before);
                if (authorMatches.Count > 0)
                {
                    author = authorMatches[authorMatches.Count - 1].Groups[1].Value;
                }

                string pic = "";
                Match picMatch = PicRegex.Match(after);
                if (picMatch.Success)
                {
                    pic = picMatch.Groups[1].Value;
                    if (pic.StartsWith("//"))
                    {
                        pic = "https:" + pic;
                    }
                }

                string view = "";
                Match playMatch = PlayRegex.Match(after);
                if (playMatch.Success)
                {
                    view = playMatch.Groups[1].Value;
                }

                string danmaku = "";
                Match danmakuMatch = DanmakuRegex.Match(after);
                if (danmakuMatch.Success)
                {
                    danmaku = danmakuMatch.Groups[1].Value;
                }

                VideoItem item = new VideoItem();
                item.Bvid = bvid;
                item.Pic = pic;
                item.Title = title;
                item.Author = author;
                item.View = view;
                item.Danmaku = danmaku;
                result.Items.Add(item);
            }

            result.FullPage = rawCount >= PageSize;

            if (result.Items.Count == 0)
            {
                if (VoucherRegex.IsMatch(body))
                {
                    result.Error = "B站风控拦截（返回 v_voucher 质询，没有下发结果）\n"
                        + "请求头设置结果：" + Http.HeaderSupport;
                    return;
                }

                if (body.IndexOf("\"result\":", StringComparison.Ordinal) < 0)
                {
                    result.Error = "搜索接口没有返回结果数组，响应开头: " + JsonText.Head(body, 160);
                    return;
                }

                Match codeMatch = CodeRegex.Match(body);
                if (codeMatch.Success && codeMatch.Groups[1].Value != "0")
                {
                    string message = "";
                    Match messageMatch = MessageRegex.Match(body);
                    if (messageMatch.Success)
                    {
                        message = JsonText.Unescape(messageMatch.Groups[1].Value);
                    }
                    result.Error = "接口返回 code=" + codeMatch.Groups[1].Value
                        + (message.Length > 0 ? " " + message : "");
                }
            }
        }

    }
}
