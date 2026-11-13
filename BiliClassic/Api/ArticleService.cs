using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class ArticleInfo
    {
        public long Id;
        public string Title = "";
        public string Summary = "";
        public string Banner = "";
        public long Ctime;
        public string AuthorMid = "";
        public string AuthorName = "";
        public string AuthorFace = "";
        public int View;
        public int Favorite;
        public int Like;
        public int Reply;
        public int Coin;
        public int WordCount;
        public string ContentHtml = "";
    }

    public static class ArticleService
    {
        private const string ViewUrl = "https://api.bilibili.com/x/article/view";

        public static void Fetch(string id, Action<ArticleInfo, string> onDone)
        {
            long articleId;
            if (!long.TryParse(id, out articleId) || articleId <= 0)
            {
                onDone(null, "缺少专栏 ID");
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
                parameters["gaia_source"] = "main_web";
                parameters["web_location"] = "333.976";

                string url;
                try
                {
                    url = WbiSigner.Shared.SignUrl(ViewUrl, parameters);
                }
                catch (Exception)
                {
                    url = ViewUrl + "?id=" + id + "&gaia_source=main_web&web_location=333.976";
                }

                Http.GetText(url, Http.Referer, delegate(HttpResult http)
                {
                    ArticleInfo info;
                    string error = Parse(http, articleId, out info);
                    onDone(info, error);
                });
            });
        }

        private static string Parse(HttpResult http, long id, out ArticleInfo info)
        {
            info = null;
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
            if (!data.IsObject)
            {
                return "专栏不存在或已被删除";
            }

            ArticleInfo article = new ArticleInfo();
            article.Id = id;
            article.Title = data["title"].AsString();
            article.Summary = data["summary"].AsString();
            article.Banner = Normalize(data["banner_url"].AsString());
            article.Ctime = data["ctime"].AsLong();

            JsonValue author = data["author"];
            article.AuthorMid = author["mid"].AsString();
            article.AuthorName = author["name"].AsString();
            article.AuthorFace = Normalize(author["face"].AsString());

            JsonValue stats = data["stats"];
            article.View = stats["view"].AsInt();
            article.Favorite = stats["favorite"].AsInt();
            article.Like = stats["like"].AsInt();
            article.Reply = stats["reply"].AsInt();
            article.Coin = stats["coin"].AsInt();

            article.WordCount = data["words"].AsInt();
            article.ContentHtml = data["content"].AsString();

            info = article;
            return "";
        }

        public static string HtmlToText(string html)
        {
            if (string.IsNullOrEmpty(html))
            {
                return "";
            }

            string text = html;
            text = Regex.Replace(text, @"<\s*br\s*/?\s*>", "\n", RegexOptions.IgnoreCase);
            text = Regex.Replace(text, @"<\s*/\s*p\s*>", "\n\n", RegexOptions.IgnoreCase);
            text = Regex.Replace(text, @"<\s*p[^>]*>", "", RegexOptions.IgnoreCase);
            text = Regex.Replace(text, @"<\s*/\s*(div|figure|blockquote|li)\s*>", "\n", RegexOptions.IgnoreCase);
            text = Regex.Replace(text, @"<[^>]+>", "");
            text = DecodeEntities(text);
            text = Regex.Replace(text, @"[ \t]+\n", "\n");
            text = Regex.Replace(text, @"\n{3,}", "\n\n");
            return text.Trim();
        }

        private static string DecodeEntities(string text)
        {
            string s = text;
            s = Regex.Replace(s, @"&#x([0-9a-fA-F]+);", delegate(Match m)
            {
                int value;
                if (int.TryParse(m.Groups[1].Value,
                    System.Globalization.NumberStyles.HexNumber,
                    System.Globalization.CultureInfo.InvariantCulture, out value))
                {
                    return FromCodePoint(value);
                }
                return m.Value;
            });
            s = Regex.Replace(s, @"&#(\d+);", delegate(Match m)
            {
                int value;
                if (int.TryParse(m.Groups[1].Value, out value))
                {
                    return FromCodePoint(value);
                }
                return m.Value;
            });

            s = s.Replace("&nbsp;", " ");
            s = s.Replace("&lt;", "<");
            s = s.Replace("&gt;", ">");
            s = s.Replace("&quot;", "\"");
            s = s.Replace("&#39;", "'");
            s = s.Replace("&apos;", "'");
            s = s.Replace("&mdash;", "—");
            s = s.Replace("&hellip;", "…");
            s = s.Replace("&amp;", "&");
            return s;
        }

        private static string FromCodePoint(int value)
        {
            if (value <= 0 || value > 0x10FFFF)
            {
                return "";
            }
            if (value <= 0xFFFF)
            {
                return ((char)value).ToString();
            }

            int offset = value - 0x10000;
            char high = (char)(0xD800 + (offset >> 10));
            char low = (char)(0xDC00 + (offset & 0x3FF));
            return new string(new char[] { high, low });
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
