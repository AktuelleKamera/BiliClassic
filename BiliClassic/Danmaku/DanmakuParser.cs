using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text.RegularExpressions;
using System.Windows.Media;

namespace BiliClassic.Danmaku
{
    public static class DanmakuParser
    {
        private static readonly Regex ItemRegex =
            new Regex("<d\\s+p=\"([^\"]*)\">([^<]*)</d>", RegexOptions.Singleline);

        public static List<DanmakuItem> Parse(string xml)
        {
            List<DanmakuItem> items = new List<DanmakuItem>();
            if (string.IsNullOrEmpty(xml))
            {
                return items;
            }

            foreach (Match match in ItemRegex.Matches(xml))
            {
                DanmakuItem item = ParseOne(match.Groups[1].Value, match.Groups[2].Value);
                if (item != null)
                {
                    items.Add(item);
                }
            }
            items.Sort(CompareByTime);
            return items;
        }

        private static int CompareByTime(DanmakuItem a, DanmakuItem b)
        {
            return a.TimeMs.CompareTo(b.TimeMs);
        }

        private static DanmakuItem ParseOne(string p, string text)
        {
            if (string.IsNullOrEmpty(p))
            {
                return null;
            }

            string[] parts = p.Split(',');
            if (parts.Length < 4)
            {
                return null;
            }

            string content = UnescapeXml(text);
            if (content.Length == 0)
            {
                return null;
            }

            DanmakuItem item = new DanmakuItem();
            item.Text = content;
            item.TimeMs = (int)(ParseNumber(parts[0]) * 1000.0);
            item.Mode = ParseMode(parts[1]);
            item.TextSize = (int)ParseNumber(parts[2]);
            item.Color = ParseColor((int)ParseNumber(parts[3]));

            if (parts.Length > 4)
            {
                item.PostTime = (long)ParseNumber(parts[4]);
            }
            if (parts.Length > 7)
            {
                item.Dmid = (long)ParseNumber(parts[7]);
            }

            if (!item.Mode.IsSupported())
            {
                return null;
            }

            return item;
        }

        private static DanmakuMode ParseMode(string raw)
        {
            int value = (int)ParseNumber(raw);
            if (value == 2 || value == 3)
            {
                value = 1;
            }
            if (value == (int)DanmakuMode.Bottom
                || value == (int)DanmakuMode.Top
                || value == (int)DanmakuMode.Reverse)
            {
                return (DanmakuMode)value;
            }
            return DanmakuMode.Scroll;
        }

        private static double ParseNumber(string s)
        {
            double value;
            if (double.TryParse(s, NumberStyles.Float, CultureInfo.InvariantCulture, out value))
            {
                return value;
            }
            return 0;
        }

        private static Color ParseColor(int rgb)
        {
            byte r = (byte)((rgb >> 16) & 0xFF);
            byte g = (byte)((rgb >> 8) & 0xFF);
            byte b = (byte)(rgb & 0xFF);
            return Color.FromArgb(255, r, g, b);
        }

        private static string UnescapeXml(string s)
        {
            if (string.IsNullOrEmpty(s) || s.IndexOf('&') < 0)
            {
                return s ?? "";
            }

            s = s.Replace("&lt;", "<")
                 .Replace("&gt;", ">")
                 .Replace("&quot;", "\"")
                 .Replace("&apos;", "'")
                 .Replace("&#39;", "'");
            return s.Replace("&amp;", "&");
        }
    }
}
