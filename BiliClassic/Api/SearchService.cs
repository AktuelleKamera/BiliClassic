using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>一页搜索的结果</summary>
    public sealed class SearchResult
    {
        /// <summary>本页条目，已按bvid页内去重</summary>
        public List<VideoItem> Items = new List<VideoItem>();

        /// <summary>
        /// 服务端是否返回满一页20条
        /// 跨页去重后仍有新增才算下一页，判断留给调用方
        /// </summary>
        public bool FullPage;

        /// <summary>非空表示失败，内容给用户看</summary>
        public string Error = "";
    }

    /// <summary>
    /// 视频搜索，接口需WBI签名
    /// 解析以"bvid"为锚点，title/pic/play/video_review往后取600字符
    /// author往前取200字符内最后一个匹配
    /// 风控被拦时code可能是0且只返回v_voucher，要先判它
    /// </summary>
    public static class SearchService
    {
        public const string Endpoint = "https://api.bilibili.com/x/web-interface/wbi/search/type";

        /// <summary>风控兜底端点</summary>
        public const string LegacyEndpoint = "https://api.bilibili.com/x/web-interface/search/type";

        /// <summary>每页20条</summary>
        public const int PageSize = 20;

        private static readonly Regex BvidRegex = new Regex(@"""bvid"":\s*""([^""]+)""");
        private static readonly Regex TitleRegex = new Regex(@"""title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex AuthorRegex = new Regex(@"""author"":\s*""([^""]*)""");
        private static readonly Regex PicRegex = new Regex(@"""pic"":\s*""([^""]+)""");
        private static readonly Regex PlayRegex = new Regex(@"""play"":\s*(\d+)");
        private static readonly Regex DanmakuRegex = new Regex(@"""video_review"":\s*(\d+)");
        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");
        private static readonly Regex MessageRegex = new Regex(@"""message"":\s*""([^""]*)""");

        /// <summary>gaia风控质询</summary>
        private static readonly Regex VoucherRegex = new Regex(@"""v_voucher""");

        /// <summary>搜索第page页，回调不在UI线程</summary>
        public static void Search(string keyword, int page, Action<SearchResult> onDone)
        {
            SearchResult result = new SearchResult();

            if (string.IsNullOrEmpty(keyword))
            {
                result.Error = "请输入关键词";
                onDone(result);
                return;
            }

            // WBI密钥全局共享，只补缺失的那次
            // 拿不到密钥也不失败，旧端点不需签名
            WbiSigner.EnsureReady(delegate(string keyError)
            {
                DoSearch(keyword, page, result, onDone, string.IsNullOrEmpty(keyError));
            });
        }

        /// <summary>
        /// 发一次搜索请求，useWbi为false走旧端点不签名
        /// 被风控会换端点重试一次，可能递归一层
        /// </summary>
        private static void DoSearch(string keyword, int page, SearchResult result,
                                     Action<SearchResult> onDone, bool useWbi)
        {
            string url;
            if (useWbi)
            {
                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters["search_type"] = "video";
                parameters["keyword"] = keyword;
                parameters["page"] = page.ToString();
                url = WbiSigner.Shared.SignUrl(Endpoint, parameters);
            }
            else
            {
                url = LegacyEndpoint
                    + "?search_type=video"
                    + "&keyword=" + Uri.EscapeDataString(keyword)
                    + "&page=" + page.ToString()
                    + "&pagesize=" + PageSize.ToString();
            }

            Http.GetText(url, "https://search.bilibili.com/", delegate(HttpResult http)
            {
                try
                {
                    // 重试复用同一个result，先清上一次残留
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

                        // WBI端点被gaia拦下但请求是通的，换旧端点再试
                        if (useWbi && result.Items.Count == 0 && VoucherRegex.IsMatch(http.Body))
                        {
                            DoSearch(keyword, page, result, onDone, false);
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

            // 先判风控再判code，风控返回的code是0
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

        // 标题清理已抽到Api\JsonText.cs，搜索与推荐共用
    }
}
