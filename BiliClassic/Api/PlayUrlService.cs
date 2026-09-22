using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class PlayUrl
    {
        public List<string> Urls = new List<string>();

        public List<string> DashVideoUrls = new List<string>();

        public List<string> DashAudioUrls = new List<string>();

        public List<int> AcceptQuality = new List<int>();

        public double DurationSeconds;

        public int Quality;

        public string Error = "";

        public bool Ok
        {
            get { return Error.Length == 0 && Urls.Count > 0; }
        }

        public bool DashOk
        {
            get
            {
                return Error.Length == 0
                    && DashVideoUrls.Count > 0
                    && DashAudioUrls.Count > 0;
            }
        }
    }

    // 【重要】未登录必须带 try_look=1 才有 64/80 档；DASH 只有 pc 平台给地址，喵
    public static class PlayUrlService
    {
        public const string Endpoint = "https://api.bilibili.com/x/player/wbi/playurl";

        private static readonly Regex CodeRegex = new Regex(@"""code"":\s*(-?\d+)");
        private static readonly Regex DurlRegex = new Regex(@"""durl""");
        private static readonly Regex UrlRegex = new Regex(@"""url""\s*:\s*""([^""]+)""");

        private static readonly Regex AnyUrlRegex = new Regex(@"""(https?://[^""]+)""");

        private static readonly Regex BaseUrlRegex =
            new Regex(@"""(?:baseUrl|base_url)"":\s*""([^""]+)""");

        private static readonly Regex BackupRegex =
            new Regex(@"""(?:backupUrl|backup_url)"":\s*\[([^\]]*)\]");

        private static readonly Regex TrackIdRegex = new Regex(@"""id"":\s*(\d+)");

        private static readonly Regex CodecsRegex = new Regex(@"""codecs"":\s*""([^""]+)""");

        private static readonly Regex AcceptQualityRegex =
            new Regex(@"""accept_quality"":\s*\[([^\]]*)\]");

        private const int DashWindow = 30000;

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
                parameters["qn"] = AppSettings.PlayQuality.ToString();
                parameters["fnval"] = "1";
                parameters["fnver"] = "0";
                parameters["platform"] = platform;
                parameters["voice_balance"] = "1";
                parameters["gaia_source"] = "pre-load";
                parameters["isGaiaAvoided"] = "true";

                parameters["try_look"] = "1";

                if (platform == "html5" && AppSettings.PlayQuality >= 80)
                {
                    parameters["high_quality"] = "1";
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

            ParseAccept(body, result);

            Match quality = Regex.Match(JsonText.Head(body, 300), @"""quality"":\s*(\d+)");
            if (quality.Success)
            {
                int value;
                if (int.TryParse(quality.Groups[1].Value, out value))
                {
                    result.Quality = value;
                }
            }

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

            result.Urls.Sort(delegate(string a, string b)
            {
                int ra = IsOfficialCdn(a) ? 0 : 1;
                int rb = IsOfficialCdn(b) ? 0 : 1;
                return ra.CompareTo(rb);
            });
        }

        private static void ParseAccept(string body, PlayUrl result)
        {
            result.AcceptQuality.Clear();

            Match array = AcceptQualityRegex.Match(body);
            if (!array.Success)
            {
                return;
            }

            MatchCollection numbers = Regex.Matches(array.Groups[1].Value, @"[0-9]+");
            foreach (Match number in numbers)
            {
                int qn;
                if (!int.TryParse(number.Value, out qn) || qn == 6)
                {
                    continue;
                }
                if (!result.AcceptQuality.Contains(qn))
                {
                    result.AcceptQuality.Add(qn);
                }
            }
        }

        public static string Describe(int quality)
        {
            switch (quality)
            {
                case 6: return "240P";
                case 16: return "360P";
                case 32: return "480P";
                case 64: return "720P";
                case 80: return "1080P";
                case 112: return "1080P+";
                case 120: return "4K";
            }
            return quality + "P";
        }

        private static bool IsOfficialCdn(string url)
        {
            if (url.IndexOf("mcdn", StringComparison.OrdinalIgnoreCase) >= 0
                || url.IndexOf("mountaintoys", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                return false;
            }
            return url.IndexOf("bilivideo", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        private static void CollectUrls(string region, List<string> urls)
        {
            Match primary = UrlRegex.Match(region);
            if (primary.Success)
            {
                AddUrl(urls, Normalize(primary.Groups[1].Value));
            }

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

        // 【重要】DASH 得自己带 Referer，走 DashSource / LocalProxy，喵
        public static void FetchDash(string bvid, string cid, Action<PlayUrl> onDone)
        {
            PlayUrl result = new PlayUrl();

            if (string.IsNullOrEmpty(bvid) || string.IsNullOrEmpty(cid))
            {
                result.Error = "缺少 bvid 或 cid";
                onDone(result);
                return;
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
                parameters["qn"] = AppSettings.PlayQuality.ToString();
                parameters["fnval"] = "16";
                parameters["fnver"] = "0";
                parameters["platform"] = "pc";
                parameters["try_look"] = "1";
                parameters["voice_balance"] = "1";
                parameters["gaia_source"] = "pre-load";
                parameters["isGaiaAvoided"] = "true";

                string url = WbiSigner.Shared.SignUrl(Endpoint, parameters);

                Http.GetText(url, Http.Referer, delegate(HttpResult http)
                {
                    try
                    {
                        if (http == null || string.IsNullOrEmpty(http.Body))
                        {
                            result.Error = (http == null || string.IsNullOrEmpty(http.Error))
                                ? "取DASH地址失败：无响应"
                                : "取DASH地址失败: " + http.Error;
                        }
                        else
                        {
                            ParseDash(http.Body, result);
                        }
                    }
                    catch (Exception ex)
                    {
                        result.Error = "解析DASH失败: " + ex.Message;
                    }
                    onDone(result);
                });
            });
        }

        public static void FetchBangumi(long aid, long cid, int seasonType, Action<PlayUrl> onDone)
        {
            PlayUrl result = new PlayUrl();
            if (aid <= 0 || cid <= 0)
            {
                result.Error = "缺少 aid 或 cid";
                onDone(result);
                return;
            }

            string url = "https://api.bilibili.com/pgc/player/web/playurl"
                + "?aid=" + aid
                + "&cid=" + cid
                + "&qn=" + AppSettings.PlayQuality
                + "&fnval=16"
                + "&fnver=0"
                + "&season_type=" + (seasonType > 0 ? seasonType : 1)
                + "&platform=pc";

            Http.GetText(url, "https://www.bilibili.com/", delegate(HttpResult http)
            {
                try
                {
                    if (string.IsNullOrEmpty(http.Body))
                    {
                        result.Error = string.IsNullOrEmpty(http.Error)
                            ? "取番剧地址失败：无响应"
                            : "取番剧地址失败: " + http.Error;
                    }
                    else
                    {
                        ParseDash(http.Body, result);
                        if (!result.DashOk)
                        {
                            result.Error = "";
                            Parse(http.Body, result);
                        }
                    }
                }
                catch (Exception ex)
                {
                    result.Error = "解析番剧地址失败: " + ex.Message;
                }
                onDone(result);
            });
        }

        private static void ParseDash(string body, PlayUrl result)
        {
            Match code = CodeRegex.Match(body);
            if (code.Success && code.Groups[1].Value != "0")
            {
                string message = JsonText.ReadString(body, "message");
                result.Error = "接口返回 code=" + code.Groups[1].Value
                    + (message.Length > 0 ? " " + message : "");
                return;
            }

            Match quality = Regex.Match(JsonText.Head(body, 300), @"""quality"":\s*(\d+)");
            if (quality.Success)
            {
                int value;
                if (int.TryParse(quality.Groups[1].Value, out value))
                {
                    result.Quality = value;
                }
            }

            ParseAccept(body, result);

            Match length = Regex.Match(JsonText.Head(body, 300), @"""timelength"":\s*(\d+)");
            if (length.Success)
            {
                long ms;
                if (long.TryParse(length.Groups[1].Value, out ms) && ms > 0)
                {
                    result.DurationSeconds = ms / 1000.0;
                }
            }

            int dashStart = body.IndexOf("\"dash\":{", StringComparison.Ordinal);
            if (dashStart < 0)
            {
                result.Error = "响应里没有 dash（服务端没给DASH，或该视频需要登录）";
                return;
            }
            string dash = body.Substring(dashStart);

            int dashEnd = dash.IndexOf("\"support_formats\"", StringComparison.Ordinal);
            if (dashEnd > 0)
            {
                dash = dash.Substring(0, dashEnd);
            }

            if (result.DurationSeconds <= 0)
            {
                Match duration = Regex.Match(dash, @"""duration"":\s*(\d+)");
                if (duration.Success)
                {
                    long seconds;
                    if (long.TryParse(duration.Groups[1].Value, out seconds) && seconds > 0)
                    {
                        result.DurationSeconds = seconds;
                    }
                }
            }

            MatchCollection entries = TrackIdRegex.Matches(dash);
            if (entries.Count == 0)
            {
                result.Error = "dash里没有轨道id";
                return;
            }

            int want = AppSettings.PlayQuality;
            List<int> ids = new List<int>();
            List<string> codecsList = new List<string>();
            List<List<string>> videoTracks = new List<List<string>>();
            List<List<string>> audioTracks = new List<List<string>>();
            List<string> audioCodecs = new List<string>();

            for (int i = 0; i < entries.Count; i++)
            {
                int start = entries[i].Index;
                int end = (i + 1 < entries.Count) ? entries[i + 1].Index : dash.Length;
                if (end - start > DashWindow)
                {
                    end = start + DashWindow;
                }
                string window = dash.Substring(start, end - start);

                string codecs = FirstGroup(CodecsRegex, window);
                bool isVideo = IsVideoCodec(codecs);
                bool isAudio = codecs.StartsWith("mp4a", StringComparison.OrdinalIgnoreCase);
                if (!isVideo && !isAudio)
                {
                    if (window.IndexOf("\"width\":", StringComparison.Ordinal) >= 0)
                    {
                        isVideo = true;
                    }
                    else if (window.IndexOf("audio/", StringComparison.Ordinal) >= 0)
                    {
                        isAudio = true;
                    }
                }

                if (!isVideo && !isAudio)
                {
                    continue;
                }

                int id;
                if (!int.TryParse(entries[i].Groups[1].Value, out id))
                {
                    continue;
                }

                Match baseUrl = BaseUrlRegex.Match(window);
                if (!baseUrl.Success)
                {
                    continue;
                }

                List<string> urls = CollectTrackUrls(baseUrl.Groups[1].Value, window);
                if (urls.Count == 0)
                {
                    continue;
                }

                if (isVideo)
                {
                    ids.Add(id);
                    codecsList.Add(codecs);
                    videoTracks.Add(urls);
                }
                else
                {
                    audioTracks.Add(urls);
                    audioCodecs.Add(codecs);
                }
            }

            if (videoTracks.Count == 0 || audioTracks.Count == 0)
            {
                result.Error = "dash里没有可用的视频轨或音频轨";
                return;
            }

            int bestId = SelectQn(ids, want);
            result.Quality = bestId;
            result.DashVideoUrls = SelectVideo(videoTracks, ids, codecsList, bestId);
            result.DashAudioUrls = SelectAudio(audioTracks, audioCodecs);
        }

        private static List<string> SelectAudio(List<List<string>> tracks, List<string> codecs)
        {
            for (int i = 0; i < tracks.Count; i++)
            {
                if (codecs[i].StartsWith("mp4a.40.2", StringComparison.OrdinalIgnoreCase))
                {
                    return tracks[i];
                }
            }
            return tracks[0];
        }

        private static int SelectQn(List<int> ids, int target)
        {
            int best = -1;
            for (int i = 0; i < ids.Count; i++)
            {
                int id = ids[i];
                if (id == target)
                {
                    return target;
                }
                if (id < target && id > best)
                {
                    best = id;
                }
            }

            if (best < 0)
            {
                for (int i = 0; i < ids.Count; i++)
                {
                    if (best < 0 || ids[i] < best)
                    {
                        best = ids[i];
                    }
                }
            }
            return best;
        }

        private static List<string> SelectVideo(List<List<string>> tracks, List<int> ids,
                                                List<string> codecs, int bestId)
        {
            List<string> fallback = null;
            List<string> hevc = null;

            for (int i = 0; i < tracks.Count; i++)
            {
                if (ids[i] != bestId)
                {
                    continue;
                }

                if (fallback == null)
                {
                    fallback = tracks[i];
                }

                string c = codecs[i];
                if (c.StartsWith("avc1", StringComparison.OrdinalIgnoreCase)
                    || c.StartsWith("avc3", StringComparison.OrdinalIgnoreCase))
                {
                    return tracks[i];
                }
                if ((c.StartsWith("hev1", StringComparison.OrdinalIgnoreCase)
                    || c.StartsWith("hvc1", StringComparison.OrdinalIgnoreCase)) && hevc == null)
                {
                    hevc = tracks[i];
                }
            }

            return hevc != null ? hevc : fallback;
        }

        private static bool IsVideoCodec(string codecs)
        {
            return codecs.StartsWith("avc1", StringComparison.OrdinalIgnoreCase)
                || codecs.StartsWith("avc3", StringComparison.OrdinalIgnoreCase)
                || codecs.StartsWith("hev1", StringComparison.OrdinalIgnoreCase)
                || codecs.StartsWith("hvc1", StringComparison.OrdinalIgnoreCase);
        }

        private static List<string> CollectTrackUrls(string baseUrl, string window)
        {
            List<string> urls = new List<string>();
            AddUrl(urls, Normalize(baseUrl));

            Match backup = BackupRegex.Match(window);
            if (backup.Success)
            {
                MatchCollection matches = AnyUrlRegex.Matches(backup.Groups[1].Value);
                foreach (Match match in matches)
                {
                    AddUrl(urls, Normalize(match.Groups[1].Value));
                }
            }

            urls.Sort(delegate(string a, string b)
            {
                int ra = IsOfficialCdn(a) ? 0 : 1;
                int rb = IsOfficialCdn(b) ? 0 : 1;
                return ra.CompareTo(rb);
            });
            return urls;
        }

        private static string FirstGroup(Regex regex, string text)
        {
            Match match = regex.Match(text);
            return match.Success ? match.Groups[1].Value : "";
        }

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
