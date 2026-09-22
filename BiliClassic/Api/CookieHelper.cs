using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public static class CookieHelper
    {
        public static string ParseAndBuildCookie(string content)
        {
            if (string.IsNullOrEmpty(content))
            {
                return null;
            }

            string sessdata = ExtractValue(content, "SESSDATA");
            if (string.IsNullOrEmpty(sessdata))
            {
                return null;
            }

            List<string> parts = new List<string>();
            AddPair(parts, "SESSDATA", sessdata);
            AddPair(parts, "bili_jct", ExtractValue(content, "bili_jct"));
            AddPair(parts, "DedeUserID", ExtractValue(content, "DedeUserID"));
            AddPair(parts, "buvid3", ExtractValue(content, "buvid3"));
            AddPair(parts, "buvid4", ExtractValue(content, "buvid4"));
            AddPair(parts, "sid", ExtractValue(content, "sid"));
            return string.Join("; ", parts.ToArray());
        }

        public static bool LooksLikeCookieString(string content)
        {
            if (string.IsNullOrEmpty(content))
            {
                return false;
            }
            if (content.IndexOf('=') < 0)
            {
                return false;
            }
            return content.IndexOf(';') >= 0
                || content.IndexOf("SESSDATA=", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        private static void AddPair(List<string> parts, string name, string value)
        {
            if (!string.IsNullOrEmpty(value))
            {
                parts.Add(name + "=" + value);
            }
        }

        private static string ExtractValue(string content, string key)
        {
            string[] patterns = new string[]
            {
                key + "=([^;\\s\\n\\r\"]+?)(?:;|\\s|$)",
                key + "=\"([^\"]+?)\"",
                "\"" + key + "\"\\s*:\\s*\"([^\"]+?)\"",
                key + "=([^;\\s\\n\\r\"]+?)(?:;|\\s|$)",
                key + "=([^;\\s\\u4e00-\\u9fa5]+)"
            };

            for (int i = 0; i < patterns.Length; i++)
            {
                try
                {
                    Match match = Regex.Match(content, patterns[i], RegexOptions.IgnoreCase);
                    if (!match.Success)
                    {
                        continue;
                    }

                    string value = (match.Groups[1].Value ?? "").Trim();
                    value = Regex.Replace(value, "[;\\s]+$", "");
                    if (value.Length > 0)
                    {
                        return value;
                    }
                }
                catch (Exception)
                {
                }
            }
            return null;
        }
    }
}
