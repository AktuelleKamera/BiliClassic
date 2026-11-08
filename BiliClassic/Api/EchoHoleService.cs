using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;
using System.IO;
using System.Net;
using System.Threading;

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
        public const string Url = "http://www.biliclassic.cn/api/echo.json";
        public const string Post_Url = "http://oldpods.cn/biliclassic_echo.php";

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


        //下面是投稿回声洞的部分
        
        public static string SendEchoHole(string context, string name, string device, string date)
        {
            string res_html = "";
            string return_string = "";
            string body = "content=" + HttpUtility.UrlEncode(context) + "&sign=" + HttpUtility.UrlEncode(name) + "&model=" + HttpUtility.UrlEncode(device) + "&date=" + HttpUtility.UrlEncode(date);
            try
            {
                System.Diagnostics.Debug.WriteLine(111);
                res_html = Post(Post_Url, body);
                System.Diagnostics.Debug.WriteLine(222);
                return_string = EatHtml(res_html);
                System.Diagnostics.Debug.WriteLine(333);
            }
            catch (Exception ex)
            {
                return_string = "E" + ex.Message;
            }


            return return_string;
        }

        public static string EatHtml(string html)//<--好土的名字（
        {
            string return_string = "";
            const string Err_Div = "<div class=\"flash err\">";
            const string OK_Div = "<div class=\"flash ok\">";
            const string Div = "</div>";

            if (html.Contains(Err_Div))
            {
                string[] html_split_1 = html.Split(new[] { Err_Div }, StringSplitOptions.None);
                string[] html_split_2 = html_split_1[1].Split(new[] { Div }, StringSplitOptions.None);
                return_string = "E" + html_split_2[0];
            }
            else if (html.Contains(OK_Div))
            {
                string[] html_split_1 = html.Split(new[] { OK_Div }, StringSplitOptions.None);
                string[] html_split_2 = html_split_1[1].Split(new[] { Div }, StringSplitOptions.None);
                return_string = "O" + html_split_2[0];
            }
            else
            {
                return_string = "E未知的错误";
            }
            return return_string;
        }

        public static string Post(string url, string body)
        {
            string result = null;
            Exception exception = null;
            AutoResetEvent waitHandle = new AutoResetEvent(false);

            WebClient client = new WebClient();

            client.Headers[HttpRequestHeader.ContentType] = "application/x-www-form-urlencoded";

            client.UploadStringCompleted += (sender, e) =>
            {
                if (e.Error != null)
                {
                    exception = e.Error;
                }
                else if (e.Cancelled)
                {
                    exception = new Exception("请求被取消");
                }
                else
                {
                    result = e.Result;
                }
                waitHandle.Set();
            };

            //client.DownloadStringAsync(new Uri(url));
            System.Diagnostics.Debug.WriteLine(body);

            client.UploadStringAsync(new Uri(url), "POST", body);

            if (!waitHandle.WaitOne(30000))
            {
                client.CancelAsync();
                throw new TimeoutException("请求超时");
            }

            if (exception != null)
            {
                throw exception;
            }

            return result;
        }
    }
}
