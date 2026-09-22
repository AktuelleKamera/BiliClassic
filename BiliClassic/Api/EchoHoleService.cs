using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class EchoItem
    {
        public string Text = "";
        public string Author = "";
        public string Device = "";
        public string Time = "";
    }

    public static class EchoHoleService
    {
        private const string Url = "http://www.biliclassic.cn/api/echo.json";

        private static readonly Random Rnd = new Random();

        private static int _lastIndex = -1;

        public static void FetchRandom(Action<string, string> onDone)
        {
            Http.GetText(Url, Http.Referer, delegate(HttpResult http)
            {
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(null, string.IsNullOrEmpty(http.Error) ? "网络错误" : http.Error);
                    return;
                }

                List<EchoItem> items = Parse(http.Body);
                if (items.Count == 0)
                {
                    onDone(null, "回声洞暂无内容");
                    return;
                }

                EchoItem item = items[Pick(items.Count)];
                onDone(Format(item), "");
            });
        }

        private static int Pick(int count)
        {
            if (count <= 1)
            {
                _lastIndex = 0;
                return 0;
            }

            int index = Rnd.Next(count);
            int guard = 0;
            while (index == _lastIndex && guard < 20)
            {
                index = Rnd.Next(count);
                guard++;
            }
            _lastIndex = index;
            return index;
        }

        private static string Format(EchoItem item)
        {
            string message = item.Text;
            message += "\n\n" + (item.Author.Length > 0 ? item.Author : "匿名");
            if (item.Device.Length > 0)
            {
                message += "\n来自 " + item.Device;
            }
            message += "\n" + (item.Time.Length > 0 ? item.Time : "未知");
            return message;
        }

        private static List<EchoItem> Parse(string body)
        {
            List<EchoItem> items = new List<EchoItem>();
            MatchCollection objects = Regex.Matches(body, @"\{[^{}]*\}");
            for (int i = 0; i < objects.Count; i++)
            {
                string json = objects[i].Value;
                EchoItem item = new EchoItem();
                item.Text = JsonText.ReadString(json, "text");
                item.Author = JsonText.ReadString(json, "author");
                item.Device = JsonText.ReadString(json, "device");
                item.Time = JsonText.ReadString(json, "time");
                if (item.Text.Length > 0)
                {
                    items.Add(item);
                }
            }
            return items;
        }
    }
}
