using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public enum QrLoginState
    {
        NotScanned = 86101,

        Scanned = 86090,

        Expired = 86038,

        Success = 0,

        Failed = -1
    }

    public sealed class QrPollResult
    {
        public QrLoginState State = QrLoginState.Failed;
        public string Error = "";
    }

    public static class LoginService
    {
        private const string QrGenerateUrl =
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
            + "?source=main-fe-header&go_url=https:%2F%2Fwww.bilibili.com%2F";

        private const string QrPollUrl =
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
            + "?source=main-fe-header&qrcode_key=";

        private const string SsoListUrl =
            "https://passport.bilibili.com/x/passport-login/web/sso/list";

        private const string TvAppKey = "4409e2ce8ffd12b8";
        private const string TvAppSec = "59b43e04ad6965f34319062b478f83dd";

        private const string TvAuthUrl =
            "https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code";
        private const string TvPollUrl =
            "https://passport.bilibili.com/x/passport-tv-login/qrcode/poll";

        private static string _tvAuthCode = "";
        private static bool _tvMode;

        public static string QrContent = "";

        private static string _flowSummary = "";

        private static string _qrKey = "";

        public static void RequestQrCode(Action<string, string> onDone)
        {
            _tvMode = false;
            _qrKey = "";
            _tvAuthCode = "";

            RequestTvQrCode(delegate(string tvContent, string tvError)
            {
                if (!string.IsNullOrEmpty(tvContent))
                {
                    onDone(tvContent, null);
                    return;
                }

                RequestWebQrCode(onDone);
            });
        }

        private static void RequestTvQrCode(Action<string, string> onDone)
        {
            Dictionary<string, string> parameters = new Dictionary<string, string>();
            parameters["local_id"] = "0";
            parameters["mobi_app"] = "android_tv_yst";

            string body = AppSign.BuildBody(parameters, TvAppKey, TvAppSec);

            Http.PostForm(TvAuthUrl, body, Http.Referer, delegate(HttpResult http)
            {
                string content = null;
                string error = null;
                try
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        error = "TV二维码无响应";
                    }
                    else
                    {
                        Match url = Regex.Match(http.Body, @"""url"":\s*""([^""]+)""");
                        Match auth = Regex.Match(http.Body, @"""auth_code"":\s*""([^""]+)""");
                        if (!url.Success)
                        {
                            error = "TV二维码解析失败: " + Head(http.Body, 120);
                        }
                        else
                        {
                            _tvAuthCode = auth.Success ? auth.Groups[1].Value : "";
                            content = UnescapeJson(url.Groups[1].Value);
                            QrContent = content;
                            _tvMode = true;
                        }
                    }
                }
                catch (Exception ex)
                {
                    error = "解析TV二维码失败: " + ex.Message;
                }

                if (onDone != null)
                {
                    onDone(content, error);
                }
            });
        }

        private static void RequestWebQrCode(Action<string, string> onDone)
        {
            Http.GetText(QrGenerateUrl, Http.Referer, delegate(HttpResult http)
            {
                string content = null;
                string error = null;
                try
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        error = "获取二维码失败: " + (http == null ? "无响应" : http.Error);
                    }
                    else
                    {
                        Match key = Regex.Match(http.Body, @"""qrcode_key"":\s*""([^""]+)""");
                        Match url = Regex.Match(http.Body, @"""url"":\s*""([^""]+)""");
                        if (!key.Success || !url.Success)
                        {
                            error = "二维码响应无法解析: " + Head(http.Body, 160);
                        }
                        else
                        {
                            _qrKey = key.Groups[1].Value;
                            content = UnescapeJson(url.Groups[1].Value);
                            QrContent = content;
                        }
                    }
                }
                catch (Exception ex)
                {
                    error = "解析二维码失败: " + ex.Message;
                }

                if (onDone != null)
                {
                    onDone(content, error);
                }
            });
        }

        public static void PollQrCode(Action<QrPollResult> onDone)
        {
            if (_tvMode)
            {
                PollTvQrCode(onDone);
                return;
            }

            QrPollResult result = new QrPollResult();

            if (string.IsNullOrEmpty(_qrKey))
            {
                result.Error = "请先获取二维码";
                onDone(result);
                return;
            }

            Http.GetText(QrPollUrl + _qrKey, Http.Referer, delegate(HttpResult http)
            {
                try
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        result.Error = "轮询失败: " + (http == null ? "无响应" : http.Error);
                        onDone(result);
                        return;
                    }

                    Match outer = Regex.Match(http.Body, @"""code"":\s*(-?\d+)");
                    if (!outer.Success || outer.Groups[1].Value != "0")
                    {
                        result.Error = "接口返回 code=" + (outer.Success ? outer.Groups[1].Value : "?");
                        onDone(result);
                        return;
                    }

                    int dataIndex = http.Body.IndexOf("\"data\"", StringComparison.Ordinal);
                    string tail = dataIndex >= 0 ? http.Body.Substring(dataIndex) : http.Body;
                    Match inner = Regex.Match(tail, @"""code"":\s*(-?\d+)");

                    int scanCode;
                    if (!inner.Success || !int.TryParse(inner.Groups[1].Value, out scanCode))
                    {
                        result.Error = "无法解析扫码状态: " + Head(http.Body, 160);
                        onDone(result);
                        return;
                    }

                    if (scanCode == 0)
                    {
                        result.State = QrLoginState.Success;

                        Match mid = Regex.Match(tail, @"""mid"":\s*(\d+)");
                        if (mid.Success)
                        {
                            BiliSession.Mid = mid.Groups[1].Value;
                        }
                        string csrf = JsonText.ReadString(tail, "csrf");
                        if (csrf.Length > 0)
                        {
                            BiliSession.Csrf = csrf;
                        }

                        Match cross = Regex.Match(tail, @"""url"":\s*""([^""]+)""");
                        string crossUrl = cross.Success
                            ? UnescapeJson(cross.Groups[1].Value)
                            : "";

                        BiliSession.MergeSetCookie(http.SetCookie);
                        BiliSession.MergeCookiesInText(http.Body);
                        FinishLogin(crossUrl, http.SetCookie, result, onDone);
                        return;
                    }
                    else if (scanCode == 86101)
                    {
                        result.State = QrLoginState.NotScanned;
                    }
                    else if (scanCode == 86090)
                    {
                        result.State = QrLoginState.Scanned;
                    }
                    else if (scanCode == 86038)
                    {
                        result.State = QrLoginState.Expired;
                    }
                    else
                    {
                        result.Error = "未知扫码状态 " + scanCode;
                    }
                }
                catch (Exception ex)
                {
                    result.Error = "解析轮询结果失败: " + ex.Message;
                }

                onDone(result);
            });
        }

        private static void PollTvQrCode(Action<QrPollResult> onDone)
        {
            QrPollResult result = new QrPollResult();

            if (string.IsNullOrEmpty(_tvAuthCode))
            {
                result.Error = "请先获取二维码";
                onDone(result);
                return;
            }

            Dictionary<string, string> parameters = new Dictionary<string, string>();
            parameters["auth_code"] = _tvAuthCode;
            parameters["local_id"] = "0";

            string body = AppSign.BuildBody(parameters, TvAppKey, TvAppSec);

            Http.PostForm(TvPollUrl, body, Http.Referer, delegate(HttpResult http)
            {
                try
                {
                    if (http == null || string.IsNullOrEmpty(http.Body))
                    {
                        result.Error = "轮询失败: " + (http == null ? "无响应" : http.Error);
                        onDone(result);
                        return;
                    }

                    int code = ReadInt(http.Body, "code", -1);
                    if (code == 0)
                    {
                        string cookies = ExtractCookieInfo(http.Body);
                        if (cookies.Length == 0)
                        {
                            result.State = QrLoginState.Failed;
                            result.Error = "登录成功但cookie_info是空的: " + Head(http.Body, 120);
                        }
                        else
                        {
                            BiliSession.MergeCookieString(cookies);
                            string mid = JsonText.ReadString(http.Body, "mid");
                            if (mid.Length > 0)
                            {
                                BiliSession.Mid = mid;
                            }
                            BiliSession.Save();

                            if (BiliSession.IsLoggedIn)
                            {
                                result.State = QrLoginState.Success;
                            }
                            else
                            {
                                result.State = QrLoginState.Failed;
                                result.Error = "cookie_info里缺SESSDATA: " + Head(cookies, 100);
                            }
                        }
                    }
                    else if (code == 86038)
                    {
                        result.State = QrLoginState.Expired;
                    }
                    else if (code == 86039 || code == 86090)
                    {
                        result.State = QrLoginState.Scanned;
                    }
                    else if (code == 86101)
                    {
                        result.State = QrLoginState.NotScanned;
                    }
                    else
                    {
                        result.Error = "轮询返回 code=" + code + " " + Head(http.Body, 100);
                    }
                }
                catch (Exception ex)
                {
                    result.Error = "解析轮询结果失败: " + ex.Message;
                }

                onDone(result);
            });
        }

        private static string ExtractCookieInfo(string body)
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
                string value = UnescapeJson(values[i].Groups[1].Value);
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
            Match m = Regex.Match(json, @"""" + field + @""":\s*(-?\d+)");
            int value;
            return m.Success && int.TryParse(m.Groups[1].Value, out value) ? value : fallback;
        }

        public static void LoginWithCookie(string cookieString)
        {
            string normalized = NormalizeCookieInput(cookieString);

            if (!CookieHelper.LooksLikeCookieString(normalized))
            {
                string rebuilt = CookieHelper.ParseAndBuildCookie(normalized);
                if (!string.IsNullOrEmpty(rebuilt))
                {
                    normalized = rebuilt;
                }
            }

            BiliSession.SetCookieString(normalized);
            BiliSession.Save();
        }

        private static void FinishLogin(string crossUrl, string pollSetCookie, QrPollResult result,
                                       Action<QrPollResult> onDone)
        {
            BiliSession.HarvestFromContainer();
            MergeQueryCookies(crossUrl);

            string detail = "轮询的url：" + Describe(crossUrl)
                + "\n轮询Set-Cookie：" + LengthOf(pollSetCookie)
                + "\ncsrf长度：" + (BiliSession.Csrf == null ? 0 : BiliSession.Csrf.Length)
                + "\n容器cookie：" + BiliSession.DescribeCookies();

            _flowSummary = "轮询SC " + ShortLen(pollSetCookie)
                + " csrf " + (BiliSession.Csrf == null ? 0 : BiliSession.Csrf.Length)
                + " 容器SESSDATA " + (BiliSession.HasCookie("SESSDATA") ? "有" : "无");

            if (BiliSession.IsLoggedIn)
            {
                onDone(result);
                return;
            }

            if (string.IsNullOrEmpty(crossUrl))
            {
                RequestSsoList(detail, result, onDone);
                return;
            }

            Http.GetText(crossUrl, Http.Referer, delegate(HttpResult http)
            {
                BiliSession.MergeSetCookie(http == null ? "" : http.SetCookie);
                BiliSession.MergeCookiesInText(http == null ? "" : http.Body);
                BiliSession.HarvestFromContainer();
                if (BiliSession.IsLoggedIn)
                {
                    onDone(result);
                    return;
                }

                RequestSsoList(detail + "\n跨域Set-Cookie："
                    + LengthOf(http == null ? "" : http.SetCookie)
                    + "，正文里有SESSDATA：" + HasSession(http == null ? "" : http.Body),
                    result, onDone);
            });
        }

        private static string HasSession(string body)
        {
            if (string.IsNullOrEmpty(body))
            {
                return "无正文";
            }
            return body.IndexOf("SESSDATA", StringComparison.OrdinalIgnoreCase) >= 0
                ? "有"
                : "没有";
        }

        private static string ShortLen(string setCookie)
        {
            return string.IsNullOrEmpty(setCookie) ? "无" : setCookie.Length.ToString();
        }

        private static string LengthOf(string setCookie)
        {
            if (string.IsNullOrEmpty(setCookie))
            {
                return "无";
            }
            if (setCookie == "!BLOCKED")
            {
                return "读不到（框架受限）";
            }
            return setCookie.Length + "字符";
        }

        private static string Describe(string url)
        {
            if (string.IsNullOrEmpty(url))
            {
                return "无";
            }
            return HostOf(url) + PathOf(url) + " 参数名：" + KeysOf(url);
        }

        private static void MergeQueryCookies(string url)
        {
            if (string.IsNullOrEmpty(url))
            {
                return;
            }

            int q = url.IndexOf('?');
            if (q < 0 || q + 1 >= url.Length)
            {
                return;
            }

            string[] pairs = url.Substring(q + 1).Split('&');
            List<string> cookies = new List<string>();
            for (int i = 0; i < pairs.Length; i++)
            {
                int eq = pairs[i].IndexOf('=');
                if (eq <= 0)
                {
                    continue;
                }
                string key = pairs[i].Substring(0, eq);
                string value = pairs[i].Substring(eq + 1);
                if (key == "gourl" || key == "go_url" || key == "url" || value.Length == 0)
                {
                    continue;
                }
                cookies.Add(key + "=" + value);
            }

            if (cookies.Count > 0)
            {
                BiliSession.MergeCookieString(string.Join("; ", cookies.ToArray()));
            }
        }

        private static void RequestSsoList(string detail, QrPollResult result,
                                          Action<QrPollResult> onDone)
        {
            string form = "csrf=" + (BiliSession.Csrf ?? "");

            Http.PostForm(SsoListUrl, form, Http.Referer, delegate(HttpResult http)
            {
                BiliSession.MergeSetCookie(http == null ? "" : http.SetCookie);
                BiliSession.MergeCookiesInText(http == null ? "" : http.Body);
                BiliSession.HarvestFromContainer();
                if (BiliSession.IsLoggedIn)
                {
                    onDone(result);
                    return;
                }

                List<string> urls = ReadSsoList(http);
                if (urls.Count == 0)
                {
                    result.State = QrLoginState.Failed;
                    result.Error = "扫码成功但缺SESSDATA（" + _flowSummary + " sso 0）"
                        + "\n" + detail
                        + "\nsso返回：" + (http == null ? "无响应" : Head(http.Body, 120));
                    onDone(result);
                    return;
                }

                _flowSummary += " sso " + urls.Count;

                RequestSsoUrl(urls, 0, detail, result, onDone);
            });
        }

        private static void RequestSsoUrl(List<string> urls, int index, string detail,
                                         QrPollResult result, Action<QrPollResult> onDone)
        {
            if (BiliSession.IsLoggedIn)
            {
                onDone(result);
                return;
            }

            if (urls == null || index >= urls.Count)
            {
                result.State = QrLoginState.Failed;
                result.Error = "扫码成功但缺SESSDATA（" + _flowSummary + "）"
                    + "\n" + detail
                    + "\n跨域地址数：" + (urls == null ? 0 : urls.Count)
                    + (urls != null && urls.Count > 0
                        ? "\n第一个：" + Describe(urls[0])
                        : "");
                onDone(result);
                return;
            }

            string one = urls[index];
            Http.PostForm(one, "", Http.Referer, delegate(HttpResult posted)
            {
                string postCookie = posted == null ? "" : posted.SetCookie;
                BiliSession.MergeSetCookie(postCookie);
                BiliSession.MergeCookiesInText(posted == null ? "" : posted.Body);
                BiliSession.HarvestFromContainer();
                if (BiliSession.IsLoggedIn)
                {
                    onDone(result);
                    return;
                }

                Http.GetText(one, Http.Referer, delegate(HttpResult got)
                {
                    string getCookie = got == null ? "" : got.SetCookie;
                    BiliSession.MergeSetCookie(getCookie);
                    BiliSession.MergeCookiesInText(got == null ? "" : got.Body);
                    BiliSession.HarvestFromContainer();

                    string step = detail
                        + "\nsso[" + index + "]Set-Cookie：POST " + LengthOf(postCookie)
                        + " / GET " + LengthOf(getCookie)
                        + "\nsso[" + index + "]正文里有SESSDATA："
                        + HasSession(posted == null ? "" : posted.Body)
                        + " / " + HasSession(got == null ? "" : got.Body)
                        + "\nsso[" + index + "]返回："
                        + Head(posted == null ? "" : posted.Body, 60);

                    bool bodyHasSession =
                        HasSession(posted == null ? "" : posted.Body) == "有"
                        || HasSession(got == null ? "" : got.Body) == "有";

                    _flowSummary += " #" + index + " " + ShortLen(postCookie)
                        + "/" + ShortLen(getCookie) + "文" + (bodyHasSession ? "有" : "无");

                    RequestSsoUrl(urls, index + 1, step, result, onDone);
                });
            });
        }

        private static List<string> ReadSsoList(HttpResult http)
        {
            List<string> urls = new List<string>();
            if (http == null || string.IsNullOrEmpty(http.Body))
            {
                return urls;
            }

            Match array = Regex.Match(http.Body, @"""sso""\s*:\s*\[(.*?)\]",
                RegexOptions.Singleline);
            if (!array.Success)
            {
                return urls;
            }

            foreach (Match m in Regex.Matches(array.Groups[1].Value,
                @"""((?:[^""\\]|\\.)*)"""))
            {
                string one = UnescapeJson(m.Groups[1].Value);
                if (one.Length > 0)
                {
                    urls.Add(one);
                }
            }
            return urls;
        }

        private static string HostOf(string url)
        {
            try
            {
                return new Uri(url, UriKind.Absolute).Host;
            }
            catch (Exception)
            {
                return "(无法解析)";
            }
        }

        private static string PathOf(string url)
        {
            try
            {
                return new Uri(url, UriKind.Absolute).AbsolutePath;
            }
            catch (Exception)
            {
                return "(无法解析)";
            }
        }

        private static string KeysOf(string url)
        {
            int q = url == null ? -1 : url.IndexOf('?');
            if (q < 0 || q + 1 >= url.Length)
            {
                return "(没有 query)";
            }

            StringBuilder sb = new StringBuilder();
            string[] pairs = url.Substring(q + 1).Split('&');
            for (int i = 0; i < pairs.Length; i++)
            {
                int eq = pairs[i].IndexOf('=');
                if (eq <= 0)
                {
                    continue;
                }
                if (sb.Length > 0)
                {
                    sb.Append(", ");
                }
                sb.Append(pairs[i].Substring(0, eq));
            }
            return sb.Length > 0 ? sb.ToString() : "(没有参数)";
        }

        private static string NormalizeCookieInput(string input)
        {
            if (string.IsNullOrEmpty(input))
            {
                return "";
            }
            string text = input.Trim();
            text = text.Replace("\r", " ").Replace("\n", " ").Replace("\t", " ");
            if (text.StartsWith("Cookie:", StringComparison.OrdinalIgnoreCase))
            {
                text = text.Substring(7).Trim();
            }
            while (text.IndexOf("  ", StringComparison.Ordinal) >= 0)
            {
                text = text.Replace("  ", " ");
            }
            return text;
        }

        private static string UnescapeJson(string s)
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
    }
}
