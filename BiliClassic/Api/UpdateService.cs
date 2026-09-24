using System;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    public sealed class UpdateInfo
    {
        public bool HasUpdate;
        public bool ForceUpdate;
        public string Latest = "";
        public string Changelog = "";
        public string DownloadUrl = "";
        public string Error = "";
    }

    public static class UpdateService
    {
        private const string ManifestUrl = "http://www.biliclassic.cn/wp/api/version.json";

        public const string VersionNumber = "0.3.0.0";

        public const string DisplayVersion = "0.3.0";

        public const int VersionCode = 300;

        public static void Check(Action<UpdateInfo> onDone)
        {
            UpdateInfo info = new UpdateInfo();

            Http.GetText(ManifestUrl, Http.Referer, delegate(HttpResult http)
            {
                if (http == null || string.IsNullOrEmpty(http.Body))
                {
                    info.Error = string.IsNullOrEmpty(http.Error) ? "没有返回内容" : http.Error;
                    onDone(info);
                    return;
                }

                try
                {
                    string body = http.Body;
                    info.Latest = JsonText.ReadString(body, "version");
                    info.DownloadUrl = JsonText.ReadString(body, "download_url");
                    info.Changelog = ReadChangelog(body);
                    info.ForceUpdate = Regex.IsMatch(body, @"""force_update""\s*:\s*true");
                    info.HasUpdate = ReadNumber(body, "version_code") > VersionCode;

                    if (info.Latest.Length == 0)
                    {
                        info.Error = "清单里没有version字段";
                    }
                }
                catch (Exception ex)
                {
                    info.Error = ex.Message;
                }

                onDone(info);
            });
        }

        private static string ReadChangelog(string json)
        {
            Match array = Regex.Match(json, @"""changelog""\s*:\s*\[(.*?)\]",
                RegexOptions.Singleline);
            if (!array.Success)
            {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            foreach (Match line in Regex.Matches(array.Groups[1].Value,
                @"""((?:[^""\\]|\\.)*)"""))
            {
                if (sb.Length > 0)
                {
                    sb.Append('\n');
                }
                sb.Append(JsonText.Unescape(line.Groups[1].Value));
            }
            return sb.ToString();
        }

        private static long ReadNumber(string json, string field)
        {
            Match m = Regex.Match(json, @"""" + field + @""":\s*(\d+)");
            if (!m.Success)
            {
                return 0;
            }

            long value;
            return long.TryParse(m.Groups[1].Value, out value) ? value : 0;
        }
    }
}
