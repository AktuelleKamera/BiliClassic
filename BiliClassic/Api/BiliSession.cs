using System;
using System.Collections.Generic;
using System.IO;
using System.IO.IsolatedStorage;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public static class BiliSession
    {
        private const string StoreFile = "biliclassic_cookies.txt";
        private const string CookieDomain = ".bilibili.com";
        private const string GameCookieDomain = ".biligame.com";

        private static readonly CookieContainer Container = new CookieContainer();

        private static readonly Uri CookieTarget = new Uri("https://www.bilibili.com");

        private static readonly Uri GameTarget = new Uri("https://passport.biligame.com");

        private static readonly Uri[] CookieTargets = new Uri[]
        {
            new Uri("https://www.bilibili.com"),
            new Uri("https://api.bilibili.com"),
            new Uri("https://passport.bilibili.com"),
            new Uri("https://passport.biligame.com"),
            new Uri("https://www.biligame.com")
        };

        private static string _cookieString = "";

        public static string UserName = "";

        public static string Mid = "";

        public static string Csrf = "";

        public static string CookieString
        {
            get { return _cookieString; }
        }

        public static bool IsLoggedIn
        {
            get
            {
                return _cookieString.IndexOf("SESSDATA=", StringComparison.OrdinalIgnoreCase) >= 0
                    && _cookieString.IndexOf("bili_jct=", StringComparison.OrdinalIgnoreCase) >= 0;
            }
        }

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

        public static void SetCookieString(string cookieString)
        {
            _cookieString = cookieString ?? "";
            RebuildContainer();
            SyncLoginState();
        }

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

        public static void MergeSetCookie(string raw)
        {
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

                int semi = line.IndexOf(';');
                string first = semi >= 0 ? line.Substring(0, semi) : line;
                AddPair(parts, first);

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

            MatchCollection pairs = Regex.Matches(text,
                @"""name""\s*:\s*""([^""]+)""\s*,\s*""value""\s*:\s*""([^""]*)""");
            foreach (Match m in pairs)
            {
                if (IsLoginCookie(m.Groups[1].Value) && m.Groups[2].Value.Length > 0)
                {
                    parts.Add(m.Groups[1].Value + "=" + m.Groups[2].Value);
                }
            }

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

        private static bool IsLoginCookie(string name)
        {
            return name == "SESSDATA" || name == "bili_jct" || name == "DedeUserID"
                || name == "DedeUserID__ckMd5" || name == "sid";
        }

        private static bool IsCookieAttribute(string key)
        {
            string lower = key.ToLower();
            return lower == "path" || lower == "domain" || lower == "expires"
                || lower == "max-age" || lower == "secure" || lower == "httponly"
                || lower == "samesite" || lower == "version" || lower == "comment";
        }

        public static void HarvestFromContainer()
        {
            List<string> parts = new List<string>();
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
