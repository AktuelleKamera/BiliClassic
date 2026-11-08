using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class SmsSendResult
    {
        public int Code = -1;
        public string Message = "";

        public string CaptchaKey = "";

        public string RawBody = "";

        public bool Ok
        {
            get { return Code == 0; }
        }
    }

    public sealed class SmsLoginResult
    {
        public int Code = -1;
        public string Message = "";
        public string CookieString = "";
        public string RawBody = "";

        public bool Ok
        {
            get { return Code == 0; }
        }
    }

    public static class SmsLoginService
    {
        private const string AppKey = "dfca71928277209b";
        private const string AppSec = "b5475a8825547a4fc26c7d518eaaa02e";

        private const string AppUa =
            "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android model/android_hd"
            + " mobi_app/android_hd build/2001100 channel/master innerVer/2001100 osVer/15 network/2";

        private const string Statistics = "{\"appId\":5,\"platform\":3,\"version\":\"2.0.1\",\"abtest\":\"\"}";

        private const string SendUrl = "https://passport.bilibili.com/x/passport-login/sms/send";
        private const string LoginUrl = "https://passport.bilibili.com/x/passport-login/login/sms";

        private const string KeyDeviceId = "bili_device_id";


        public static void SendSms(string tel, Action<SmsSendResult> onDone)
        {
            EnsureBuvid(delegate(string buvid)
            {
                SendSmsCore(tel, buvid, onDone);
            });
        }

        public static void LoginBySms(string tel, string code, string captchaKey, Action<SmsLoginResult> onDone)
        {
            EnsureBuvid(delegate(string buvid)
            {
                LoginBySmsCore(tel, code, captchaKey, buvid, onDone);
            });
        }

        private static void SendSmsCore(string tel, string buvid, Action<SmsSendResult> onDone)
        {
            SmsSendResult result = new SmsSendResult();
            long tsMs = DateTime.UtcNow.Ticks / TimeSpan.TicksPerMillisecond;

            Dictionary<string, string> parameters = new Dictionary<string, string>();
            parameters["build"] = "2001100";
            parameters["buvid"] = buvid;
            parameters["c_locale"] = "zh_CN";
            parameters["channel"] = "master";
            parameters["cid"] = "86";
            parameters["disable_rcmd"] = "0";
            parameters["local_id"] = buvid;
            parameters["login_session_id"] = Md5Util.Hex(buvid + tsMs);
            parameters["mobi_app"] = "android_hd";
            parameters["platform"] = "android";
            parameters["s_locale"] = "zh_CN";
            parameters["statistics"] = Statistics;
            parameters["tel"] = tel;
            parameters["ts"] = (tsMs / 1000).ToString();

            string body = SignAndBuildBody(parameters);

            Http.PostForm(SendUrl, body, Http.Referer, AppHeaders(buvid), AppUa, delegate(HttpResult http)
            {
                try
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        result.Message = http == null ? "无响应" : http.Error;
                    }
                    else
                    {
                        result.RawBody = Head(http.Body, 300);
                        result.Code = ReadInt(http.Body, "code", -1);
                        result.Message = ReadString(http.Body, "message");
                        if (result.Code == 0)
                        {
                            result.CaptchaKey = ReadString(http.Body, "captcha_key");
                            string recaptcha = ReadString(http.Body, "recaptcha_url");
                            if (recaptcha.Length > 0)
                            {
                                result.Message = "服务端要求人机验证（recaptcha_url 非空）";
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    result.Message = ex.Message;
                }
                onDone(result);
            });
        }

        private static void LoginBySmsCore(string tel, string code, string captchaKey, string buvid,
                                           Action<SmsLoginResult> onDone)
        {
            SmsLoginResult result = new SmsLoginResult();
            string deviceId = GetDeviceId();

            Dictionary<string, string> parameters = new Dictionary<string, string>();
            parameters["bili_local_id"] = deviceId;
            parameters["build"] = "2001100";
            parameters["buvid"] = buvid;
            parameters["c_locale"] = "zh_CN";
            parameters["captcha_key"] = captchaKey ?? "";
            parameters["channel"] = "master";
            parameters["cid"] = "86";
            parameters["code"] = code;
            parameters["device"] = "phone";
            parameters["device_id"] = deviceId;
            parameters["device_name"] = "vivo";
            parameters["device_platform"] = "Android14vivo";
            parameters["disable_rcmd"] = "0";
            parameters["from_pv"] = "main.my-information.my-login.0.click";
            parameters["from_url"] = "bilibili://user_center/mine";
            parameters["local_id"] = buvid;
            parameters["mobi_app"] = "android_hd";
            parameters["platform"] = "android";
            parameters["s_locale"] = "zh_CN";
            parameters["statistics"] = Statistics;
            parameters["tel"] = tel;

            string body = SignAndBuildBody(parameters);

            Http.PostForm(LoginUrl, body, Http.Referer, AppHeaders(buvid), AppUa, delegate(HttpResult http)
            {
                try
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        result.Message = http == null ? "无响应" : http.Error;
                    }
                    else
                    {
                        result.RawBody = Head(http.Body, 300);
                        result.Code = ReadInt(http.Body, "code", -1);
                        result.Message = ReadString(http.Body, "message");
                        if (result.Code == 0)
                        {
                            result.CookieString = ExtractCookies(http.Body);
                            if (result.CookieString.Length == 0)
                            {
                                result.Message = "登录成功但没解析出 cookie";
                            }
                            else
                            {
                                BiliSession.MergeCookieString(result.CookieString);
                                BiliSession.Save();
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    result.Message = ex.Message;
                }
                onDone(result);
            });
        }


        private static void EnsureBuvid(Action<string> onDone)
        {
            string cached = CookieGenerator.GetBuvid3();
            if (cached.Length > 0)
            {
                onDone(cached);
                return;
            }

            CookieGenerator.Ensure(delegate
            {
                onDone(CookieGenerator.GetBuvid3());
            });
        }

        private static string GetDeviceId()
        {
            string cached = LocalStore.Get(KeyDeviceId);
            if (!string.IsNullOrEmpty(cached))
            {
                return cached;
            }

            string id = Md5Util.Hex(Guid.NewGuid().ToByteArray()).Substring(0, 16);
            LocalStore.Set(KeyDeviceId, id);
            return id;
        }


        private static string SignAndBuildBody(Dictionary<string, string> parameters)
        {
            parameters["appkey"] = AppKey;
            parameters["ts"] = CurrentUnixSeconds().ToString();

            List<string> keys = new List<string>(parameters.Keys);
            keys.Sort(StringComparer.Ordinal);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.Count; i++)
            {
                if (sb.Length > 0)
                {
                    sb.Append('&');
                }
                sb.Append(FormEncode(keys[i])).Append('=').Append(FormEncode(parameters[keys[i]]));
            }

            parameters["sign"] = Md5Util.Hex(sb.ToString() + AppSec);
            return BuildBody(parameters);
        }

        private static string BuildBody(Dictionary<string, string> parameters)
        {
            StringBuilder sb = new StringBuilder();
            foreach (KeyValuePair<string, string> pair in parameters)
            {
                if (sb.Length > 0)
                {
                    sb.Append('&');
                }
                sb.Append(FormEncode(pair.Key)).Append('=').Append(FormEncode(pair.Value));
            }
            return sb.ToString();
        }

        private static string FormEncode(string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            byte[] bytes = Encoding.UTF8.GetBytes(value);
            for (int i = 0; i < bytes.Length; i++)
            {
                char c = (char)bytes[i];
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '*')
                {
                    sb.Append(c);
                }
                else if (c == ' ')
                {
                    sb.Append('+');
                }
                else
                {
                    sb.Append('%');
                    sb.Append(bytes[i].ToString("X2"));
                }
            }
            return sb.ToString();
        }

        private static Dictionary<string, string> AppHeaders(string buvid)
        {
            Dictionary<string, string> headers = new Dictionary<string, string>();
            headers["buvid"] = buvid;
            headers["env"] = "prod";
            headers["app-key"] = "android_hd";
            headers["x-bili-trace-id"] = "11111111111111111111111111111111:1111111111111111:0:0";
            return headers;
        }


        private static string ExtractCookies(string body)
        {
            int index = body.IndexOf("\"cookie_info\"", StringComparison.Ordinal);
            if (index < 0)
            {
                return "";
            }

            string tail = body.Substring(index);
            MatchCollection names = Regex.Matches(tail, @"""name"":\s*""([^""]*)""");
            MatchCollection values = Regex.Matches(tail, @"""value"":\s*""([^""]*)""");

            List<string> cookies = new List<string>();
            int count = Math.Min(names.Count, values.Count);
            for (int i = 0; i < count; i++)
            {
                string name = names[i].Groups[1].Value;
                string value = Unescape(values[i].Groups[1].Value);
                if (name.Length == 0 || value.Length == 0)
                {
                    continue;
                }
                cookies.Add(name + "=" + value);
            }
            return string.Join("; ", cookies.ToArray());
        }

        private static int ReadInt(string json, string field, int fallback)
        {
            Match match = Regex.Match(json, @"""" + field + @""":\s*(-?\d+)");
            int value;
            if (match.Success && int.TryParse(match.Groups[1].Value, out value))
            {
                return value;
            }
            return fallback;
        }

        private static string ReadString(string json, string field)
        {
            Match match = Regex.Match(json, @"""" + field + @""":\s*""((?:[^""\\]|\\.)*)""");
            return match.Success ? Unescape(match.Groups[1].Value) : "";
        }

        private static string Unescape(string s)
        {
            if (string.IsNullOrEmpty(s))
            {
                return "";
            }
            return s.Replace("\\/", "/").Replace("\\u0026", "&").Replace("\\\"", "\"");
        }

        private static string Head(string s, int max)
        {
            if (string.IsNullOrEmpty(s))
            {
                return "";
            }
            s = s.Replace("\r", " ").Replace("\n", " ");
            return s.Length <= max ? s : s.Substring(0, max) + "…";
        }

        private static long CurrentUnixSeconds()
        {
            DateTime epoch = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return (long)DateTime.UtcNow.Subtract(epoch).TotalSeconds;
        }

    }
}
