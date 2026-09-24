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
                parameters["pn"] = page.ToString();
                parameters["ps"] = PageSize.ToString();
                parameters["tid"] = "0";
                parameters["order"] = "pubdate";
                parameters["keyword"] = "";
                parameters["order_avoided"] = "true";
                parameters["web_location"] = "333.999";

                string url = WbiSigner.Shared.SignUrl(ApiRoot + "/x/space/wbi/arc/search", parameters);

                Http.GetText(url, Http.Referer, delegate(HttpResult http)
                {
                    List<VideoItem> items = new List<VideoItem>();
                    bool hasMore = false;
                    string error = ParseVideos(http, items, out hasMore);
                    onDone(items, hasMore, error);
                });
            });
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

            MatchCollection bvids = BvidRegex.Matches(body);
            for (int i = 0; i < bvids.Count; i++)
            {
                string bvid = bvids[i].Groups[1].Value;
                if (bvid.Length == 0)
                {
                    continue;
                }

                int start = bvids[i].Index;
                int end = (i + 1 < bvids.Count) ? bvids[i + 1].Index : body.Length;
                if (end - start > 2000)
                {
                    end = start + 2000;
                }
                string region = body.Substring(start, end - start);

                string pic = FirstGroup(PicRegex, region);
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
                item.Title = JsonText.StripHtml(JsonText.Unescape(FirstGroup(TitleRegex, region)));
                item.Author = FirstGroup(AuthorRegex, region);
                item.View = FirstGroup(PlayRegex, region);
                items.Add(item);
            }

            hasMore = items.Count >= PageSize;
            return "";
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
