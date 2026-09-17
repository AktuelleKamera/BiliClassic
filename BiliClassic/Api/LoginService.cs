using System;
using System.Collections.Generic;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>扫码状态，用官方语义</summary>
    public enum QrLoginState
    {
        /// <summary>还没扫</summary>
        NotScanned = 86101,

        /// <summary>已扫码，等确认</summary>
        Scanned = 86090,

        /// <summary>二维码过期</summary>
        Expired = 86038,

        /// <summary>确认成功，凭证已保存</summary>
        Success = 0,

        /// <summary>请求或解析出错</summary>
        Failed = -1
    }

    public sealed class QrPollResult
    {
        public QrLoginState State = QrLoginState.Failed;
        public string Error = "";
    }

    /// <summary>
    /// 扫码与短信登录接口
    /// 轮询时外层code为0，再看内层data.code
    /// </summary>
    public static class LoginService
    {
        private const string QrGenerateUrl =
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
            + "?source=main-fe-header&go_url=https:%2F%2Fwww.bilibili.com%2F";

        private const string QrPollUrl =
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
            + "?source=main-fe-header&qrcode_key=";

        /// <summary>跨域地址列表，扫码成功后按它下发凭证</summary>
        private const string SsoListUrl =
            "https://passport.bilibili.com/x/passport-login/web/sso/list";

        /// <summary>TV/APP扫码：成功后cookie以JSON下发，不用解析Set-Cookie</summary>
        private const string TvAppKey = "4409e2ce8ffd12b8";
        private const string TvAppSec = "59b43e04ad6965f34319062b478f83dd";

        private const string TvAuthUrl =
            "https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code";
        private const string TvPollUrl =
            "https://passport.bilibili.com/x/passport-tv-login/qrcode/poll";

        private static string _tvAuthCode = "";
        private static bool _tvMode;

        /// <summary>当前二维码内容</summary>
        public static string QrContent = "";

        /// <summary>
        /// 收尾过程的一行摘要
        /// 手机上错误框显示不了长文，关键信息放第一行
        /// </summary>
        private static string _flowSummary = "";

        private static string _qrKey = "";

        /// <summary>
        /// 获取二维码
        /// 先用TV/APP扫码：它把cookie放进正文的cookie_info
        /// 网页版那条路在WP7上抠不到SESSDATA（HttpOnly，框架不给），只作后备
        /// </summary>
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

        /// <summary>TV/APP二维码</summary>
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

        /// <summary>网页版二维码，TV那条路不通时的后备</summary>
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
                        // qrcode_key先出现url后出现，取url字段
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

        /// <summary>轮询扫码状态，成功后凭证已写入BiliSession</summary>
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

                    // 外层code，请求本身是否成功
                    Match outer = Regex.Match(http.Body, @"""code"":\s*(-?\d+)");
                    if (!outer.Success || outer.Groups[1].Value != "0")
                    {
                        result.Error = "接口返回 code=" + (outer.Success ? outer.Groups[1].Value : "?");
                        onDone(result);
                        return;
                    }

                    // 内层data.code是扫码状态，从data之后开始找
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

                        // mid/csrf可能只在这一步给
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

                        // 容器不一定收得下跨域cookie，原文也抠一遍
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

        /// <summary>
        /// TV/APP轮询
        /// 成功时cookie_info里直接是name/value，不用碰Set-Cookie
        /// </summary>
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

        /// <summary>从cookie_info.cookies[]按出现顺序配对name/value</summary>
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

        /// <summary>手动提交cookie，用于验证CookieContainer链路</summary>
        public static void LoginWithCookie(string cookieString)
        {
            string normalized = NormalizeCookieInput(cookieString);

            // 已是标准cookie串就别重建，CookieHelper只保留6个字段
            // 会丢掉DedeUserID__ckMd5和sid这些风控要的字段
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

        /// <summary>
        /// 收尾，把凭证落进BiliSession
        /// 轮询那一步可能已经用Set-Cookie下发了，先回读
        /// 不够再请求跨域地址，最后走sso列表
        /// 回调放在最后，保证发nav时cookie已就位
        /// </summary>
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

        /// <summary>正文里有没有SESSDATA，只报有无</summary>
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

        /// <summary>短摘要用的长度：无 / 数字</summary>
        private static string ShortLen(string setCookie)
        {
            return string.IsNullOrEmpty(setCookie) ? "无" : setCookie.Length.ToString();
        }

        /// <summary>只报长度，不报内容</summary>
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

        /// <summary>只描述地址，不带值</summary>
        private static string Describe(string url)
        {
            if (string.IsNullOrEmpty(url))
            {
                return "无";
            }
            return HostOf(url) + PathOf(url) + " 参数名：" + KeysOf(url);
        }

        /// <summary>把跨域地址query里的凭证并进登录态</summary>
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
                // 保持URL编码，Cookie类不接受带逗号的值
                cookies.Add(key + "=" + value);
            }

            if (cookies.Count > 0)
            {
                BiliSession.MergeCookieString(string.Join("; ", cookies.ToArray()));
            }
        }

        /// <summary>
        /// 列sso并逐个请求
        /// 轮询只给一个地址，真正的凭证要这些地址的Set-Cookie
        /// </summary>
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
                    // 列表为空就把响应带上，否则查不下去
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

        /// <summary>一个个试，拿到完整凭证就停</summary>
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

                // 浏览器是直接跳这个地址的，POST不行就再GET一次
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

        /// <summary>data.sso是地址数组</summary>
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

        /// <summary>只取主机名，query里可能带SESSDATA</summary>
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

        /// <summary>只取路径，query里有凭证</summary>
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

        /// <summary>只列参数名不列值</summary>
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

        /// <summary>容忍整行cookie文本</summary>
        private static string NormalizeCookieInput(string input)
        {
            if (string.IsNullOrEmpty(input))
            {
                return "";
            }
            string text = input.Trim();
            text = text.Replace("\r", " ").Replace("\n", " ").Replace("\t", " ");
            // 去掉Cookie前缀
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
