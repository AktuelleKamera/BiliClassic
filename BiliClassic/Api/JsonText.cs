using System;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public static class JsonText
    {
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
                        if (next == '/') { sb.Append('/'); i += 2; continue; }
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

        public static string ReadString(string json, string field)
        {
            Match match = Regex.Match(json, @"""" + field + @""":\s*""((?:[^""\\]|\\.)*)""");
            return match.Success ? Unescape(match.Groups[1].Value) : "";
        }

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
