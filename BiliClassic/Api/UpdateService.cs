using System;
using System.Text;
using System.Text.RegularExpressions;

namespace BiliClassic.Api
{
    /// <summary>更新检查结果</summary>
    public sealed class UpdateInfo
    {
        public bool HasUpdate;
        public bool ForceUpdate;
        public string Latest = "";
        public string Changelog = "";
        public string DownloadUrl = "";
        public string Error = "";
    }

    /// <summary>
    /// 更新检查
    /// 清单是扁平格式：version / version_code / download_url / force_update / changelog
    /// 只提示不下载
    /// </summary>
    public static class UpdateService
    {
        private const string ManifestUrl = "http://www.biliclassic.cn/wp/api/version.json";

        /// <summary>
        /// 完整版本，四段
        /// 改版本时和AssemblyInfo一起改
        /// </summary>
        public const string VersionNumber = "0.1.0.0";

        /// <summary>显示用版本，前三段</summary>
        public const string DisplayVersion = "0.1.0";

        /// <summary>
        /// 版本代码，和服务端version_code同一套编码
        /// major*1000+minor*100+build*10+revision
        /// </summary>
        public const int VersionCode = 100;

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

                    // 不是清单（比如撞上错误页）时给原因
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

        /// <summary>changelog是字符串数组，逐条取出</summary>
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
