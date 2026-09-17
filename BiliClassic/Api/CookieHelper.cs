using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>
    /// 解析粘贴进来的Cookie文本
    /// 输入可能是整条Cookie头、片段或JSON
    /// 多模式抠出关键字段，重建成标准cookie串
    /// 抠不到SESSDATA返回null
    /// </summary>
    public static class CookieHelper
    {
        /// <summary>重建cookie串，无SESSDATA返回null</summary>
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

        /// <summary>
        /// 输入是否已是标准cookie串
        /// 是就不要再重建：重建只留6个字段
        /// 会丢掉DedeUserID__ckMd5等风控字段
        /// </summary>
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

        /// <summary>
        /// 多种模式依次尝试：
        /// Cookie形式、带引号、JSON "key":"value"、宽松兜底
        /// </summary>
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
