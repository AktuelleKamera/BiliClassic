using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>登录态</summary>
    public static class BiliSession
    {
        private const string StoreFile = "biliclassic_cookies.txt";
        private const string CookieDomain = ".bilibili.com";
        private const string GameCookieDomain = ".biligame.com";

        private static readonly CookieContainer Container = new CookieContainer();

        /// <summary>加cookie用的目标Uri</summary>
        private static readonly Uri CookieTarget = new Uri("https://www.bilibili.com");

        /// <summary>跨域那几步落在biligame域上</summary>
        private static readonly Uri GameTarget = new Uri("https://passport.biligame.com");

        /// <summary>
        /// 回读cookie时逐个问这些主机的容器
        /// Set-Cookie是发给请求那个主机的，只问www那一个会漏掉
        /// </summary>
        private static readonly Uri[] CookieTargets = new Uri[]
        {
            new Uri("https://www.bilibili.com"),
            new Uri("https://api.bilibili.com"),
            new Uri("https://passport.bilibili.com"),
            new Uri("https://passport.biligame.com"),
            new Uri("https://www.biligame.com")
        };

        private static string _cookieString = "";

        /// <summary>当前用户名，验证nav后填</summary>
        public static string UserName = "";

        /// <summary>当前用户mid</summary>
        public static string Mid = "";

        /// <summary>csrf token，写操作要用</summary>
        public static string Csrf = "";

        /// <summary>原始cookie串</summary>
        public static string CookieString
        {
            get { return _cookieString; }
        }

        /// <summary>登录态是否完整，SESSDATA和bili_jct都要有</summary>
        public static bool IsLoggedIn
        {
            get
            {
                return _cookieString.IndexOf("SESSDATA=", StringComparison.OrdinalIgnoreCase) >= 0
                    && _cookieString.IndexOf("bili_jct=", StringComparison.OrdinalIgnoreCase) >= 0;
            }
        }

        /// <summary>登录态里有没有这个cookie名</summary>
        public static bool HasCookie(string name)
        {
            if (string.IsNullOrEmpty(name))
            {
                return false;
            }
            return _cookieString.IndexOf(name + "=", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        public static CookieContainer GetContainer()
        {
            return Container;
        }

        /// <summary>整体替换登录态</summary>
        public static void SetCookieString(string cookieString)
        {
            _cookieString = cookieString ?? "";
            RebuildContainer();
            SyncLoginState();
        }

        /// <summary>
        /// 合并cookie，同名以新值为准
        /// 扫码返回一整套，验证码可能只有一部分
        /// </summary>
        public static void MergeCookieString(string cookieString)
        {
            if (string.IsNullOrEmpty(cookieString))
            {
                return;
            }
            Dictionary<string, string> merged = ParseCookies(_cookieString);
            Dictionary<string, string> incoming = ParseCookies(cookieString);
            foreach (KeyValuePair<string, string> pair in incoming)
            {
                merged[pair.Key] = pair.Value;
            }

            List<string> parts = new List<string>();
            foreach (KeyValuePair<string, string> pair in merged)
            {
                parts.Add(pair.Key + "=" + pair.Value);
            }
            _cookieString = string.Join("; ", parts.ToArray());
            RebuildContainer();
            SyncLoginState();
        }

        public static void Clear()
        {
            _cookieString = "";
            UserName = "";
            Mid = "";
            Csrf = "";
            RebuildContainer();
            Save();
        }

        /// <summary>从IsolatedStorage读回登录态</summary>
        public static void Load()
        {
            try
            {
                using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    if (!store.FileExists(StoreFile))
                    {
                        return;
                    }
                    using (IsolatedStorageFileStream stream = store.OpenFile(StoreFile, FileMode.Open, FileAccess.Read))
                    {
                        using (StreamReader reader = new StreamReader(stream))
                        {
                            SetCookieString(reader.ReadToEnd().Trim());
                        }
                    }
                }
            }
            catch (Exception)
            {
            }
        }

        public static void Save()
        {
            try
            {
                using (IsolatedStorageFile store = IsolatedStorageFile.GetUserStoreForApplication())
                {
                    using (IsolatedStorageFileStream stream = store.OpenFile(StoreFile, FileMode.Create, FileAccess.Write))
                    {
                        using (StreamWriter writer = new StreamWriter(stream))
                        {
                            writer.Write(_cookieString);
                        }
                    }
                }
            }
            catch (Exception)
            {
            }
        }

        /// <summary>
        /// 请求nav验证登录态，code为0即成功
        /// 成功时填UserName和Mid
        /// </summary>
        public static void Verify(Action<bool> onDone)
        {
            Http.GetText("https://api.bilibili.com/x/web-interface/nav", Http.Referer, delegate(HttpResult http)
            {
                bool ok = false;
                try
                {
                    if (http != null && !string.IsNullOrEmpty(http.Body))
                    {
                        Match code = Regex.Match(http.Body, @"""code"":\s*(-?\d+)");
                        if (code.Success && code.Groups[1].Value == "0")
                        {
                            ok = true;
                            Match name = Regex.Match(http.Body, @"""uname"":\s*""([^""]*)""");
                            if (name.Success)
                            {
                                UserName = name.Groups[1].Value;
                            }
                            // nav里的mid是权威值，扫码跨域地址不一定带DedeUserID
                            Match mid = Regex.Match(http.Body, @"""mid"":\s*(\d+)");
                            if (mid.Success)
                            {
                                Mid = mid.Groups[1].Value;
                            }
                        }
                    }
                }
                catch (Exception)
                {
                    ok = false;
                }

                if (ok)
                {
                    Save();
                }
                if (onDone != null)
                {
                    onDone(ok);
                }
            });
        }

        /// <summary>从cookie串同步mid和csrf</summary>
        private static void SyncLoginState()
        {
            Dictionary<string, string> map = ParseCookies(_cookieString);
            string value;
            if (map.TryGetValue("bili_jct", out value))
            {
                Csrf = value;
            }
            if (map.TryGetValue("DedeUserID", out value))
            {
                Mid = value;
            }
        }

        /// <summary>
        /// 解析k=v; k=v形式的cookie串
        /// 值保持原样不解码，解码后可能出现逗号，Cookie类会抛异常
        /// 也正好和浏览器发出的Cookie头一致
        /// </summary>
        private static Dictionary<string, string> ParseCookies(string cookieString)
        {
            Dictionary<string, string> map = new Dictionary<string, string>();
            if (string.IsNullOrEmpty(cookieString))
            {
                return map;
            }

            string[] parts = cookieString.Split(';');
            for (int i = 0; i < parts.Length; i++)
            {
                string part = parts[i].Trim();
                if (part.Length == 0)
                {
                    continue;
                }
                int eq = part.IndexOf('=');
                if (eq <= 0)
                {
                    continue;
                }
                string key = part.Substring(0, eq).Trim();
                string value = part.Substring(eq + 1).Trim();
                if (key.Length > 0 && value.Length > 0)
                {
                    map[key] = value;
                }
            }
            return map;
        }

        private static void RebuildContainer()
        {
            Dictionary<string, string> target = new Dictionary<string, string>();

            List<KeyValuePair<string, string>> synthetic = CookieGenerator.GetSynthetic();
            for (int i = 0; i < synthetic.Count; i++)
            {
                target[synthetic[i].Key] = synthetic[i].Value;
            }

            Dictionary<string, string> logged = ParseCookies(_cookieString);
            foreach (KeyValuePair<string, string> pair in logged)
            {
                target[pair.Key] = pair.Value;
            }

            List<string> existing = new List<string>();
            foreach (Cookie cookie in Container.GetCookies(CookieTarget))
            {
                existing.Add(cookie.Name);
            }

            for (int i = 0; i < existing.Count; i++)
            {
                if (target.ContainsKey(existing[i]))
                {
                    continue;
                }
                try
                {
                    Container.Add(CookieTarget, new Cookie(existing[i], "", "/", CookieDomain)
                    {
                        Expires = DateTime.Now.AddDays(-1)
                    });
                }
                catch (Exception)
                {
                }
            }

            foreach (KeyValuePair<string, string> pair in target)
            {
                try
                {
                    Container.Add(CookieTarget, new Cookie(pair.Key, pair.Value, "/", CookieDomain));
                }
                catch (Exception)
                {
                }

                // passport.biligame.com是.bilibili.com之外的域
                // 不按biligame再灌一份，sso/set那步就带不上登录态
                try
                {
                    Container.Add(GameTarget, new Cookie(pair.Key, pair.Value, "/", GameCookieDomain));
                }
                catch (Exception)
                {
                }
            }
        }

        public static void Refresh()
        {
            RebuildContainer();
        }

        /// <summary>
        /// 从Set-Cookie原文里抠出name=value
        /// 不依赖CookieContainer：容器收不收得下是服务端行为和框架共同决定的
        /// 每个头一行，先取第一段，再扫属性段
        /// </summary>
        public static void MergeSetCookie(string raw)
        {
            // 框架挡住Set-Cookie时Http会返回这个标记
            if (string.IsNullOrEmpty(raw) || raw == "!BLOCKED")
            {
                return;
            }

            List<string> parts = new List<string>();
            string[] lines = raw.Split('\n');
            for (int i = 0; i < lines.Length; i++)
            {
                string line = lines[i].Trim();
                if (line.Length == 0)
                {
                    continue;
                }

                // 一个Set-Cookie头里，第一段就是cookie，后面全是属性
                int semi = line.IndexOf(';');
                string first = semi >= 0 ? line.Substring(0, semi) : line;
                AddPair(parts, first);

                // 万一框架把多个头合并成一行，属性段里还藏着别的cookie
                if (semi >= 0)
                {
                    string[] rest = line.Substring(semi + 1).Split(new char[] { ',', ';' });
                    for (int j = 0; j < rest.Length; j++)
                    {
                        AddPair(parts, rest[j]);
                    }
                }
            }

            if (parts.Count > 0)
            {
                MergeCookieString(string.Join("; ", parts.ToArray()));
            }
        }

        /// <summary>把一段name=value收进来，属性和非登录名跳过</summary>
        private static void AddPair(List<string> parts, string piece)
        {
            piece = piece.Trim();
            int eq = piece.IndexOf('=');
            if (eq <= 0)
            {
                return;
            }

            string name = piece.Substring(0, eq).Trim();
            string value = piece.Substring(eq + 1).Trim();
            if (value.Length == 0 || IsCookieAttribute(name) || name.IndexOf(' ') >= 0)
            {
                return;
            }
            parts.Add(name + "=" + value);
        }

        /// <summary>
        /// 从响应正文里捞cookie
        /// 跨域那几步的凭证可能是JSON、HTML或JS里的document.cookie
        /// 只认登录相关的几个名字，避免误抓
        /// </summary>
        public static bool MergeCookiesInText(string text)
        {
            if (string.IsNullOrEmpty(text))
            {
                return false;
            }

            List<string> parts = new List<string>();
            MatchCollection matches = Regex.Matches(text,
                @"\b(SESSDATA|bili_jct|DedeUserID__ckMd5|DedeUserID|sid)=([^""'&;<>\s\\]{6,})");
            foreach (Match m in matches)
            {
                parts.Add(m.Groups[1].Value + "=" + m.Groups[2].Value);
            }

            // 另一种形态：{"name":"SESSDATA","value":"..."}
            MatchCollection pairs = Regex.Matches(text,
                @"""name""\s*:\s*""([^""]+)""\s*,\s*""value""\s*:\s*""([^""]*)""");
            foreach (Match m in pairs)
            {
                if (IsLoginCookie(m.Groups[1].Value) && m.Groups[2].Value.Length > 0)
                {
                    parts.Add(m.Groups[1].Value + "=" + m.Groups[2].Value);
                }
            }

            // 还有 ["SESSDATA","..."] 这种
            MatchCollection arrays = Regex.Matches(text, @"\[""([^""]+)"",\s*""([^""]*)""\]");
            foreach (Match m in arrays)
            {
                if (IsLoginCookie(m.Groups[1].Value) && m.Groups[2].Value.Length > 0)
                {
                    parts.Add(m.Groups[1].Value + "=" + m.Groups[2].Value);
                }
            }

            if (parts.Count == 0)
            {
                return false;
            }

            MergeCookieString(string.Join("; ", parts.ToArray()));
            return true;
        }

        /// <summary>只认登录相关的几个cookie名</summary>
        private static bool IsLoginCookie(string name)
        {
            return name == "SESSDATA" || name == "bili_jct" || name == "DedeUserID"
                || name == "DedeUserID__ckMd5" || name == "sid";
        }

        /// <summary>Set-Cookie的属性名，不是cookie</summary>
        private static bool IsCookieAttribute(string key)
        {
            string lower = key.ToLower();
            return lower == "path" || lower == "domain" || lower == "expires"
                || lower == "max-age" || lower == "secure" || lower == "httponly"
                || lower == "samesite" || lower == "version" || lower == "comment";
        }

        /// <summary>从容器回读cookie</summary>
        public static void HarvestFromContainer()
        {
            List<string> parts = new List<string>();
            // WP7的BCL里没有HashSet，用List去重
            List<string> seen = new List<string>();

            for (int i = 0; i < CookieTargets.Length; i++)
            {
                try
                {
                    foreach (Cookie cookie in Container.GetCookies(CookieTargets[i]))
                    {
                        if (string.IsNullOrEmpty(cookie.Name) || string.IsNullOrEmpty(cookie.Value))
                        {
                            continue;
                        }
                        // 同名cookie跨主机只会重复，取第一次见到的
                        if (seen.Contains(cookie.Name))
                        {
                            continue;
                        }
                        seen.Add(cookie.Name);
                        parts.Add(cookie.Name + "=" + cookie.Value);
                    }
                }
                catch (Exception)
                {
                }
            }

            if (parts.Count == 0)
            {
                return;
            }

            MergeCookieString(string.Join("; ", parts.ToArray()));
            Save();
        }

        /// <summary>
        /// 调试用：每个主机能看到的cookie名，不含值
        /// 用来判断Set-Cookie到底有没有进容器
        /// </summary>
        public static string DescribeCookies()
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < CookieTargets.Length; i++)
            {
                string names = "";
                try
                {
                    foreach (Cookie cookie in Container.GetCookies(CookieTargets[i]))
                    {
                        if (names.Length > 0)
                        {
                            names += ",";
                        }
                        names += cookie.Name;
                    }
                }
                catch (Exception)
                {
                }

                if (sb.Length > 0)
                {
                    sb.Append(" | ");
                }
                sb.Append(CookieTargets[i].Host).Append(": ");
                sb.Append(names.Length > 0 ? names : "无");
            }
            return sb.ToString();
        }
    }
}
