using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>当前用户名片</summary>
    public sealed class MineCard
    {
        public long Mid;
        public string Name = "";
        public string Face = "";
        public string Sign = "";
        public int Fans;
        public int Level;
        public string Official = "";

        /// <summary>未登录时为false</summary>
        public bool LoggedIn;
    }

    /// <summary>收藏夹</summary>
    public sealed class FavFolder
    {
        public long Fid;
        public string Title = "";
        public int Count;
        public bool IsPrivate;

        /// <summary>第一个视频的封面，取不到就是空</summary>
        public string Cover = "";
    }

    /// <summary>「我的」页的数据接口</summary>
    public static class MineService
    {
        private const string ApiRoot = "https://api.bilibili.com";

        // ---------------- 我的名片 ----------------

        public static void FetchCard(Action<MineCard, string> onDone)
        {
            Http.GetText(ApiRoot + "/x/space/myinfo", Http.Referer, delegate(HttpResult http)
            {
                MineCard card = new MineCard();
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(card, string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error);
                    return;
                }

                Match code = Regex.Match(http.Body, @"""code"":\s*(-?\d+)");
                if (code.Success && code.Groups[1].Value != "0")
                {
                    // -101是没登录，不报错，界面显示未登录
                    onDone(card, "未登录（code=" + code.Groups[1].Value + "）");
                    return;
                }

                card.LoggedIn = true;
                card.Name = JsonText.ReadString(http.Body, "name");
                card.Face = JsonText.ReadString(http.Body, "face");
                card.Sign = JsonText.ReadString(http.Body, "sign");
                card.Official = JsonText.ReadString(http.Body, "desc");
                card.Fans = (int)ReadNumber(http.Body, "follower");
                card.Level = (int)ReadNumber(http.Body, "level");

                Match mid = Regex.Match(http.Body, @"""mid"":\s*(\d+)");
                if (mid.Success)
                {
                    long.TryParse(mid.Groups[1].Value, out card.Mid);
                }

                onDone(card, "");
            });
        }

        // ---------------- 关注的人 ----------------

        public const int PageSize = 20;

        /// <summary>
        /// 关注的人，映射成UserItem以复用列表模板
        /// 关注的是用户不是视频，Bvid留空，头像走AvatarLoader
        /// </summary>
        public static void FetchFollowing(long mid, int page,
                                          Action<List<UserItem>, bool, string> onDone)
        {
            if (mid <= 0)
            {
                onDone(null, false, "未登录");
                return;
            }

            string url = ApiRoot + "/x/relation/followings?vmid=" + mid
                + "&pn=" + page + "&ps=" + PageSize + "&order=desc&order_type=attention";

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                List<UserItem> users = new List<UserItem>();
                bool hasMore = false;
                string error = ParseFollowing(http, users, out hasMore);
                onDone(users, hasMore, error);
            });
        }

        /// <summary>解析关注列表</summary>
        private static string ParseFollowing(HttpResult http, List<UserItem> users, out bool hasMore)
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

            // 锚点用uname，每个用户一个
            foreach (Match m in Regex.Matches(body, @"""uname"":\s*""((?:[^""\\]|\\.)*)"""))
            {
                string after = Window(body, m.Index, 500);

                UserItem user = new UserItem();
                user.Name = JsonText.Unescape(m.Groups[1].Value);
                user.AvatarUrl = FirstString(after, "face");

                string sign = JsonText.StripHtml(JsonText.Unescape(FirstString(after, "sign")));
                user.Sign = sign.Length > 0 ? sign : "（这个人很懒）";

                // 往前取最后一个mid
                int start = m.Index - 400;
                if (start < 0)
                {
                    start = 0;
                }
                long mid = LastNumber(body.Substring(start, m.Index - start), "mid");
                user.Mid = mid > 0 ? mid.ToString() : "";

                users.Add(user);
            }

            hasMore = users.Count >= PageSize;
            return "";
        }

        // ---------------- 播放历史 ----------------

        // 游标分页，游标存成静态的
        private static string _historyBusiness = "";
        private static long _historyMax;
        private static long _historyViewAt;

        public static void FetchHistory(int page, Action<List<VideoItem>, bool, string> onDone)
        {
            if (page <= 1)
            {
                _historyBusiness = "";
                _historyMax = 0;
                _historyViewAt = 0;
            }

            string url = ApiRoot + "/x/web-interface/history/cursor?type=archive"
                + "&view_at=" + _historyViewAt
                + "&business=" + _historyBusiness
                + "&max=" + _historyMax;

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                List<VideoItem> items = new List<VideoItem>();
                bool hasMore = false;
                string error = ParseHistory(http, items, out hasMore);
                onDone(items, hasMore, error);
            });
        }

        private static string ParseHistory(HttpResult http, List<VideoItem> items, out bool hasMore)
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

            // 锚点用整个history对象
            // oid和bvid之间还夹着page/cid/business，连着匹配一条都取不到
            foreach (Match m in Regex.Matches(body, @"""history"":\s*\{([^}]*)\}"))
            {
                string record = m.Groups[1].Value;
                string bvid = FirstString(record, "bvid");
                if (bvid.Length == 0)
                {
                    continue;
                }

                int start = m.Index - 900;
                if (start < 0)
                {
                    start = 0;
                }
                string before = body.Substring(start, m.Index - start);
                // 窗口从history对象之后开始，免得把progress取成别的字段
                string after = Window(body, m.Index + m.Length, 700);

                long progress = ReadNumber(after, "progress");

                VideoItem item = new VideoItem();
                item.Bvid = bvid;
                item.Title = JsonText.StripHtml(JsonText.Unescape(LastString(before, "title")));
                item.Pic = Normalize(LastString(before, "cover"));
                item.Author = LastString(after, "author_name");

                // progress小于0是已看完，等于0是没看过，走Subtitle
                if (progress < 0)
                {
                    item.Subtitle = "已看完";
                }
                else if (progress == 0)
                {
                    item.Subtitle = "还没看过";
                }
                else
                {
                    item.Subtitle = "看到 " + FormatTime((int)progress);
                }

                items.Add(item);
            }

            // 游标下次请求要原样带回
            Match cursor = Regex.Match(body, @"""cursor"":\s*\{[^}]*\}");
            if (cursor.Success)
            {
                string c = cursor.Value;
                _historyBusiness = FirstString(c, "business");
                _historyMax = ReadNumber(c, "max");
                _historyViewAt = ReadNumber(c, "view_at");
                hasMore = _historyViewAt > 0;
            }

            return "";
        }

        // ---------------- 我的收藏 ----------------

        public static void FetchFavoriteFolders(long mid, Action<List<FavFolder>, string> onDone)
        {
            if (mid <= 0)
            {
                onDone(null, "未登录");
                return;
            }

            string url = ApiRoot + "/x/v3/fav/folder/created/list-all?up_mid=" + mid + "&type=0";

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                List<FavFolder> folders = new List<FavFolder>();
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(null, string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error);
                    return;
                }

                string error = DescribeCode(http.Body);
                if (error != null)
                {
                    onDone(null, error);
                    return;
                }

                // 锚点用media_count，它在收藏夹对象末段
                foreach (Match m in Regex.Matches(http.Body, @"""media_count"":\s*(\d+)"))
                {
                    int start = m.Index - 600;
                    if (start < 0)
                    {
                        start = 0;
                    }
                    string before = http.Body.Substring(start, m.Index - start);

                    FavFolder folder = new FavFolder();
                    folder.Count = (int)ParseLong(m.Groups[1].Value);
                    folder.Title = JsonText.Unescape(LastString(before, "title"));
                    folder.Fid = LastNumber(before, "fid");
                    if (folder.Fid == 0)
                    {
                        // 有的响应只给完整id（mlid），fid缺失时回退
                        folder.Fid = LastNumber(before, "id");
                    }

                    int attr = (int)LastNumber(before, "attr");
                    folder.IsPrivate = (attr & 1) != 0;

                    if (folder.Title.Length == 0)
                    {
                        folder.Title = "未命名收藏夹";
                    }
                    folders.Add(folder);
                }

                FillFolderCovers(mid, folders, delegate
                {
                    onDone(folders, "");
                });
            });
        }

        /// <summary>收藏夹封面：列表接口不给cover，逐个取第一个视频</summary>
        private static void FillFolderCovers(long mid, List<FavFolder> folders, Action onDone)
        {
            FillMissingCovers(mid, folders, 0, onDone);
        }

        /// <summary>还没封面的夹子逐个取第一个视频</summary>
        private static void FillMissingCovers(long mid, List<FavFolder> folders, int index,
                                              Action onDone)
        {
            if (index >= folders.Count)
            {
                onDone();
                return;
            }

            FavFolder folder = folders[index];
            if (folder.Cover.Length > 0 || folder.Fid <= 0)
            {
                FillMissingCovers(mid, folders, index + 1, onDone);
                return;
            }

            string url = ApiRoot + "/x/space/fav/arc?vmid=" + mid
                + "&ps=1&fid=" + folder.Fid + "&tid=0&keyword=&pn=1&order=fav_time";

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                string cover = "";
                if (http != null && !string.IsNullOrEmpty(http.Body)
                    && DescribeCode(http.Body) == null)
                {
                    cover = FirstPic(http.Body);
                }

                if (cover.Length > 0)
                {
                    folder.Cover = Normalize(cover);
                    FillMissingCovers(mid, folders, index + 1, onDone);
                    return;
                }

                // 老接口没给就换网页那个
                string alt = ApiRoot + "/x/v3/fav/resource/list?media_id=" + folder.Fid
                    + "&pn=1&ps=1&keyword=&order=mtime&type=0&tid=0&platform=web";

                Http.GetText(alt, Http.Referer, delegate(HttpResult second)
                {
                    if (second != null && !string.IsNullOrEmpty(second.Body))
                    {
                        string pic = FirstPic(second.Body);
                        if (pic.Length > 0)
                        {
                            folder.Cover = Normalize(pic);
                        }
                    }
                    FillMissingCovers(mid, folders, index + 1, onDone);
                });
            });
        }

        /// <summary>第一个pic或cover，两个接口字段名不一样</summary>
        private static string FirstPic(string body)
        {
            Match pic = Regex.Match(body, @"""pic"":\s*""([^""]+)""");
            if (pic.Success)
            {
                return JsonText.Unescape(pic.Groups[1].Value);
            }
            Match cover = Regex.Match(body, @"""cover"":\s*""([^""]+)""");
            return cover.Success ? JsonText.Unescape(cover.Groups[1].Value) : "";
        }

        /// <summary>收藏夹里的视频</summary>
        public static void FetchFolderVideos(long mid, long fid, int page,
                                             Action<List<VideoItem>, bool, string> onDone)
        {
            string url = ApiRoot + "/x/space/fav/arc?vmid=" + mid
                + "&ps=30&fid=" + fid + "&tid=0&keyword=&pn=" + page + "&order=fav_time";

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                if (http != null && !string.IsNullOrEmpty(http.Body)
                    && DescribeCode(http.Body) == null)
                {
                    List<VideoItem> items = ParseArchives(http.Body);
                    if (items.Count > 0)
                    {
                        onDone(items, HasMorePage(http.Body, page, items.Count), "");
                        return;
                    }
                }

                // 老接口没数据就换网页在用的那个
                FetchResourceList(fid, page, onDone);
            });
        }

        /// <summary>
        /// 解析fav/arc的archives[]
        /// 这个接口不给bvid字段，BV号只在对象末尾的short_link_v2里
        /// 两个short_link_v2之间正好是一个对象，和字段顺序无关
        /// </summary>
        private static List<VideoItem> ParseArchives(string body)
        {
            List<VideoItem> items = new List<VideoItem>();
            MatchCollection links = Regex.Matches(body,
                @"""short_link_v2"":\s*""https://b23\.tv/(BV1[0-9A-Za-z]{9})""");

            int cursor = 0;
            for (int i = 0; i < links.Count; i++)
            {
                int end = links[i].Index;
                string region = body.Substring(cursor, end - cursor);
                cursor = end;

                string bvid = links[i].Groups[1].Value;
                if (ContainsBvid(items, bvid))
                {
                    continue;
                }

                VideoItem item = ParseArchiveRegion(region, bvid);
                if (item != null)
                {
                    items.Add(item);
                }
            }
            return items;
        }

        /// <summary>一个对象块里取title/pic/UP主/播放数</summary>
        private static VideoItem ParseArchiveRegion(string region, string bvid)
        {
            VideoItem item = new VideoItem();
            item.Bvid = bvid;
            item.Title = JsonText.StripHtml(JsonText.ReadString(region, "title"));
            item.Pic = Normalize(JsonText.ReadString(region, "pic"));
            item.Author = JsonText.ReadString(region, "name");

            long count = ReadNumber(region, "view");
            if (count > 0)
            {
                item.View = count.ToString();
            }

            if (item.Title.Length == 0 && item.Pic.Length == 0)
            {
                return null;
            }
            return item;
        }

        private static bool ContainsBvid(List<VideoItem> items, string bvid)
        {
            for (int i = 0; i < items.Count; i++)
            {
                if (items[i].Bvid == bvid)
                {
                    return true;
                }
            }
            return false;
        }

        /// <summary>
        /// 还有下一页吗
        /// 用pagecount：偶有视频缺short_link_v2，按本页条数判断会误判
        /// </summary>
        private static bool HasMorePage(string body, int page, int parsedCount)
        {
            Match count = Regex.Match(body, @"""pagecount"":\s*(\d+)");
            if (count.Success)
            {
                return ParseLong(count.Groups[1].Value) > page;
            }
            return parsedCount >= 30;
        }

        /// <summary>网页版收藏夹接口，20条一页，带bvid字段</summary>
        private static void FetchResourceList(long fid, int page,
                                             Action<List<VideoItem>, bool, string> onDone)
        {
            string url = ApiRoot + "/x/v3/fav/resource/list?media_id=" + fid
                + "&pn=" + page + "&ps=20&keyword=&order=mtime&type=0&tid=0&platform=web";

            Http.GetText(url, Http.Referer, delegate(HttpResult http)
            {
                List<VideoItem> items = new List<VideoItem>();
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(items, false, string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error);
                    return;
                }

                string error = DescribeCode(http.Body);
                if (error != null)
                {
                    onDone(items, false, error);
                    return;
                }

                items = ParseByBvid(http.Body);
                onDone(items, items.Count >= 20, "");
            });
        }

        /// <summary>
        /// 以bvid为锚点，窗口取到下一个bvid
        /// 收藏接口的title和pic都在bvid之后，往前取会串到上一条
        /// </summary>
        private static List<VideoItem> ParseByBvid(string body)
        {
            List<VideoItem> items = new List<VideoItem>();
            MatchCollection ids = Regex.Matches(body, @"""bvid"":\s*""([^""]+)""");

            for (int i = 0; i < ids.Count; i++)
            {
                int start = ids[i].Index;
                int end = i + 1 < ids.Count ? ids[i + 1].Index : body.Length;
                if (end - start > 3000)
                {
                    end = start + 3000;
                }
                string region = body.Substring(start, end - start);

                VideoItem item = new VideoItem();
                item.Bvid = ids[i].Groups[1].Value;
                item.Title = JsonText.StripHtml(JsonText.ReadString(region, "title"));
                item.Pic = Normalize(JsonText.ReadString(region, "pic"));
                if (item.Pic.Length == 0)
                {
                    item.Pic = Normalize(JsonText.ReadString(region, "cover"));
                }
                item.Author = JsonText.ReadString(region, "name");

                // 老接口是stat.view，网页接口是cnt_info.play
                long count = ReadNumber(region, "view");
                if (count <= 0)
                {
                    count = ReadNumber(region, "play");
                }
                if (count > 0)
                {
                    item.View = count.ToString();
                }

                if (item.Title.Length > 0 || item.Pic.Length > 0)
                {
                    items.Add(item);
                }
            }
            return items;
        }

        // ---------------- 公共小工具 ----------------

        /// <summary>
        /// 取code非0时的错误描述，返回null表示正常
        /// 必须先查code：正常响应也带code=0，不判会把错误当没数据
        /// </summary>
        private static string DescribeCode(string body)
        {
            Match code = Regex.Match(body, @"""code"":\s*(-?\d+)");
            if (!code.Success || code.Groups[1].Value == "0")
            {
                return null;
            }

            string message = JsonText.ReadString(body, "message");
            return "接口返回 code=" + code.Groups[1].Value
                + (message.Length > 0 ? " " + message : "");
        }

        private static string Window(string body, int index, int length)
        {
            int count = Math.Min(length, body.Length - index);
            return body.Substring(index, count);
        }

        private static string FirstString(string json, string field)
        {
            return JsonText.ReadString(json, field);
        }

        private static string LastString(string json, string field)
        {
            MatchCollection matches = Regex.Matches(json,
                @"""" + field + @""":\s*""((?:[^""\\]|\\.)*)""");
            return matches.Count > 0
                ? matches[matches.Count - 1].Groups[1].Value
                : "";
        }

        private static long LastNumber(string json, string field)
        {
            MatchCollection matches = Regex.Matches(json, @"""" + field + @""":\s*(-?\d+)");
            return matches.Count > 0 ? ParseLong(matches[matches.Count - 1].Groups[1].Value) : 0;
        }

        private static long ReadNumber(string json, string field)
        {
            Match m = Regex.Match(json, @"""" + field + @""":\s*(-?\d+)");
            return m.Success ? ParseLong(m.Groups[1].Value) : 0;
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

        private static string FormatTime(int seconds)
        {
            if (seconds >= 3600)
            {
                return (seconds / 3600) + ":" + ((seconds / 60) % 60).ToString("D2")
                    + ":" + (seconds % 60).ToString("D2");
            }
            return (seconds / 60) + ":" + (seconds % 60).ToString("D2");
        }
    }
}
