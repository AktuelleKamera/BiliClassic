using System;
using System.Net;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Ink;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows.Shapes;

namespace BiliClassic.Api
{
    public class DanmakuSender
    {
        private const string SendUrl = "https://api.bilibili.com/x/v2/dm/post";



        public static void Send(string aid, string cid, string message, int secend, Action<bool, string> onDone)
        {
            System.Diagnostics.Debug.WriteLine(cid + " " + message + " " + secend);
            if (string.IsNullOrEmpty(cid) || string.IsNullOrEmpty(aid))
            {
                onDone(false, "缺少cid或aid");
                return;
            }
            if (!BiliSession.IsLoggedIn)
            {
                onDone(false, "请先登录");
                return;
            }
            if (string.IsNullOrEmpty(message))
            {
                onDone(false, "弹幕不能为空");
                return;
            }
            if (message.Length > 1000)
            {
                onDone(false, "弹幕不能超过100字");
                return;
            }

            long timestamp = (long)(DateTime.UtcNow - new DateTime(1970, 1, 1)).TotalSeconds;
            long rnd = timestamp * 1000000;
            string rndValue = rnd.ToString();

            string form = "oid=" + cid + "&aid=" + aid + "&type=1&progress=" + secend + "&msg="
                + UrlEncode(message) + "&csrf=" + BiliSession.Csrf + "&mode=1&color=16777215&fontsize=25&rnd=" + rndValue;

            Http.PostForm(SendUrl, form, Http.Referer, delegate(HttpResult http)
            {
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    onDone(false, string.IsNullOrEmpty(http.Error) ? "发送失败" : http.Error);
                    return;
                }

                Match code = Regex.Match(JsonText.Head(http.Body, 200), @"""code"":\s*(-?\d+)");
                if (code.Success && code.Groups[1].Value == "0")
                {
                    onDone(true, "");
                    return;
                }

                string value = code.Success ? code.Groups[1].Value : "";
                string friendly = FriendlyCode(value);
                string message2 = JsonText.ReadString(http.Body, "message");
                onDone(false, "发送失败 code=" + (value.Length > 0 ? value : "?")
                    + (friendly.Length > 0 ? "（" + friendly + "）" : "")
                    + (message2.Length > 0 && message2 != "0" ? " " + message2 : ""));
            });
        }

        private static string UrlEncode(string value)
        {
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
                    sb.Append('%').Append(bytes[i].ToString("X2"));
                }
            }
            return sb.ToString();
        }
        private static string FriendlyCode(string code)
        {
            if (code == "-404" || code == "12061")
            {
                return "评论区不可用";
            }
            if (code == "-352" || code == "-412")
            {
                return "被风控拦了";
            }
            if (code == "-403")
            {
                return "访问权限不足";
            }

            if (code == "-101")
            {
                return "账号未登录（SESSDATA 失效）";
            }
            if (code == "-111")
            {
                return "csrf 校验失败（csrf 与 bili_jct 不匹配）";
            }
            if (code == "-102")
            {
                return "账号被封停";
            }

            if (code == "36701")
            {
                return "弹幕包含被禁止的内容";
            }
            if (code == "36702")
            {
                return "弹幕过长（>100字）";
            }
            if (code == "36704")
            {
                return "视频未审核，禁止发送";
            }
            if (code == "36711")
            {
                return "该视频关闭了弹幕功能";
            }

            if (code == "36703")
            {
                return "发送频率过快（建议间隔 ≥ 8-9.5 秒）";
            }
            if (code == "36715")
            {
                return "当日发送数量超过上限";
            }
            if (code == "36712")
            {
                return "Lv1 用户字数限制（20字）";
            }

            return "";
        }
    }
}
