using System;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>
    /// 无JSON库时的文本处理
    /// 与SearchService共用同一套清理规则
    /// </summary>
    public static class JsonText
    {
        /// <summary>JSON反转义（\uXXXX、\n、\r、\t、\"、\\）</summary>
        public static string Unescape(string s)
        {
            try
            {
                StringBuilder sb = new StringBuilder();
                int i = 0;
                while (i < s.Length)
                {
                    if (s[i] == '\\' && i + 1 < s.Length)
                    {
                        char next = s[i + 1];
                        if (next == 'u' && i + 5 < s.Length)
                        {
                            string hex = s.Substring(i + 2, 4);
                            try
                            {
                                int codePoint = int.Parse(hex, System.Globalization.NumberStyles.HexNumber);
                                sb.Append((char)codePoint);
                            }
                            catch (Exception)
                            {
                            }
                            i += 6;
                            continue;
                        }
                        if (next == 'n') { sb.Append('\n'); i += 2; continue; }
                        if (next == 'r') { sb.Append('\r'); i += 2; continue; }
                        if (next == 't') { sb.Append('\t'); i += 2; continue; }
                        if (next == '"') { sb.Append('"'); i += 2; continue; }
                        if (next == '\\') { sb.Append('\\'); i += 2; continue; }
                    }

                    sb.Append(s[i]);
                    i++;
                }
                return sb.ToString();
            }
            catch (Exception)
            {
                return s;
            }
        }

        /// <summary>
        /// 去掉标题里的HTML标签并反转义
        /// @"\u0026"是字面量反斜杠+u0026，不是Unicode转义
        /// 用来还原被JSON二次转义的&amp;
        /// </summary>
        public static string StripHtml(string s)
        {
            if (s == null)
            {
                return "";
            }

            try
            {
                s = Regex.Replace(s, @"<[^>]*>", "");
            }
            catch (Exception)
            {
            }

            s = s.Replace(@"\u0026", "&");
            s = s.Replace(@"\/", "/");
            s = s.Replace("\\\\\"", "\"");
            return s;
        }

        /// <summary>取"field":"value"字符串，已反转义，取不到返回空串</summary>
        public static string ReadString(string json, string field)
        {
            Match match = Regex.Match(json, @"""" + field + @""":\s*""((?:[^""\\]|\\.)*)""");
            return match.Success ? Unescape(match.Groups[1].Value) : "";
        }

        /// <summary>截断文本，避免整段响应塞进界面</summary>
        public static string Head(string s, int max)
        {
            if (string.IsNullOrEmpty(s))
            {
                return "";
            }
            s = s.Replace("\r", " ").Replace("\n", " ");
            return s.Length <= max ? s : s.Substring(0, max) + "…";
        }
    }
}
