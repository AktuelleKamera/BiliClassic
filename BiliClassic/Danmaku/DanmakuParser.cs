using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text.RegularExpressions;
using System.Windows.Media;

namespace BiliClassic.Danmaku
{
    /// <summary>
    /// 弹幕 XML 解析，BiliDanmukuParser
    /// </summary>
    public static class DanmakuParser
    {
        /// <summary>
        /// 匹配
        /// </summary>
        private static readonly Regex ItemRegex =
            new Regex("<d\\s+p=\"([^\"]*)\">([^<]*)</d>", RegexOptions.Singleline);

        /// <summary>解析</summary>
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
            // 至少要有 时间/模式/字号/颜色 四位
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

            // 飞掉高级和BAS弹幕，弹幕能飞
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

        /// <summary>
        /// InvariantCulture，手机区域设置可能是用逗号当小数点的（中文区不是，但欧洲区是），那样会解析失败的说
        /// </summary>
        private static double ParseNumber(string s)
        {
            double value;
            if (double.TryParse(s, NumberStyles.Float, CultureInfo.InvariantCulture, out value))
            {
                return value;
            }
            return 0;
        }

        /// <summary>p 第 4 位是十进制 RGB</summary>
        private static Color ParseColor(int rgb)
        {
            byte r = (byte)((rgb >> 16) & 0xFF);
            byte g = (byte)((rgb >> 8) & 0xFF);
            byte b = (byte)(rgb & 0xFF);
            return Color.FromArgb(255, r, g, b);
        }

        /// <summary>
        /// XML实体反转义
        /// </summary>
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
