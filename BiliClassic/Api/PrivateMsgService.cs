using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Media;

namespace BiliClassic.Api
{
    public sealed class PrivateMsgItem
    {
        private static readonly Color MineBubbleColor = Color.FromArgb(0xFF, 0xD8, 0x6D, 0xA5);
        private static readonly Color OtherBubbleColor = Color.FromArgb(0x33, 0xFF, 0xFF, 0xFF);
        private static readonly Color MineTextColor = Color.FromArgb(0xFF, 0xFF, 0xFF, 0xFF);
        private static readonly Color OtherTextColor = Color.FromArgb(0xFF, 0xE8, 0xE8, 0xE8);
        private static readonly Color NoticeTextColor = Color.FromArgb(0xFF, 0x99, 0x99, 0x99);
        private static readonly Color NoBubbleColor = Color.FromArgb(0x00, 0x00, 0x00, 0x00);

        private static Brush _mineBubble;
        private static Brush _otherBubble;
        private static Brush _mineText;
        private static Brush _otherText;
        private static Brush _noticeText;
        private static Brush _noBubble;

        public string Text { get; set; }
        public string TimeText { get; set; }
        public bool IsMine { get; set; }

        public bool IsNotice { get; set; }

        public long Seqno { get; set; }

        public PrivateMsgItem()
        {
            Text = "";
            TimeText = "";
        }

        public HorizontalAlignment Align
        {
            get
            {
                if (IsNotice)
                {
                    return HorizontalAlignment.Center;
                }
                return IsMine ? HorizontalAlignment.Right : HorizontalAlignment.Left;
            }
        }

        public Brush Bubble
        {
            get
            {
                if (IsNotice)
                {
                    return Lazy(ref _noBubble, NoBubbleColor);
                }
                return IsMine
                    ? Lazy(ref _mineBubble, MineBubbleColor)
                    : Lazy(ref _otherBubble, OtherBubbleColor);
            }
        }

        public Brush TextBrush
        {
            get
            {
                if (IsNotice)
                {
                    return Lazy(ref _noticeText, NoticeTextColor);
                }
                return IsMine
                    ? Lazy(ref _mineText, MineTextColor)
                    : Lazy(ref _otherText, OtherTextColor);
            }
        }

        private static Brush Lazy(ref Brush cache, Color color)
        {
            if (cache == null)
            {
                cache = new SolidColorBrush(color);
            }
            return cache;
        }
    }

    public sealed class PrivateMsgSession
    {
        public long TalkerUid { get; set; }
        public int Unread { get; set; }
        public string Preview { get; set; }
        public string TimeText { get; set; }
        public UserItem User { get; set; }

        public PrivateMsgSession()
        {
            Preview = "";
            TimeText = "";
            User = new UserItem();
        }

        public string BadgeText
        {
            get { return Unread > 0 ? Unread.ToString() : ""; }
        }

        public Visibility BadgeVisibility
        {
            get { return Unread > 0 ? Visibility.Visible : Visibility.Collapsed; }
        }

        /// <summary>列表里的预览只留一行，和关注列表同一套截断</summary>
        public string PreviewOneLine
        {
            get
            {
                string text = Preview == null ? "" : Preview;
                text = text.Replace("\r", "").Replace("\n", " ");
                return LimitOneLine(text, 26);
            }
        }

        // 制表宽度：CJK算2，其它算1，超了补省略号
        private static string LimitOneLine(string text, int budget)
        {
            if (string.IsNullOrEmpty(text))
            {
                return "";
            }
            int used = 0;
            int cut = 0;
            for (int i = 0; i < text.Length; i++)
            {
                int width = text[i] > 0x2E80 ? 2 : 1;
                if (used + width > budget)
                {
                    break;
                }
                used += width;
                cut = i + 1;
            }
            return cut >= text.Length ? text : text.Substring(0, cut) + "…";
        }
    }

    public static class PrivateMsgService
    {
        private const string Root = "https://api.vc.bilibili.com";

        private const string MsgReferer = "https://message.bilibili.com/";

        private static readonly List<string> NameKeys = new List<string>();
        private static readonly List<string> NameValues = new List<string>();


        public static void FetchSessions(Action<List<PrivateMsgSession>, string> onDone)
        {
            string primary = Root + "/session_svr/v1/session_svr/new_sessions?size=50&mobi_app=web";
            FetchSessionUrl(primary, delegate(List<PrivateMsgSession> first, string error)
            {
                if (first.Count > 0 || !string.IsNullOrEmpty(error))
                {
                    SortUnreadFirst(first);
                    onDone(first, error);
                    return;
                }
                FetchSessionUrl(Root
                    + "/session_svr/v1/session_svr/get_sessions?session_type=1&size=50",
                    delegate(List<PrivateMsgSession> sessions, string secondError)
                    {
                        SortUnreadFirst(sessions);
                        onDone(sessions, secondError);
                    });
            });
        }

        /// <summary>未读排前，组内保持接口原本的时间顺序</summary>
        private static void SortUnreadFirst(List<PrivateMsgSession> sessions)
        {
            if (sessions == null || sessions.Count < 2)
            {
                return;
            }

            List<PrivateMsgSession> sorted = new List<PrivateMsgSession>();
            for (int i = 0; i < sessions.Count; i++)
            {
                if (sessions[i].Unread > 0)
                {
                    sorted.Add(sessions[i]);
                }
            }
            for (int i = 0; i < sessions.Count; i++)
            {
                if (sessions[i].Unread <= 0)
                {
                    sorted.Add(sessions[i]);
                }
            }

            sessions.Clear();
            sessions.AddRange(sorted);
        }

        private static void FetchSessionUrl(string url,
                                            Action<List<PrivateMsgSession>, string> onDone)
        {
            Http.GetText(url, MsgReferer, delegate(HttpResult http)
            {
                List<PrivateMsgSession> sessions = new List<PrivateMsgSession>();
                string error = ParseSessions(http, sessions);
                if (!string.IsNullOrEmpty(error) || sessions.Count == 0)
                {
                    onDone(sessions, error);
                    return;
                }
                FillMissingUsers(sessions, delegate
                {
                    onDone(sessions, "");
                });
            });
        }

        private static void FillMissingUsers(List<PrivateMsgSession> sessions, Action onDone)
        {
            List<string> uids = new List<string>();
            for (int i = 0; i < sessions.Count; i++)
            {
                if (string.IsNullOrEmpty(sessions[i].User.Name)
                    || string.IsNullOrEmpty(sessions[i].User.AvatarUrl))
                {
                    uids.Add(sessions[i].TalkerUid.ToString());
                }
            }

            if (uids.Count == 0)
            {
                ApplyFallbackNames(sessions);
                onDone();
                return;
            }

            FetchUsersInfo(uids, delegate(Dictionary<string, UserItem> map)
            {
                for (int i = 0; i < sessions.Count; i++)
                {
                    UserItem user;
                    if (!map.TryGetValue(sessions[i].TalkerUid.ToString(), out user))
                    {
                        continue;
                    }
                    if (string.IsNullOrEmpty(sessions[i].User.Name))
                    {
                        sessions[i].User.Name = user.Name;
                        CacheName(sessions[i].TalkerUid.ToString(), user.Name);
                    }
                    if (string.IsNullOrEmpty(sessions[i].User.AvatarUrl))
                    {
                        sessions[i].User.AvatarUrl = user.AvatarUrl;
                    }
                }
                ApplyFallbackNames(sessions);
                onDone();
            });
        }

        private static void ApplyFallbackNames(List<PrivateMsgSession> sessions)
        {
            for (int i = 0; i < sessions.Count; i++)
            {
                if (string.IsNullOrEmpty(sessions[i].User.Name))
                {
                    sessions[i].User.Name = "用户_" + sessions[i].TalkerUid;
                }
            }
        }

        private static void FetchUsersInfo(List<string> uids,
                                           Action<Dictionary<string, UserItem>> onDone)
        {
            Dictionary<string, UserItem> map = new Dictionary<string, UserItem>();
            if (uids.Count == 0)
            {
                onDone(map);
                return;
            }

            StringBuilder ids = new StringBuilder();
            for (int i = 0; i < uids.Count; i++)
            {
                if (i > 0)
                {
                    ids.Append(',');
                }
                ids.Append(uids[i]);
            }

            Http.GetText(Root + "/account/v1/user/cards?uids=" + ids, MsgReferer,
                delegate(HttpResult http)
                {
                    if (http != null && !string.IsNullOrEmpty(http.Body)
                        && DescribeCode(http.Body) == null)
                    {
                        ParseUserCards(http.Body, map);
                    }
                    onDone(map);
                });
        }

        private static void ParseUserCards(string body, Dictionary<string, UserItem> map)
        {
            MatchCollection anchors = Regex.Matches(body, @"""mid"":\s*(\d+)");
            for (int i = 0; i < anchors.Count; i++)
            {
                int start = anchors[i].Index;
                int end = (i + 1 < anchors.Count) ? anchors[i + 1].Index : body.Length;
                if (end - start > 3000)
                {
                    end = start + 3000;
                }
                string region = body.Substring(start, end - start);

                string uid = anchors[i].Groups[1].Value;
                UserItem user = new UserItem();
                user.Mid = uid;
                user.Name = JsonText.ReadString(region, "name");
                user.AvatarUrl = Normalize(JsonText.ReadString(region, "face"));
                map[uid] = user;
            }
        }

        private static string ParseSessions(HttpResult http, List<PrivateMsgSession> sessions)
        {
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

            MatchCollection anchors = Regex.Matches(body, @"""talker_id"":\s*(\d+)");
            for (int i = 0; i < anchors.Count; i++)
            {
                int start = anchors[i].Index;
                int end = (i + 1 < anchors.Count) ? anchors[i + 1].Index : body.Length;
                if (end - start > 5000)
                {
                    end = start + 5000;
                }
                string region = body.Substring(start, end - start);

                long uid = ParseLong(anchors[i].Groups[1].Value);
                if (uid <= 0)
                {
                    continue;
                }

                PrivateMsgSession session = new PrivateMsgSession();
                session.TalkerUid = uid;
                session.Unread = (int)ReadNumber(region, "unread_count");

                string name = JsonText.ReadString(region, "name");
                string face = JsonText.ReadString(region, "face");
                session.User.Mid = uid.ToString();
                session.User.Name = name;
                session.User.AvatarUrl = Normalize(face);
                CacheName(uid.ToString(), name);

                int type = (int)ReadNumber(region, "msg_type");
                string content = JsonText.ReadString(region, "content");
                session.Preview = PreviewText(type, content);

                session.TimeText = FormatTime(ReadNumber(region, "timestamp"));
                sessions.Add(session);
            }

            return "";
        }


        public static void FetchMessages(string talkerUid, string talkerName,
                                         Action<List<PrivateMsgItem>, string> onDone)
        {
            if (string.IsNullOrEmpty(talkerUid))
            {
                onDone(new List<PrivateMsgItem>(), "缺少会话ID");
                return;
            }

            string url = Root + "/svr_sync/v1/svr_sync/fetch_session_msgs?session_type=1&talker_id="
                + talkerUid + "&size=50&begin_seqno=0&end_seqno=0";

            Http.GetText(url, MsgReferer, delegate(HttpResult http)
            {
                List<PrivateMsgItem> items = new List<PrivateMsgItem>();
                string error = ParseMessages(http, talkerName, items);
                onDone(items, error);
            });
        }

        private static string ParseMessages(HttpResult http, string talkerName,
                                            List<PrivateMsgItem> items)
        {
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

            long myUid = ParseLong(BiliSession.Mid);
            if (talkerName == null)
            {
                talkerName = "";
            }

            MatchCollection anchors = Regex.Matches(body, @"""sender_uid"":\s*(\d+)");
            for (int i = 0; i < anchors.Count; i++)
            {
                int start = anchors[i].Index;
                int end = (i + 1 < anchors.Count) ? anchors[i + 1].Index : body.Length;
                if (end - start > 5000)
                {
                    end = start + 5000;
                }
                string region = body.Substring(start, end - start);

                long sender = ParseLong(anchors[i].Groups[1].Value);
                int type = (int)ReadNumber(region, "msg_type");
                string content = JsonText.ReadString(region, "content");

                PrivateMsgItem item = new PrivateMsgItem();
                item.IsMine = myUid > 0 && sender == myUid;
                item.IsNotice = (type == 5 || type == 18);
                item.Seqno = ReadNumber(region, "msg_seqno");
                item.TimeText = FormatTime(ReadNumber(region, "timestamp"));
                item.Text = MessageText(type, content, item.IsMine, talkerName);

                if (item.Text.Length == 0 && !item.IsNotice)
                {
                    continue;
                }
                items.Add(item);
            }

            items.Reverse();
            return "";
        }


        public static void Send(string talkerUid, string text, Action<bool, string> onDone)
        {
            string csrf = BiliSession.Csrf;
            string myUid = BiliSession.Mid;
            if (string.IsNullOrEmpty(myUid) || string.IsNullOrEmpty(csrf))
            {
                onDone(false, "请先登录");
                return;
            }
            if (string.IsNullOrEmpty(text))
            {
                onDone(false, "还没有输入内容");
                return;
            }

            string content = "{\"content\":\"" + EscapeJson(text) + "\"}";
            StringBuilder form = new StringBuilder();
            AppendForm(form, "msg[dev_id]", Guid.NewGuid().ToString());
            AppendForm(form, "msg[msg_type]", "1");
            AppendForm(form, "msg[content]", content);
            AppendForm(form, "msg[receiver_type]", "1");
            AppendForm(form, "msg[sender_uid]", myUid);
            AppendForm(form, "msg[receiver_id]", talkerUid);
            AppendForm(form, "msg[timestamp]", CurrentUnixSeconds().ToString());
            AppendForm(form, "csrf", csrf);

            Http.PostForm(Root + "/web_im/v1/web_im/send_msg", form.ToString(), MsgReferer,
                delegate(HttpResult http)
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        onDone(false, string.IsNullOrEmpty(http.Error) ? "发送失败" : http.Error);
                        return;
                    }

                    Match code = Regex.Match(http.Body, @"""code"":\s*(-?\d+)");
                    if (code.Success && code.Groups[1].Value == "0")
                    {
                        onDone(true, "");
                        return;
                    }

                    string message = JsonText.ReadString(http.Body, "message");
                    onDone(false, "发送失败 code="
                        + (code.Success ? code.Groups[1].Value : "?")
                        + (message.Length > 0 ? " " + message : ""));
                });
        }

        public static void MarkRead(string talkerUid)
        {
            string csrf = BiliSession.Csrf;
            if (string.IsNullOrEmpty(csrf) || string.IsNullOrEmpty(talkerUid))
            {
                return;
            }

            StringBuilder form = new StringBuilder();
            AppendForm(form, "talker_id", talkerUid);
            AppendForm(form, "session_type", "1");
            AppendForm(form, "csrf_token", csrf);
            AppendForm(form, "csrf", csrf);
            AppendForm(form, "build", "0");
            AppendForm(form, "mobi_app", "web");

            Http.PostForm(Root + "/session_svr/v1/session_svr/update_ack", form.ToString(),
                MsgReferer, delegate(HttpResult http) { });
        }


        public static string GetCachedName(string uid)
        {
            if (string.IsNullOrEmpty(uid))
            {
                return "";
            }
            int index = NameKeys.IndexOf(uid);
            return index >= 0 ? NameValues[index] : "";
        }

        private static void CacheName(string uid, string name)
        {
            if (string.IsNullOrEmpty(uid) || string.IsNullOrEmpty(name))
            {
                return;
            }
            int index = NameKeys.IndexOf(uid);
            if (index >= 0)
            {
                NameValues[index] = name;
                return;
            }
            NameKeys.Add(uid);
            NameValues.Add(name);
        }

        public static void FetchCard(string uid, Action<UserItem, string> onDone)
        {
            UserSpaceService.FetchCard(uid, delegate(UserSpaceCard card, string error)
            {
                UserItem user = new UserItem();
                user.Mid = uid;
                if (card != null)
                {
                    user.Name = card.Name;
                    user.AvatarUrl = card.Face;
                }
                if (user.Name.Length > 0)
                {
                    CacheName(uid, user.Name);
                }
                onDone(user, error);
            });
        }


        private static string PreviewText(int type, string content)
        {
            switch (type)
            {
                case 1:
                    return InnerString(content, "content");
                case 2:
                case 6:
                    return "[图片]";
                case 7:
                case 10:
                case 13:
                    return "[视频]";
                case 16:
                    return InnerString(content, "reply_content");
                case 5:
                    return "[撤回消息]";
                case 18:
                    return InnerString(content, "text");
                default:
                    return "";
            }
        }

        private static string MessageText(int type, string content, bool isMine, string talkerName)
        {
            switch (type)
            {
                case 1:
                    return InnerString(content, "content");
                case 2:
                case 6:
                    return "[图片]";
                case 7:
                case 10:
                case 13:
                    {
                        string title = InnerString(content, "title");
                        return title.Length > 0 ? "[视频] " + title : "[视频]";
                    }
                case 16:
                    return InnerString(content, "reply_content");
                case 5:
                    return (isMine ? "我" : (talkerName.Length > 0 ? talkerName : "对方"))
                        + "撤回了一条消息";
                case 18:
                    return InnerString(content, "text");
                default:
                    return "[暂不支持的消息]";
            }
        }

        private static string InnerString(string content, string field)
        {
            if (string.IsNullOrEmpty(content))
            {
                return "";
            }
            return JsonText.ReadString(content, field);
        }


        private static string DescribeCode(string body)
        {
            Match code = Regex.Match(JsonText.Head(body, 200), @"""code"":\s*(-?\d+)");
            if (!code.Success)
            {
                return "响应里没有code";
            }
            string value = code.Groups[1].Value;
            if (value == "0")
            {
                return null;
            }
            string message = JsonText.ReadString(body, "message");
            return "接口返回 code=" + value + (message.Length > 0 ? " " + message : "");
        }

        private static long ReadNumber(string json, string field)
        {
            Match m = Regex.Match(json, @"""" + field + @""":\s*(-?\d+)");
            return m.Success ? ParseLong(m.Groups[1].Value) : 0;
        }

        private static long ParseLong(string value)
        {
            long result;
            return long.TryParse(value, out result) ? result : 0;
        }

        private static string Normalize(string url)
        {
            if (url != null && url.StartsWith("//", StringComparison.Ordinal))
            {
                return "https:" + url;
            }
            return url ?? "";
        }

        private static string FormatTime(long unixSeconds)
        {
            if (unixSeconds <= 0)
            {
                return "";
            }
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            DateTime time = epoch.AddSeconds(unixSeconds).ToLocalTime();
            DateTime now = DateTime.Now;
            if (time.Date == now.Date)
            {
                return time.ToString("HH:mm");
            }
            if (time.Year == now.Year)
            {
                return time.ToString("MM-dd HH:mm");
            }
            return time.ToString("yyyy-MM-dd");
        }

        private static long CurrentUnixSeconds()
        {
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return (long)DateTime.UtcNow.Subtract(epoch).TotalSeconds;
        }

        private static string EscapeJson(string text)
        {
            StringBuilder sb = new StringBuilder(text.Length + 8);
            for (int i = 0; i < text.Length; i++)
            {
                char c = text[i];
                if (c == '\\' || c == '"')
                {
                    sb.Append('\\').Append(c);
                }
                else if (c == '\n')
                {
                    sb.Append("\\n");
                }
                else if (c == '\r')
                {
                    sb.Append("\\r");
                }
                else if (c == '\t')
                {
                    sb.Append("\\t");
                }
                else
                {
                    sb.Append(c);
                }
            }
            return sb.ToString();
        }

        private static void AppendForm(StringBuilder sb, string key, string value)
        {
            if (sb.Length > 0)
            {
                sb.Append('&');
            }
            sb.Append(FormEncode(key)).Append('=').Append(FormEncode(value));
        }

        private static string FormEncode(string value)
        {
            if (value == null)
            {
                value = "";
            }

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
    }
}
