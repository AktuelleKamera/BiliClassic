using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class CommentItem
    {
        public UserItem Author { get; set; }

        public string Rpid { get; set; }

        public string Message { get; set; }

        public string InfoLine { get; set; }

        public CommentItem()
        {
            Author = new UserItem();
            Rpid = "";
            Message = "";
            InfoLine = "";
        }
    }

    public static class CommentService
    {
        private const string WbiUrl = "https://api.bilibili.com/x/v2/reply/wbi/main";
        private const string LegacyUrl = "https://api.bilibili.com/x/v2/reply";

        public const int PageSize = 20;

        private const string SortMode = "3";

        private static string _offset = "";
        private static bool _ended;

        public static void Fetch(string aid, int page,
                                 Action<List<CommentItem>, bool, string> onDone)
        {
            if (string.IsNullOrEmpty(aid))
            {
                onDone(new List<CommentItem>(), false, "缺少aid，取不了评论");
                return;
            }

            if (page <= 1)
            {
                _offset = "";
                _ended = false;
            }

            if (_ended)
            {
                onDone(new List<CommentItem>(), false, "");
                return;
            }

            WbiSigner.EnsureReady(delegate(string keyError)
            {
                if (!string.IsNullOrEmpty(keyError))
                {
                    FetchLegacy(aid, page, delegate(List<CommentItem> legacy, bool more, string legacyError)
                    {
                        if (legacy.Count == 0 && legacyError.Length == 0)
                        {
                            onDone(legacy, false, "取签名密钥失败：" + keyError);
                            return;
                        }
                        onDone(legacy, more, legacyError);
                    });
                    return;
                }
                FetchWbi(aid, onDone);
            });
        }

        private static void FetchWbi(string aid, Action<List<CommentItem>, bool, string> onDone)
        {
            Dictionary<string, string> parameters = new Dictionary<string, string>();
            parameters["type"] = "1";
            parameters["oid"] = aid;
            parameters["plat"] = "1";
            parameters["web_location"] = "1315875";
            parameters["mode"] = SortMode;
            parameters["pagination_str"] = "{\"offset\":" + JsonQuote(_offset) + "}";

            string query = WbiSigner.Shared.SignQuery(parameters);
            if (query.Length == 0)
            {
                FetchLegacy(aid, 1, onDone);
                return;
            }

            Http.GetText(WbiUrl + "?" + query, Http.Referer, delegate(HttpResult http)
            {
                List<CommentItem> items = new List<CommentItem>();
                bool hasMore = false;
                string error = Parse(http, items, out hasMore, true);

                if (items.Count == 0 && !string.IsNullOrEmpty(error))
                {
                    string wbiError = error;
                    FetchLegacy(aid, 1, delegate(List<CommentItem> legacy, bool more, string legacyError)
                    {
                        if (legacy.Count == 0 && legacyError.Length == 0)
                        {
                            onDone(legacy, false, wbiError);
                            return;
                        }
                        onDone(legacy, more, legacyError);
                    });
                    return;
                }

                onDone(items, hasMore, error);
            });
        }

        private static void FetchLegacy(string aid, int page,
                                        Action<List<CommentItem>, bool, string> onDone)
        {
            string url = LegacyUrl + "?type=1&oid=" + aid
                + "&pn=" + page + "&ps=" + PageSize + "&sort=2";

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                List<CommentItem> items = new List<CommentItem>();
                bool hasMore = false;
                string error = Parse(http, items, out hasMore, false);
                onDone(items, hasMore, error);
            });
        }

        private const string AddUrl = "https://api.bilibili.com/x/v2/reply/add";

        public static void Add(string aid, string message, Action<bool, string> onDone)
        {
            if (string.IsNullOrEmpty(aid))
            {
                onDone(false, "缺少aid");
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                onDone(false, "请先登录");
                return;
            }
            if (string.IsNullOrEmpty(message))
            {
                onDone(false, "评论不能为空");
                return;
            }
            if (message.Length > 1000)
            {
                onDone(false, "评论不能超过1000字");
                return;
            }

            string form = "oid=" + aid + "&type=1&root=0&parent=0&message="
                + UrlEncode(message) + "&jsonp=jsonp&csrf=" + BiliSession.Csrf;

            Http.PostForm(AddUrl, form, Http.Referer, delegate(HttpResult http)
            {
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(false, string.IsNullOrEmpty(http.Error) ? "发送失败" : http.Error);
                    return;
                }

                Match code = Regex.Match(JsonText.Head(http.Body, 200), @"""code"":\s*(-?\d+)");
                if (code.Success && code.Groups[1].Value == "0")
                {
                    onDone(true, "");
                    return;
                }

                string value = code.Success ? code.Groups[1].Value : "";
                string friendly = FriendlyCode(value);
                string message2 = JsonText.ReadString(http.Body, "message");
                onDone(false, "发送失败 code=" + (value.Length > 0 ? value : "?")
                    + (friendly.Length > 0 ? "（" + friendly + "）" : "")
                    + (message2.Length > 0 && message2 != "0" ? " " + message2 : ""));
            });
        }

        private static string UrlEncode(string value)
        {
            StringBuilder sb = new StringBuilder();
            byte[] bytes = Encoding.UTF8.GetBytes(value);
            for (int i = 0; i < bytes.Length; i++)
            {
                char c = (char)bytes[i];
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~')
                {
                    sb.Append(c);
                }
                else
                {
                    sb.Append('%').Append(bytes[i].ToString("X2"));
                }
            }
            return sb.ToString();
        }

        private static string Parse(HttpResult http, List<CommentItem> items, out bool hasMore,
                                    bool cursorPaging)
        {
            hasMore = false;
            if (http == null || string.IsNullOrEmpty(http.Body))
            {
                return string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error;
            }

            string body = http.Body;
            string error = DescribeCode(body);
            if (error != null)
            {
                return error;
            }

            ParseReplies(body, items);

            if (items.Count == 0)
            {
                if (body.IndexOf("\"rpid\"", StringComparison.Ordinal) >= 0)
                {
                    return "有评论数据但解析到0条：" + JsonText.Head(body, 150);
                }

                _offset = "";
                _ended = true;
                return "";
            }

            if (cursorPaging)
            {
                string next = JsonText.ReadString(body, "next_offset");
                bool isEnd = Regex.IsMatch(body, @"""is_end"":\s*true");
                _offset = next;
                _ended = isEnd || next.Length == 0;
                hasMore = !_ended;
            }
            else
            {
                hasMore = items.Count >= PageSize;
            }

            return "";
        }

        private static void ParseReplies(string body, List<CommentItem> items)
        {
            MatchCollection ids = Regex.Matches(body, @"""rpid"":\s*(\d+)");
            for (int i = 0; i < ids.Count; i++)
            {
                int start = ids[i].Index;
                int end = i + 1 < ids.Count ? ids[i + 1].Index : body.Length;
                if (end - start > 4000)
                {
                    end = start + 4000;
                }
                string region = body.Substring(start, end - start);

                string message = JsonText.ReadString(region, "message");
                if (message.Length == 0)
                {
                    continue;
                }

                string rpid = ids[i].Groups[1].Value;
                if (ContainsRpid(items, rpid))
                {
                    continue;
                }

                CommentItem item = new CommentItem();
                item.Rpid = rpid;
                item.Author.Name = JsonText.ReadString(region, "uname");
                item.Author.Mid = ReadMid(region);
                item.Author.AvatarUrl = Normalize(JsonText.ReadString(region, "avatar"));
                item.Message = message;

                string time = FormatTimeAgo(ReadNumber(region, "ctime"));
                long like = ReadNumber(region, "like");
                item.InfoLine = like > 0 ? time + "   赞 " + like : time;

                items.Add(item);
            }
        }

        private static string ReadMid(string region)
        {
            Match m = Regex.Match(region, @"""mid"":\s*""?(\d+)""?");
            return m.Success ? m.Groups[1].Value : "";
        }

        private static bool ContainsRpid(List<CommentItem> items, string rpid)
        {
            for (int i = 0; i < items.Count; i++)
            {
                if (items[i].Rpid == rpid)
                {
                    return true;
                }
            }
            return false;
        }

        private static string FormatTimeAgo(long unixSeconds)
        {
            if (unixSeconds <= 0)
            {
                return "";
            }

            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            DateTime time = epoch.AddSeconds(unixSeconds).ToLocalTime();
            TimeSpan span = DateTime.Now - time;

            if (span.TotalMinutes < 1)
            {
                return "刚刚";
            }
            if (span.TotalHours < 1)
            {
                return (int)span.TotalMinutes + "分钟前";
            }
            if (span.TotalDays < 1)
            {
                return (int)span.TotalHours + "小时前";
            }
            if (span.TotalDays < 30)
            {
                return (int)span.TotalDays + "天前";
            }
            return time.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        }

        private static string DescribeCode(string body)
        {
            Match code = Regex.Match(JsonText.Head(body, 200), @"""code"":\s*(-?\d+)");
            if (!code.Success)
            {
                return "响应里没有code：" + JsonText.Head(body, 120);
            }

            string value = code.Groups[1].Value;
            if (value == "0")
            {
                return null;
            }

            string message = JsonText.ReadString(body, "message");
            string friendly = FriendlyCode(value);
            return "接口返回 code=" + value
                + (friendly.Length > 0 ? "（" + friendly + "）" : "")
                + (message.Length > 0 && message != "0" ? " " + message : "");
        }

        private static string FriendlyCode(string code)
        {
            if (code == "-404" || code == "12061")
            {
                return "评论区不可用";
            }
            if (code == "-352" || code == "-412")
            {
                return "被风控拦了";
            }
            if (code == "-101")
            {
                return "未登录";
            }
            if (code == "-403")
            {
                return "访问权限不足";
            }
            return "";
        }

        private static long ReadNumber(string json, string field)
        {
            Match m = Regex.Match(json, @"""" + field + @""":\s*(-?\d+)");
            long value;
            return m.Success && long.TryParse(m.Groups[1].Value, out value) ? value : 0;
        }

        private static string JsonQuote(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return "\"\"";
            }

            StringBuilder sb = new StringBuilder(value.Length + 8);
            sb.Append('"');
            for (int i = 0; i < value.Length; i++)
            {
                char c = value[i];
                if (c == '"' || c == '\\')
                {
                    sb.Append('\\');
                }
                sb.Append(c);
            }
            sb.Append('"');
            return sb.ToString();
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
