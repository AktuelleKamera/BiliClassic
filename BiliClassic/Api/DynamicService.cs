using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public static class DynamicService
    {
        private const string FeedUrl =
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?type=all";

        private static readonly Regex IdRegex = new Regex(@"""id_str"":\s*""(\d+)""");
        private static readonly Regex TypeRegex = new Regex(@"""type"":\s*""([^""]+)""");
        private static readonly Regex NameRegex = new Regex(@"""name"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex FaceRegex = new Regex(@"""face"":\s*""([^""]+)""");
        private static readonly Regex PubTimeRegex = new Regex(@"""pub_time"":\s*""([^""]*)""");
        private static readonly Regex TextRegex = new Regex(@"""text"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex CoverRegex = new Regex(@"""cover"":\s*""([^""]+)""");
        private static readonly Regex PicRegex = new Regex(@"""src"":\s*""([^""]+)""");
        private static readonly Regex BvidRegex = new Regex(@"""bvid"":\s*""([^""]+)""");
        private static readonly Regex RoomIdRegex = new Regex(@"""room_id"":\s*(\d+)");
        private static readonly Regex OffsetRegex = new Regex(@"""offset"":\s*""([^""]*)""");
        private static readonly Regex HasMoreRegex = new Regex(@"""has_more"":\s*(true|false)");

        public static void FetchFeed(string offset,
            Action<List<VideoItem>, string, string> onDone)
        {
            string plain = FeedUrl;
            if (!string.IsNullOrEmpty(offset))
            {
                plain += "&offset=" + Uri.EscapeDataString(offset);
            }

            WbiSigner.EnsureReady(delegate(string keyError)
            {
                string url = plain;
                if (string.IsNullOrEmpty(keyError))
                {
                    try
                    {
                        System.Collections.Generic.Dictionary<string, string> parameters =
                            new System.Collections.Generic.Dictionary<string, string>();
                        parameters["type"] = "all";
                        if (!string.IsNullOrEmpty(offset))
                        {
                            parameters["offset"] = offset;
                        }
                        url = WbiSigner.Shared.SignUrl(
                            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all",
                            parameters);
                    }
                    catch (Exception)
                    {
                        url = plain;
                    }
                }

                Http.GetText(url, Http.Referer, delegate(HttpResult http)
                {
                    List<VideoItem> items = new List<VideoItem>();
                    string nextOffset = "";
                    string error = Parse(http, items, out nextOffset);
                    onDone(items, nextOffset, error);
                });
            });
        }

        private static string Parse(HttpResult http, List<VideoItem> items, out string nextOffset)
        {
            nextOffset = "";
            if (http == null || string.IsNullOrEmpty(http.Body))
            {
                return string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error;
            }

            string body = http.Body;
            Match code = Regex.Match(body, @"""code"":\s*(-?\d+)");
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = JsonText.ReadString(body, "message");
                return "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
            }

            Match more = HasMoreRegex.Match(body);
            if (more.Success && more.Groups[1].Value == "true")
            {
                nextOffset = JsonText.ReadString(body, "offset");
            }

            MatchCollection ids = IdRegex.Matches(body);
            for (int i = 0; i < ids.Count; i++)
            {
                int start = ids[i].Index;
                int end = (i + 1 < ids.Count) ? ids[i + 1].Index : body.Length;
                int window = Math.Min(end - start, 4000);
                string region = body.Substring(start, window);

                VideoItem item = BuildItem(region);
                if (item != null)
                {
                    items.Add(item);
                }
            }

            return "";
        }

        private static VideoItem BuildItem(string region)
        {
            string type = FirstGroup(TypeRegex, region);
            if (type == "DYNAMIC_TYPE_NONE")
            {
                return null;
            }

            string author = JsonText.Unescape(FirstGroup(NameRegex, region));
            string pubTime = JsonText.Unescape(FirstGroup(PubTimeRegex, region));

            string text = ExtractDescText(region);

            string bvid = FirstGroup(BvidRegex, region);
            string cover = "";
            string cardLabel = "";
            long roomId = 0;

            if (bvid.Length > 0)
            {
                cover = Normalize(FirstGroup(CoverRegex, region));
                cardLabel = "投稿视频";
            }
            else
            {
                Match pic = PicRegex.Match(region);
                if (pic.Success)
                {
                    cover = Normalize(pic.Groups[1].Value);
                }
                Match room = RoomIdRegex.Match(region);
                if (room.Success)
                {
                    roomId = ParseLong(room.Groups[1].Value);
                    cardLabel = "直播间";
                }
            }

            if (text.Length == 0 && bvid.Length == 0 && roomId <= 0 && cover.Length == 0)
            {
                return null;
            }

            VideoItem item = new VideoItem();
            item.Bvid = bvid;
            item.RoomId = roomId;
            item.Pic = cover;

            string head = author.Length > 0 ? author + "：" : "";
            string main = text.Length > 0 ? text : (bvid.Length > 0 ? "发布了视频" : "开播了");
            item.Title = head + main;
            item.Subtitle = pubTime + (cardLabel.Length > 0 ? "  " + cardLabel : "");
            return item;
        }

        private static string ExtractDescText(string region)
        {
            int mdyn = region.IndexOf("\"module_dynamic\":", StringComparison.Ordinal);
            if (mdyn < 0)
            {
                return "";
            }

            int nodes = region.IndexOf("\"rich_text_nodes\":", mdyn, StringComparison.Ordinal);
            if (nodes < 0)
            {
                return "";
            }

            int end = region.IndexOf(']', nodes);
            string area = end > nodes ? region.Substring(nodes, end - nodes) : region.Substring(nodes);

            System.Text.StringBuilder sb = new System.Text.StringBuilder();
            MatchCollection texts = TextRegex.Matches(area);
            for (int i = 0; i < texts.Count; i++)
            {
                sb.Append(JsonText.Unescape(texts[i].Groups[1].Value));
            }
            return sb.ToString();
        }

        private static string FirstGroup(Regex regex, string text)
        {
            Match match = regex.Match(text);
            return match.Success ? match.Groups[1].Value : "";
        }

        private static long ParseLong(string s)
        {
            long value;
            return long.TryParse(s, out value) ? value : 0;
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
