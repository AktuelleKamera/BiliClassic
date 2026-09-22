using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    // 【重要】WBI 签名过不了整个播放接口都拿不到地址，先 EnsureReady 等密钥，喵
    public sealed class WbiSigner
    {
        private const string NavUrl = "https://api.bilibili.com/x/web-interface/nav";

        private static readonly int[] MixinKeyEncTab = new int[64]
        {
            46, 47, 18,  2, 53,  8, 23, 32, 15, 50, 10, 31, 58,  3, 45, 35,
            27, 43,  5, 49, 33,  9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48,  7, 16, 24, 55, 40, 61, 26, 17,  0,  1, 60, 51, 30,  4,
            22, 25, 54, 21, 56, 59,  6, 63, 57, 62, 11, 36, 20, 34, 44, 52
        };

        private string _mixinKey = "";

        private static readonly WbiSigner SharedInstance = new WbiSigner();

        public static WbiSigner Shared
        {
            get { return SharedInstance; }
        }

        public static void EnsureReady(Action<string> onDone)
        {
            if (SharedInstance.IsReady)
            {
                if (onDone != null)
                {
                    onDone(null);
                }
                return;
            }

            SharedInstance.FetchKeys(delegate(string error)
            {
                if (onDone != null)
                {
                    onDone(error);
                }
            });
        }

        public bool IsReady
        {
            get { return _mixinKey != null && _mixinKey.Length == 32; }
        }

        public string MixinKey
        {
            get { return _mixinKey; }
        }

        public void FetchKeys(Action<string> onDone)
        {
            Http.GetText(NavUrl, Http.Referer, delegate(HttpResult http)
            {
                string error = null;
                try
                {
                    if (string.IsNullOrEmpty(http.Body))
                    {
                        error = string.IsNullOrEmpty(http.Error)
                            ? "nav 返回空响应"
                            : "nav 请求失败: " + http.Error;
                    }
                    else
                    {
                        string imgUrl = ExtractJsonString(http.Body, "img_url");
                        string subUrl = ExtractJsonString(http.Body, "sub_url");
                        if (string.IsNullOrEmpty(imgUrl) || string.IsNullOrEmpty(subUrl))
                        {
                            error = "nav 响应里没有 wbi_img，返回开头: " + Head(http.Body, 160);
                        }
                        else
                        {
                            string imgKey = KeyFromUrl(imgUrl);
                            string subKey = KeyFromUrl(subUrl);
                            if (imgKey.Length == 0 || subKey.Length == 0)
                            {
                                error = "wbi_img 地址解析不出 key: " + Head(imgUrl + " | " + subUrl, 140);
                            }
                            else
                            {
                                _mixinKey = ComputeMixinKey(imgKey, subKey);
                                if (!IsReady)
                                {
                                    error = "mixinKey 长度异常: "
                                        + (_mixinKey == null ? "null" : _mixinKey.Length.ToString());
                                }
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    error = "解析 nav 出错: " + ex.Message;
                }

                if (onDone != null)
                {
                    onDone(error);
                }
            });
        }

        private static string Head(string s, int max)
        {
            if (string.IsNullOrEmpty(s))
            {
                return "";
            }
            s = s.Replace("\r", " ").Replace("\n", " ");
            return s.Length <= max ? s : s.Substring(0, max) + "…";
        }

        public string SignUrl(string baseUrl, Dictionary<string, string> parameters)
        {
            string query = SignQuery(parameters);
            if (string.IsNullOrEmpty(query))
            {
                return baseUrl;
            }
            if (baseUrl.IndexOf('?') >= 0)
            {
                return baseUrl + "&" + query;
            }
            return baseUrl + "?" + query;
        }

        public string SignQuery(Dictionary<string, string> parameters)
        {
            if (!IsReady)
            {
                return "";
            }

            long wts = CurrentUnixSeconds();

            Dictionary<string, string> values = new Dictionary<string, string>();
            List<string> keys = new List<string>();
            foreach (KeyValuePair<string, string> pair in parameters)
            {
                values[pair.Key] = pair.Value;
                if (!keys.Contains(pair.Key))
                {
                    keys.Add(pair.Key);
                }
            }

            values["wts"] = wts.ToString();
            if (!keys.Contains("wts"))
            {
                keys.Add("wts");
            }

            string[] sorted = keys.ToArray();
            Array.Sort(sorted, StringComparer.Ordinal);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sorted.Length; i++)
            {
                string k = sorted[i];
                string v = values[k] ?? "";
                v = v.Replace("!", "").Replace("'", "").Replace("(", "").Replace(")", "").Replace("*", "");
                if (sb.Length > 0)
                {
                    sb.Append('&');
                }
                sb.Append(Encode(k)).Append('=').Append(Encode(v));
            }

            string query = sb.ToString();
            string wRid = Md5Util.Hex(query + _mixinKey);
            return query + "&w_rid=" + wRid;
        }

        private static long CurrentUnixSeconds()
        {
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return (long)DateTime.UtcNow.Subtract(epoch).TotalSeconds;
        }

        private static string ComputeMixinKey(string imgKey, string subKey)
        {
            string raw = imgKey + subKey;
            if (raw.Length < 64)
            {
                return "";
            }
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 32; i++)
            {
                sb.Append(raw[MixinKeyEncTab[i]]);
            }
            return sb.ToString();
        }

        private static string KeyFromUrl(string url)
        {
            int idx = url.LastIndexOf('/');
            string fileName = url.Substring(idx + 1);
            idx = fileName.LastIndexOf('.');
            if (idx >= 0)
            {
                fileName = fileName.Substring(0, idx);
            }
            return fileName;
        }

        private static string ExtractJsonString(string json, string field)
        {
            Match m = Regex.Match(json, @"""" + field + @""":\s*""([^""]*)""");
            return m.Success ? m.Groups[1].Value : "";
        }

        private static string Encode(string value)
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
                    sb.Append('%');
                    sb.Append(bytes[i].ToString("X2"));
                }
            }
            return sb.ToString();
        }
    }
}
