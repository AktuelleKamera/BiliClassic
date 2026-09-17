using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class PlayUrl
    {
        /// <summary>候选播放地址，按优先级排列，正式CDN在前</summary>
        public List<string> Urls = new List<string>();

        public string Error = "";

        public bool Ok
        {
            get { return Error.Length == 0 && Urls.Count > 0; }
        }
    }

    /// <summary>
    /// 取播放地址，需WBI签名
    /// fnval=1取muxed MP4，DASH在WP7上做不了
    /// html5无referer鉴权，故优先
    /// </summary>
    public static class PlayUrlService
    {
        public const string Endpoint = "https://api.bilibili.com/x/player/wbi/playurl";

        /// <summary>360P，未登录只有这个清晰度</summary>
        public const string Quality = "16";

        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");
        private static readonly Regex DurlRegex = new Regex(@"""durl""");
        private static readonly Regex UrlRegex = new Regex(@"""url""\s*:\s*""([^""]+)""");

        /// <summary>捞backup_url里的地址</summary>
        private static readonly Regex AnyUrlRegex = new Regex(@"""(https?://[^""]+)""");

        /// <summary>
        /// 取播放地址，platform决定返回哪套CDN
        /// 回调不在UI线程
        /// </summary>
        public static void Fetch(string bvid, string cid, string platform, Action<PlayUrl> onDone)
        {
            PlayUrl result = new PlayUrl();

            if (string.IsNullOrEmpty(bvid) || string.IsNullOrEmpty(cid))
            {
                result.Error = "缺少 bvid 或 cid";
                onDone(result);
                return;
            }

            if (string.IsNullOrEmpty(platform))
            {
                platform = "pc";
            }

            WbiSigner.EnsureReady(delegate(string keyError)
            {
                if (!string.IsNullOrEmpty(keyError))
                {
                    result.Error = "WBI 密钥获取失败 → " + keyError;
                    onDone(result);
                    return;
                }

                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters["bvid"] = bvid;
                parameters["cid"] = cid;
                parameters["qn"] = Quality;
                parameters["fnval"] = "1";
                parameters["fnver"] = "0";
                parameters["platform"] = platform;
                parameters["voice_balance"] = "1";
                parameters["gaia_source"] = "pre-load";
                parameters["isGaiaAvoided"] = "true";

                if (platform == "html5")
                {
                    // html5专有参数
                    // high_quality=1 画质到1080P，仅html5有效
                    // try_look=1 未登录也能拿720P/1080P
                    // 拿不到会自动降级，加上无坏处
                    parameters["high_quality"] = "1";
                    parameters["try_look"] = "1";
                }

                string url = WbiSigner.Shared.SignUrl(Endpoint, parameters);

                Http.GetText(url, Http.Referer, delegate(HttpResult http)
                {
                    try
                    {
                        if (http == null || string.IsNullOrEmpty(http.Body))
                        {
                            result.Error = (http == null || string.IsNullOrEmpty(http.Error))
                                ? "取播放地址失败：无响应"
                                : "取播放地址失败: " + http.Error;
                        }
                        else
                        {
                            Parse(http.Body, result);
                        }
                    }
                    catch (Exception ex)
                    {
                        result.Error = "解析播放地址失败: " + ex.Message;
                    }
                    onDone(result);
                });
            });
        }

        private static void Parse(string body, PlayUrl result)
        {
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = JsonText.ReadString(body, "message");
                result.Error = "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
                return;
            }

            // 只在"durl"之后找地址
            // 响应里dash/accept_*也有url字段，不限位会取错
            Match durl = DurlRegex.Match(body);
            if (!durl.Success)
            {
                result.Error = "响应里没有 durl（服务端返回的是 DASH，或该视频需要登录）";
                return;
            }

            CollectUrls(body.Substring(durl.Index), result.Urls);

            if (result.Urls.Count == 0)
            {
                result.Error = "durl 里没有可用地址";
                return;
            }

            // 正式CDN排前面
            // B站会把PCDN节点塞进主地址，随机域名+非标准端口，经常连不上
            result.Urls.Sort(delegate(string a, string b)
            {
                int ra = IsOfficialCdn(a) ? 0 : 1;
                int rb = IsOfficialCdn(b) ? 0 : 1;
                return ra.CompareTo(rb);
            });
        }

        private static bool IsOfficialCdn(string url)
        {
            return url.IndexOf("bilivideo", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        /// <summary>收集主地址+backup_url全部地址，去重</summary>
        private static void CollectUrls(string region, List<string> urls)
        {
            Match primary = UrlRegex.Match(region);
            if (primary.Success)
            {
                AddUrl(urls, Normalize(primary.Groups[1].Value));
            }

            // backup_url是字符串数组，用]卡边界，免得捞进后面字段的地址
            int start = region.IndexOf("\"backup_url\":[", StringComparison.Ordinal);
            if (start < 0)
            {
                return;
            }

            int end = region.IndexOf(']', start);
            string array = end > start
                ? region.Substring(start, end - start)
                : region.Substring(start);

            MatchCollection matches = AnyUrlRegex.Matches(array);
            foreach (Match match in matches)
            {
                AddUrl(urls, Normalize(match.Groups[1].Value));
            }
        }

        /// <summary>
        /// 加地址，同时补http变体
        /// WP7的MediaElement对https流偶有画面出不来，两个协议CDN都给
        /// </summary>
        private static void AddUrl(List<string> urls, string url)
        {
            if (string.IsNullOrEmpty(url))
            {
                return;
            }

            if (!urls.Contains(url))
            {
                urls.Add(url);
            }

            if (url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                string plain = "http://" + url.Substring(8);
                if (!urls.Contains(plain))
                {
                    urls.Add(plain);
                }
            }
        }

        /// <summary>还原JSON转义，直链里的&amp;常写成\u0026，不还原会截断URL</summary>
        private static string Normalize(string raw)
        {
            string value = JsonText.Unescape(raw).Trim();
            if (value.StartsWith("//", StringComparison.Ordinal))
            {
                value = "https:" + value;
            }
            return value;
        }
    }
}
