using System;
using System.Collections.Generic;
using System.Text;
using BiliClassic.Api;

namespace BiliClassic.Danmaku
{
    /// <summary>
    /// 弹幕拉取
    /// </summary>
    public static class DanmakuLoader
    {
        public static string UrlFor(string cid)
        {
            return "https://comment.bilibili.com/" + cid + ".xml";
        }

        public static void Fetch(string cid, Action<List<DanmakuItem>, string> onDone)
        {
            if (string.IsNullOrEmpty(cid))
            {
                onDone(null, "缺少 cid");
                return;
            }

            Http.GetBytes(UrlFor(cid), "https://www.bilibili.com/", delegate(byte[] data, string error)
            {
                if (data == null || data.Length == 0)
                {
                    onDone(null, string.IsNullOrEmpty(error)
                        ? "弹幕接口没有返回内容"
                        : "弹幕请求失败: " + error);
                    return;
                }

                string xml;
                if (!TryDecodeXml(data, out xml))
                {
                    onDone(null, "弹幕响应彻底失败了喵！: "
                        + DescribeHead(data, 60));
                    return;
                }

                try
                {
                    List<DanmakuItem> items = DanmakuParser.Parse(xml);

                    if (items.Count == 0)
                    {
                        onDone(items, "没有解析到弹幕条目，响应开头: "
                            + JsonText.Head(xml, 140));
                        return;
                    }

                    onDone(items, "");
                }
                catch (Exception ex)
                {
                    onDone(null, "解析弹幕失败: " + ex.Message);
                }
            });
        }

        /// <summary>
        /// 明文测试
        /// </summary>
        private static bool TryDecodeXml(byte[] data, out string xml)
        {
            string plain = DecodeUtf8(data, 0, data.Length);
            if (LooksLikeXml(plain))
            {
                xml = plain;
                return true;
            }

            byte[] raw;
            try
            {
                raw = Inflate.Decompress(data);
            }
            catch (Exception)
            {
                xml = null;
                return false;
            }

            string text = DecodeUtf8(raw, 0, raw.Length);
            if (!LooksLikeXml(text))
            {
                xml = null;
                return false;
            }

            xml = text;
            return true;
        }

        private static bool LooksLikeXml(string text)
        {
            return !string.IsNullOrEmpty(text)
                && text.IndexOf("<chatserver>", StringComparison.Ordinal) >= 0;
        }

        private static string DecodeUtf8(byte[] data, int offset, int count)
        {
            try
            {
                // 替换回退
                return Encoding.UTF8.GetString(data, offset, count);
            }
            catch (Exception)
            {
                return "";
            }
        }

        private static string DescribeHead(byte[] data, int max)
        {
            StringBuilder sb = new StringBuilder();
            int count = data.Length < max ? data.Length : max;
            for (int i = 0; i < count; i++)
            {
                if (i > 0)
                {
                    sb.Append(' ');
                }
                sb.Append(data[i].ToString("X2"));
            }
            return sb.ToString();
        }
    }
}
