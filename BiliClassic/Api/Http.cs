using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;

namespace BiliClassic.Api
{
    public sealed class HttpResult
    {
        public string Body = "";

        public string Error = "";

        public string SetCookie = "";

        public bool Ok
        {
            get { return Error.Length == 0; }
        }
    }

    public static class Http
    {
        public const string UserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36";

        // 【重要】播放地址/图片都要它，缺了 CDN 直接 403，喵
        public const string Referer = "https://www.bilibili.com/";

        public const string Origin = "https://www.bilibili.com";

        public static string HeaderSupport = "（还没发过请求）";

        private static readonly Dictionary<string, string> HeaderResults =
            new Dictionary<string, string>();

        private static readonly object HeaderLock = new object();

        public static void GetText(string url, string referer, Action<HttpResult> onDone)
        {
            GetText(url, referer, onDone, 2);
        }

        private static void GetText(string url, string referer, Action<HttpResult> onDone, int attemptsLeft)
        {
            if (url != null && url.StartsWith("//"))
            {
                url = "https:" + url;
            }

            if (string.IsNullOrEmpty(url))
            {
                onDone(Fail("URL 为空"));
                return;
            }

            HttpWebRequest request;
            try
            {
                request = (HttpWebRequest)WebRequest.Create(CreateUri(url));
                request.Method = "GET";
                request.AllowReadStreamBuffering = true;
                request.UserAgent = UserAgent;
                ApplyCommonHeaders(request, referer);
            }
            catch (Exception ex)
            {
                onDone(Fail("创建请求失败: " + ex.Message));
                return;
            }

            string retryUrl = url;
            string retryReferer = referer;
            HttpWebRequest captured = request;

            request.BeginGetResponse(delegate(IAsyncResult ar)
            {
                HttpResult result = new HttpResult();
                try
                {
                    HttpWebResponse response = (HttpWebResponse)captured.EndGetResponse(ar);
                    using (Stream stream = response.GetResponseStream())
                    {
                        using (StreamReader reader = new StreamReader(stream, Encoding.UTF8))
                        {
                            result.Body = reader.ReadToEnd();
                        }
                    }
                    result.SetCookie = ReadSetCookie(response);
                    response.Close();
                }
                catch (WebException wex)
                {
                    result.Body = ReadWebExceptionBody(wex);
                    if (string.IsNullOrEmpty(result.Body))
                    {
                        result.Error = "HTTP 失败 [" + wex.Status + DescribeStatus(wex.Status) + "]: " + wex.Message
                            + (wex.Response == null ? " (无响应对象)" : "")
                            + (wex.InnerException == null
                                ? ""
                                : " 内部: " + wex.InnerException.GetType().Name + ": " + wex.InnerException.Message);
                    }
                }
                catch (Exception ex)
                {
                    result.Error = "读取响应失败: " + ex.Message;
                }

                if (!result.Ok && attemptsLeft > 1)
                {
                    GetText(retryUrl, retryReferer, onDone, attemptsLeft - 1);
                    return;
                }

                onDone(result);
            }, null);
        }

        private static string DescribeStatus(WebExceptionStatus status)
        {
            string name = status.ToString();
            if (name == "TrustFailure") return " (证书不被信任)";
            if (name == "NameResolutionFailure") return " (DNS 解析失败)";
            if (name == "ConnectFailure") return " (连不上服务器)";
            if (name == "Timeout") return " (超时)";
            if (name == "ProtocolError") return " (HTTP 协议错误)";
            if (name == "SecureChannelFailure") return " (安全通道失败/TLS)";
            if (name == "UnknownError") return " (未知错误；Silverlight 下常见于 TLS 握手失败)";
            return "";
        }

        private static string ReadWebExceptionBody(WebException wex)
        {
            try
            {
                if (wex.Response == null)
                {
                    return "";
                }
                using (Stream stream = wex.Response.GetResponseStream())
                {
                    using (StreamReader reader = new StreamReader(stream, Encoding.UTF8))
                    {
                        return reader.ReadToEnd();
                    }
                }
            }
            catch (Exception)
            {
                return "";
            }
        }

        public static void GetBytes(string url, string referer, Action<byte[], string> onDone)
        {
            if (url != null && url.StartsWith("//"))
            {
                url = "https:" + url;
            }

            if (string.IsNullOrEmpty(url))
            {
                onDone(null, "URL 为空");
                return;
            }

            HttpWebRequest request;
            try
            {
                request = (HttpWebRequest)WebRequest.Create(CreateUri(url));
                request.Method = "GET";
                request.AllowReadStreamBuffering = true;
                request.UserAgent = UserAgent;
                ApplyCommonHeaders(request, referer);
            }
            catch (Exception ex)
            {
                onDone(null, "创建请求失败: " + ex.Message);
                return;
            }

            request.BeginGetResponse(delegate(IAsyncResult ar)
            {
                try
                {
                    HttpWebResponse response = (HttpWebResponse)request.EndGetResponse(ar);
                    using (Stream stream = response.GetResponseStream())
                    {
                        MemoryStream buffer = new MemoryStream();
                        byte[] chunk = new byte[8192];
                        int read = stream.Read(chunk, 0, chunk.Length);
                        while (read > 0)
                        {
                            buffer.Write(chunk, 0, read);
                            read = stream.Read(chunk, 0, chunk.Length);
                        }
                        response.Close();
                        onDone(buffer.ToArray(), "");
                    }
                }
                catch (WebException wex)
                {
                    onDone(null, "HTTP 失败 [" + wex.Status + "]: " + wex.Message);
                }
                catch (Exception ex)
                {
                    onDone(null, "读取失败: " + ex.Message);
                }
            }, null);
        }

        public static void PostForm(string url, string formBody, string referer, Action<HttpResult> onDone)
        {
            PostForm(url, formBody, referer, null, null, onDone);
        }

        public static void PostForm(string url, string formBody, string referer,
                                    Dictionary<string, string> headers, string userAgent,
                                    Action<HttpResult> onDone)
        {
            if (string.IsNullOrEmpty(url))
            {
                onDone(Fail("URL 为空"));
                return;
            }

            HttpWebRequest request;
            try
            {
                request = (HttpWebRequest)WebRequest.Create(CreateUri(url));
                request.Method = "POST";
                request.ContentType = "application/x-www-form-urlencoded; charset=utf-8";
                request.AllowReadStreamBuffering = true;
                request.UserAgent = string.IsNullOrEmpty(userAgent) ? UserAgent : userAgent;
                ApplyCommonHeaders(request, referer);
                if (headers != null)
                {
                    foreach (KeyValuePair<string, string> pair in headers)
                    {
                        TrySetHeader(request, pair.Key, pair.Value);
                    }
                }
            }
            catch (Exception ex)
            {
                onDone(Fail("创建请求失败: " + ex.Message));
                return;
            }

            byte[] payload = Encoding.UTF8.GetBytes(formBody ?? "");

            request.BeginGetRequestStream(delegate(IAsyncResult ar)
            {
                try
                {
                    using (Stream stream = request.EndGetRequestStream(ar))
                    {
                        stream.Write(payload, 0, payload.Length);
                    }
                }
                catch (Exception ex)
                {
                    onDone(Fail("写请求体失败: " + ex.Message));
                    return;
                }

                request.BeginGetResponse(delegate(IAsyncResult ar2)
                {
                    HttpResult result = new HttpResult();
                    try
                    {
                        HttpWebResponse response = (HttpWebResponse)request.EndGetResponse(ar2);
                        using (Stream stream = response.GetResponseStream())
                        {
                            using (StreamReader reader = new StreamReader(stream, Encoding.UTF8))
                            {
                                result.Body = reader.ReadToEnd();
                            }
                        }
                        result.SetCookie = ReadSetCookie(response);
                        response.Close();
                    }
                    catch (WebException wex)
                    {
                        result.Body = ReadWebExceptionBody(wex);
                        if (string.IsNullOrEmpty(result.Body))
                        {
                            result.Error = "HTTP 失败 [" + wex.Status + DescribeStatus(wex.Status) + "]: " + wex.Message;
                        }
                    }
                    catch (Exception ex)
                    {
                        result.Error = "读取响应失败: " + ex.Message;
                    }
                    onDone(result);
                }, null);
            }, null);
        }

        private static HttpResult Fail(string error)
        {
            HttpResult result = new HttpResult();
            result.Error = error;
            return result;
        }

        private static string ReadSetCookie(HttpWebResponse response)
        {
            StringBuilder sb = new StringBuilder();

            try
            {
                string[] keys = response.Headers.AllKeys;
                for (int i = 0; i < keys.Length; i++)
                {
                    if (string.Compare(keys[i], "Set-Cookie", StringComparison.OrdinalIgnoreCase) != 0)
                    {
                        continue;
                    }
                    string value = response.Headers[keys[i]];
                    if (!string.IsNullOrEmpty(value))
                    {
                        sb.Append(value).Append('\n');
                    }
                }
            }
            catch (Exception)
            {
            }

            try
            {
                foreach (Cookie cookie in response.Cookies)
                {
                    if (string.IsNullOrEmpty(cookie.Name) || string.IsNullOrEmpty(cookie.Value))
                    {
                        continue;
                    }
                    sb.Append(cookie.Name).Append('=').Append(cookie.Value).Append('\n');
                }
            }
            catch (Exception)
            {
            }

            return sb.ToString();
        }

        private static Uri CreateUri(string url)
        {
            try
            {
                return new Uri(url, UriKind.Absolute);
            }
            catch (Exception)
            {
                string encoded = url.Replace("[", "%5B").Replace("]", "%5D");
                return new Uri(encoded, UriKind.Absolute);
            }
        }

        private static void ApplyCommonHeaders(HttpWebRequest request, string referer)
        {
            TrySetHeader(request, "Accept", "application/json, text/plain, */*");
            TrySetHeader(request, "Referer", string.IsNullOrEmpty(referer) ? Referer : referer);
            TrySetHeader(request, "Origin", Origin);

            try
            {
                request.CookieContainer = BiliSession.GetContainer();
            }
            catch (Exception)
            {
            }
        }

        private static void TrySetHeader(HttpWebRequest request, string name, string value)
        {
            if (string.IsNullOrEmpty(value))
            {
                return;
            }
            try
            {
                request.Headers[name] = value;
                RecordHeader(name, "OK");
            }
            catch (Exception ex)
            {
                RecordHeader(name, "失败(" + ex.GetType().Name + ")");
            }
        }

        private static void RecordHeader(string name, string result)
        {
            lock (HeaderLock)
            {
                HeaderResults[name] = result;

                StringBuilder sb = new StringBuilder();
                foreach (KeyValuePair<string, string> pair in HeaderResults)
                {
                    if (sb.Length > 0)
                    {
                        sb.Append(", ");
                    }
                    sb.Append(pair.Key).Append('=').Append(pair.Value);
                }
                HeaderSupport = sb.ToString();
            }
        }
    }
}
