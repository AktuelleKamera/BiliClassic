using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class UserSpaceCard
    {
        public string Mid = "";
        public string Name = "";
        public string Face = "";
        public string Sign = "";
        public int Level;
        public int Fans;
        public int Following;
    }

    public static class UserSpaceService
    {
        private const string ApiRoot = "https://api.bilibili.com";

        public const int PageSize = 40;

        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");
        private static readonly Regex LevelRegex = new Regex(@"""current_level"":\s*(\d+)");
        private static readonly Regex BvidRegex = new Regex(@"""bvid"":\s*""([^""]+)""");
        private static readonly Regex PicRegex = new Regex(@"""pic"":\s*""([^""]+)""");
        private static readonly Regex TitleRegex = new Regex(@"""title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex PlayRegex = new Regex(@"""play"":\s*(\d+)");
        private static readonly Regex AuthorRegex = new Regex(@"""author"":\s*""([^""]*)""");


        public static void FetchCard(string mid, Action<UserSpaceCard, string> onDone)
        {
            UserSpaceCard card = new UserSpaceCard();
            card.Mid = mid == null ? "" : mid;

            if (string.IsNullOrEmpty(mid))
            {
                onDone(card, "缺少mid");
                return;
            }

            Http.GetText(ApiRoot + "/x/web-interface/card?mid=" + mid, Http.Referer,
                delegate(HttpResult http)
                {
                    onDone(card, ParseCard(http, card));
                });
        }

        private static string ParseCard(HttpResult http, UserSpaceCard card)
        {
            if (http == null || string.IsNullOrEmpty(http.Body))
            {
                return string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error;
            }

            string body = http.Body;
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                return "接口返回 code=" + code.Groups[1].Value;
            }

            card.Name = JsonText.ReadString(body, "name");
            card.Face = JsonText.ReadString(body, "face");
            card.Sign = JsonText.ReadString(body, "sign");
            card.Fans = (int)ReadNumber(body, "follower");
            card.Following = (int)ReadNumber(body, "following");

            Match level = LevelRegex.Match(body);
            if (level.Success)
            {
                int.TryParse(level.Groups[1].Value, out card.Level);
            }

            return "";
        }


        public static void FetchVideos(string mid, int page,
                                       Action<List<VideoItem>, bool, string> onDone)
        {
            FetchVideos(mid, page, 0, onDone);
        }

        private static void FetchVideos(string mid, int page, int attempt,
                                        Action<List<VideoItem>, bool, string> onDone)
        {
            if (string.IsNullOrEmpty(mid))
            {
                onDone(new List<VideoItem>(), false, "缺少mid");
                return;
            }

            if (page < 1)
            {
                page = 1;
            }

            WbiSigner.EnsureReady(delegate(string keyError)
            {
                if (!string.IsNullOrEmpty(keyError))
                {
                    onDone(new List<VideoItem>(), false, keyError);
                    return;
                }

                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters["mid"] = mid;
                parameters["order_avoided"] = "true";
                parameters["order"] = "pubdate";
                parameters["platform"] = "web";
                parameters["pn"] = page.ToString();
                parameters["ps"] = PageSize.ToString();
                parameters["tid"] = "0";
                parameters["web_location"] = "333.1387";

                string url = WbiSigner.Shared.SignUrl(ApiRoot + "/x/space/wbi/arc/search", parameters);

                Http.GetText(url,
                    "https://space.bilibili.com/" + mid,
                    "https://space.bilibili.com",
                    delegate(HttpResult http)
                {
                    List<VideoItem> items = new List<VideoItem>();
                    bool hasMore = false;
                    string error = ParseVideos(http, items, out hasMore);

                    if (attempt < 1 && IsRiskControl(error))
                    {
                        // 风控：刷新 WBI 后重试一次
                        WbiSigner.Shared.FetchKeys(delegate(string refreshError)
                        {
                            FetchVideos(mid, page, attempt + 1, onDone);
                        });
                        return;
                    }

                    onDone(items, hasMore, error);
                });
            });
        }

        private static bool IsRiskControl(string error)
        {
            return !string.IsNullOrEmpty(error)
                && (error.IndexOf("-352", StringComparison.Ordinal) >= 0
                    || error.IndexOf("-412", StringComparison.Ordinal) >= 0);
        }

        private static string ParseVideos(HttpResult http, List<VideoItem> items, out bool hasMore)
        {
            hasMore = false;
            if (http == null || string.IsNullOrEmpty(http.Body))
            {
                return string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error;
            }

            string body = http.Body;
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                return "接口返回 code=" + code.Groups[1].Value;
            }

            // 只取 vlist 数组，逐对象解析，避免混入合集等其它块
            string array = ExtractArray(body, "vlist");
            if (array == null)
            {
                return "接口返回里没有 vlist";
            }

            List<string> objects = SplitObjects(array);
            for (int i = 0; i < objects.Count; i++)
            {
                string obj = objects[i];

                string bvid = FirstGroup(BvidRegex, obj);
                if (bvid.Length == 0)
                {
                    continue;
                }

                string pic = FirstGroup(PicRegex, obj);
                if (pic.Length == 0)
                {
                    continue;
                }
                if (pic.StartsWith("//", StringComparison.Ordinal))
                {
                    pic = "https:" + pic;
                }

                VideoItem item = new VideoItem();
                item.Bvid = bvid;
                item.Pic = pic;
                item.Title = JsonText.StripHtml(JsonText.Unescape(FirstGroup(TitleRegex, obj)));
                item.Author = FirstGroup(AuthorRegex, obj);
                item.View = FirstGroup(PlayRegex, obj);
                items.Add(item);
            }

            hasMore = items.Count >= PageSize;
            return "";
        }

        private static string ExtractArray(string json, string key)
        {
            int k = json.IndexOf("\"" + key + "\"", StringComparison.Ordinal);
            if (k < 0)
            {
                return null;
            }
            int colon = json.IndexOf(':', k);
            if (colon < 0)
            {
                return null;
            }
            int start = json.IndexOf('[', colon);
            if (start < 0)
            {
                return null;
            }

            int depth = 0;
            bool inString = false;
            bool escape = false;
            for (int i = start; i < json.Length; i++)
            {
                char c = json[i];
                if (inString)
                {
                    if (escape)
                    {
                        escape = false;
                    }
                    else if (c == '\\')
                    {
                        escape = true;
                    }
                    else if (c == '"')
                    {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"')
                {
                    inString = true;
                }
                else if (c == '[')
                {
                    depth++;
                }
                else if (c == ']')
                {
                    depth--;
                    if (depth == 0)
                    {
                        return json.Substring(start, i - start + 1);
                    }
                }
            }
            return null;
        }

        private static List<string> SplitObjects(string array)
        {
            List<string> objects = new List<string>();
            int depth = 0;
            bool inString = false;
            bool escape = false;
            int start = -1;
            for (int i = 0; i < array.Length; i++)
            {
                char c = array[i];
                if (inString)
                {
                    if (escape)
                    {
                        escape = false;
                    }
                    else if (c == '\\')
                    {
                        escape = true;
                    }
                    else if (c == '"')
                    {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"')
                {
                    inString = true;
                }
                else if (c == '{')
                {
                    if (depth == 0)
                    {
                        start = i;
                    }
                    depth++;
                }
                else if (c == '}')
                {
                    depth--;
                    if (depth == 0 && start >= 0)
                    {
                        objects.Add(array.Substring(start, i - start + 1));
                        start = -1;
                    }
                }
            }
            return objects;
        }

        private static long ReadNumber(string text, string field)
        {
            string pattern = (char)34 + field + (char)34 + ": *([0-9]+)";
            Match m = Regex.Match(text, pattern);
            long value;
            return m.Success && long.TryParse(m.Groups[1].Value, out value) ? value : 0;
        }

        private static string FirstGroup(Regex regex, string text)
        {
            Match m = regex.Match(text);
            return m.Success ? m.Groups[1].Value : "";
        }
    }
}
