using System;
using System.Collections.Generic;
using System.Text;

namespace BiliClassic.Api
{
    public static class DynamicService
    {
        private const string FeedUrl =
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all";

        private const string DetailUrl =
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail";

        private const string Features =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,"
            + "decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete";

        public static void FetchFeed(string offset,
            Action<List<DynamicItem>, string, string> onDone)
        {
            WbiSigner.EnsureReady(delegate(string keyError)
            {
                if (!string.IsNullOrEmpty(keyError))
                {
                    onDone(new List<DynamicItem>(), "", keyError);
                    return;
                }

                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters["type"] = "all";
                parameters["features"] = Features;
                if (!string.IsNullOrEmpty(offset))
                {
                    parameters["offset"] = offset;
                }
                DmImgParam.AddTo(parameters);

                string url;
                try
                {
                    url = WbiSigner.Shared.SignUrl(FeedUrl, parameters);
                }
                catch (Exception)
                {
                    url = FeedUrl + "?type=all&features=" + Features;
                    if (!string.IsNullOrEmpty(offset))
                    {
                        url += "&offset=" + Uri.EscapeDataString(offset);
                    }
                }

                Http.GetText(url, Http.Referer, delegate(HttpResult http)
                {
                    List<DynamicItem> items = new List<DynamicItem>();
                    string nextOffset = "";
                    string error = Parse(http, items, out nextOffset);
                    onDone(items, nextOffset, error);
                });
            });
        }

        public static void FetchDetail(string id, Action<DynamicItem, string> onDone)
        {
            long dynamicId;
            if (!long.TryParse(id, out dynamicId) || dynamicId <= 0)
            {
                onDone(null, "缺少动态 ID");
                return;
            }

            WbiSigner.EnsureReady(delegate(string keyError)
            {
                if (!string.IsNullOrEmpty(keyError))
                {
                    onDone(null, keyError);
                    return;
                }

                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters["id"] = id;
                parameters["features"] = Features;
                DmImgParam.AddTo(parameters);

                string url;
                try
                {
                    url = WbiSigner.Shared.SignUrl(DetailUrl, parameters);
                }
                catch (Exception)
                {
                    url = DetailUrl + "?id=" + id + "&features=" + Features;
                }

                Http.GetText(url, Http.Referer, delegate(HttpResult http)
                {
                    DynamicItem item;
                    string error = ParseDetail(http, out item);
                    onDone(item, error);
                });
            });
        }

        private static string ParseDetail(HttpResult http, out DynamicItem item)
        {
            item = null;
            if (http == null || string.IsNullOrEmpty(http.Body))
            {
                return string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error;
            }

            JsonValue root = JsonValue.Parse(http.Body);
            int code = root["code"].AsInt();
            if (code != 0)
            {
                string message = root["message"].AsString();
                return "接口返回 code=" + code + (message.Length > 0 ? " " + message : "");
            }

            JsonValue itemJson = root["data"]["item"];
            if (!itemJson.IsObject || itemJson.Count == 0)
            {
                return "动态不存在或已被删除";
            }

            DynamicItem dynamicItem = ParseItem(itemJson);
            if (dynamicItem == null || dynamicItem.Type == "DYNAMIC_TYPE_NONE")
            {
                return "动态不存在或已被删除";
            }

            item = dynamicItem;
            return "";
        }

        private static string Parse(HttpResult http, List<DynamicItem> items, out string nextOffset)
        {
            nextOffset = "";
            if (http == null || string.IsNullOrEmpty(http.Body))
            {
                return string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error;
            }

            JsonValue root = JsonValue.Parse(http.Body);
            int code = root["code"].AsInt();
            if (code != 0)
            {
                string message = root["message"].AsString();
                return "接口返回 code=" + code + (message.Length > 0 ? " " + message : "");
            }

            JsonValue data = root["data"];
            if (data["has_more"].AsBool())
            {
                nextOffset = data["offset"].AsString();
            }

            JsonValue list = data["items"];
            for (int i = 0; i < list.Count; i++)
            {
                try
                {
                    DynamicItem item = ParseItem(list[i]);
                    if (item != null && item.DynamicId.Length > 0
                        && item.Type != "DYNAMIC_TYPE_NONE")
                    {
                        items.Add(item);
                    }
                }
                catch (Exception)
                {
                }
            }
            return "";
        }

        private static DynamicItem ParseItem(JsonValue item)
        {
            if (item == null || !item.IsObject)
            {
                return null;
            }

            DynamicItem d = new DynamicItem();
            d.DynamicId = item["id_str"].AsString();
            d.Type = item["type"].AsString();

            JsonValue basic = item["basic"];
            d.CommentId = basic["comment_id_str"].AsString();
            int commentType = basic["comment_type"].AsInt();
            d.CommentType = commentType > 0 ? commentType : 17;
            if (d.CommentId.Length == 0)
            {
                d.CommentId = d.DynamicId;
            }

            JsonValue modules = item["modules"];
            JsonValue author = modules["module_author"];
            d.Mid = author["mid"].AsString();
            d.Author = author["name"].AsString();
            d.AvatarUrl = Normalize(author["face"].AsString());
            d.PubTime = author["pub_time"].AsString();

            if (d.Type == "DYNAMIC_TYPE_NONE")
            {
                d.Content = "[动态不存在]";
                return d;
            }

            JsonValue mdyn = modules["module_dynamic"];
            if (mdyn.IsObject)
            {
                d.Content = ParseRichText(mdyn["desc"]);
                JsonValue major = mdyn["major"];
                if (major.IsObject && major.Count > 0)
                {
                    ParseMajor(d, major);
                }
            }

            JsonValue stat = modules["module_stat"];
            if (stat.IsObject)
            {
                JsonValue like = stat["like"];
                d.LikeCount = like["count"].AsInt();
                d.Liked = like["status"].AsBool();
            }

            JsonValue orig = item["orig"];
            if (orig.IsObject && orig.Count > 0)
            {
                d.Forward = ParseItem(orig);
            }

            return d;
        }

        private static void ParseMajor(DynamicItem d, JsonValue major)
        {
            string type = major["type"].AsString();

            if (type == "MAJOR_TYPE_ARCHIVE")
            {
                ApplyCard(d, major["archive"], "投稿视频");
            }
            else if (type == "MAJOR_TYPE_UGC_SEASON")
            {
                ApplyCard(d, major["ugc_season"], "视频合集");
            }
            else if (type == "MAJOR_TYPE_PGC")
            {
                JsonValue pgc = major["pgc"];
                d.CardTitle = pgc["title"].AsString();
                d.CardCoverUrl = Normalize(pgc["cover"].AsString());
                d.CardEpid = pgc["epid"].AsString();
                d.CardLabel = "番剧";
                d.CardInfo = JoinInfo("番剧", pgc["stat"]["play"].AsString());
            }
            else if (type == "MAJOR_TYPE_ARTICLE")
            {
                JsonValue article = major["article"];
                JsonValue covers = article["covers"];
                d.CardTitle = article["title"].AsString();
                d.CardCoverUrl = covers.Count > 0 ? Normalize(covers[0].AsString()) : "";
                d.CardArticleId = article["id"].AsString();
                d.CardLabel = "专栏文章";
                d.CardInfo = "专栏文章";
            }
            else if (type == "MAJOR_TYPE_DRAW")
            {
                AddPics(d, major["draw"]["items"], "src");
            }
            else if (type == "MAJOR_TYPE_OPUS")
            {
                JsonValue opus = major["opus"];
                AddPics(d, opus["pics"], "url");
                string summary = ParseRichText(opus["summary"]);
                if (summary.Length > 0)
                {
                    d.Content = d.Content.Length > 0 ? d.Content + "\n" + summary : summary;
                }
            }
            else if (type == "MAJOR_TYPE_LIVE_RCMD")
            {
                JsonValue liveRcmd = major["live_rcmd"];
                JsonValue info = JsonValue.Parse(liveRcmd["content"].AsString())["live_play_info"];
                ApplyLive(d, info);
            }
            else if (type == "MAJOR_TYPE_LIVE")
            {
                ApplyLive(d, major["live"]);
            }
            else if (type.Length > 0)
            {
                d.Content = d.Content + "\n[暂时无法显示此动态的附加内容]";
            }
        }

        private static void ApplyCard(DynamicItem d, JsonValue value, string label)
        {
            d.CardTitle = value["title"].AsString();
            d.CardCoverUrl = Normalize(value["cover"].AsString());
            d.CardBvid = value["bvid"].AsString();
            d.CardAid = value["aid"].AsString();
            d.CardLabel = label;
            d.CardInfo = JoinInfo(label, value["stat"]["play"].AsString());
        }

        private static void ApplyLive(DynamicItem d, JsonValue info)
        {
            if (info == null || info.IsNull)
            {
                return;
            }

            d.CardRoomId = info["room_id"].AsString();
            if (d.CardRoomId.Length == 0)
            {
                d.CardRoomId = info["id"].AsString();
            }
            d.CardTitle = info["title"].AsString();
            d.CardCoverUrl = Normalize(info["cover"].AsString());
            d.CardLabel = "直播间";
            d.CardInfo = "直播间";
        }

        private static void AddPics(DynamicItem d, JsonValue array, string field)
        {
            int total = array.Count;
            int show = total > 3 ? 3 : total;
            for (int i = 0; i < show; i++)
            {
                string url = Normalize(array[i][field].AsString());
                if (url.Length == 0)
                {
                    continue;
                }

                PictureItem picture = new PictureItem();
                picture.Url = url;
                d.Pics.Add(picture);
                picture.Group = d.Pics;
            }
        }

        private static string ParseRichText(JsonValue text)
        {
            if (text == null || text.IsNull)
            {
                return "";
            }

            JsonValue nodes = text["rich_text_nodes"];
            if (nodes.IsArray && nodes.Count > 0)
            {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < nodes.Count; i++)
                {
                    sb.Append(nodes[i]["text"].AsString());
                }
                if (sb.Length > 0)
                {
                    return sb.ToString();
                }
            }
            return text["text"].AsString();
        }

        private static string JoinInfo(string label, string play)
        {
            if (string.IsNullOrEmpty(play) || play == "0")
            {
                return label;
            }
            return label + "  " + VideoItem.FormatCount(play) + " 播放";
        }

        private static string Normalize(string url)
        {
            if (!string.IsNullOrEmpty(url) && url.StartsWith("//", StringComparison.Ordinal))
            {
                return "https:" + url;
            }
            return url ?? "";
        }
    }
}
