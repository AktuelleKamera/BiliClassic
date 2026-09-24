using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class RecommendResult
    {
        public List<VideoItem> Items = new List<VideoItem>();

        public bool HasMore;

        public string Error = "";
    }

    public static class RecommendService
    {
        public const string Endpoint = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd";

        public const int PageSize = 20;

        private const int MaxWindow = 1500;

        private static readonly Regex BvidRegex = new Regex(@"""bvid"":\s*""([^""]+)""");
        private static readonly Regex PicRegex = new Regex(@"""pic"":\s*""([^""]+)""");
        private static readonly Regex TitleRegex = new Regex(@"""title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex NameRegex = new Regex(@"""name"":\s*""([^""]*)""");
        private static readonly Regex ViewRegex = new Regex(@"""view"":\s*(\d+)");
        private static readonly Regex DanmakuRegex = new Regex(@"""danmaku"":\s*(\d+)");
        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");
        private static readonly Regex MessageRegex = new Regex(@"""message"":\s*""([^""]*)""");

        public static void Fetch(int page, Action<RecommendResult> onDone)
        {
            RecommendResult result = new RecommendResult();
            if (page < 1)
            {
                page = 1;
            }

            WbiSigner.EnsureReady(delegate(string keyError)
            {
                if (!string.IsNullOrEmpty(keyError))
                {
                    result.Error = keyError;
                    onDone(result);
                    return;
                }

                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters["web_location"] = "1430650";
                parameters["feed_version"] = "V8";
                parameters["homepage_ver"] = "1";
                parameters["screen"] = "480-800";
                parameters["fresh_idx"] = page.ToString();
                parameters["fresh_idx_1h"] = page.ToString();
                parameters["brush"] = page.ToString();
                parameters["fetch_row"] = ((page - 1) * PageSize).ToString();

                string url = WbiSigner.Shared.SignUrl(Endpoint, parameters);

                Http.GetText(url, Http.Referer, delegate(HttpResult http)
                {
                    try
                    {
                        if (http == null || string.IsNullOrEmpty(http.Body))
                        {
                            result.Error = (http == null || string.IsNullOrEmpty(http.Error))
                                ? "推荐请求无响应"
                                : "推荐请求失败: " + http.Error;
                        }
                        else
                        {
                            Parse(http.Body, result);
                        }
                    }
                    catch (Exception ex)
                    {
                        result.Error = "解析推荐失败: " + ex.Message;
                    }
                    onDone(result);
                });
            });
        }

        private static void Parse(string body, RecommendResult result)
        {
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = ReadMessage(body);
                result.Error = "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
                return;
            }

            int start = body.IndexOf("\"item\":[", StringComparison.Ordinal);
            if (start < 0)
            {
                result.Error = "推荐响应里没有 data.item: " + JsonText.Head(body, 160);
                return;
            }

            int end = body.IndexOf("\"business_card\"", start, StringComparison.Ordinal);
            string region = end > start
                ? body.Substring(start, end - start)
                : body.Substring(start);

            MatchCollection bvids = BvidRegex.Matches(region);
            foreach (Match m in bvids)
            {
                string bvid = m.Groups[1].Value;
                if (bvid.Length == 0)
                {
                    continue;
                }

                string after = Window(region, m.Index);

                string pic = FirstGroup(PicRegex, after);
                if (pic.Length == 0)
                {
                    continue;
                }
                if (pic.StartsWith("//", StringComparison.Ordinal))
                {
                    pic = "https:" + pic;
                }

                string title = "";
                Match titleMatch = TitleRegex.Match(after);
                if (titleMatch.Success)
                {
                    title = JsonText.StripHtml(JsonText.Unescape(titleMatch.Groups[1].Value));
                }

                VideoItem item = new VideoItem();
                item.Bvid = bvid;
                item.Pic = pic;
                item.Title = title;
                item.Author = LastGroup(NameRegex, after);
                item.View = FirstGroup(ViewRegex, after);
                item.Danmaku = FirstGroup(DanmakuRegex, after);
                result.Items.Add(item);
            }

            result.HasMore = result.Items.Count >= PageSize;
        }

        private static string Window(string region, int bvidIndex)
        {
            int next = region.IndexOf("\"bvid\"", bvidIndex + 6, StringComparison.Ordinal);
            int limit = next > bvidIndex ? next : region.Length;
            if (limit > bvidIndex + MaxWindow)
            {
                limit = bvidIndex + MaxWindow;
            }
            return region.Substring(bvidIndex, limit - bvidIndex);
        }

        private static string FirstGroup(Regex regex, string text)
        {
            Match match = regex.Match(text);
            return match.Success ? match.Groups[1].Value : "";
        }

        private static string LastGroup(Regex regex, string text)
        {
            MatchCollection matches = regex.Matches(text);
            return matches.Count > 0 ? matches[matches.Count - 1].Groups[1].Value : "";
        }

        private static string ReadMessage(string body)
        {
            Match match = MessageRegex.Match(body);
            return match.Success ? JsonText.Unescape(match.Groups[1].Value) : "";
        }
    }
}
