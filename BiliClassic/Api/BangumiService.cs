using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class BangumiEpisode
    {
        public string Aid = "";
        public string Cid = "";
        public string Bvid = "";
        public string Title = "";
        public string LongTitle = "";
        public string Badge = "";
    }

    public sealed class BangumiInfo
    {
        public long SeasonId;
        public long MediaId;

        public int SeasonType;

        public string Title = "";
        public string Cover = "";
        public string Evaluate = "";
        public string Areas = "";
        public double Score;

        public readonly List<BangumiEpisode> Episodes = new List<BangumiEpisode>();

        public string Error = "";
    }

    public static class BangumiService
    {
        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");
        private static readonly Regex MessageRegex = new Regex(@"""message"":\s*""([^""]*)""");
        private static readonly Regex SeasonIdRegex = new Regex(@"""season_id"":\s*(\d+)");
        private static readonly Regex MediaIdRegex = new Regex(@"""media_id"":\s*(\d+)");
        private static readonly Regex CidRegex = new Regex(@"""cid"":\s*(\d+)");
        private static readonly Regex AidRegex = new Regex(@"""aid"":\s*(\d+)");
        private static readonly Regex EpIdRegex = new Regex(@"""ep_id"":\s*(\d+)");
        private static readonly Regex IdRegex = new Regex(@"""id"":\s*(\d+)");
        private static readonly Regex BvidRegex = new Regex(@"""bvid"":\s*""([^""]+)""");
        private static readonly Regex CoverRegex = new Regex(@"""cover"":\s*""([^""]+)""");
        private static readonly Regex TitleRegex = new Regex(@"""title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex LongTitleRegex = new Regex(@"""long_title"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex BadgeRegex = new Regex(@"""badge"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex EvaluateRegex = new Regex(@"""evaluate"":\s*""((?:[^""\\]|\\.)*)""");
        private static readonly Regex ViewRegex = new Regex(@"""view"":\s*(\d+)");
        private static readonly Regex ScoreRegex = new Regex(@"""score"":\s*([0-9.]+)");

        public static void FetchSeason(long seasonId, Action<BangumiInfo> onDone)
        {
            BangumiInfo info = new BangumiInfo();
            info.SeasonId = seasonId;

            if (seasonId <= 0)
            {
                info.Error = "番剧ID为空";
                onDone(info);
                return;
            }

            string url = "https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId;
            Http.GetText(url, "https://www.bilibili.com/", delegate(HttpResult http)
            {
                try
                {
                    if (string.IsNullOrEmpty(http.Body))
                    {
                        info.Error = string.IsNullOrEmpty(http.Error)
                            ? "番剧详情无响应"
                            : "番剧详情失败: " + http.Error;
                    }
                    else
                    {
                        ParseSeason(http.Body, info);
                    }
                }
                catch (Exception ex)
                {
                    info.Error = "解析番剧失败: " + ex.Message;
                }
                onDone(info);
            });
        }

        public static void FetchSeasonByEp(long epId, Action<BangumiInfo> onDone)
        {
            BangumiInfo info = new BangumiInfo();
            if (epId <= 0)
            {
                info.Error = "番剧ID为空";
                onDone(info);
                return;
            }

            string url = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epId;
            Http.GetText(url, "https://www.bilibili.com/", delegate(HttpResult http)
            {
                try
                {
                    if (string.IsNullOrEmpty(http.Body))
                    {
                        info.Error = string.IsNullOrEmpty(http.Error)
                            ? "番剧详情无响应"
                            : "番剧详情失败: " + http.Error;
                    }
                    else
                    {
                        ParseSeason(http.Body, info);
                    }
                }
                catch (Exception ex)
                {
                    info.Error = "解析番剧失败: " + ex.Message;
                }
                onDone(info);
            });
        }

        private static void ParseSeason(string body, BangumiInfo info)
        {
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = JsonText.ReadString(body, "message");
                info.Error = "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
                return;
            }

            int start = body.IndexOf("\"result\":", StringComparison.Ordinal);
            string scope = start >= 0 ? body.Substring(start) : body;

            string title = LastGroup(TitleRegex, scope);
            if (title.Length == 0)
            {
                title = FirstGroup(new Regex(@"""season_title"":\s*""((?:[^""\\]|\\.)*)"""), scope);
            }
            info.Title = JsonText.StripHtml(JsonText.Unescape(title));
            info.Cover = FirstGroup(CoverRegex, scope);
            if (info.Cover.StartsWith("//"))
            {
                info.Cover = "https:" + info.Cover;
            }
            info.Evaluate = JsonText.StripHtml(JsonText.Unescape(FirstGroup(EvaluateRegex, scope)));
            info.MediaId = ParseLong(FirstGroup(MediaIdRegex, scope));

            long seasonId = ParseLong(FirstGroup(SeasonIdRegex, scope));
            if (seasonId > 0)
            {
                info.SeasonId = seasonId;
            }

            Match score = ScoreRegex.Match(scope);
            if (score.Success)
            {
                double value;
                if (double.TryParse(score.Groups[1].Value, out value))
                {
                    info.Score = value;
                }
            }

            Match type = Regex.Match(scope, @"""type"":\s*(\d+)");
            if (type.Success)
            {
                int value;
                if (int.TryParse(type.Groups[1].Value, out value))
                {
                    info.SeasonType = value;
                }
            }

            ParseEpisodes(scope, info);

            if (info.Episodes.Count == 0 && info.Error.Length == 0)
            {
                info.Error = "这季没有可播放的分集";
            }
        }

        private static void ParseEpisodes(string scope, BangumiInfo info)
        {
            MatchCollection cids = CidRegex.Matches(scope);
            List<string> seen = new List<string>();

            for (int i = 0; i < cids.Count; i++)
            {
                int m = cids[i].Index;
                int next = (i + 1 < cids.Count) ? cids[i + 1].Index : scope.Length;
                string cid = cids[i].Groups[1].Value;
                if (seen.Contains(cid))
                {
                    continue;
                }
                seen.Add(cid);

                string forward = scope.Substring(m, Math.Min(next - m, 1200));
                int backStart = m - 800;
                if (backStart < 0)
                {
                    backStart = 0;
                }
                string backward = scope.Substring(backStart, m - backStart);

                BangumiEpisode ep = new BangumiEpisode();
                ep.Cid = cid;
                ep.Aid = LastGroup(AidRegex, backward);
                ep.Bvid = LastGroup(BvidRegex, backward);
                ep.Badge = LastGroup(BadgeRegex, backward);
                ep.Title = JsonText.StripHtml(JsonText.Unescape(FirstGroup(TitleRegex, forward)));
                ep.LongTitle = JsonText.StripHtml(JsonText.Unescape(FirstGroup(LongTitleRegex, forward)));

                if (ep.Aid.Length == 0)
                {
                    continue;
                }
                info.Episodes.Add(ep);
            }
        }

        public static void FetchFollowing(long mid, int page, Action<List<VideoItem>, bool, string> onDone)
        {
            List<VideoItem> items = new List<VideoItem>();
            if (mid <= 0)
            {
                onDone(items, false, "未登录");
                return;
            }

            string url = "https://api.bilibili.com/x/space/bangumi/follow/list?type=1&follow_status=0"
                + "&pn=" + page + "&ps=15&vmid=" + mid;

            Http.GetText(url, "https://space.bilibili.com/", delegate(HttpResult http)
            {
                string error = "";
                try
                {
                    if (string.IsNullOrEmpty(http.Body))
                    {
                        error = string.IsNullOrEmpty(http.Error)
                            ? "追番列表无响应"
                            : "追番列表失败: " + http.Error;
                    }
                    else
                    {
                        error = ParseFollowing(http.Body, items);
                    }
                }
                catch (Exception ex)
                {
                    error = "解析追番失败: " + ex.Message;
                }
                onDone(items, items.Count >= 15, error);
            });
        }

        private static string ParseFollowing(string body, List<VideoItem> items)
        {
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = JsonText.ReadString(body, "message");
                return "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
            }

            int start = body.IndexOf("\"list\":", StringComparison.Ordinal);
            string scope = start >= 0 ? body.Substring(start) : body;

            MatchCollection mediaIds = MediaIdRegex.Matches(scope);
            List<string> seen = new List<string>();
            for (int i = 0; i < mediaIds.Count; i++)
            {
                int m = mediaIds[i].Index;
                int next = (i + 1 < mediaIds.Count) ? mediaIds[i + 1].Index : scope.Length;
                string window = scope.Substring(m, Math.Min(next - m, 2000));
                string mediaId = mediaIds[i].Groups[1].Value;
                if (seen.Contains(mediaId))
                {
                    continue;
                }
                seen.Add(mediaId);

                VideoItem item = new VideoItem();
                item.IsBangumi = true;
                item.SeasonId = ParseLong(FirstGroup(SeasonIdRegex, window));
                item.Title = JsonText.StripHtml(JsonText.Unescape(FirstGroup(TitleRegex, window)));
                item.Pic = FirstGroup(CoverRegex, window);
                item.View = FirstGroup(ViewRegex, window);
                if (item.SeasonId <= 0)
                {
                    continue;
                }
                items.Add(item);
            }

            if (items.Count == 0)
            {
                return "这里还没有追番";
            }
            return "";
        }

        public static void SearchBangumi(string keyword, int page, Action<List<VideoItem>, bool, string> onDone)
        {
            List<VideoItem> items = new List<VideoItem>();
            if (string.IsNullOrEmpty(keyword))
            {
                onDone(items, false, "");
                return;
            }

            WbiSigner.EnsureReady(delegate(string keyError)
            {
                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters["search_type"] = "media_bangumi";
                parameters["keyword"] = keyword;
                parameters["page"] = page.ToString();
                parameters["page_size"] = "20";

                string url = WbiSigner.Shared.SignUrl(
                    "https://api.bilibili.com/x/web-interface/wbi/search/type", parameters);

                Http.GetText(url, "https://search.bilibili.com/", delegate(HttpResult http)
                {
                    string error = "";
                    bool full = false;
                    try
                    {
                        if (string.IsNullOrEmpty(http.Body))
                        {
                            error = string.IsNullOrEmpty(http.Error)
                                ? "番剧搜索无响应"
                                : "番剧搜索失败: " + http.Error;
                        }
                        else
                        {
                            error = ParseBangumiSearch(http.Body, items, out full);
                        }
                    }
                    catch (Exception ex)
                    {
                        error = "解析番剧搜索失败: " + ex.Message;
                    }
                    onDone(items, full, error);
                });
            });
        }

        private static string ParseBangumiSearch(string body, List<VideoItem> items, out bool full)
        {
            full = false;

            int start = body.IndexOf("\"result\":", StringComparison.Ordinal);
            if (start < 0)
            {
                return "搜索接口没有返回结果数组，响应开头: " + JsonText.Head(body, 120);
            }
            string scope = body.Substring(start);

            MatchCollection seasons = SeasonIdRegex.Matches(scope);
            List<string> seen = new List<string>();
            for (int i = 0; i < seasons.Count; i++)
            {
                int m = seasons[i].Index;
                int next = (i + 1 < seasons.Count) ? seasons[i + 1].Index : scope.Length;
                string window = scope.Substring(m, Math.Min(next - m, 2000));
                string seasonId = seasons[i].Groups[1].Value;
                if (seen.Contains(seasonId))
                {
                    continue;
                }
                seen.Add(seasonId);

                VideoItem item = new VideoItem();
                item.IsBangumi = true;
                item.SeasonId = ParseLong(seasonId);
                item.Title = JsonText.StripHtml(JsonText.Unescape(FirstGroup(TitleRegex, window)));
                item.Pic = FirstGroup(CoverRegex, window);
                item.Subtitle = "番剧";
                if (item.SeasonId <= 0)
                {
                    continue;
                }
                items.Add(item);
            }

            full = seasons.Count >= 20;
            return "";
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

        private static long ParseLong(string value)
        {
            long result;
            return long.TryParse(value, out result) ? result : 0;
        }
    }
}
