using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>
    /// 一条评论
    /// </summary>
    public sealed class CommentItem
    {
        /// <summary>评论者</summary>
        public UserItem Author { get; set; }

        /// <summary>去重用</summary>
        public string Rpid { get; set; }

        public string Message { get; set; }

        /// <summary>时间加点赞数</summary>
        public string InfoLine { get; set; }

        public CommentItem()
        {
            Author = new UserItem();
            Rpid = "";
            Message = "";
            InfoLine = "";
        }
    }

    /// <summary>
    /// 视频评论
    /// </summary>
    public static class CommentService
    {
        private const string WbiUrl = "https://api.bilibili.com/x/v2/reply/wbi/main";
        private const string LegacyUrl = "https://api.bilibili.com/x/v2/reply";

        /// <summary>每页20条</summary>
        public const int PageSize = 20;

        /// <summary>热度排序</summary>
        private const string SortMode = "3";

        /// <summary>当前游标与是否到底</summary>
        private static string _offset = "";
        private static bool _ended;

        /// <summary>取第page页，回调不在UI线程</summary>
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
                    // 签不出来就退回老接口
                    // 老接口也空的话必须把签名错误报出来，否则会被当成"没有评论"
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

        // WBI接口

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
                    // WBI被拒（风控或参数变了），退回老接口再试
                    // 老接口也空的话把WBI那条错误报出来，别被吞掉
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

        /// <summary>老接口</summary>
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

        // 解析

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
                // 没有rpid就是真的没有评论
                if (body.IndexOf("\"rpid\"", StringComparison.Ordinal) >= 0)
                {
                    return "有评论数据但解析到0条：" + JsonText.Head(body, 150);
                }

                // 没评论就没有下一页，游标一并收尾
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
                item.Author.AvatarUrl = Normalize(JsonText.ReadString(region, "avatar"));
                item.Message = message;

                string time = FormatTimeAgo(ReadNumber(region, "ctime"));
                long like = ReadNumber(region, "like");
                item.InfoLine = like > 0 ? time + "   赞 " + like : time;

                items.Add(item);
            }
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

        /// <summary>Unix秒转相对时间</summary>
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

        /// <summary>code非0时返回错误描述，返回null表示正常</summary>
        private static string DescribeCode(string body)
        {
            // 只看开头：顶层code一定在最前面，往整段里找会被data里的同名键误伤
            Match code = Regex.Match(JsonText.Head(body, 200), @"""code"":\s*(-?\d+)");
            if (!code.Success)
            {
                // 连code都没有，多半不是接口响应（风控页或半截响应）
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

        /// <summary>常见错误码翻成人话</summary>
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

        /// <summary>
        /// 把游标包成JSON字符串值
        /// offset自己就是一段JSON，引号必须转义
        /// </summary>
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
